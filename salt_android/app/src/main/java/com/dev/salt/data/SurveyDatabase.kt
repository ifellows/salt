package com.dev.salt.data
import androidx.room.*
import androidx.room.OnConflictStrategy
import java.util.UUID

@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey
    val id: Int = 1, // Single row for sync status
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING",
    @ColumnInfo(name = "last_error") val lastError: String? = null
)

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "question_short_name") val questionShortName: String,
    @ColumnInfo(name = "statement") var statement: String,
    @ColumnInfo(name = "audio_file_name") var audioFileName: String,
    @ColumnInfo(name = "question_language") var questionLanguage: String,
    @ColumnInfo(name = "primary_language_text") var primaryLanguageText: String,
    @ColumnInfo(name = "question_type") var questionType: String = "multiple_choice",
    @ColumnInfo(name = "pre_script") var preScript: String? = null, // a script to run before the question is asked
    @ColumnInfo(name = "validation_script") var validationScript: String? = null, // a script to determine if an answer value is valid
    @ColumnInfo(name = "validation_error_text") var validationErrorText: String? = "Invalid Answer" // a script to determine if an answer value is valid
)

@Entity(tableName = "options")
data class Option(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "question_id") var questionId: Int,
    @ColumnInfo(name = "text") var text: String,
    @ColumnInfo(name = "audio_file_name") var audioFileName: String,
    @ColumnInfo(name = "option_question_index") var optionQuestionIndex: Int, // New field
    @ColumnInfo(name = "language") var language: String, // New field
    @ColumnInfo(name = "primary_language_text") var primaryLanguageText: String // New field
)

@Entity(tableName = "surveys")
data class Survey(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "subject_id") var subjectId: String,
    @ColumnInfo(name = "start_datetime") var startDatetime: Long,
    @ColumnInfo(name = "language") var language: String,
    @ColumnInfo(name = "referral_coupon_code") var referralCouponCode: String? = null,
    @ColumnInfo(name = "contact_phone") var contactPhone: String? = null,
    @ColumnInfo(name = "contact_email") var contactEmail: String? = null,
    @ColumnInfo(name = "contact_consent") var contactConsent: Boolean = false
) {
    @Ignore
    var questions: MutableList<Question> = mutableListOf()

    @Ignore
    var answers: MutableList<Answer> = mutableListOf()

    fun populateFields(surveyDao: SurveyDao) {
        this.questions = surveyDao.getQuestionsByLanguage(this.language)
        this.answers = surveyDao.getAnswersBySurveyId(this.id)
        if(answers.size != questions.size){
            for(i in answers.size until questions.size){
                answers.add(Answer(
                    surveyId = id,
                    questionId = questions[i].id,
                    optionQuestionIndex = null,
                    answerLanguage = this.language,
                    answerPrimaryLanguageText = null))
            }
        }
    }
}


@Entity(tableName = "answers")
data class Answer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "survey_id") var surveyId: String,
    @ColumnInfo(name = "question_id") var questionId: Int,
    @ColumnInfo(name = "option_question_index") var optionQuestionIndex: Int?,
    @ColumnInfo(name = "answer_language") var answerLanguage: String,
    @ColumnInfo(name = "answer_primary_language_text") var answerPrimaryLanguageText: String?,
    @ColumnInfo(name = "is_numeric") var isNumeric: Boolean = false,
    @ColumnInfo(name = "numeric_value") var numericValue: Double? = null
) {
    fun getValue(returnIndex: Boolean = true): Any? {
        if(isNumeric){
            return numericValue
        }
        if(optionQuestionIndex == null && answerPrimaryLanguageText != null){
            return answerPrimaryLanguageText
        }
        if(returnIndex) {
            return optionQuestionIndex
        }
        return answerPrimaryLanguageText
    }
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val userName: String,
    val hashedPassword: String,
    val fullName: String,
    val role: String, // e.g., "SURVEY_STAFF", "ADMINISTRATOR"
    @ColumnInfo(name = "biometric_key_hash") val biometricKeyHash: String? = null,
    @ColumnInfo(name = "biometric_enabled") val biometricEnabled: Boolean = false,
    @ColumnInfo(name = "biometric_enrolled_date") val biometricEnrolledDate: Long? = null,
    @ColumnInfo(name = "last_biometric_auth") val lastBiometricAuth: Long? = null,
    @ColumnInfo(name = "session_timeout_minutes") val sessionTimeoutMinutes: Long = 30,
    @ColumnInfo(name = "last_login_time") val lastLoginTime: Long? = null,
    @ColumnInfo(name = "last_activity_time") val lastActivityTime: Long? = null,
    @ColumnInfo(name = "upload_server_url") val uploadServerUrl: String? = null,
    @ColumnInfo(name = "upload_api_key") val uploadApiKey: String? = null
)

@Entity(tableName = "survey_upload_state")
data class SurveyUploadState(
    @PrimaryKey
    val surveyId: String,
    @ColumnInfo(name = "upload_status") val uploadStatus: String, // PENDING, UPLOADING, COMPLETED, FAILED
    @ColumnInfo(name = "last_attempt_time") val lastAttemptTime: Long? = null,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "created_time") val createdTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_time") val completedTime: Long? = null
)

@Entity(tableName = "coupons")
data class Coupon(
    @PrimaryKey
    val couponCode: String,
    @ColumnInfo(name = "issued_to_survey_id") val issuedToSurveyId: String? = null,
    @ColumnInfo(name = "issued_date") val issuedDate: Long? = null,
    @ColumnInfo(name = "used_by_survey_id") val usedBySurveyId: String? = null,
    @ColumnInfo(name = "used_date") val usedDate: Long? = null,
    @ColumnInfo(name = "status") val status: String = "UNUSED", // UNUSED, ISSUED, USED
    @ColumnInfo(name = "created_date") val createdDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "facility_config")
data class FacilityConfig(
    @PrimaryKey
    val id: Int = 1, // Single row for facility configuration
    @ColumnInfo(name = "facility_id") val facilityId: Int? = null,
    @ColumnInfo(name = "facility_name") val facilityName: String? = null,
    @ColumnInfo(name = "allow_non_coupon_participants") val allowNonCouponParticipants: Boolean = true,
    @ColumnInfo(name = "coupons_to_issue") val couponsToIssue: Int = 3,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long? = null,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING"
)

enum class CouponStatus {
    UNUSED,
    ISSUED, 
    USED
}

enum class UploadStatus {
    PENDING,
    UPLOADING, 
    COMPLETED,
    FAILED
}

data class ServerConfig(
    @ColumnInfo(name = "upload_server_url") val uploadServerUrl: String?,
    @ColumnInfo(name = "upload_api_key") val uploadApiKey: String?
)

@Dao
interface SurveyDao {

    @Query("SELECT * FROM questions WHERE question_language = :language ORDER BY question_id")
    fun getQuestionsByLanguage(language: String): MutableList<Question>

    @Query("SELECT * FROM answers WHERE survey_id = :surveyId")
    fun getAnswersBySurveyId(surveyId: String): MutableList<Answer>

    @Query("SELECT * FROM questions")
    fun getAllQuestions(): List<Question>

    @Query("SELECT * FROM options WHERE question_id = :questionId")
    fun getOptionsForQuestion(questionId: Int): List<Option>

    @Insert
    fun insertQuestion(question: Question)

    @Insert
    fun insertOption(option: Option)

    @Insert
    fun insertSurvey(survey: Survey)

    @Insert
    fun insertAnswer(answer: Answer)

    @Delete
    fun deleteSurvey(survey: Survey)

    @Delete
    fun deleteAnswer(answer: Answer)

    @Query("SELECT * FROM surveys WHERE id = :surveyId LIMIT 1")
    fun getSurveyById(surveyId: String): Survey?
    
    @Update
    fun updateSurvey(survey: Survey)
    
    @Delete
    fun deleteQuestion(question: Question)
    
    @Delete
    fun deleteOption(option: Option)
}

fun saveSurvey(survey: Survey, surveyDao: SurveyDao) {
    surveyDao.insertSurvey(survey)
    survey.answers.forEach { answer ->
        surveyDao.insertAnswer(answer)
    }
}

fun deleteSurvey(survey: Survey, surveyDao: SurveyDao) {
    surveyDao.getAnswersBySurveyId(survey.id).forEach { answer ->
        surveyDao.deleteAnswer(answer)
    }
    surveyDao.deleteSurvey(survey)
}

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE userName = :userName LIMIT 1")
    fun getUserByUserName(userName: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsers(): List<User>

    @Query("SELECT * FROM users WHERE role = :role")
    fun getUsersByRole(role: String): List<User>

    @Query("DELETE FROM users WHERE userName = :userName")
    fun deleteUser(userName: String)

    @Query("UPDATE users SET hashedPassword = :hashedPassword WHERE userName = :userName")
    fun updateUserPassword(userName: String, hashedPassword: String)

    @Query("UPDATE users SET fullName = :fullName WHERE userName = :userName")
    fun updateUserFullName(userName: String, fullName: String)

    @Query("UPDATE users SET role = :role WHERE userName = :userName")
    fun updateUserRole(userName: String, role: String)

    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMINISTRATOR'")
    fun getAdminCount(): Int

    @Query("UPDATE users SET biometric_key_hash = :keyHash, biometric_enabled = :enabled, biometric_enrolled_date = :enrolledDate WHERE userName = :userName")
    fun updateUserBiometric(userName: String, keyHash: String?, enabled: Boolean, enrolledDate: Long?)

    @Query("UPDATE users SET last_biometric_auth = :authTime WHERE userName = :userName")
    fun updateLastBiometricAuth(userName: String, authTime: Long)

    @Query("SELECT * FROM users WHERE userName = :userName AND biometric_enabled = 1 LIMIT 1")
    fun getUserForBiometricAuth(userName: String): User?

    @Query("SELECT * FROM users WHERE biometric_enabled = 1")
    fun getUsersWithBiometricEnabled(): List<User>

    @Query("UPDATE users SET last_login_time = :loginTime, last_activity_time = :activityTime WHERE userName = :userName")
    fun updateUserSessionTimes(userName: String, loginTime: Long, activityTime: Long)

    @Query("UPDATE users SET last_activity_time = :activityTime WHERE userName = :userName")
    fun updateUserActivity(userName: String, activityTime: Long)

    @Query("UPDATE users SET session_timeout_minutes = :timeoutMinutes WHERE userName = :userName")
    fun updateUserSessionTimeout(userName: String, timeoutMinutes: Long)

    @Query("UPDATE users SET upload_server_url = :serverUrl, upload_api_key = :apiKey WHERE userName = :userName")
    fun updateUserServerConfig(userName: String, serverUrl: String?, apiKey: String?)

    @Query("SELECT upload_server_url, upload_api_key FROM users WHERE userName = :userName AND role = 'ADMINISTRATOR' LIMIT 1")
    fun getAdminServerConfig(userName: String): ServerConfig?
    
    @Query("SELECT upload_server_url, upload_api_key FROM users WHERE upload_server_url IS NOT NULL AND upload_api_key IS NOT NULL AND role = 'ADMINISTRATOR' LIMIT 1")
    fun getAnyServerConfig(): ServerConfig?
}

@Dao
interface UploadStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUploadState(uploadState: SurveyUploadState)

    @Query("SELECT * FROM survey_upload_state WHERE surveyId = :surveyId")
    fun getUploadState(surveyId: String): SurveyUploadState?

    @Query("SELECT * FROM survey_upload_state WHERE upload_status = :status ORDER BY created_time ASC")
    fun getUploadStatesByStatus(status: String): List<SurveyUploadState>

    @Query("SELECT * FROM survey_upload_state WHERE upload_status IN ('PENDING', 'FAILED') ORDER BY created_time ASC")
    fun getPendingUploads(): List<SurveyUploadState>

    @Query("SELECT * FROM survey_upload_state ORDER BY created_time DESC")
    fun getAllUploadStates(): List<SurveyUploadState>

    @Query("UPDATE survey_upload_state SET upload_status = :status, last_attempt_time = :attemptTime, attempt_count = :attemptCount, error_message = :errorMessage WHERE surveyId = :surveyId")
    fun updateUploadAttempt(surveyId: String, status: String, attemptTime: Long, attemptCount: Int, errorMessage: String?)

    @Query("UPDATE survey_upload_state SET upload_status = :status, completed_time = :completedTime WHERE surveyId = :surveyId")
    fun markUploadCompleted(surveyId: String, status: String, completedTime: Long)

    @Query("DELETE FROM survey_upload_state WHERE surveyId = :surveyId")
    fun deleteUploadState(surveyId: String)

    @Query("DELETE FROM survey_upload_state WHERE upload_status = 'COMPLETED' AND completed_time < :beforeTime")
    fun cleanupOldCompletedUploads(beforeTime: Long)
}

@Dao
interface CouponDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCoupon(coupon: Coupon)
    
    @Query("SELECT * FROM coupons WHERE couponCode = :code LIMIT 1")
    fun getCouponByCode(code: String): Coupon?
    
    @Query("SELECT * FROM coupons WHERE issued_to_survey_id = :surveyId")
    fun getCouponsIssuedToSurvey(surveyId: String): List<Coupon>
    
    @Query("SELECT * FROM coupons WHERE status = :status")
    fun getCouponsByStatus(status: String): List<Coupon>
    
    @Query("UPDATE coupons SET status = 'ISSUED', issued_to_survey_id = :surveyId, issued_date = :issuedDate WHERE couponCode = :code")
    fun markCouponIssued(code: String, surveyId: String, issuedDate: Long)
    
    @Query("UPDATE coupons SET status = 'USED', used_by_survey_id = :surveyId, used_date = :usedDate WHERE couponCode = :code")
    fun markCouponUsed(code: String, surveyId: String, usedDate: Long)
    
    @Query("SELECT COUNT(*) FROM coupons WHERE status = 'UNUSED'")
    fun getUnusedCouponCount(): Int
    
    @Query("DELETE FROM coupons WHERE couponCode = :code")
    fun deleteCoupon(code: String)
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun getSyncMetadata(): SyncMetadata?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSyncMetadata(metadata: SyncMetadata)
    
    @Query("UPDATE sync_metadata SET sync_status = :status, last_error = :error WHERE id = 1")
    fun updateSyncStatus(status: String, error: String? = null)
    
    @Query("UPDATE sync_metadata SET last_sync_time = :time, sync_status = 'SUCCESS' WHERE id = 1")
    fun updateLastSyncSuccess(time: Long)
}

@Dao
interface FacilityConfigDao {
    @Query("SELECT * FROM facility_config WHERE id = 1 LIMIT 1")
    fun getFacilityConfig(): FacilityConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFacilityConfig(config: FacilityConfig)
    
    @Query("UPDATE facility_config SET allow_non_coupon_participants = :allow, coupons_to_issue = :count WHERE id = 1")
    fun updateFacilitySettings(allow: Boolean, count: Int)
    
    @Query("UPDATE facility_config SET facility_id = :facilityId, facility_name = :facilityName WHERE id = 1")
    fun updateFacilityInfo(facilityId: Int, facilityName: String)
    
    @Query("UPDATE facility_config SET last_sync_time = :time, sync_status = 'SUCCESS' WHERE id = 1")
    fun updateLastSyncSuccess(time: Long)
}

@Database(entities = [Question::class, Option::class, Survey::class, Answer::class, User::class, SurveyUploadState::class, SyncMetadata::class, Coupon::class, FacilityConfig::class], version = 20)
abstract class SurveyDatabase : RoomDatabase() {
    abstract fun surveyDao(): SurveyDao
    abstract fun userDao(): UserDao
    abstract fun uploadStateDao(): UploadStateDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun couponDao(): CouponDao
    abstract fun facilityConfigDao(): FacilityConfigDao
    companion object {
        private var instance: SurveyDatabase? = null

        fun getInstance(context: android.content.Context): SurveyDatabase {
            return instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    SurveyDatabase::class.java,
                    "survey_database"
                ).fallbackToDestructiveMigration().allowMainThreadQueries().build().also { instance = it }
            }
        }
    }
}
