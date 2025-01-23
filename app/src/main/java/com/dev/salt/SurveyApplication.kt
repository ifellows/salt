package com.dev.salt

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dev.salt.data.Question
import com.dev.salt.data.Option
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.concurrent.read
import kotlin.io.path.exists
import java.io.File
import java.io.FileOutputStream

class SurveyApplication : Application() {
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        populateSampleData()
    }

    fun populateSampleData() {

        val database = SurveyDatabase.getInstance(this)
        val dao = database.surveyDao()

        // Check if the database is empty
        if (dao.getAllQuestions().isEmpty()) {
            // Add sample questions and options
            dao.insertQuestion(Question(1, "Have you every been told by a doctor or nurse that you have HIV?","question1.mp3"))
            dao.insertOption(Option(1, 1, "Yes", "yes.mp3"))
            dao.insertOption(Option(2, 1, "No","no.mp3"))
            dao.insertOption(Option(3, 1, "Don't know", "dont_know.mp3"))
            dao.insertOption(Option(4, 1, "Don't want to answer", "dont_want_to_answer.mp3"))

            dao.insertQuestion(Question(2, "When was the last time you were tested for HIV?","question2.mp3"))
            dao.insertOption(Option(5, 2, "Less than a year ago", "less_than_a_year_ago.mp3"))
            dao.insertOption(Option(6, 2, "Over a year ago", "over_a_year_ago.mp3"))
            dao.insertOption(Option(7, 2, "Don't know", "dont_know.mp3"))
            dao.insertOption(Option(8, 2, "Don't want to answer", "dont_want_to_answer.mp3"))

            dao.insertQuestion(Question(3, "Did you use a condom with the last partner you had anal sex with?","question3.mp3"))
            dao.insertOption(Option(9, 3, "Yes", "yes.mp3"))
            dao.insertOption(Option(10, 3, "No", "no.mp3"))
            dao.insertOption(Option(11, 3, "Don't remember", "dont_remember.mp3"))
            dao.insertOption(Option(12, 3, "Don't want to answer", "dont_want_to_answer.mp3"))

            dao.insertQuestion(Question(4, "How many sexual partners have you had in the last month?","question4.mp3"))
            dao.insertOption(Option(13, 4, "None", "none.mp3"))
            dao.insertOption(Option(14, 4, "One", "one.mp3"))
            dao.insertOption(Option(15, 4, "More than one", "more_than_one.mp3"))
            dao.insertOption(Option(16, 4, "Don't know or don't want to answer", "dont_know_or_dont_want_to_answer.mp3"))
        }
    }

    /*fun copyRawFilesToLocalStorage(context: Context) {
        val rawResources = context.resources.getIdentifier("raw", "raw", context.packageName)
        val rawFiles = context.resources.assets.list("raw") ?: emptyArray()

        val audioDir = File(context.filesDir, "audio")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }

        for (fileName in rawFiles) {
            try {
                val inputStream = context.resources.openRawResource(rawResources)
                val outputStream = FileOutputStream(File(audioDir, fileName))

                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }

                inputStream.close()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }*/

    fun copyRawFilesToLocalStorage(context: Context) {
        val rawFiles = listOf("question1", "yes", "no", "dont_know", "dont_want_to_answer", "question2",
            "less_than_a_year_ago", "over_a_year_ago", "question3", "dont_remember", "question4", "none", "one", "more_than_one", "dont_know_or_dont_want_to_answer") // Replace with your actual raw file names

        val audioDir = File(context.filesDir, "audio")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }

        for (fileName in rawFiles) {
            try {
                val resourceId = context.resources.getIdentifier(fileName, "raw", context.packageName)
                val inputStream = context.resources.openRawResource(resourceId)
                val outputStream = FileOutputStream(File(audioDir, "$fileName.mp3")) // Assuming your raw files are MP3s

                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }

                inputStream.close()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

}