package com.dev.salt.sync

import com.dev.salt.data.Coupon
import com.dev.salt.data.Survey
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.logging.AppLogger as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Restores coupon + completed-survey state from the management server after a
 * tablet has been wiped and re-registered to a facility. Without this, all
 * locally-issued coupons are forgotten — newly scanned codes wouldn't be
 * recognized as valid, recruitment chains break, and previously paid coupons
 * could be paid again.
 *
 * Invoked once from the facility setup flow. Hard fail on error (caller is
 * expected to roll back the facility setup so the user re-enters the setup
 * code) — silently proceeding would mean permanently losing the recovery
 * opportunity.
 */
class CouponRestoreManager(
    private val database: SurveyDatabase
) {
    companion object {
        private const val TAG = "CouponRestore"
    }

    data class Result(
        val couponsRestored: Int,
        val stubsRestored: Int
    )

    /**
     * Calls the server's restore endpoint and replays the response into the
     * local Room DB. Throws on any failure (HTTP error, malformed JSON, DB
     * error). The caller is responsible for rolling back.
     */
    suspend fun restoreFromServer(serverUrl: String, apiKey: String): Result {
        return withContext(Dispatchers.IO) {
            val url = URL("$serverUrl/api/sync/facility/restore")
            val connection = url.openConnection() as HttpURLConnection
            val responseBody: String
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("X-API-Key", apiKey)
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                val code = connection.responseCode
                responseBody = if (code in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "(no body)"
                    throw RestoreException("Restore failed: HTTP $code: $err")
                }
            } finally {
                connection.disconnect()
            }

            val root = JSONObject(responseBody)
            val status = root.optString("status")
            if (status != "success") {
                throw RestoreException("Restore failed: server status=$status, message=${root.optString("message")}")
            }
            val data = root.getJSONObject("data")

            val stubsArray = data.getJSONArray("surveyStubs")
            val couponsArray = data.getJSONArray("coupons")

            val surveyDao = database.surveyDao()
            val couponDao = database.couponDao()

            // Insert stubs first so coupons pointing at them via
            // issued_to_survey_id can resolve immediately on lookup.
            var stubsRestored = 0
            for (i in 0 until stubsArray.length()) {
                val s = stubsArray.getJSONObject(i)
                val stub = stubJsonToSurvey(s) ?: continue
                val rowId = surveyDao.insertSurveyIfAbsent(stub)
                if (rowId != -1L) stubsRestored++
            }

            var couponsRestored = 0
            for (i in 0 until couponsArray.length()) {
                val c = couponsArray.getJSONObject(i)
                val coupon = couponJsonToEntity(c) ?: continue
                val rowId = couponDao.insertCouponIfAbsent(coupon)
                if (rowId != -1L) couponsRestored++
            }

            Log.d(TAG, "Restored coupons=$couponsRestored stubs=$stubsRestored " +
                    "(received coupons=${couponsArray.length()} stubs=${stubsArray.length()})")

            Result(couponsRestored = couponsRestored, stubsRestored = stubsRestored)
        }
    }

    private fun stubJsonToSurvey(s: JSONObject): Survey? {
        val id = s.optString("id").takeIf { it.isNotBlank() } ?: return null
        val participantId = s.optString("participantId").takeIf { it.isNotBlank() } ?: return null
        val startedAt = parseServerTimestamp(s.optString("startedAt"))
            ?: parseServerTimestamp(s.optString("completedAt"))
            ?: return null
        val language = s.optString("language").takeIf { it.isNotBlank() } ?: "Unknown"
        val referralCouponCode = s.optString("referralCouponCode").takeIf { it.isNotBlank() }
        // paymentConfirmed reflects whether the recruit was eligible and paid.
        // RecruitmentPaymentScreen reads it to classify chain participants —
        // without it, restored stubs hit the "completed but not paid" branch
        // and get labeled INELIGIBLE.
        val paymentConfirmed = if (s.has("paymentConfirmed") && !s.isNull("paymentConfirmed")) {
            s.optBoolean("paymentConfirmed", false)
        } else {
            null
        }
        return Survey(
            id = id,
            subjectId = participantId,
            startDatetime = startedAt,
            language = language,
            referralCouponCode = referralCouponCode,
            paymentConfirmed = paymentConfirmed,
            isCompleted = true,
            isStub = true
        )
    }

    private fun couponJsonToEntity(c: JSONObject): Coupon? {
        val code = c.optString("code").takeIf { it.isNotBlank() } ?: return null
        val issuedBySurveyId = c.optString("issuedBySurveyId").takeIf { it.isNotBlank() }
        val usedBySurveyId = c.optString("usedBySurveyId").takeIf { it.isNotBlank() }
        val issuedAt = parseServerTimestamp(c.optString("issuedAt"))
        val usedAt = parseServerTimestamp(c.optString("usedAt"))
        val status = c.optString("status").takeIf { it.isNotBlank() } ?: "UNUSED"
        val paymentDate = parseServerTimestamp(c.optString("recruitmentPaymentDate"))
        return Coupon(
            couponCode = code,
            issuedToSurveyId = issuedBySurveyId,
            issuedDate = issuedAt,
            usedBySurveyId = usedBySurveyId,
            usedDate = usedAt,
            status = status,
            createdDate = issuedAt ?: System.currentTimeMillis(),
            recruitmentPaymentDate = paymentDate,
            recruitmentPaymentSignature = null
        )
    }

    /**
     * Server emits timestamps in two shapes:
     *   - "2025-12-14T20:17:10.239Z" (ISO 8601, from completed_surveys)
     *   - "2025-12-14 20:17:10"      (SQLite DATETIME, from coupon_usage)
     * Returns epoch millis or null if blank/unparseable.
     */
    private fun parseServerTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US)
                if (!p.contains('X')) fmt.timeZone = TimeZone.getTimeZone("UTC")
                return fmt.parse(raw)?.time
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        Log.w(TAG, "Unparseable timestamp from server: $raw")
        return null
    }

    class RestoreException(message: String) : Exception(message)
}
