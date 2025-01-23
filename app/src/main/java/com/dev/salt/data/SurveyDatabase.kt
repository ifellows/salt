package com.dev.salt.data
import androidx.room.*

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "statement") val statement: String,
    @ColumnInfo(name = "audio_file_name") val audioFileName: String
)

@Entity(tableName = "options")
data class Option(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "audio_file_name") val audioFileName: String
)

@Dao
interface SurveyDao {
    @Query("SELECT * FROM questions")
    fun getAllQuestions(): List<Question>

    @Query("SELECT * FROM options WHERE question_id = :questionId")
    fun getOptionsForQuestion(questionId: Int): List<Option>

    @Insert
    fun insertQuestion(question: Question)

    @Insert
    fun insertOption(option: Option)
}

@Database(entities = [Question::class, Option::class], version = 1)
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
                ).allowMainThreadQueries().build().also { instance = it }
            }
        }
    }
}
