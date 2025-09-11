package com.dev.salt
import com.dev.salt.data.User
import android.app.Application
import android.content.Context
import android.util.Log
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
import com.dev.salt.PasswordUtils.hashPasswordWithNewSalt

class SurveyApplication : Application() {
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.e("SurveyApplication", "onCreate called")
        runBlocking {
            populateSampleData()
        }
        setupGlobalExceptionHandler()
    }

    fun populateSampleData() {

        val database = SurveyDatabase.getInstance(this)
        val dao = database.surveyDao()
        val userDao = database.userDao() // <-- Get the UserDao

        // --- Populate Users ---
        // Check if users are already populated (optional, but good practice)
        if (userDao.getAllUsers().isEmpty()) {
            Log.d("SurveyApplication", "Populating sample users...")
            // IMPORTANT: Replace these with securely hashed passwords in a real app!
            // Use a proper password hashing library/utility.
            // These are placeholders for demonstration.
            val adminUser = User(
                userName = "admin",
                // Example: In a real app, this would be the output of a hashing function
                // e.g., PasswordHasher.hash("adminpass")
                hashedPassword = hashPasswordWithNewSalt("123")!!,
                fullName = "Administrator User",
                role = "ADMINISTRATOR", // Consistent with your UserRole enum or string constants
                // Default server configuration for testing
                uploadServerUrl = "http://10.0.2.2:3000",
                uploadApiKey = "fac_b83d45ae08113b380154e6134fa733a7fa945097983a24adee35d1350818ddbd"
            )
            val staffUser = User(
                userName = "staff",
                hashedPassword = hashPasswordWithNewSalt("123")!!,
                fullName = "Survey Staff User",
                role = "SURVEY_STAFF"
            )
            userDao.insertUser(adminUser)
            userDao.insertUser(staffUser)
            Log.d("SurveyApplication", "Sample users populated.")
        } else {
            Log.d("SurveyApplication", "Users already exist, checking for server config...")
            // Update existing admin users with default server config if they don't have any
            val existingAdmins = userDao.getAllUsers().filter { it.role == "ADMINISTRATOR" }
            for (admin in existingAdmins) {
                if (admin.uploadServerUrl.isNullOrBlank() || admin.uploadApiKey.isNullOrBlank()) {
                    userDao.updateUserServerConfig(
                        admin.userName,
                        "http://10.0.2.2:3000",
                        "fac_b83d45ae08113b380154e6134fa733a7fa945097983a24adee35d1350818ddbd"
                    )
                    Log.d("SurveyApplication", "Updated admin user '${admin.userName}' with default server config")
                }
            }
        }
        // Check if the database is empty
        if (dao.getAllQuestions().isEmpty()) {
            // Add sample questions and options
            dao.insertQuestion(Question(1, 1,"q1",  "Have you every been told by a doctor or nurse that you have HIV?","question1.mp3",
                "en","Have you every been told by a doctor or nurse that you have HIV?"))
            dao.insertOption(Option(1, 1, "Yes", "yes.mp3", 0,"en", "Yes"))
            dao.insertOption(Option(2, 1, "No","no.mp3", 1,"en","No"))
            dao.insertOption(Option(3, 1, "Don't know", "dont_know.mp3",2,"en","Don't know"))
            dao.insertOption(Option(4, 1, "Don't want to answer", "dont_want_to_answer.mp3",3,
                "en","Don't want to answer"))

            dao.insertQuestion(Question(2, 2, "q2", "When was the last time you were tested for HIV?","question2.mp3",
                "en","When was the last time you were tested for HIV?"))
            dao.insertOption(Option(5, 2, "Less than a year ago", "less_than_a_year_ago.mp3",
                0,"en","Less than a year ago"))
            dao.insertOption(Option(6, 2, "Over a year ago", "over_a_year_ago.mp3", 1,
                "en","Over a year ago"))
            dao.insertOption(Option(7, 2, "Don't know", "dont_know.mp3",2,"en",
                "Don't know"))
            dao.insertOption(Option(8, 2, "Don't want to answer", "dont_want_to_answer.mp3",3,
                "en","Don't want to answer"))

            dao.insertQuestion(Question(3, 3,"q3","Did you use a condom with the last partner you had anal sex with?","question3.mp3",
                "en","Did you use a condom with the last partner you had anal sex with?"))
            dao.insertOption(Option(9, 3, "Yes", "yes.mp3",0,"en","Yes"))
            dao.insertOption(Option(10, 3, "No", "no.mp3",1,"en","No"))
            dao.insertOption(Option(11, 3, "Don't remember", "dont_remember.mp3",2,"en",
                "Don't remember"))
            dao.insertOption(Option(12, 3, "Don't want to answer", "dont_want_to_answer.mp3",3,
                "en","Don't want to answer"))

            dao.insertQuestion(Question(4, 4,"q4","How many sexual partners have you had in the last month?","question4.mp3",
                "en","How many sexual partners have you had in the last month?", preScript = "q3 == 1" ))
            dao.insertOption(Option(13, 4, "None", "none.mp3",0,"en","None"))
            dao.insertOption(Option(14, 4, "One", "one.mp3",1,"en","One"))
            dao.insertOption(Option(15, 4, "More than one", "more_than_one.mp3",2,"en",
                "More than one"))
            dao.insertOption(Option(16, 4, "Don't know or don't want to answer", "dont_know_or_dont_want_to_answer.mp3",3,
                "en","Don't know or don't want to answer"))

            dao.insertQuestion(Question(5, 5,"q5","What is your age?","question5.mp3",
                "en","What is your age?", questionType = "numeric", validationScript = "(q5 > 15) && (q5 < 115)"))

        }
    }


    fun copyRawFilesToLocalStorage(context: Context) {
        val rawFiles = listOf("question1", "yes", "no", "dont_know", "dont_want_to_answer", "question2",
            "less_than_a_year_ago", "over_a_year_ago", "question3", "dont_remember", "question4", "none", "one", "more_than_one", "dont_know_or_dont_want_to_answer", "question5") // Replace with your actual raw file names

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

    private fun setupGlobalExceptionHandler() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log the exception
            Log.e("MyApplication", "Unhandled exception in thread: ${thread.name}", throwable)

            // You can add more custom logic here, like:
            // - Sending the crash report to a server
            // - Displaying a user-friendly error message (though be careful with UI from here)
            // - Performing cleanup tasks

            // It's generally a good idea to also call the original default handler
            // if you want the system to handle the crash as it normally would (e.g., show "App has stopped").
            // If you don't call it, the app might just freeze or behave unexpectedly.
            defaultExceptionHandler?.uncaughtException(thread, throwable)

            // Alternatively, if you want to force the app to close immediately after logging:
            // exitProcess(1) // Be cautious with this, as it bypasses normal shutdown procedures.
        }
    }

}