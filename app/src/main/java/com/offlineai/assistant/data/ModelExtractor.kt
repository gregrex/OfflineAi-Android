package com.offlineai.assistant.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ModelExtractor {
    private const val TAG = "ModelExtractor"
    const val ASSET_MODEL_PATH = "models/llm_model.gguf"
    const val TARGET_MODEL_NAME = "llm_model.gguf"

    suspend fun ensureModelExtracted(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "models")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, TARGET_MODEL_NAME)

        // Sprawdź czy plik już istnieje i ma rozmiar > 10MB
        if (targetFile.exists() && targetFile.length() > 10 * 1024 * 1024) {
            Log.i(TAG, "Model already exists at: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            onProgress(1.0f)
            return@withContext targetFile
        }

        Log.i(TAG, "Extracting model from assets to ${targetFile.absolutePath}...")
        val assetManager = context.assets

        // Pobierz rozmiar z deskryptora zasobu, jeśli dostępny
        val expectedSize: Long = try {
            assetManager.openFd(ASSET_MODEL_PATH).use { it.length }
        } catch (e: Exception) {
            -1L
        }

        assetManager.open(ASSET_MODEL_PATH).use { inputStream: InputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (expectedSize > 0) {
                        onProgress(totalBytesRead.toFloat() / expectedSize.toFloat())
                    }
                }
                outputStream.flush()
            }
        }

        Log.i(TAG, "Model extraction completed! Size: ${targetFile.length()} bytes")
        onProgress(1.0f)
        targetFile
    }

    fun isModelExtracted(context: Context): Boolean {
        val targetFile = File(File(context.filesDir, "models"), TARGET_MODEL_NAME)
        return targetFile.exists() && targetFile.length() > 10 * 1024 * 1024
    }

    fun getExtractedModelFile(context: Context): File {
        return File(File(context.filesDir, "models"), TARGET_MODEL_NAME)
    }
}
