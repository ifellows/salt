package com.dev.salt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.dev.salt.data.SurveyDatabase
import com.dev.salt.SurveyApplication
import com.dev.salt.viewmodel.SurveyViewModel

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    //this.deleteDatabase("survey_database")
                    val database = SurveyDatabase.getInstance(this)
                    val viewModel: SurveyViewModel = viewModel { SurveyViewModel(database) }
                    val surveyApplication = SurveyApplication()
                    surveyApplication.populateSampleData()
                    surveyApplication.copyRawFilesToLocalStorage(this)
                    SurveyScreen(viewModel)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SurveyScreen(viewModel: SurveyViewModel) {
    //var currentQuestion = viewModel.currentQuestion

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SALT Survey") },
                actions = {
                    IconButton(onClick = { /* Handle abort action */ }) {
                        Icon(Icons.Filled.Close, contentDescription = "Abort")
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
                    viewModel.loadPreviousQuestion()
                }, enabled = viewModel.hasPreviousQuestion.value) {
                    Text("Previous")
                }
                Button(onClick = {
                    viewModel.loadNextQuestion()
                    //currentQuestion = viewModel.currentQuestion

                }, enabled = true) {
                    Text("Next")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)) {
            val currentQuestion by viewModel.currentQuestion.collectAsState()
            currentQuestion?.let { (question, options, answer) ->
                Text(text = question.statement, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                options.forEach { option ->
                    val buttonColors = if (answer == option.text) { // Check for match
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
            } ?: Text("Survey completed!", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

