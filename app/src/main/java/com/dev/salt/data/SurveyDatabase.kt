package com.dev.salt.data
import androidx.room.*
import java.util.UUID
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
    @ColumnInfo(name = "language") var language: String
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
    val role: String // e.g., "SURVEY_STAFF", "ADMINISTRATOR"
)

@Dao
interface SurveyDao {

    @Query("SELECT * FROM questions WHERE question_language = :language")
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

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Or IGNORE
    fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE userName = :userName LIMIT 1")
    fun getUserByUserName(userName: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsers(): List<User>

    // You can add other user-specific operations here if needed in the future,
    // like updateUser, deleteUser, etc.
    @Query("DELETE FROM users WHERE userName = :userName")
    fun deleteUser(userName: String)
}

@Database(entities = [Question::class, Option::class, Survey::class, Answer::class, User::class], version = 13)
abstract class SurveyDatabase : RoomDatabase() {
    abstract fun surveyDao(): SurveyDao
    abstract fun userDao(): UserDao
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
