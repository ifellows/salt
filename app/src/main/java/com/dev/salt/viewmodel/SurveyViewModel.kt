package com.dev.salt.viewmodel
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.salt.data.Question
import com.dev.salt.data.Option
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.data.Survey
import com.dev.salt.data.Answer
import com.dev.salt.randomHash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.State

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

    fun loadNextQuestion() {
        currentQuestionIndex++
        updateCurrentQuestion()
    }

    fun loadPreviousQuestion() {
        currentQuestionIndex--
        updateCurrentQuestion()
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


}

