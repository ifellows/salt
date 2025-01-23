package com.dev.salt

import android.content.Context
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
import java.io.IOException
import kotlin.io.path.exists
import java.io.File
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val currentQuestion by viewModel.currentQuestion.collectAsState()
    var highlightedButtonIndex by remember { mutableStateOf<Int?>(null) }
    var mediaPlayer: MediaPlayer? = remember { null }


    LaunchedEffect(currentQuestion) {
        currentQuestion?.let { (question, options, _) ->
            // Play question audio and wait for completion
            playAudio(context, question.audioFileName)?.let { player ->
                player.setOnCompletionListener { it.release() }
                player.start()
                player.awaitCompletion() // Suspend until completion
            }

            // Play option audios sequentially
            for (option in options) {
                withContext(Dispatchers.Main) { // Update highlightedButtonIndex on main thread
                    highlightedButtonIndex = option.id
                }
                playAudio(context, option.audioFileName)?.let { player ->
                    player.setOnCompletionListener { it.release() }
                    player.start()
                    player.awaitCompletion() // Suspend until completion
                }
                withContext(Dispatchers.Main) { // Reset highlightedButtonIndex on main thread
                    highlightedButtonIndex = null
                }
            }
        }
    }

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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            val currentQuestion by viewModel.currentQuestion.collectAsState()
            currentQuestion?.let { (question, options, answer) ->
                Text(text = question.statement, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                options.forEach { option ->
                    val isHighlighted by remember(highlightedButtonIndex, option.id)
                    { // Derived state
                        derivedStateOf { highlightedButtonIndex == option.id }
                    }
                    val buttonColors = if(isHighlighted){
                        ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                    }else if(answer == option.text) { // Check for match
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


// Helper function to play audio and return MediaPlayer
private fun playAudio(context: Context, audioFileName: String): MediaPlayer? {
    val audioFile = File(context.filesDir, "audio/$audioFileName")
    return if (audioFile.exists()) {
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(audioFile.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    } else {
        null
    }
}
// Extension function to suspend until MediaPlayer completion
suspend fun MediaPlayer.awaitCompletion() {
    suspendCancellableCoroutine { continuation ->
        setOnCompletionListener {
            continuation.resume(Unit) {
                release() // Release MediaPlayer on cancellation
            }
        }
    }
}