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
import com.dev.salt.data.Coupon
import com.dev.salt.data.AppServerConfig
import com.dev.salt.data.CouponStatus
import net.sqlcipher.database.SQLiteDatabase
import com.dev.salt.util.EmulatorDetector

class SurveyApplication : Application() {
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.e("SurveyApplication", "onCreate called")
        
        // Initialize SQLCipher
        SQLiteDatabase.loadLibs(this)
        Log.d("SurveyApplication", "SQLCipher initialized")
        
        runBlocking {
            populateSampleData()
        }
        setupGlobalExceptionHandler()
    }

    fun populateSampleData() {

        val database = SurveyDatabase.getInstance(this)
        val dao = database.surveyDao()
        val userDao = database.userDao() // <-- Get the UserDao

        // Determine the default server URL based on whether we're on emulator or real device
        val defaultServerUrl = if (EmulatorDetector.isEmulator()) {
            "http://10.0.2.2:3000"
        } else {
            "http://192.168.1.137:3000"
        }
        Log.d("SurveyApplication", "Using default server URL: $defaultServerUrl (Emulator: ${EmulatorDetector.isEmulator()})")

        // --- User Setup Removed ---
        // Users are now created through the first-time setup wizard.
        // No sample users are auto-populated on first launch.
        // The setup wizard will guide users to:
        // 1. Configure server URL
        // 2. Create initial admin account with fingerprint
        // 3. Enter facility setup code
        Log.d("SurveyApplication", "Sample user auto-population disabled. Users will be created via setup wizard.")
        
        // Initialize facility configuration with defaults if not present
        val facilityConfigDao = database.facilityConfigDao()
        if (facilityConfigDao.getFacilityConfig() == null) {
            Log.d("SurveyApplication", "Initializing facility configuration with defaults")
            val defaultConfig = com.dev.salt.data.FacilityConfig(
                id = 1,
                allowNonCouponParticipants = true,
                couponsToIssue = 3,
                syncStatus = "PENDING"
            )
            facilityConfigDao.insertFacilityConfig(defaultConfig)
        }
        
        // Populate test coupons if none exist
        val couponDao = database.couponDao()
        if (couponDao.getUnusedCouponCount() == 0 && couponDao.getCouponsByStatus(CouponStatus.ISSUED.name).isEmpty()) {
            Log.d("SurveyApplication", "Populating test coupons...")
            // Create some test coupons that are already issued (as if from previous surveys)
            val testCoupons = listOf(
                Coupon(
                    couponCode = "TEST01",
                    status = CouponStatus.ISSUED.name,
                    issuedToSurveyId = "test-survey-1",
                    issuedDate = System.currentTimeMillis() - 86400000 // Issued 1 day ago
                ),
                Coupon(
                    couponCode = "TEST02",
                    status = CouponStatus.ISSUED.name,
                    issuedToSurveyId = "test-survey-2",
                    issuedDate = System.currentTimeMillis() - 172800000 // Issued 2 days ago
                ),
                Coupon(
                    couponCode = "ABC123",
                    status = CouponStatus.ISSUED.name,
                    issuedToSurveyId = "test-survey-3",
                    issuedDate = System.currentTimeMillis() - 259200000 // Issued 3 days ago
                ),
                Coupon(
                    couponCode = "XYZ789",
                    status = CouponStatus.ISSUED.name,
                    issuedToSurveyId = "test-survey-4",
                    issuedDate = System.currentTimeMillis() - 345600000 // Issued 4 days ago
                )
            )
            
            testCoupons.forEach { coupon ->
                couponDao.insertCoupon(coupon)
            }
            Log.d("SurveyApplication", "Test coupons populated: ${testCoupons.map { it.couponCode }}")
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