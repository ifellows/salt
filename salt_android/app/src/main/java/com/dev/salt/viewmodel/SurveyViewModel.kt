package com.dev.salt.viewmodel
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.salt.data.Question
import com.dev.salt.data.Option
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.data.Survey
import com.dev.salt.data.Answer
import com.dev.salt.data.saveSurvey
import com.dev.salt.randomHash
import com.dev.salt.evaluateJexlScript
import com.dev.salt.upload.SurveyUploadManager
import com.dev.salt.upload.SurveyUploadWorkManager
import com.dev.salt.upload.UploadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import android.content.Context
import com.dev.salt.util.CouponGenerator

class SurveyViewModel(
    private val database: SurveyDatabase,
    private val context: Context? = null,
    private val referralCouponCode: String? = null,
    private val surveyId: String? = null
) : ViewModel() {
    private val _currentQuestion = MutableStateFlow<Triple<Question, List<Option>, Answer?>?>(null)
    val currentQuestion: StateFlow<Triple<Question, List<Option>, Answer?>?> = _currentQuestion

    private val _hasPreviousQuestion = mutableStateOf(false)
    val hasPreviousQuestion: State<Boolean> = _hasPreviousQuestion
    
    private val _generatedCoupons = MutableStateFlow<List<String>>(emptyList())
    val generatedCoupons: StateFlow<List<String>> = _generatedCoupons
    
    private val couponGenerator = CouponGenerator(database.couponDao())
    //public var currentQuestion: Pair<Question, List<Option>>? = null//StateFlow<Pair<Question, List<Option>>?> = _currentQuestion
    public var survey : Survey? = null
    public var questions: List<Question> = emptyList()
    //public var answers: MutableList<String?> = MutableList<String?>(0, { null })
    public var currentQuestionIndex = -1

    init {
        viewModelScope.launch {
            Log.d("SurveyViewModel", "Init called with surveyId=$surveyId, referralCouponCode=$referralCouponCode")
            loadQuestions()
            
            // Load existing survey if surveyId is provided, otherwise create new
            survey = if (surveyId != null) {
                val loadedSurvey = database.surveyDao().getSurveyById(surveyId)
                Log.d("SurveyViewModel", "Loaded existing survey: id=${loadedSurvey?.id}, language=${loadedSurvey?.language}")
                loadedSurvey ?: makeNewSurvey("en")
            } else {
                Log.d("SurveyViewModel", "Creating new survey")
                makeNewSurvey("en")
            }
            
            // Mark the referral coupon as used if one was provided
            referralCouponCode?.let { code ->
                try {
                    database.couponDao().markCouponUsed(
                        code = code,
                        surveyId = survey!!.id,
                        usedDate = System.currentTimeMillis()
                    )
                    Log.i("SurveyViewModel", "Marked coupon $code as used for survey ${survey!!.id}")
                } catch (e: Exception) {
                    Log.e("SurveyViewModel", "Failed to mark coupon as used", e)
                }
            }
            
            loadSurvey(survey!!)
            loadNextQuestion()
        }
    }

    private fun makeNewSurvey(language: String): Survey {
        val survey: Survey = Survey(
            language = language, 
            subjectId = randomHash(), 
            startDatetime = System.currentTimeMillis(),
            referralCouponCode = referralCouponCode
        )
        return survey
    }

    private fun loadSurvey(surv: Survey){
        survey = surv
        survey?.populateFields(database.surveyDao())
        questions = survey?.questions ?: emptyList()
        Log.d("SurveyViewModel", "Loaded survey with language: ${survey?.language}")
        Log.d("SurveyViewModel", "Number of questions loaded: ${questions.size}")
        
        // Also check what languages have questions
        val allLanguages = database.surveyDao().getDistinctLanguages()
        Log.d("SurveyViewModel", "Available languages in DB: $allLanguages")
        
        questions.forEach { q ->
            Log.d("SurveyViewModel", "Question: id=${q.id}, questionId=${q.questionId}, language=${q.questionLanguage}, text=${q.statement.take(50)}")
        }
    }

    private fun loadQuestions() {
        questions = database.surveyDao().getAllQuestions()
    }

    private fun updateCurrentQuestion() {
        if(survey == null){
            return
        }
        if(currentQuestionIndex > 0){
            _hasPreviousQuestion.value = true
        }else{
            _hasPreviousQuestion.value = false
        }
        if (currentQuestionIndex < questions.size) {
            val question = questions[currentQuestionIndex]
            val options = database.surveyDao().getOptionsForQuestion(question.id)
            _currentQuestion.value = Triple(question, options, survey!!.answers[currentQuestionIndex])
            //currentQuestion = Pair(question, options)
        } else {
            _currentQuestion.value = null
            //currentQuestion = null // Survey completed
            
            Log.i("SurveyViewModel", "Survey completed! Index: $currentQuestionIndex, Questions size: ${questions.size}")
            
            // Survey is completed - save and trigger upload
            survey?.let { completedSurvey ->
                viewModelScope.launch {
                    try {
                        Log.i("SurveyViewModel", "Processing completed survey ${completedSurvey.id}")
                        
                        // Save the survey answers (the survey itself was created earlier)
                        Log.i("SurveyViewModel", "Saving survey answers for ${completedSurvey.id}")
                        completedSurvey.answers.forEach { answer ->
                            database.surveyDao().insertAnswer(answer)
                        }
                        
                        // Check if coupons have already been generated for this survey
                        val existingCoupons = database.couponDao().getCouponsIssuedToSurvey(completedSurvey.id)
                        
                        if (existingCoupons.isEmpty()) {
                            // Generate coupons for completed survey
                            try {
                                // Get the number of coupons to issue from facility config
                                val facilityConfig = database.facilityConfigDao().getFacilityConfig()
                                val couponsToIssue = facilityConfig?.couponsToIssue ?: 3
                                
                                Log.i("SurveyViewModel", "Generating $couponsToIssue coupons for survey ${completedSurvey.id}")
                                val coupons = couponGenerator.issueCouponsForSurvey(completedSurvey.id, couponsToIssue)
                                _generatedCoupons.value = coupons
                                Log.i("SurveyViewModel", "Generated ${coupons.size} coupons for survey ${completedSurvey.id}: $coupons")
                            } catch (e: Exception) {
                                Log.e("SurveyViewModel", "Failed to generate coupons for survey ${completedSurvey.id}", e)
                                // Still set empty list so navigation can proceed
                                _generatedCoupons.value = emptyList()
                            }
                        } else {
                            Log.i("SurveyViewModel", "Found ${existingCoupons.size} existing coupons for survey ${completedSurvey.id}")
                            _generatedCoupons.value = existingCoupons.map { it.couponCode }
                            Log.i("SurveyViewModel", "Using existing coupons: ${existingCoupons.map { it.couponCode }}")
                        }
                        
                        // Upload is now triggered from StaffValidationScreen after all steps are complete
                        // This ensures sample collection status is recorded before upload
                        Log.i("SurveyViewModel", "Survey ready for upload: ${completedSurvey.id} (upload will happen after staff validation)")
                    } catch (e: Exception) {
                        Log.e("SurveyViewModel", "Error completing survey", e)
                    }
                }
            }
        }
    }

    fun evaluateCurrentQuestionPreScript(): String{
        val script = _currentQuestion?.value?.first?.preScript
        if(script.isNullOrBlank()) {
            return "continue"
        }
        val context = buildJexlContext()
        var result = "continue" // Default action if no script or evaluation fails
        try {
            val ret = evaluateJexlScript(script, context)
            Log.d("JEXLResult", "Result of preScript: $result")
            if(ret == null || ret == false) {
                result = "skip"
            } else if(ret == true){
                result = "continue"
            } else{
                result = ret.toString()
            }
        } catch (e: Exception) {
            Log.e("JEXLError", "Error evaluating preScript: ${e.message}")
            result ="continue" // Default action on error
        }
        return result
    }

    fun evaluateCurrentQuestionValidationScript(): Boolean{
        val script = _currentQuestion?.value?.first?.validationScript
        if(script.isNullOrBlank()) {
            return true
        }
        val context = buildJexlContext()
        var result = true // Default action if no script or evaluation fails
        try {
            val ret = evaluateJexlScript(script, context)
            Log.d("JEXLResult", "Result of preScript: $result")
            if(ret == null || ret == false) {
                result = false
            } else{
                result = true
            }
        } catch (e: Exception) {
            Log.e("JEXLError", "Error evaluating preScript: ${e.message}")
            result = true // Default action on error
        }
        return result
    }

    // NEW: Helper function to build the JEXL context
    private fun buildJexlContext(): Map<String, Any> {
        val context = mutableMapOf<String, Any>()
        val ans = survey!!.answers
        val qus = survey!!.questions
        for(i in 0 until ans.size){
            if(ans[i].getValue() != null){
                val av : Any = ans[i].getValue()!!
                context[qus[i].questionShortName] = av
                Log.d("JEXLContext", "Adding to context: ${qus[i].questionShortName} = ${ans[i].getValue()}")
            }

        }
        // You can add other global variables to the context if needed
        // context["userAge"] = 25 // Example
        Log.d("SurveyViewModel", "JEXL Context built: $context")
        return context
    }
    //@Composable
    //@OptIn(ExperimentalMaterial3Api::class)
    fun loadNextQuestion() : String? {
        // Check multi-select validation first
        val multiSelectError = validateMultiSelectAnswer()
        if (multiSelectError != null) {
            Log.d("SurveyViewModel", "Multi-select validation failed: $multiSelectError")
            return multiSelectError
        }
        
        val valid = evaluateCurrentQuestionValidationScript()
        if(!valid){
            Log.d("SurveyViewModel", "Validation failed for question at index $currentQuestionIndex")
            val errorText = _currentQuestion?.value?.first?.validationErrorText ?: "Invalid Answer"
            return errorText
        }
        currentQuestionIndex++
        updateCurrentQuestion()
        val action = evaluateCurrentQuestionPreScript()
        if(action == "skip"){
            loadNextQuestion()
        }
        return null
    }

    fun loadPreviousQuestion() {
        currentQuestionIndex--
        updateCurrentQuestion()
        val action = evaluateCurrentQuestionPreScript()
        if(action == "skip"){
            loadPreviousQuestion()
        }
    }

    fun answerQuestion(optionId: Int) {
        if(survey == null)
            return

        val options = _currentQuestion.value?.second ?: return
        val option = options.find { it.id == optionId }
        val text = option?.text ?: return
        val optionIndex = option?.optionQuestionIndex
        survey!!.answers[currentQuestionIndex].optionQuestionIndex = optionIndex
        survey!!.answers[currentQuestionIndex].numericValue = optionIndex?.toDouble()
        survey!!.answers[currentQuestionIndex].answerPrimaryLanguageText = text
        _currentQuestion.value = _currentQuestion.value?.copy(third = survey!!.answers[currentQuestionIndex] )
        viewModelScope.launch {
            loadNextQuestion()
        }
    }

    // NEW: Overloaded answerQuestion for text/numeric input
    fun answerQuestion(textAnswer: String) {
        survey!!.answers[currentQuestionIndex].numericValue = textAnswer.toDoubleOrNull()
        survey!!.answers[currentQuestionIndex].answerPrimaryLanguageText = textAnswer
        _currentQuestion.value = _currentQuestion.value?.copy(third = survey!!.answers[currentQuestionIndex] )
        //viewModelScope.launch {
        //    loadNextQuestion()
        //}
    }
    
    // Handle multi-select option toggle
    fun toggleMultiSelectOption(optionIndex: Int) {
        if (survey == null) return
        
        val currentQuestion = _currentQuestion.value?.first ?: return
        val currentAnswer = survey!!.answers[currentQuestionIndex]
        
        // Get current selected indices
        val selectedIndices = currentAnswer.getSelectedIndices().toMutableList()
        
        if (selectedIndices.contains(optionIndex)) {
            // Remove if already selected
            selectedIndices.remove(optionIndex)
        } else {
            // Check if we can add more selections
            val maxSelections = currentQuestion.maxSelections
            if (maxSelections != null && selectedIndices.size >= maxSelections) {
                // Already at max selections, don't add
                return
            }
            selectedIndices.add(optionIndex)
        }
        
        // Create a new Answer object with updated selections to ensure UI recomposition
        val updatedAnswer = currentAnswer.copy(
            isMultiSelect = true,
            multiSelectIndices = selectedIndices.sorted().joinToString(",")
        )
        
        // Update the actual answer in the survey
        survey!!.answers[currentQuestionIndex] = updatedAnswer
        
        Log.d("SurveyViewModel", "Toggle multi-select: optionIndex=$optionIndex, selectedIndices=$selectedIndices, multiSelectIndices=${updatedAnswer.multiSelectIndices}")
        
        // Force recomposition by creating a new Triple with the new answer object
        // This ensures the UI detects the change because the answer reference has changed
        val updatedTriple = Triple(
            _currentQuestion.value!!.first,
            _currentQuestion.value!!.second,
            updatedAnswer
        )
        _currentQuestion.value = updatedTriple
    }
    
    // Validate multi-select answer
    fun validateMultiSelectAnswer(): String? {
        val currentQuestion = _currentQuestion.value?.first ?: return null
        
        // Only validate if it's a multi_select question
        if (currentQuestion.questionType != "multi_select") return null
        
        val currentAnswer = survey!!.answers[currentQuestionIndex]
        val selectedCount = currentAnswer.getSelectedIndices().size
        val minSelections = currentQuestion.minSelections
        val maxSelections = currentQuestion.maxSelections
        
        Log.d("SurveyViewModel", "Multi-select validation: selected=$selectedCount, min=$minSelections, max=$maxSelections")
        
        if (minSelections != null && selectedCount < minSelections) {
            return currentQuestion.validationErrorText ?: 
                "Please select at least $minSelections options"
        }
        
        if (maxSelections != null && selectedCount > maxSelections) {
            return currentQuestion.validationErrorText ?: 
                "Please select at most $maxSelections options"
        }
        
        return null
    }

    /**
     * Get the current language being used in the survey
     */
    fun getCurrentLanguage(): String {
        return survey?.language ?: "en"
    }

    /**
     * Get the staff validation message in the specified language
     * This would typically come from the survey configuration
     */
    fun getStaffValidationMessage(language: String): String? {
        // TODO: Get this from actual survey configuration once synced from server
        // For now, return a default message
        return when (language) {
            "es" -> "Por favor devuelva la tableta al miembro del personal que se la entregó"
            "fr" -> "Veuillez remettre la tablette au membre du personnel qui vous l'a donnée"
            else -> "Please hand the tablet back to the staff member who gave it to you"
        }
    }

}

