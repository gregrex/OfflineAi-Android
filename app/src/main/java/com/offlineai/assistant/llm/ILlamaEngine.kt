package com.offlineai.assistant.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ILlamaEngine {
    val isModelLoaded: StateFlow<Boolean>
    suspend fun loadModel(modelPath: String, contextSize: Int = 2048): Boolean
    fun generateResponse(prompt: String): Flow<String>
    fun stopGeneration()
    fun unloadModel()
    fun getSystemInfo(): String
}
