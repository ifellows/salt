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
import com.dev.salt.data.Survey
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showWelcomeScreen by remember { mutableStateOf(true) }

            if (showWelcomeScreen) {
                WelcomeScreen(onContinueClicked = { showWelcomeScreen = false })
            } else {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val database = SurveyDatabase.getInstance(this)
                        /*delete the database*/
                        /*database.clearAllTables()
                        val viewModel: SurveyViewModel = viewModel { SurveyViewModel(database) }
                        val surveyApplication = SurveyApplication()
                        surveyApplication.populateSampleData()
                        surveyApplication.copyRawFilesToLocalStorage(this)
                        val coroutineScope = rememberCoroutineScope()
                        SurveyScreen(viewModel, coroutineScope)*/

                        MainScreen(this)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(context: Context) {
    var currentScreen by remember { mutableStateOf("welcome") }

    when (currentScreen) {
        "welcome" -> WelcomeScreen(onContinueClicked = { currentScreen = "menu" })
        "menu" -> MenuScreen(
            onStartNewSurvey = { currentScreen = "survey" },
            onContinueSurvey = { currentScreen = "continue" },
            onSettings = { currentScreen = "settings" }
        )
        "survey" -> {
            val database = SurveyDatabase.getInstance(context)
            // delete the database
            //database.clearAllTables()
            val viewModel: SurveyViewModel = viewModel { SurveyViewModel(database) }
            val surveyApplication = SurveyApplication()
            surveyApplication.populateSampleData()
            surveyApplication.copyRawFilesToLocalStorage(context)
            val coroutineScope = rememberCoroutineScope()
            SurveyScreen(viewModel, coroutineScope)
        }
        "continue" -> PlaceholderScreen("Continue Survey")
        "settings" -> PlaceholderScreen("Settings")
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SurveyScreen(viewModel: SurveyViewModel, coroutineScope: CoroutineScope) {
    //var currentQuestion = viewModel.currentQuestion
    val context = LocalContext.current
    val currentQuestion by viewModel.currentQuestion.collectAsState()
    var highlightedButtonIndex by remember { mutableStateOf<Int?>(null) }
    //var mediaPlayer: MediaPlayer? = remember { null }
    var currentMediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }


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

                options.forEach { option ->
                    val isHighlighted by remember(highlightedButtonIndex, option.id)
                    { // Derived state
                        derivedStateOf { highlightedButtonIndex == option.id }
                    }
                    val buttonColors = if(isHighlighted){
                        ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                    }else if(answer?.numericValue?.toInt() == option.optionQuestionIndex) { // Check for match
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

@Composable
fun WelcomeScreen(onContinueClicked: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Welcome to the SALT Survey", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onContinueClicked) {
                Text(text = "Continue")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MenuScreen(onStartNewSurvey: () -> Unit, onContinueSurvey: () -> Unit, onSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu") },
                actions = {
                    IconButton(onClick = { /* Handle exit action */ }) {
                        Icon(Icons.Filled.Close, contentDescription = "Exit")
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Menu", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartNewSurvey) {
                    Text(text = "Start New Survey")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onContinueSurvey) {
                    Text(text = "Continue Survey")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSettings) {
                    Text(text = "Settings")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlaceholderScreen(title: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = { /* Handle exit action */ }) {
                        Icon(Icons.Filled.Close, contentDescription = "Exit")
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
            }
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