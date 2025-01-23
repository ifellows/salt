package com.dev.salt.viewmodel
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.salt.data.Question
import com.dev.salt.data.Option
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.State

class SurveyViewModel(private val database: SurveyDatabase) : ViewModel() {
    private val _currentQuestion = MutableStateFlow<Triple<Question, List<Option>, String?>?>(null)
    val currentQuestion: StateFlow<Triple<Question, List<Option>, String?>?> = _currentQuestion

    private val _hasPreviousQuestion = mutableStateOf(false)
    val hasPreviousQuestion: State<Boolean> = _hasPreviousQuestion
    //public var currentQuestion: Pair<Question, List<Option>>? = null//StateFlow<Pair<Question, List<Option>>?> = _currentQuestion

    public var questions: List<Question> = emptyList()
    public var answers: MutableList<String?> = MutableList<String?>(0, { null })
    public var currentQuestionIndex = -1

    init {
        viewModelScope.launch {
            loadQuestions()
            loadNextQuestion()
        }
    }

    private fun loadQuestions() {
        questions = database.surveyDao().getAllQuestions()
        for (question in questions) {
            answers.addLast(null)
        }
    }

    private fun updateCurrentQuestion() {
        if(currentQuestionIndex > 0){
            _hasPreviousQuestion.value = true
        }else{
            _hasPreviousQuestion.value = false
        }
        if (currentQuestionIndex < questions.size) {
            val question = questions[currentQuestionIndex]
            val options = database.surveyDao().getOptionsForQuestion(question.id)
            _currentQuestion.value = Triple(question, options, answers[currentQuestionIndex])
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
        // Here you would typically save the user's answer
        // For this example, we'll just move to the next question
        val options = _currentQuestion.value?.second ?: return
        val text = options.find { it.id == optionId }?.text ?: return
        answers[currentQuestionIndex] = text
        _currentQuestion.value = _currentQuestion.value?.copy(third = text )
        viewModelScope.launch {
            loadNextQuestion()
        }
    }


}

