package com.dev.salt.upload

import com.dev.salt.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class SerializedSurvey(
    val surveyId: String,
    val subjectId: String,
    val startDatetime: String,
    val language: String,
    val questions: List<SerializedQuestion>,
    val answers: List<SerializedAnswer>,
    val completedAt: String,
    val deviceInfo: DeviceInfo
)

data class SerializedQuestion(
    val id: Int,
    val questionId: Int,
    val questionShortName: String,
    val statement: String,
    val questionType: String,
    val options: List<SerializedOption>
)

data class SerializedOption(
    val id: Int,
    val text: String,
    val optionQuestionIndex: Int
)

data class SerializedAnswer(
    val questionId: Int,
    val questionShortName: String,
    val answerValue: Any?,
    val answerType: String, // "multiple_choice", "numeric", "text"
    val optionText: String? = null
)

data class DeviceInfo(
    val deviceId: String,
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String
)

class SurveySerializer {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    fun serializeSurvey(
        survey: Survey, 
        questions: List<Question>,
        answers: List<Answer>,
        options: Map<Int, List<Option>>,
        deviceInfo: DeviceInfo
    ): JSONObject {
        
        val serializedQuestions = questions.map { question ->
            SerializedQuestion(
                id = question.id,
                questionId = question.questionId,
                questionShortName = question.questionShortName,
                statement = question.statement,
                questionType = question.questionType,
                options = options[question.id]?.map { option ->
                    SerializedOption(
                        id = option.id,
                        text = option.text,
                        optionQuestionIndex = option.optionQuestionIndex
                    )
                } ?: emptyList()
            )
        }
        
        val serializedAnswers = answers.mapNotNull { answer ->
            val question = questions.find { it.id == answer.questionId }
            if (question != null) {
                val (answerValue, answerType, optionText) = when {
                    answer.isNumeric -> Triple(answer.numericValue, "numeric", null)
                    answer.optionQuestionIndex != null -> {
                        val option = options[question.id]?.find { it.optionQuestionIndex == answer.optionQuestionIndex }
                        Triple(answer.optionQuestionIndex, "multiple_choice", option?.text)
                    }
                    else -> Triple(answer.answerPrimaryLanguageText, "text", null)
                }
                
                SerializedAnswer(
                    questionId = question.questionId,
                    questionShortName = question.questionShortName,
                    answerValue = answerValue,
                    answerType = answerType,
                    optionText = optionText
                )
            } else null
        }
        
        val serializedSurvey = SerializedSurvey(
            surveyId = survey.id,
            subjectId = survey.subjectId,
            startDatetime = dateFormat.format(Date(survey.startDatetime)),
            language = survey.language,
            questions = serializedQuestions,
            answers = serializedAnswers,
            completedAt = dateFormat.format(Date()),
            deviceInfo = deviceInfo
        )
        
        return toJSON(serializedSurvey)
    }
    
    private fun toJSON(survey: SerializedSurvey): JSONObject {
        val json = JSONObject().apply {
            put("surveyId", survey.surveyId)
            put("subjectId", survey.subjectId)
            put("startDatetime", survey.startDatetime)
            put("language", survey.language)
            put("completedAt", survey.completedAt)
            
            // Device info
            put("deviceInfo", JSONObject().apply {
                put("deviceId", survey.deviceInfo.deviceId)
                put("appVersion", survey.deviceInfo.appVersion)
                put("androidVersion", survey.deviceInfo.androidVersion)
                put("deviceModel", survey.deviceInfo.deviceModel)
            })
            
            // Questions array
            put("questions", JSONArray().apply {
                survey.questions.forEach { question ->
                    put(JSONObject().apply {
                        put("id", question.id)
                        put("questionId", question.questionId)
                        put("questionShortName", question.questionShortName)
                        put("statement", question.statement)
                        put("questionType", question.questionType)
                        
                        put("options", JSONArray().apply {
                            question.options.forEach { option ->
                                put(JSONObject().apply {
                                    put("id", option.id)
                                    put("text", option.text)
                                    put("optionQuestionIndex", option.optionQuestionIndex)
                                })
                            }
                        })
                    })
                }
            })
            
            // Answers array
            put("answers", JSONArray().apply {
                survey.answers.forEach { answer ->
                    put(JSONObject().apply {
                        put("questionId", answer.questionId)
                        put("questionShortName", answer.questionShortName)
                        put("answerValue", answer.answerValue)
                        put("answerType", answer.answerType)
                        answer.optionText?.let { put("optionText", it) }
                    })
                }
            })
        }
        
        return json
    }
    
    fun createDeviceInfo(context: android.content.Context): DeviceInfo {
        return DeviceInfo(
            deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown",
            appVersion = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            },
            androidVersion = android.os.Build.VERSION.RELEASE,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
    }
}