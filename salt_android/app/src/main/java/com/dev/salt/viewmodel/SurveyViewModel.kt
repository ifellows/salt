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
import com.dev.salt.generateWalkInSubjectId
import com.dev.salt.evaluateJexlScript
import com.dev.salt.upload.SurveyUploadManager
import com.dev.salt.upload.SurveyUploadWorkManager
import com.dev.salt.upload.UploadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    // Section tracking
    private var sections: List<com.dev.salt.data.Section> = emptyList()
    private var currentSection: com.dev.salt.data.Section? = null

    // Eligibility check flow state
    private val _needsEligibilityCheck = MutableStateFlow(false)
    val needsEligibilityCheck: StateFlow<Boolean> = _needsEligibilityCheck

    private val _isEligible = MutableStateFlow<Boolean?>(null)
    val isEligible: StateFlow<Boolean?> = _isEligible

    // Track if we need to show HIV test after eligibility
    private val _needsHivTestAfterEligibility = MutableStateFlow(false)
    val needsHivTestAfterEligibility: StateFlow<Boolean> = _needsHivTestAfterEligibility

    // Track if HIV test has been completed
    private val _hivTestCompleted = MutableStateFlow(false)
    val hivTestCompleted: StateFlow<Boolean> = _hivTestCompleted

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
        // Use coupon code as subject ID if participant has one, otherwise generate walk-in ID
        val subjectId = if (!referralCouponCode.isNullOrEmpty()) {
            // Participant has a coupon - use it as their subject ID
            referralCouponCode
        } else {
            // Walk-in participant - generate ID with "W" prefix to avoid collisions
            generateWalkInSubjectId()
        }

        // Load eligibility script from SurveyConfig
        val eligibilityScript = database.surveyConfigDao().getSurveyConfig()?.eligibilityScript
        Log.d("SurveyViewModel", "Loaded eligibility script from SurveyConfig: $eligibilityScript")

        val survey: Survey = Survey(
            language = language,
            subjectId = subjectId,
            startDatetime = System.currentTimeMillis(),
            referralCouponCode = referralCouponCode,
            eligibilityScript = eligibilityScript
        )

        Log.d("SurveyViewModel", "Created survey with subjectId=$subjectId, referralCoupon=$referralCouponCode, eligibilityScript=$eligibilityScript")
        return survey
    }

    private fun loadSurvey(surv: Survey){
        survey = surv

        // Load eligibility script from SurveyConfig if not already set
        if (survey?.eligibilityScript.isNullOrEmpty()) {
            val script = database.surveyConfigDao().getSurveyConfig()?.eligibilityScript
            survey?.eligibilityScript = script
            Log.d("SurveyViewModel", "Loaded eligibility script from SurveyConfig in loadSurvey: $script")
        }

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

        // Load sections
        viewModelScope.launch {
            try {
                sections = database.sectionDao().getAllSections()
                Log.d("SurveyViewModel", "Loaded ${sections.size} sections")

                // Determine initial section
                if (sections.isNotEmpty()) {
                    currentSection = sections.firstOrNull { it.sectionType == "eligibility" }
                        ?: sections.firstOrNull()
                    Log.d("SurveyViewModel", "Starting with section: ${currentSection?.name} (${currentSection?.sectionType})")
                }
            } catch (e: Exception) {
                Log.e("SurveyViewModel", "Error loading sections", e)
            }
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

            // Check if we've moved to a new section
            checkSectionTransition(question)

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

    // Evaluate skip-to logic for current question
    private fun evaluateSkipToLogic(): String? {
        val currentQ = _currentQuestion?.value?.first ?: return null
        val skipToScript = currentQ.skipToScript
        val skipToTarget = currentQ.skipToTarget

        // Both script and target must be present
        if (skipToScript.isNullOrBlank() || skipToTarget.isNullOrBlank()) {
            return null
        }

        val context = buildJexlContext()
        return try {
            val result = evaluateJexlScript(skipToScript, context)
            Log.d("SurveyViewModel", "Skip-to script evaluation: $result")

            if (result == true) {
                skipToTarget // Return the target question short name
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SurveyViewModel", "Error evaluating skip-to script: ${e.message}")
            null // Don't skip on error
        }
    }

    // Jump to a specific question by short name
    private fun jumpToQuestion(targetShortName: String) {
        val questions = survey?.questions ?: return

        // Find the target question index
        val targetIndex = questions.indexOfFirst { it.questionShortName == targetShortName }

        if (targetIndex >= 0) {
            Log.d("SurveyViewModel", "Jumping from index $currentQuestionIndex to $targetIndex (question: $targetShortName)")
            currentQuestionIndex = targetIndex
            updateCurrentQuestion()

            // Check if the target question should be skipped
            val action = evaluateCurrentQuestionPreScript()
            if (action == "skip") {
                loadNextQuestion()
            }
        } else {
            Log.w("SurveyViewModel", "Skip-to target not found: $targetShortName")
            // If target not found, just proceed normally
            currentQuestionIndex++
            updateCurrentQuestion()
        }
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

        // Check for skip-to logic after validation passes
        val skipToTarget = evaluateSkipToLogic()
        if (skipToTarget != null) {
            Log.d("SurveyViewModel", "Skip-to logic triggered, jumping to: $skipToTarget")
            jumpToQuestion(skipToTarget)
            return null
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

    /**
     * Check if we've transitioned between sections
     */
    private fun checkSectionTransition(question: Question) {
        viewModelScope.launch {
            try {
                // Get the section for the current question
                val newSection = sections.firstOrNull { it.id == question.sectionId }

                if (newSection != null && newSection != currentSection) {
                    Log.d("SurveyViewModel", "Transitioning from section ${currentSection?.name} to ${newSection.name}")

                    // Check if we're leaving the eligibility section
                    if (currentSection?.sectionType == "eligibility" && newSection.sectionType != "eligibility") {
                        Log.d("SurveyViewModel", "Completed eligibility section, checking eligibility")
                        checkEligibility()
                    }

                    currentSection = newSection
                }
            } catch (e: Exception) {
                Log.e("SurveyViewModel", "Error checking section transition", e)
            }
        }
    }

    /**
     * Clear the eligibility check flag to continue with the survey
     */
    fun clearEligibilityCheck() {
        _needsEligibilityCheck.value = false
    }

    /**
     * Evaluate the eligibility script to determine if the participant is eligible
     */
    private fun checkEligibility() {
        val eligibilityScript = survey?.eligibilityScript
        if (eligibilityScript.isNullOrBlank()) {
            Log.d("SurveyViewModel", "No eligibility script defined, defaulting to eligible")
            _isEligible.value = true
            _needsEligibilityCheck.value = false
            return
        }

        try {
            val context = buildJexlContext()
            val result = evaluateJexlScript(eligibilityScript, context)

            val eligible = when (result) {
                is Boolean -> result
                is Number -> result.toInt() != 0
                is String -> result.equals("true", ignoreCase = true) || result == "1"
                null -> false
                else -> result.toString().equals("true", ignoreCase = true)
            }

            Log.d("SurveyViewModel", "Eligibility check result: $eligible (script: $eligibilityScript)")
            _isEligible.value = eligible
            _needsEligibilityCheck.value = true

            // Check if HIV test is needed after eligibility
            if (eligible && !_hivTestCompleted.value) {
                viewModelScope.launch(Dispatchers.IO) {
                    val config = database.surveyConfigDao().getSurveyConfig()
                    if (config?.hivRapidTestEnabled == true) {
                        Log.d("SurveyViewModel", "HIV test enabled and participant eligible - will need HIV test")
                        _needsHivTestAfterEligibility.value = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SurveyViewModel", "Error evaluating eligibility script", e)
            // Default to eligible on error to avoid blocking participants
            _isEligible.value = true
            _needsEligibilityCheck.value = false
        }
    }

    /**
     * Called when eligibility check is acknowledged and survey should continue
     */
    fun acknowledgeEligibilityCheck() {
        _needsEligibilityCheck.value = false
        // Continue with next question
        // The navigation will be handled by the SurveyScreen
    }

    /**
     * Called when HIV test has been completed and survey should continue
     */
    fun markHivTestCompleted() {
        _hivTestCompleted.value = true
        _needsHivTestAfterEligibility.value = false
        Log.d("SurveyViewModel", "HIV test marked as completed")
    }

    /**
     * Reset eligibility check state (useful for testing)
     */
    fun resetEligibilityCheck() {
        _needsEligibilityCheck.value = false
        _isEligible.value = null
    }

}

