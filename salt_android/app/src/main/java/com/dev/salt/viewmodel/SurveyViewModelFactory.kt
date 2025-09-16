package com.dev.salt.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dev.salt.data.SurveyDatabase

class SurveyViewModelFactory(
    private val database: SurveyDatabase,
    private val context: Context? = null,
    private val referralCouponCode: String? = null,
    private val surveyId: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SurveyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SurveyViewModel(database, context, referralCouponCode, surveyId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}