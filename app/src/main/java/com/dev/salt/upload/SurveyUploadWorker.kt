package com.dev.salt.upload

import android.content.Context
import android.util.Log
import androidx.work.*
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SurveyUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SurveyUploadWorker"
        const val WORK_NAME = "survey_upload_retry"
        
        fun schedulePeriodicUploadRetry(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val uploadWorkRequest = PeriodicWorkRequestBuilder<SurveyUploadWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    uploadWorkRequest
                )
            
            Log.i(TAG, "Scheduled periodic upload retry worker")
        }
        
        fun scheduleImmediateUploadRetry(context: Context, surveyId: String? = null) {
            val inputData = if (surveyId != null) {
                Data.Builder().putString("surveyId", surveyId).build()
            } else {
                Data.Builder().build()
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val uploadWorkRequest = OneTimeWorkRequestBuilder<SurveyUploadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueue(uploadWorkRequest)
            
            Log.i(TAG, "Scheduled immediate upload retry${if (surveyId != null) " for survey: $surveyId" else ""}")
        }
        
        fun cancelUploadWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Cancelled upload retry worker")
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting upload retry worker")
        
        try {
            val database = SurveyDatabase.getInstance(applicationContext)
            val uploadManager = SurveyUploadManager(applicationContext, database)
            
            val specificSurveyId = inputData.getString("surveyId")
            
            if (specificSurveyId != null) {
                // Upload specific survey
                Log.i(TAG, "Retrying upload for specific survey: $specificSurveyId")
                val result = uploadManager.uploadSurvey(specificSurveyId)
                
                when (result) {
                    is UploadResult.Success -> {
                        Log.i(TAG, "Successfully uploaded survey: $specificSurveyId")
                        Result.success()
                    }
                    is UploadResult.NetworkError -> {
                        Log.w(TAG, "Network error uploading survey: $specificSurveyId - ${result.message}")
                        Result.retry()
                    }
                    is UploadResult.ServerError -> {
                        if (result.code in 500..599) {
                            Log.w(TAG, "Server error uploading survey: $specificSurveyId - retrying")
                            Result.retry()
                        } else {
                            Log.e(TAG, "Client error uploading survey: $specificSurveyId - not retrying")
                            Result.failure()
                        }
                    }
                    is UploadResult.ConfigurationError -> {
                        Log.e(TAG, "Configuration error uploading survey: $specificSurveyId - ${result.message}")
                        Result.failure()
                    }
                    is UploadResult.UnknownError -> {
                        Log.e(TAG, "Unknown error uploading survey: $specificSurveyId - ${result.message}")
                        Result.retry()
                    }
                }
            } else {
                // Retry all pending uploads
                Log.i(TAG, "Retrying all pending uploads")
                val results = uploadManager.retryPendingUploads()
                
                val successCount = results.count { it.second is UploadResult.Success }
                val failureCount = results.size - successCount
                
                Log.i(TAG, "Upload retry completed: $successCount successful, $failureCount failed")
                
                // Clean up old completed uploads
                uploadManager.cleanupOldUploads()
                
                // Always return success for periodic work - individual failures are handled by the upload manager
                Result.success()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception in upload worker", e)
            Result.retry()
        }
    }
}

class SurveyUploadWorkManager(private val context: Context) {
    
    fun schedulePeriodicRetries() {
        SurveyUploadWorker.schedulePeriodicUploadRetry(context)
    }
    
    fun scheduleImmediateRetry(surveyId: String? = null) {
        SurveyUploadWorker.scheduleImmediateUploadRetry(context, surveyId)
    }
    
    fun cancelScheduledWork() {
        SurveyUploadWorker.cancelUploadWork(context)
    }
    
    fun getWorkInfo(): androidx.lifecycle.LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(SurveyUploadWorker.WORK_NAME)
    }
}