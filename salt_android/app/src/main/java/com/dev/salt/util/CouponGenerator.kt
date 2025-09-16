package com.dev.salt.util

import com.dev.salt.data.Coupon
import com.dev.salt.data.CouponDao
import com.dev.salt.data.CouponStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class CouponGenerator(
    private val couponDao: CouponDao
) {
    companion object {
        private const val COUPON_LENGTH = 6
        private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
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
     */
    private fun generateRandomCode(): String {
        return (1..COUPON_LENGTH)
            .map { CHARS[Random.nextInt(CHARS.length)] }
            .joinToString("")
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
                    status = CouponStatus.ISSUED.name
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
}