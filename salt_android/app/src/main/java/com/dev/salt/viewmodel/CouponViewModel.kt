package com.dev.salt.viewmodel

import com.dev.salt.logging.AppLogger as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dev.salt.data.CouponStatus
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CouponViewModel(
    private val database: SurveyDatabase
) : ViewModel() {
    
    private val _hasCoupon = MutableStateFlow<Boolean?>(null)
    val hasCoupon: StateFlow<Boolean?> = _hasCoupon
    
    private val _couponCode = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode
    
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError
    
    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating
    
    // Store the validated coupon for later use in survey
    var validatedCouponCode: String? = null
        private set
    
    companion object {
        private const val TAG = "CouponViewModel"
        const val COUPON_LENGTH = 6 // Expected length of coupon codes
    }
    
    fun setHasCoupon(value: Boolean?) {
        _hasCoupon.value = value
        if (value == false) {
            // Clear coupon data if they don't have one
            _couponCode.value = ""
            _validationError.value = null
            validatedCouponCode = null
        }
    }
    
    fun setCouponCode(code: String) {
        // Limit to expected coupon length and uppercase
        _couponCode.value = code.take(COUPON_LENGTH).uppercase()
    }
    
    fun clearError() {
        _validationError.value = null
    }
    
    fun setNoCouponError() {
        _validationError.value = "Coupon required for participation"
    }
    
    fun validateCoupon(onResult: (Boolean) -> Unit) {
        val code = _couponCode.value.trim()
        
        // Basic validation
        if (code.length != COUPON_LENGTH) {
            _validationError.value = "Coupon code must be $COUPON_LENGTH characters"
            onResult(false)
            return
        }
        
        viewModelScope.launch {
            _isValidating.value = true
            _validationError.value = null
            
            try {
                val coupon = database.couponDao().getCouponByCode(code)
                
                when {
                    coupon == null -> {
                        _validationError.value = "Invalid coupon code"
                        Log.w(TAG, "Coupon not found: $code")
                        onResult(false)
                    }
                    coupon.status == CouponStatus.USED.name -> {
                        _validationError.value = "This coupon has already been used"
                        Log.w(TAG, "Coupon already used: $code")
                        onResult(false)
                    }
                    coupon.status != CouponStatus.ISSUED.name -> {
                        _validationError.value = "This coupon has not been issued yet"
                        Log.w(TAG, "Coupon not issued: $code, status: ${coupon.status}")
                        onResult(false)
                    }
                    else -> {
                        // Valid coupon - store for use but don't mark as used yet
                        // It will be marked as used when the survey is actually created
                        validatedCouponCode = code
                        Log.i(TAG, "Coupon validated: $code (will be marked as used when survey starts)")
                        onResult(true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error validating coupon", e)
                _validationError.value = "Error validating coupon. Please try again."
                onResult(false)
            } finally {
                _isValidating.value = false
            }
        }
    }
}

class CouponViewModelFactory(
    private val database: SurveyDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CouponViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CouponViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}