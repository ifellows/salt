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
import com.dev.salt.randomHash
import com.dev.salt.evaluateJexlScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

class SurveyViewModel(private val database: SurveyDatabase) : ViewModel() {
    private val _currentQuestion = MutableStateFlow<Triple<Question, List<Option>, Answer?>?>(null)
    val currentQuestion: StateFlow<Triple<Question, List<Option>, Answer?>?> = _currentQuestion

    private val _hasPreviousQuestion = mutableStateOf(false)
    val hasPreviousQuestion: State<Boolean> = _hasPreviousQuestion
    //public var currentQuestion: Pair<Question, List<Option>>? = null//StateFlow<Pair<Question, List<Option>>?> = _currentQuestion
    public var survey : Survey? = null
    public var questions: List<Question> = emptyList()
    //public var answers: MutableList<String?> = MutableList<String?>(0, { null })
    public var currentQuestionIndex = -1

    init {
        viewModelScope.launch {
            loadQuestions()
            survey = makeNewSurvey("en")
            loadSurvey(survey!!)
            loadNextQuestion()
        }
    }

    private fun makeNewSurvey(language: String): Survey {
        val survey: Survey = Survey(language = language, subjectId = randomHash(), startDatetime = System.currentTimeMillis())
        return survey
    }

    private fun loadSurvey(surv: Survey){
        survey = surv
        survey?.populateFields(database.surveyDao())
        questions = survey?.questions ?: emptyList()
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
        val valid = evaluateCurrentQuestionValidationScript()
        if(!valid){
            Log.d("SurveyViewModel", "Validation failed for question at index $currentQuestionIndex")
            /*AlertDialog(
                onDismissRequest = { /* Dismiss logic */ },
                title = { Text("Invalid Answer") },
                text = { Text("invalid") },
                confirmButton = { /* Confirm button logic */ }
            )*/
            return "invalid"
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


}

