package com.offlineai.assistant

import com.offlineai.assistant.domain.AssistantCategory
import com.offlineai.assistant.domain.CategoryContext
import com.offlineai.assistant.llm.ILlamaEngine
import com.offlineai.assistant.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeLlamaEngine : ILlamaEngine {
    private val _isModelLoaded = MutableStateFlow(true)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded

    var lastPromptReceived: String? = null
    var tokensToEmit: List<String> = listOf("To ", "jest ", "odpowiedź ", "modelu.")
    var wasStopCalled = false

    override suspend fun loadModel(modelPath: String, contextSize: Int): Boolean {
        _isModelLoaded.value = true
        return true
    }

    override fun generateResponse(prompt: String): Flow<String> = flow {
        lastPromptReceived = prompt
        for (token in tokensToEmit) {
            emit(token)
        }
    }

    override fun stopGeneration() {
        wasStopCalled = true
    }

    override fun unloadModel() {
        _isModelLoaded.value = false
    }

    override fun getSystemInfo(): String = "Fake Llama Engine v1.0"
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEngine: FakeLlamaEngine
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeEngine = FakeLlamaEngine()
        viewModel = ChatViewModel(llamaEngine = fakeEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial category is Agriculture with proper system prompt`() {
        val prompt = viewModel.buildPromptForSending("Jak nawadniać kukurydzę?")

        assertTrue("Prompt powinien zawierać tag systemowy", prompt.contains("<|im_start|>system"))
        assertTrue("Prompt powinien zawierać system prompt rolnictwa", prompt.contains("Jesteś ekspertem ds. rolnictwa w Afryce"))
        assertTrue("Prompt powinien zawierać tag użytkownika", prompt.contains("<|im_start|>user\nJak nawadniać kukurydzę?"))
        assertTrue("Prompt powinien kończyć się tagiem asystenta", prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `test EU Trade category appends EU Trade system prompt`() {
        viewModel.selectCategory(AssistantCategory.EU_TRADE)
        val prompt = viewModel.buildPromptForSending("Jak uzyskać certyfikat Fairtrade?")

        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("Jesteś doradcą biznesowym dla małych spółdzielni"))
        assertTrue(prompt.contains("Jak uzyskać certyfikat Fairtrade?"))
    }

    @Test
    fun `test Survival DIY category appends DIY system prompt`() {
        viewModel.selectCategory(AssistantCategory.SURVIVAL_DIY)
        val prompt = viewModel.buildPromptForSending("Jak zrobić filtr do wody z butelki i piasku?")

        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("Jesteś inżynierem survivalowym"))
        assertTrue(prompt.contains("Jak zrobić filtr do wody z butelki i piasku?"))
    }

    @Test
    fun `test sendMessage streams response and updates chat history`() = runTest {
        viewModel.selectCategory(AssistantCategory.AGRICULTURE)
        viewModel.sendMessage("Jak tanio nawozić ziemię?")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Powinny być 2 wiadomości (User i Assistant)", 2, state.messages.size)
        assertEquals("Jak tanio nawozić ziemię?", state.messages[0].content)
        assertTrue(state.messages[0].isUser)

        assertEquals("To jest odpowiedź modelu.", state.messages[1].content)
        assertFalse(state.messages[1].isUser)
        assertFalse("Po zakończeniu strumieniowania isGenerating powinno być false", state.isGenerating)
    }

    @Test
    fun `test stopGeneration cancels active inference`() = runTest {
        viewModel.stopGeneration()
        assertTrue(fakeEngine.wasStopCalled)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `test category prompt helper formatting directly`() {
        val agriculturePrompt = CategoryContext.formatPrompt(
            AssistantCategory.AGRICULTURE,
            "Pytanie testowe"
        )
        val expected = "<|im_start|>system\n" +
                "Jesteś ekspertem ds. rolnictwa w Afryce i Ameryce Płd. Używasz prostego języka. Zawsze doradzasz tanie, naturalne metody uprawy i nawadniania. Odpowiadaj zwięźle.<|im_end|>\n" +
                "<|im_start|>user\nPytanie testowe<|im_end|>\n<|im_start|>assistant\n"

        assertEquals(expected, agriculturePrompt)
    }
}
