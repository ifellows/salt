package com.dev.salt.data
import androidx.room.*
import java.util.UUID
@Entity(tableName = "questions")
data class Question(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "statement") val statement: String,
    @ColumnInfo(name = "audio_file_name") val audioFileName: String,
    @ColumnInfo(name = "question_language") val questionLanguage: String, // New field
    @ColumnInfo(name = "primary_language_text") val primaryLanguageText: String // New field
)

@Entity(tableName = "options")
data class Option(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "audio_file_name") val audioFileName: String,
    @ColumnInfo(name = "option_question_index") val optionQuestionIndex: Int, // New field
    @ColumnInfo(name = "language") val language: String, // New field
    @ColumnInfo(name = "primary_language_text") val primaryLanguageText: String // New field
)

@Entity(tableName = "surveys")
data class Survey(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "start_datetime") val startDatetime: Long,
    @ColumnInfo(name = "language") val language: String
) {
    @Ignore
    var questions: MutableList<Question> = mutableListOf()

    @Ignore
    var answers: MutableList<Answer> = mutableListOf()
}

fun Survey.populateFields(surveyDao: SurveyDao) {
    this.questions = surveyDao.getQuestionsByLanguage(this.language)
    this.answers = surveyDao.getAnswersBySurveyId(this.id)
}

@Entity(tableName = "answers")
data class Answer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "survey_id") val surveyId: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "option_question_index") val optionQuestionIndex: Int,
    @ColumnInfo(name = "answer_language") val answerLanguage: String,
    @ColumnInfo(name = "answer_primary_language_text") val answerPrimaryLanguageText: String,
    @ColumnInfo(name = "is_numeric") val isNumeric: Boolean = false,
    @ColumnInfo(name = "numeric_value") val numericValue: Double? = null
) {
    fun getValue(): Any? {
        return if (isNumeric) numericValue else optionQuestionIndex
    }
}

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

@Database(entities = [Question::class, Option::class, Survey::class, Answer::class], version = 6)
abstract class SurveyDatabase : RoomDatabase() {
    abstract fun surveyDao(): SurveyDao

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
