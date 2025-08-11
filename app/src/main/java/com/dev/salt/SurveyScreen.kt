package com.dev.salt

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dev.salt.viewmodel.SurveyViewModel
import com.dev.salt.playAudio
import com.dev.salt.session.SurveyStateManagerInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SurveyScreen(viewModel: SurveyViewModel, coroutineScope: CoroutineScope) {
    //var currentQuestion = viewModel.currentQuestion
    val context = LocalContext.current
    val currentQuestion by viewModel.currentQuestion.collectAsState()
    var highlightedButtonIndex by remember { mutableStateOf<Int?>(null) }
    //var mediaPlayer: MediaPlayer? = remember { null }
    var currentMediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    
    val surveyStateManager = SurveyStateManagerInstance.instance
    
    // Track survey state
    LaunchedEffect(viewModel.survey) {
        viewModel.survey?.let { survey ->
            surveyStateManager.startSurvey(survey.id, viewModel.questions.size)
        }
    }
    
    // Update question progress
    LaunchedEffect(viewModel.currentQuestionIndex) {
        if (viewModel.currentQuestionIndex >= 0) {
            surveyStateManager.updateQuestionProgress(viewModel.currentQuestionIndex)
        }
    }
    
    // End survey when completed
    LaunchedEffect(currentQuestion) {
        if (currentQuestion == null && viewModel.currentQuestionIndex >= 0) {
            surveyStateManager.endSurvey()
        }
    }

    // State for the text field input, resets when question ID changes
    var textInputValue by rememberSaveable(currentQuestion?.first?.id) {
        mutableStateOf(
            // MODIFIED: Initialize with existing text answer if available
            currentQuestion?.let { (q, _, ans) ->
                if (q.questionType != "multiple_choice") ans?.getValue(false)?.toString() else ""
            } ?: ""
        )
    }
    
    // Track unsaved changes
    LaunchedEffect(textInputValue) {
        if (textInputValue.isNotEmpty()) {
            surveyStateManager.markUnsavedChanges()
        }
    }

    // Update textInputValue when currentQuestionData changes and it's a text-based answer
    // This handles loading previous answers when navigating back/forth
    LaunchedEffect(currentQuestion) {
        currentQuestion?.let { (question, _, answer) ->
            if (question.questionType != "multiple_choice") {
                textInputValue = answer?.getValue(false)?.toString() ?: ""
            }
        }
    }

    var errorMessageForDialog by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessageForDialog) {
        if (errorMessageForDialog != null) {
            showDialog = true // Trigger the dialog to show
        }
        // You might not want to set showDialog = false here,
        // as the dialog's own dismiss actions will handle that.
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                errorMessageForDialog = null // Clear the error when dialog is dismissed
            },
            title = { Text("❌") }, // Or a more dynamic title
            text = { Text(errorMessageForDialog ?: "An error occurred.") }, // Use the state variable
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        errorMessageForDialog = null // Clear the error
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    suspend fun playCurrentQuestion(){
        currentQuestion?.let { (question, options, _) ->
            // Stop and release previous MediaPlayer
            try {
                currentMediaPlayer?.stop()
                currentMediaPlayer?.release()
                currentMediaPlayer = null
            } catch (e: Exception){ }

            // Play question audio and wait for completion
            currentMediaPlayer = playAudio(context, question.audioFileName) // Store new MediaPlayer
            currentMediaPlayer?.let { player ->
                player.setOnCompletionListener {
                    it.release()
                    currentMediaPlayer = null // Reset after completion
                }
                player.start()
                player.awaitCompletion() // Suspend until completion
            }

            // Play option audios sequentially
            for (option in options) {
                withContext(Dispatchers.Main) {
                    highlightedButtonIndex = option.id
                }
                // Stop and release previous MediaPlayer for options
                currentMediaPlayer?.stop()
                currentMediaPlayer?.release()
                currentMediaPlayer = null

                currentMediaPlayer = playAudio(context, option.audioFileName) // Store new MediaPlayer
                currentMediaPlayer?.let { player ->
                    player.setOnCompletionListener {
                        it.release()
                        currentMediaPlayer = null // Reset after completion
                    }
                    player.start()
                    player.awaitCompletion() // Suspend until completion
                }
                withContext(Dispatchers.Main) {
                    highlightedButtonIndex = null
                }
            }
        }
    }

    LaunchedEffect(currentQuestion) {
        playCurrentQuestion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SALT Survey") },
                actions = {
                    IconButton(onClick = { /* Handle exit action */ }) {
                        Icon(Icons.Filled.Close, contentDescription = "Exit")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    currentQuestion?.let { (question, _, _) ->
                        if (question.questionType != "multiple_choice") {
                            viewModel.answerQuestion(textInputValue)
                        }
                    }
                    viewModel.loadPreviousQuestion()
                }, enabled = viewModel.hasPreviousQuestion.value) {
                    Text("Previous")
                }
                Button(onClick = {
                    currentQuestion?.let { (question, _, _) ->
                        if (question.questionType != "multiple_choice") {
                            viewModel.answerQuestion(textInputValue)
                        }
                    }

                    val message = viewModel.loadNextQuestion()
                    if(message != null){
                        errorMessageForDialog = message
                        showDialog = true // Show the dialog with the error message
                    }
                    //currentQuestion = viewModel.currentQuestion

                }, enabled = true) {
                    Text("Next")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            val currentQuestion by viewModel.currentQuestion.collectAsState()
            currentQuestion?.let { (question, options, answer) ->
                Text(text = question.statement, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // Replay button
                IconButton(onClick = {
                    coroutineScope.launch {
                        playCurrentQuestion()
                    }
                    //currentMediaPlayer?.seekTo(0)
                    //currentMediaPlayer?.start()
                }) { // Replay logic
                    Icon(Icons.Filled.Replay, contentDescription = "Replay")
                }
                if (question.questionType == "multiple_choice") {
                    options.forEach { option ->
                        val isHighlighted by remember(highlightedButtonIndex, option.id)
                        { // Derived state
                            derivedStateOf { highlightedButtonIndex == option.id }
                        }
                        val buttonColors = if (isHighlighted) {
                            ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                        } else if (answer?.numericValue?.toInt() == option.optionQuestionIndex) { // Check for match
                            ButtonDefaults.buttonColors(containerColor = Color.Green) // Change color if match
                        } else {
                            ButtonDefaults.buttonColors() // Default color
                        }
                        Button(
                            onClick = { viewModel.answerQuestion(option.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = buttonColors // Apply button colors
                        ) {
                            Text(text = option.text)
                        }
                    }
                } else{
                    // Text Field for numeric or freeform text
                    OutlinedTextField(
                        value = textInputValue,
                        onValueChange = { newValue ->
                            if (question.questionType == "numeric") {
                                if (newValue.matches(Regex("^-?\\d*\\.?\\d*\$"))) {
                                    textInputValue = newValue
                                }
                            } else {
                                // For freeform text, allow any input
                                textInputValue = newValue
                            }
                        },
                        label = { Text("") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (question.questionType == "numeric") KeyboardType.Number else KeyboardType.Text,
                            imeAction = ImeAction.Done // Or ImeAction.Next
                        ),
                        singleLine = question.questionType == "numeric", // Or false for multi-line free text
                        // NEW: Save on focus lost (optional, good for UX)
                        // onFocusChanged = { focusState ->
                        //    if (!focusState.isFocused && textInputValue != (answer?.getValue(false)?.toString() ?: "")) {
                        //        viewModel.answerQuestion(textInputValue)
                        //    }
                        // }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } ?: Text("Survey completed!", style = MaterialTheme.typography.headlineMedium)
        }
    }
}