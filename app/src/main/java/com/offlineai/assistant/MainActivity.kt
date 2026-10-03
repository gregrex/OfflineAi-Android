package com.offlineai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.offlineai.assistant.ui.ChatViewModel
import com.offlineai.assistant.ui.components.CategorySelectionScreen
import com.offlineai.assistant.ui.components.ChatScreen
import com.offlineai.assistant.ui.theme.OfflineAIAssistantTheme

enum class AppScreen {
    CATEGORY_SELECTION,
    CHAT
}

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OfflineAIAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    LaunchedEffect(Unit) {
                        chatViewModel.initializeModel(context.applicationContext)
                    }

                    OfflineAiApp(viewModel = chatViewModel)
                }
            }
        }
    }
}

@Composable
fun OfflineAiApp(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf(AppScreen.CATEGORY_SELECTION) }

    when (currentScreen) {
        AppScreen.CATEGORY_SELECTION -> {
            CategorySelectionScreen(
                uiState = uiState,
                onCategorySelected = { category ->
                    viewModel.selectCategory(category)
                    currentScreen = AppScreen.CHAT
                }
            )
        }
        AppScreen.CHAT -> {
            ChatScreen(
                uiState = uiState,
                onSendMessage = { text -> viewModel.sendMessage(text) },
                onStopGeneration = { viewModel.stopGeneration() },
                onClearChat = { viewModel.clearChat() },
                onNavigateBack = { currentScreen = AppScreen.CATEGORY_SELECTION }
            )
        }
    }
}
