package com.dev.salt.util

import com.dev.salt.data.Coupon
import com.dev.salt.data.CouponDao
import com.dev.salt.data.CouponStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class CouponGenerator(
    private val couponDao: CouponDao,
    private val surveyDao: com.dev.salt.data.SurveyDao
) {
    companion object {
        private const val COUPON_LENGTH = 6
        // Cleaner character set excluding ambiguous characters:
        // Removed: 0(zero), O, 1(one), I, L, Z, 2(two), 5(five), S, 8(eight), B
        // This reduces data entry errors by eliminating look-alike characters
        private const val CHARS = "ACDEFGHJKMNPQRTUVWXY34679"
        // Same as CHARS but without W (reserved for walk-in participants)
        private const val CHARS_NO_W = "ACDEFGHJKMNPQRTUVXY34679"
        private const val DEFAULT_COUPONS_TO_ISSUE = 3 // Number of coupons to issue per completed survey
    }
    
    /**
     * Generates a unique coupon code
     */
    suspend fun generateUniqueCouponCode(): String {
        return withContext(Dispatchers.IO) {
            var code: String
            var attempts = 0
            val maxAttempts = 100
            
            do {
                code = generateRandomCode()
                attempts++
                if (attempts > maxAttempts) {
                    throw IllegalStateException("Unable to generate unique coupon code after $maxAttempts attempts")
                }
            } while (couponDao.getCouponByCode(code) != null)
            
            code
        }
    }
    
    /**
     * Generates a random coupon code
     * First character cannot be 'W' (reserved for walk-in participants)
     */
    private fun generateRandomCode(): String {
        // First character: cannot be W (reserved for walk-ins)
        val firstChar = CHARS_NO_W[Random.nextInt(CHARS_NO_W.length)]

        // Remaining characters: can be any from the clean set
        val remainingChars = (2..COUPON_LENGTH)
            .map { CHARS[Random.nextInt(CHARS.length)] }
            .joinToString("")

        return "$firstChar$remainingChars"
    }
    
    /**
     * Issues new coupons for a completed survey
     * @param surveyId The ID of the completed survey
     * @param numberOfCoupons Number of coupons to issue (default: 2)
     * @return List of generated coupon codes
     */
    suspend fun issueCouponsForSurvey(
        surveyId: String,
        numberOfCoupons: Int = DEFAULT_COUPONS_TO_ISSUE
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val issuedCoupons = mutableListOf<String>()
            val currentTime = System.currentTimeMillis()
            
            repeat(numberOfCoupons) {
                val code = generateUniqueCouponCode()
                val coupon = Coupon(
                    couponCode = code,
                    issuedToSurveyId = surveyId,
                    issuedDate = currentTime,
                    status = CouponStatus.UNUSED.name
                )

                couponDao.insertCoupon(coupon)
                issuedCoupons.add(code)
            }
            
            issuedCoupons
        }
    }
    
    /**
     * Pre-generates unused coupons for the system
     * This can be used to maintain a pool of available coupons
     */
    suspend fun preGenerateCoupons(count: Int): List<String> {
        return withContext(Dispatchers.IO) {
            val generatedCoupons = mutableListOf<String>()
            
            repeat(count) {
                val code = generateUniqueCouponCode()
                val coupon = Coupon(
                    couponCode = code,
                    status = CouponStatus.UNUSED.name
                )
                
                couponDao.insertCoupon(coupon)
                generatedCoupons.add(code)
            }
            
            generatedCoupons
        }
    }
    
    /**
     * Gets available unused coupons and marks them as issued for a survey
     */
    suspend fun assignUnusedCouponsToSurvey(
        surveyId: String,
        numberOfCoupons: Int = DEFAULT_COUPONS_TO_ISSUE
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val unusedCoupons = couponDao.getCouponsByStatus(CouponStatus.UNUSED.name)
                .take(numberOfCoupons)

            if (unusedCoupons.size < numberOfCoupons) {
                // Not enough unused coupons, generate new ones
                val neededCoupons = numberOfCoupons - unusedCoupons.size
                val newCoupons = issueCouponsForSurvey(surveyId, neededCoupons)

                // Mark existing unused coupons as issued
                unusedCoupons.forEach { coupon ->
                    couponDao.markCouponIssued(
                        code = coupon.couponCode,
                        surveyId = surveyId,
                        issuedDate = System.currentTimeMillis()
                    )
                }

                unusedCoupons.map { it.couponCode } + newCoupons
            } else {
                // Mark unused coupons as issued
                val issuedCodes = mutableListOf<String>()
                unusedCoupons.forEach { coupon ->
                    couponDao.markCouponIssued(
                        code = coupon.couponCode,
                        surveyId = surveyId,
                        issuedDate = System.currentTimeMillis()
                    )
                    issuedCodes.add(coupon.couponCode)
                }
                issuedCodes
            }
        }
    }

    /**
     * Generates a unique subject ID for walk-in participants (without coupon)
     * Format: W[cleaner-charset]{5}
     * Example: "WAC3K7"
     * The "W" prefix ensures no collision with coupon codes
     * Uses cleaner character set excluding ambiguous characters (0,O,1,I,L,Z,2,5,S,8,B)
     * Checks database to ensure uniqueness across all surveys
     */
    suspend fun generateUniqueWalkInSubjectId(): String {
        return withContext(Dispatchers.IO) {
            var subjectId: String
            var attempts = 0
            val maxAttempts = 100

            do {
                subjectId = generateWalkInSubjectId()
                attempts++
                if (attempts > maxAttempts) {
                    throw IllegalStateException("Unable to generate unique walk-in subject ID after $maxAttempts attempts")
                }
            } while (surveyDao.countSurveysWithSubjectId(subjectId) > 0)

            subjectId
        }
    }

    /**
     * Generates a random walk-in subject ID (private helper)
     * Does not check for uniqueness - use generateUniqueWalkInSubjectId() instead
     */
    private fun generateWalkInSubjectId(): String {
        val randomPart = (1..5)
            .map { CHARS[Random.nextInt(CHARS.length)] }
            .joinToString("")
        return "W$randomPart"
    }
}