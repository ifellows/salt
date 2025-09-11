package com.dev.salt.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import com.dev.salt.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class SurveySyncManager(private val context: Context) {
    private val database = SurveyDatabase.getInstance(context)
    private val surveyDao = database.surveyDao()
    private val syncMetadataDao = database.syncMetadataDao()
    private val userDao = database.userDao()
    
    // Get server configuration from database
    private fun getServerConfig(): Pair<String, String>? {
        val config = userDao.getAnyServerConfig()
        val serverUrl = config?.uploadServerUrl?.takeIf { it.isNotBlank() }
        val apiKey = config?.uploadApiKey?.takeIf { it.isNotBlank() }
        
        return if (serverUrl != null && apiKey != null) {
            Log.d("SurveySyncManager", "Using server config: URL=$serverUrl, API Key=${apiKey.take(10)}...")
            Pair(serverUrl, apiKey)
        } else {
            Log.e("SurveySyncManager", "No server configuration found. Please configure server settings.")
            null
        }
    }
    
    init {
        // Ensure sync metadata exists
        if (syncMetadataDao.getSyncMetadata() == null) {
            syncMetadataDao.insertSyncMetadata(SyncMetadata())
        }
        
        // Ensure audio directory exists
        val audioDir = File(context.filesDir, "audio")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
    }
    
    private fun calculateMD5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private fun saveAudioFromBase64(base64Data: String?): String {
        if (base64Data.isNullOrEmpty()) return ""
        
        try {
            // Extract base64 portion if it's a data URL
            val base64Content = if (base64Data.startsWith("data:")) {
                base64Data.substringAfter("base64,")
            } else {
                base64Data
            }
            
            // Calculate MD5 hash of the content
            val hash = calculateMD5(base64Content)
            val filename = "$hash.mp3"
            val audioDir = File(context.filesDir, "audio")
            val audioFile = File(audioDir, filename)
            
            // If file already exists, no need to save again
            if (audioFile.exists()) {
                Log.d("SurveySyncManager", "Audio file already exists: $filename")
                return filename
            }
            
            // Decode and save the audio
            val audioBytes = Base64.decode(base64Content, Base64.DEFAULT)
            FileOutputStream(audioFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
                fos.fd.sync() // Force write to disk
            }
            
            Log.d("SurveySyncManager", "Saved audio file: $filename")
            return filename
            
        } catch (e: Exception) {
            Log.e("SurveySyncManager", "Error saving audio", e)
            return ""
        }
    }
    
    suspend fun downloadAndReplaceSurvey(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if server is configured
            val config = getServerConfig()
            if (config == null) {
                val errorMsg = "Server not configured. Please configure server settings first."
                syncMetadataDao.updateSyncStatus("ERROR", errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }
            
            // Update sync status to syncing
            syncMetadataDao.updateSyncStatus("SYNCING")
            
            // Download survey from server
            val surveyData = downloadSurveyFromServer()
            
            if (surveyData != null) {
                // Clear existing data
                clearExistingData()
                
                // Parse and insert new data
                insertSurveyData(surveyData)
                
                // Update sync metadata
                syncMetadataDao.updateLastSyncSuccess(System.currentTimeMillis())
                
                Result.success("Survey downloaded successfully")
            } else {
                syncMetadataDao.updateSyncStatus("ERROR", "Failed to download survey")
                Result.failure(Exception("Failed to download survey"))
            }
        } catch (e: Exception) {
            Log.e("SurveySyncManager", "Error downloading survey", e)
            syncMetadataDao.updateSyncStatus("ERROR", e.message)
            Result.failure(e)
        }
    }
    
    private fun downloadSurveyFromServer(): JSONObject? {
        return try {
            val config = getServerConfig()
            if (config == null) {
                Log.e("SurveySyncManager", "Cannot download survey: No server configuration")
                return null
            }
            
            val (serverUrl, apiKey) = config
            val url = URL("$serverUrl/api/sync/survey/download")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-API-Key", apiKey) // Add API key header
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                
                if (jsonResponse.getString("status") == "success") {
                    jsonResponse.getJSONObject("data")
                } else {
                    null
                }
            } else {
                Log.e("SurveySyncManager", "Server returned ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("SurveySyncManager", "Network error", e)
            null
        }
    }
    
    private fun clearExistingData() {
        // Clear all questions and options
        database.runInTransaction {
            // Get all questions and delete them
            val allQuestions = surveyDao.getAllQuestions()
            allQuestions.forEach { question ->
                // Delete all options for this question
                val options = surveyDao.getOptionsForQuestion(question.id)
                options.forEach { option ->
                    surveyDao.deleteOption(option)
                }
                // Delete the question
                surveyDao.deleteQuestion(question)
            }
        }
    }
    
    private fun insertSurveyData(data: JSONObject) {
        database.runInTransaction {
            try {
                // Parse questions
                val questionsArray = data.getJSONArray("questions")
                for (i in 0 until questionsArray.length()) {
                    val questionJson = questionsArray.getJSONObject(i)
                    
                    // Get question text - try different formats
                    val questionText = when {
                        questionJson.has("question_text_json") -> {
                            val textJson = questionJson.getJSONObject("question_text_json")
                            textJson.optString("English", "Question $i")
                        }
                        questionJson.has("question_text") -> questionJson.getString("question_text")
                        else -> "Question $i"
                    }
                    
                    // Handle audio files
                    var audioFileName = ""
                    if (questionJson.has("audio_files_json")) {
                        try {
                            val audioJson = questionJson.getJSONObject("audio_files_json")
                            // Try to get English audio first, then any available audio
                            val audioData = audioJson.optString("English", "")
                            if (audioData.isNotEmpty()) {
                                audioFileName = saveAudioFromBase64(audioData)
                            }
                        } catch (e: Exception) {
                            Log.e("SurveySyncManager", "Error processing question audio", e)
                        }
                    }
                    
                    val question = Question(
                        id = questionJson.getInt("id"),
                        questionId = questionJson.getInt("question_index"),  // Use question_index for ordering
                        questionShortName = questionJson.optString("short_name", "q${questionJson.getInt("id")}"),
                        statement = questionText,
                        audioFileName = audioFileName,
                        questionLanguage = "en",  // Changed to match the survey language
                        primaryLanguageText = questionText,
                        questionType = questionJson.optString("question_type", "multiple_choice"),
                        preScript = questionJson.optString("pre_script", null),
                        validationScript = questionJson.optString("validation_script", null),
                        validationErrorText = "Invalid Answer"
                    )
                    surveyDao.insertQuestion(question)
                }
                
                // Parse options
                val optionsArray = data.getJSONArray("options")
                for (i in 0 until optionsArray.length()) {
                    val optionJson = optionsArray.getJSONObject(i)
                    
                    // Get option text - try different formats
                    val optionText = when {
                        optionJson.has("option_text_json") -> {
                            val textJson = optionJson.getJSONObject("option_text_json")
                            textJson.optString("English", "Option $i")
                        }
                        optionJson.has("option_text") -> optionJson.getString("option_text")
                        else -> "Option $i"
                    }
                    
                    // Handle audio files for options
                    var audioFileName = ""
                    if (optionJson.has("audio_files_json")) {
                        try {
                            val audioJson = optionJson.getJSONObject("audio_files_json")
                            // Try to get English audio first, then any available audio
                            val audioData = audioJson.optString("English", "")
                            if (audioData.isNotEmpty()) {
                                audioFileName = saveAudioFromBase64(audioData)
                            }
                        } catch (e: Exception) {
                            Log.e("SurveySyncManager", "Error processing option audio", e)
                        }
                    }
                    
                    val option = Option(
                        id = optionJson.getInt("id"),
                        questionId = optionJson.getInt("question_id"),
                        text = optionText,
                        audioFileName = audioFileName,
                        optionQuestionIndex = optionJson.optInt("option_index", i),
                        language = "en",  // Changed to match the survey language
                        primaryLanguageText = optionText
                    )
                    surveyDao.insertOption(option)
                }
                
                Log.d("SurveySyncManager", "Inserted ${questionsArray.length()} questions and ${optionsArray.length()} options")
            } catch (e: Exception) {
                Log.e("SurveySyncManager", "Error parsing survey data", e)
                throw e
            }
        }
    }
}