package com.dev.salt.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SurveyState(
    val isActive: Boolean = false,
    val surveyId: String? = null,
    val currentQuestionIndex: Int = -1,
    val totalQuestions: Int = 0,
    val hasUnsavedChanges: Boolean = false
)

class SurveyStateManager {
    private val _surveyState = MutableStateFlow(SurveyState())
    val surveyState: StateFlow<SurveyState> = _surveyState.asStateFlow()
    
    fun startSurvey(surveyId: String, totalQuestions: Int) {
        _surveyState.value = SurveyState(
            isActive = true,
            surveyId = surveyId,
            currentQuestionIndex = 0,
            totalQuestions = totalQuestions,
            hasUnsavedChanges = false
        )
    }
    
    fun updateQuestionProgress(questionIndex: Int, hasUnsavedChanges: Boolean = false) {
        val currentState = _surveyState.value
        if (currentState.isActive) {
            _surveyState.value = currentState.copy(
                currentQuestionIndex = questionIndex,
                hasUnsavedChanges = hasUnsavedChanges
            )
        }
    }
    
    fun markUnsavedChanges() {
        val currentState = _surveyState.value
        if (currentState.isActive) {
            _surveyState.value = currentState.copy(hasUnsavedChanges = true)
        }
    }
    
    fun markChangesSaved() {
        val currentState = _surveyState.value
        if (currentState.isActive) {
            _surveyState.value = currentState.copy(hasUnsavedChanges = false)
        }
    }
    
    fun endSurvey() {
        _surveyState.value = SurveyState()
    }
    
    fun isSurveyActive(): Boolean {
        return _surveyState.value.isActive
    }
    
    fun hasUnsavedChanges(): Boolean {
        return _surveyState.value.hasUnsavedChanges
    }
    
    fun getCurrentSurveyId(): String? {
        return _surveyState.value.surveyId
    }
    
    fun getSurveyProgress(): Pair<Int, Int> {
        val state = _surveyState.value
        return Pair(state.currentQuestionIndex + 1, state.totalQuestions)
    }
}

// Singleton instance for global access
object SurveyStateManagerInstance {
    val instance: SurveyStateManager by lazy { SurveyStateManager() }
}