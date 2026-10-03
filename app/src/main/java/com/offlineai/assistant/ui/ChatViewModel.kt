package com.offlineai.assistant.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.assistant.data.ChatMessage
import com.offlineai.assistant.data.ModelExtractor
import com.offlineai.assistant.domain.AssistantCategory
import com.offlineai.assistant.domain.CategoryContext
import com.offlineai.assistant.llm.ILlamaEngine
import com.offlineai.assistant.llm.LlamaCppManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentCategory: AssistantCategory = AssistantCategory.AGRICULTURE,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isLoadingModel: Boolean = false,
    val extractionProgress: Float = 0f,
    val statusMessage: String = "Gotowy do załadowania modelu",
    val errorMessage: String? = null
)

class ChatViewModel(
    private val llamaEngine: ILlamaEngine = LlamaCppManager.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            llamaEngine.isModelLoaded.collect { loaded ->
                _uiState.update { it.copy(isModelLoaded = loaded) }
            }
        }
    }

    fun selectCategory(category: AssistantCategory) {
        if (_uiState.value.currentCategory != category) {
            generationJob?.cancel()
            _uiState.update {
                it.copy(
                    currentCategory = category,
                    messages = emptyList(),
                    isGenerating = false,
                    errorMessage = null
                )
            }
        }
    }

    fun initializeModel(context: Context) {
        if (_uiState.value.isModelLoaded || _uiState.value.isLoadingModel) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingModel = true,
                    statusMessage = "Sprawdzanie pliku modelu GGUF..."
                )
            }

            try {
                LlamaCppManager.initNativeLibrary(context)

                val modelFile = ModelExtractor.ensureModelExtracted(context) { progress ->
                    _uiState.update {
                        it.copy(
                            extractionProgress = progress,
                            statusMessage = "Rozpakowywanie modelu: ${(progress * 100).toInt()}%"
                        )
                    }
                }

                _uiState.update { it.copy(statusMessage = "Ładowanie modelu do pamięci RAM...") }
                val loaded = llamaEngine.loadModel(modelFile.absolutePath, contextSize = 2048)

                if (loaded) {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = true,
                            isLoadingModel = false,
                            statusMessage = "Model Qwen 2.5 gotowy do pracy offline"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            errorMessage = "Nie udało się załadować modelu GGUF do pamięci",
                            statusMessage = "Błąd inicjalizacji"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        errorMessage = "Błąd inicjalizacji: ${e.message}",
                        statusMessage = "Błąd"
                    )
                }
            }
        }
    }

    /**
     * Buduje prompt zgodnie z wymaganiami zadania:
     * ViewModel ma połączyć System Prompt wybranej kategorii z pytaniem użytkownika.
     */
    fun buildPromptForSending(userQuestion: String): String {
        val history = _uiState.value.messages
            .filter { it.content.isNotBlank() }
            .map { (if (it.isUser) "user" else "assistant") to it.content }

        return if (history.isEmpty()) {
            CategoryContext.formatPrompt(_uiState.value.currentCategory, userQuestion)
        } else {
            CategoryContext.formatConversation(_uiState.value.currentCategory, history, userQuestion)
        }
    }

    fun sendMessage(userText: String) {
        val cleanInput = userText.trim()
        if (cleanInput.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(content = cleanInput, isUser = true)
        val fullPrompt = buildPromptForSending(cleanInput)

        val assistantMessage = ChatMessage(content = "", isUser = false)

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + assistantMessage,
                isGenerating = true,
                errorMessage = null
            )
        }

        generationJob = viewModelScope.launch {
            try {
                llamaEngine.generateResponse(fullPrompt).collect { token ->
                    _uiState.update { state ->
                        val updatedMessages = state.messages.toMutableList()
                        if (updatedMessages.isNotEmpty() && !updatedMessages.last().isUser) {
                            val last = updatedMessages.last()
                            updatedMessages[updatedMessages.size - 1] = last.copy(
                                content = last.content + token
                            )
                        }
                        state.copy(messages = updatedMessages)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val updatedMessages = state.messages.toMutableList()
                    if (updatedMessages.isNotEmpty() && !updatedMessages.last().isUser) {
                        val last = updatedMessages.last()
                        if (last.content.isEmpty()) {
                            updatedMessages[updatedMessages.size - 1] = last.copy(
                                content = "Wystąpił problem z generowaniem: ${e.message}"
                            )
                        }
                    }
                    state.copy(messages = updatedMessages)
                }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        llamaEngine.stopGeneration()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun clearChat() {
        stopGeneration()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        stopGeneration()
    }
}
