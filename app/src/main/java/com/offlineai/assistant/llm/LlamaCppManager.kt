package com.offlineai.assistant.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton / Manager odpowiedzialny za most JNI do biblioteki llama.cpp w C++.
 */
class LlamaCppManager private constructor() : ILlamaEngine {

    companion object {
        private const val TAG = "LlamaCppManager"
        private const val LIB_NAME = "offline_ai"

        @Volatile
        private var instance: LlamaCppManager? = null

        private var isLibraryLoaded = false

        fun getInstance(): LlamaCppManager {
            return instance ?: synchronized(this) {
                instance ?: LlamaCppManager().also { instance = it }
            }
        }

        fun initNativeLibrary(context: Context) {
            if (!isLibraryLoaded) {
                try {
                    System.loadLibrary(LIB_NAME)
                    isLibraryLoaded = true
                    val nativeLibDir = context.applicationInfo.nativeLibraryDir
                    getInstance().nativeInit(nativeLibDir)
                    Log.i(TAG, "Native library '$LIB_NAME' successfully loaded.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load native library '$LIB_NAME'", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception initializing native backend", e)
                }
            }
        }
    }

    // Natywne deklaracje JNI dopasowane do llama_bridge.cpp
    private external fun nativeInit(nativeLibDir: String)
    private external fun nativeLoadModel(modelPath: String, contextSize: Int): Boolean
    private external fun nativeStartCompletion(prompt: String, maxTokens: Int): Boolean
    private external fun nativeNextToken(): String?
    private external fun nativeStopCompletion()
    private external fun nativeUnload()
    private external fun nativeGetSystemInfo(): String

    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val inferenceDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val mutex = Mutex()

    @Volatile
    private var isGenerating = false

    override suspend fun loadModel(modelPath: String, contextSize: Int): Boolean = withContext(inferenceDispatcher) {
        mutex.withLock {
            try {
                val file = File(modelPath)
                if (!file.exists() || !file.canRead()) {
                    Log.e(TAG, "Model file not accessible: $modelPath")
                    _isModelLoaded.value = false
                    return@withContext false
                }

                Log.i(TAG, "Loading model: $modelPath (ctx: $contextSize)")
                val success = nativeLoadModel(modelPath, contextSize)
                _isModelLoaded.value = success
                Log.i(TAG, "Model loading result: $success")
                success
            } catch (e: Throwable) {
                Log.e(TAG, "Error loading model from $modelPath", e)
                _isModelLoaded.value = false
                false
            }
        }
    }

    override fun generateResponse(prompt: String): Flow<String> = flow {
        if (!_isModelLoaded.value) {
            emit("Błąd: Model AI nie został jeszcze załadowany do pamięci.")
            return@flow
        }

        mutex.withLock {
            isGenerating = true
            try {
                Log.i(TAG, "Starting generation for prompt of length: ${prompt.length}")
                val started = nativeStartCompletion(prompt, maxTokens = 512)
                if (!started) {
                    emit("Błąd: Nie udało się zainicjalizować generowania odpowiedzi.")
                    return@withLock
                }

                while (isGenerating) {
                    val token = nativeNextToken()
                    if (token == null) {
                        // Osiągnięto token końca generowania lub limit
                        break
                    }
                    if (token.isNotEmpty()) {
                        emit(token)
                    }
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Flow collection was cancelled by user.")
                nativeStopCompletion()
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Error during token generation", e)
                emit("\n[Wystąpił błąd podczas generowania: ${e.message}]")
            } finally {
                isGenerating = false
                nativeStopCompletion()
            }
        }
    }.flowOn(inferenceDispatcher)

    override fun stopGeneration() {
        isGenerating = false
        try {
            nativeStopCompletion()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping completion", e)
        }
    }

    override fun unloadModel() {
        stopGeneration()
        try {
            nativeUnload()
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        } finally {
            _isModelLoaded.value = false
        }
    }

    override fun getSystemInfo(): String {
        return try {
            nativeGetSystemInfo()
        } catch (e: Exception) {
            "System info unavailable: ${e.message}"
        }
    }
}
