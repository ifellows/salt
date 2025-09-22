package com.dev.salt.sync

import android.util.Log
import com.dev.salt.data.FacilityConfig
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class FacilityConfigSyncManager(
    private val database: SurveyDatabase
) {
    companion object {
        private const val TAG = "FacilityConfigSync"
    }
    
    private val userDao = database.userDao()
    private val facilityConfigDao = database.facilityConfigDao()
    
    suspend fun syncFacilityConfig(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting facility config sync")
                
                // Get server configuration
                val serverConfig = getServerConfig()
                if (serverConfig == null) {
                    Log.e(TAG, "No server configuration found")
                    return@withContext false
                }
                
                val (serverUrl, apiKey) = serverConfig
                val fullUrl = "$serverUrl/api/sync/facility/config"
                
                Log.d(TAG, "Fetching facility config from: $fullUrl")
                
                // Make HTTP request
                val url = URL(fullUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                try {
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("X-API-Key", apiKey)
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    
                    val responseCode = connection.responseCode
                    Log.d(TAG, "Response code: $responseCode")
                    
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().readText()
                        val jsonResponse = JSONObject(response)
                        
                        if (jsonResponse.getString("status") == "success") {
                            val data = jsonResponse.getJSONObject("data")
                            
                            // Parse facility configuration
                            val facilityId = data.getInt("facility_id")
                            val facilityName = data.getString("facility_name")
                            val allowNonCoupon = data.getBoolean("allow_non_coupon_participants")
                            val couponsToIssue = data.getInt("coupons_to_issue")
                            val seedRecruitmentActive = data.optBoolean("seed_recruitment_active", false)
                            val seedContactRateDays = data.optInt("seed_contact_rate_days", 7)
                            val seedRecruitmentWindowMinDays = data.optInt("seed_recruitment_window_min_days", 0)
                            val seedRecruitmentWindowMaxDays = data.optInt("seed_recruitment_window_max_days", 730)

                            // Parse payment configuration
                            val subjectPaymentType = data.optString("subject_payment_type", "None")
                            val participationPaymentAmount = data.optDouble("participation_payment_amount", 0.0)
                            val recruitmentPaymentAmount = data.optDouble("recruitment_payment_amount", 0.0)
                            val paymentCurrency = data.optString("payment_currency", "USD")
                            val paymentCurrencySymbol = data.optString("payment_currency_symbol", "$")

                            // Save to database
                            val config = FacilityConfig(
                                id = 1,
                                facilityId = facilityId,
                                facilityName = facilityName,
                                allowNonCouponParticipants = allowNonCoupon,
                                couponsToIssue = couponsToIssue,
                                seedRecruitmentActive = seedRecruitmentActive,
                                seedContactRateDays = seedContactRateDays,
                                seedRecruitmentWindowMinDays = seedRecruitmentWindowMinDays,
                                seedRecruitmentWindowMaxDays = seedRecruitmentWindowMaxDays,
                                subjectPaymentType = subjectPaymentType,
                                participationPaymentAmount = participationPaymentAmount,
                                recruitmentPaymentAmount = recruitmentPaymentAmount,
                                paymentCurrency = paymentCurrency,
                                paymentCurrencySymbol = paymentCurrencySymbol,
                                lastSyncTime = System.currentTimeMillis(),
                                syncStatus = "SUCCESS"
                            )
                            
                            facilityConfigDao.insertFacilityConfig(config)
                            
                            Log.i(TAG, "Facility config synced successfully: $facilityName (ID: $facilityId)")
                            Log.i(TAG, "Settings: Allow non-coupon=$allowNonCoupon, Coupons to issue=$couponsToIssue")
                            
                            return@withContext true
                        } else {
                            Log.e(TAG, "Server returned error status")
                            return@withContext false
                        }
                    } else {
                        Log.e(TAG, "Server returned error code: $responseCode")
                        return@withContext false
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing facility config", e)
                return@withContext false
            }
        }
    }
    
    private fun getServerConfig(): Pair<String, String>? {
        val config = userDao.getAnyServerConfig()
        val serverUrl = config?.uploadServerUrl?.takeIf { it.isNotBlank() }
        val apiKey = config?.uploadApiKey?.takeIf { it.isNotBlank() }
        
        return if (serverUrl != null && apiKey != null) {
            Log.d(TAG, "Using server config: URL=$serverUrl")
            Pair(serverUrl, apiKey)
        } else {
            Log.e(TAG, "No server configuration found")
            null
        }
    }
    
    fun getFacilityConfig(): FacilityConfig {
        // Return config from database or default values
        return facilityConfigDao.getFacilityConfig() ?: FacilityConfig(
            id = 1,
            allowNonCouponParticipants = true,
            couponsToIssue = 3
        )
    }
}