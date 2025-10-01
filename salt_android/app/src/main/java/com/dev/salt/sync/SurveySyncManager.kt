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
    private val appServerConfigDao = database.appServerConfigDao()

    // Get server configuration from global settings
    private fun getServerConfig(): Pair<String, String>? {
        val config = appServerConfigDao.getServerConfig()

        return if (config != null) {
            Log.d("SurveySyncManager", "Using server config: URL=${config.serverUrl}, API Key=${config.apiKey.take(10)}...")
            Pair(config.serverUrl, config.apiKey)
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

    suspend fun checkForSurveyUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val config = getServerConfig()
            if (config == null) {
                Log.e("SurveySyncManager", "Cannot check for updates: No server configuration")
                return@withContext false
            }

            val (serverUrl, apiKey) = config
            val url = URL("$serverUrl/api/sync/survey/version")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-API-Key", apiKey)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)

                if (jsonResponse.getString("status") == "success") {
                    val data = jsonResponse.getJSONObject("data")
                    val serverChecksum = data.optString("checksum", "")
                    val serverLastModified = data.optLong("last_modified", 0)

                    // Get current local checksum from sync metadata
                    val syncMetadata = syncMetadataDao.getSyncMetadata()
                    val localChecksum = syncMetadata?.surveyChecksum ?: ""

                    // Compare checksums to determine if update is needed
                    val needsUpdate = serverChecksum.isNotEmpty() && serverChecksum != localChecksum

                    Log.d("SurveySyncManager", "Version check: serverChecksum=$serverChecksum, localChecksum=$localChecksum, needsUpdate=$needsUpdate")
                    return@withContext needsUpdate
                } else {
                    Log.e("SurveySyncManager", "Server returned error status")
                    return@withContext false
                }
            } else {
                Log.e("SurveySyncManager", "Server returned ${connection.responseCode}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("SurveySyncManager", "Error checking for survey updates", e)
            return@withContext false
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

                // Extract and save checksum from metadata
                val checksum = surveyData.optJSONObject("metadata")?.optString("checksum") ?: ""

                // Update sync metadata with checksum and success status
                syncMetadataDao.updateSurveyChecksum(checksum)
                syncMetadataDao.updateSyncStatus("SUCCESS")
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
                // Parse survey metadata including eligibility script
                var eligibilityScript: String? = null
                if (data.has("survey")) {
                    val surveyJson = data.getJSONObject("survey")
                    eligibilityScript = if (surveyJson.has("eligibility_script")) {
                        surveyJson.getString("eligibility_script")
                    } else {
                        null
                    }
                    Log.d("SurveySyncManager", "Parsed eligibility script: $eligibilityScript")
                }

                // Parse survey configuration (including eligibility script)
                if (data.has("survey_config")) {
                    val configJson = data.getJSONObject("survey_config")
                    val surveyConfig = SurveyConfig(
                        surveyName = configJson.optString("survey_name", null),
                        fingerprintEnabled = configJson.optBoolean("fingerprint_enabled", false),
                        reEnrollmentDays = configJson.optInt("re_enrollment_days", 90),
                        lastSyncTime = System.currentTimeMillis(),
                        eligibilityScript = eligibilityScript,  // Store eligibility script in SurveyConfig
                        hivRapidTestEnabled = configJson.optBoolean("hiv_rapid_test_enabled", false)
                    )
                    database.surveyConfigDao().insertSurveyConfig(surveyConfig)
                    Log.d("SurveySyncManager", "Survey config updated: fingerprint=${surveyConfig.fingerprintEnabled}, reEnrollmentDays=${surveyConfig.reEnrollmentDays}, eligibilityScript=${surveyConfig.eligibilityScript}")
                }
                
                // Parse and save sections
                if (data.has("sections")) {
                    // Clear existing sections
                    database.sectionDao().deleteAllSections()

                    val sectionsArray = data.getJSONArray("sections")
                    for (i in 0 until sectionsArray.length()) {
                        val sectionJson = sectionsArray.getJSONObject(i)
                        val section = Section(
                            id = sectionJson.getInt("id"),
                            surveyId = sectionJson.getInt("survey_id"),
                            sectionIndex = sectionJson.getInt("section_index"),
                            sectionType = sectionJson.getString("section_type"),
                            name = sectionJson.getString("name"),
                            description = sectionJson.optString("description", null)
                        )
                        database.sectionDao().insertSection(section)
                        Log.d("SurveySyncManager", "Inserted section: ${section.name} (type: ${section.sectionType})")
                    }
                }

                // Map to track original question ID to language-specific question IDs
                val questionIdMap = mutableMapOf<Pair<Int, String>, Int>() // (originalId, language) -> newId

                // Parse questions
                val questionsArray = data.getJSONArray("questions")
                for (i in 0 until questionsArray.length()) {
                    val questionJson = questionsArray.getJSONObject(i)
                    val originalQuestionId = questionJson.getInt("id")
                    
                    // Check if we have multilingual support
                    if (questionJson.has("question_text_json")) {
                        val textJson = questionJson.getJSONObject("question_text_json")
                        val audioJson = if (questionJson.has("audio_files_json")) {
                            questionJson.getJSONObject("audio_files_json")
                        } else null
                        
                        // Create a question for each language
                        val languages = textJson.keys()
                        var questionIdCounter = originalQuestionId * 1000 // Multiply to avoid ID conflicts
                        
                        while (languages.hasNext()) {
                            val language = languages.next()
                            val questionText = textJson.getString(language)
                            
                            // Handle audio files for this language
                            var audioFileName = ""
                            if (audioJson != null && audioJson.has(language)) {
                                try {
                                    val audioData = audioJson.optString(language, "")
                                    if (audioData.isNotEmpty()) {
                                        audioFileName = saveAudioFromBase64(audioData)
                                    }
                                } catch (e: Exception) {
                                    Log.e("SurveySyncManager", "Error processing $language audio", e)
                                }
                            }
                            
                            val newQuestionId = questionIdCounter++
                            questionIdMap[Pair(originalQuestionId, language)] = newQuestionId
                            
                            val question = Question(
                                id = newQuestionId,
                                questionId = questionJson.getInt("question_index"),  // Use question_index for ordering
                                questionShortName = questionJson.optString("short_name", "q${originalQuestionId}"),
                                statement = questionText,
                                audioFileName = audioFileName,
                                questionLanguage = language,  // Use the actual language name
                                primaryLanguageText = questionText,
                                questionType = questionJson.optString("question_type", "multiple_choice"),
                                preScript = questionJson.optString("pre_script", null),
                                validationScript = questionJson.optString("validation_script", null),
                                validationErrorText = "Invalid Answer",
                                minSelections = if (questionJson.has("min_selections") && !questionJson.isNull("min_selections"))
                                    questionJson.getInt("min_selections") else null,
                                maxSelections = if (questionJson.has("max_selections") && !questionJson.isNull("max_selections"))
                                    questionJson.getInt("max_selections") else null,
                                skipToScript = questionJson.optString("skip_to_script", null),
                                skipToTarget = questionJson.optString("skip_to_target", null),
                                sectionId = if (questionJson.has("section_id") && !questionJson.isNull("section_id"))
                                    questionJson.getInt("section_id") else null
                            )
                            surveyDao.insertQuestion(question)
                        }
                    } else {
                        // Fallback for non-multilingual questions
                        val questionText = questionJson.optString("question_text", "Question $i")
                        questionIdMap[Pair(originalQuestionId, "English")] = originalQuestionId
                        
                        val question = Question(
                            id = originalQuestionId,
                            questionId = questionJson.getInt("question_index"),  // Use question_index for ordering
                            questionShortName = questionJson.optString("short_name", "q${originalQuestionId}"),
                            statement = questionText,
                            audioFileName = "",
                            questionLanguage = "English",  // Default to English
                            primaryLanguageText = questionText,
                            questionType = questionJson.optString("question_type", "multiple_choice"),
                            preScript = questionJson.optString("pre_script", null),
                            validationScript = questionJson.optString("validation_script", null),
                            validationErrorText = "Invalid Answer",
                            minSelections = if (questionJson.has("min_selections") && !questionJson.isNull("min_selections"))
                                questionJson.getInt("min_selections") else null,
                            maxSelections = if (questionJson.has("max_selections") && !questionJson.isNull("max_selections"))
                                questionJson.getInt("max_selections") else null,
                            skipToScript = questionJson.optString("skip_to_script", null),
                            skipToTarget = questionJson.optString("skip_to_target", null),
                            sectionId = if (questionJson.has("section_id") && !questionJson.isNull("section_id"))
                                questionJson.getInt("section_id") else null
                        )
                        surveyDao.insertQuestion(question)
                    }
                }
                
                // Parse options
                val optionsArray = data.getJSONArray("options")
                for (i in 0 until optionsArray.length()) {
                    val optionJson = optionsArray.getJSONObject(i)
                    val originalQuestionId = optionJson.getInt("question_id")
                    
                    // Check if we have multilingual support
                    if (optionJson.has("option_text_json")) {
                        val textJson = optionJson.getJSONObject("option_text_json")
                        val audioJson = if (optionJson.has("audio_files_json")) {
                            optionJson.getJSONObject("audio_files_json")
                        } else null
                        
                        // Create an option for each language
                        val languages = textJson.keys()
                        var optionIdCounter = optionJson.getInt("id") * 1000 // Multiply to avoid ID conflicts
                        
                        while (languages.hasNext()) {
                            val language = languages.next()
                            val optionText = textJson.getString(language)
                            
                            // Get the mapped question ID for this language
                            val mappedQuestionId = questionIdMap[Pair(originalQuestionId, language)] ?: originalQuestionId
                            
                            // Handle audio files for this language
                            var audioFileName = ""
                            if (audioJson != null && audioJson.has(language)) {
                                try {
                                    val audioData = audioJson.optString(language, "")
                                    if (audioData.isNotEmpty()) {
                                        audioFileName = saveAudioFromBase64(audioData)
                                    }
                                } catch (e: Exception) {
                                    Log.e("SurveySyncManager", "Error processing $language option audio", e)
                                }
                            }
                            
                            val option = Option(
                                id = optionIdCounter++,
                                questionId = mappedQuestionId,  // Use the mapped question ID
                                text = optionText,
                                audioFileName = audioFileName,
                                optionQuestionIndex = optionJson.optInt("option_index", i),
                                language = language,  // Use the actual language name
                                primaryLanguageText = optionText
                            )
                            surveyDao.insertOption(option)
                        }
                    } else {
                        // Fallback for non-multilingual options
                        val optionText = optionJson.optString("option_text", "Option $i")
                        val mappedQuestionId = questionIdMap[Pair(originalQuestionId, "English")] ?: originalQuestionId
                        
                        val option = Option(
                            id = optionJson.getInt("id"),
                            questionId = mappedQuestionId,  // Use the mapped question ID
                            text = optionText,
                            audioFileName = "",
                            optionQuestionIndex = optionJson.optInt("option_index", i),
                            language = "English",  // Default to English
                            primaryLanguageText = optionText
                        )
                        surveyDao.insertOption(option)
                    }
                }

                // Parse system messages
                if (data.has("messages")) {
                    val messagesArray = data.getJSONArray("messages")
                    val systemMessageDao = database.systemMessageDao()

                    // Clear existing messages
                    systemMessageDao.deleteAllSystemMessages()

                    for (i in 0 until messagesArray.length()) {
                        val messageJson = messagesArray.getJSONObject(i)
                        val messageKey = messageJson.getString("message_key")

                        // Check if we have multilingual support
                        if (messageJson.has("text")) {
                            val textJson = messageJson.getJSONObject("text")
                            val audioJson = if (messageJson.has("audio")) {
                                messageJson.getJSONObject("audio")
                            } else null

                            // Create a message for each language
                            val languages = textJson.keys()
                            while (languages.hasNext()) {
                                val language = languages.next()
                                val messageText = textJson.getString(language)

                                // Handle audio files for this language
                                var audioFileName: String? = null
                                if (audioJson != null && audioJson.has(language)) {
                                    try {
                                        val audioData = audioJson.optString(language, "")
                                        if (audioData.isNotEmpty()) {
                                            audioFileName = saveAudioFromBase64(audioData)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("SurveySyncManager", "Error processing message audio for $language", e)
                                    }
                                }

                                val systemMessage = SystemMessage(
                                    messageKey = messageKey,
                                    messageText = messageText,
                                    audioFileName = audioFileName,
                                    language = language,
                                    messageType = messageJson.optString("message_type", "system")
                                )
                                systemMessageDao.insertSystemMessage(systemMessage)
                                Log.d("SurveySyncManager", "Inserted system message: $messageKey ($language)")
                            }
                        }
                    }
                    Log.d("SurveySyncManager", "Inserted ${messagesArray.length()} system messages")
                }

                // Parse test configurations
                if (data.has("test_configurations")) {
                    val testConfigsArray = data.getJSONArray("test_configurations")
                    val testConfigDao = database.testConfigurationDao()

                    // Clear existing test configurations
                    testConfigDao.deleteAllTestConfigurations()

                    for (i in 0 until testConfigsArray.length()) {
                        val testConfigJson = testConfigsArray.getJSONObject(i)

                        // Get survey_id from survey object if available, otherwise use 1 as default
                        val surveyId = if (data.has("survey")) {
                            data.getJSONObject("survey").optLong("id", 1)
                        } else {
                            1L
                        }

                        val testConfiguration = TestConfiguration(
                            surveyId = surveyId,
                            testId = testConfigJson.getString("test_id"),
                            testName = testConfigJson.getString("test_name"),
                            enabled = testConfigJson.optInt("enabled", 0) == 1, // SQLite stores boolean as 0/1
                            displayOrder = testConfigJson.getInt("display_order")
                        )
                        testConfigDao.insertTestConfiguration(testConfiguration)
                        Log.d("SurveySyncManager", "Inserted test configuration: ${testConfiguration.testName} (enabled: ${testConfiguration.enabled})")
                    }
                    Log.d("SurveySyncManager", "Inserted ${testConfigsArray.length()} test configurations")
                }

                Log.d("SurveySyncManager", "Inserted ${questionsArray.length()} questions and ${optionsArray.length()} options")
            } catch (e: Exception) {
                Log.e("SurveySyncManager", "Error parsing survey data", e)
                throw e
            }
        }
    }
}