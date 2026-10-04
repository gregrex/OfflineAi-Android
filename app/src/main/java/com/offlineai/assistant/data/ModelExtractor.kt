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
        // Opcja 1: Sprawdź czy użytkownik lub skrypt wgrał nowszy model do pamięci zewnętrznej aplikacji
        val extDir = context.getExternalFilesDir("models")
        val extModel = if (extDir != null) File(extDir, TARGET_MODEL_NAME) else null
        if (extModel != null && extModel.exists() && extModel.length() > 50 * 1024 * 1024) {
            Log.i(TAG, "Using external model override from: ${extModel.absolutePath} (${extModel.length()} bytes)")
            onProgress(1.0f)
            return@withContext extModel
        }

        val targetFile = File(targetDir, TARGET_MODEL_NAME)
        val assetManager = context.assets

        // Pobierz rozmiar z deskryptora zasobu, jeśli dostępny
        val expectedSize: Long = try {
            assetManager.openFd(ASSET_MODEL_PATH).use { it.length }
        } catch (e: Exception) {
            -1L
        }

        // Sprawdz czy plik juz istnieje i ma prawidlowy rozmiar
        if (targetFile.exists() && targetFile.length() > 10 * 1024 * 1024) {
            if (expectedSize <= 0 || targetFile.length() == expectedSize) {
                Log.i(TAG, "Model already exists and size matches: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                onProgress(1.0f)
                return@withContext targetFile
            } else {
                Log.i(TAG, "Model size mismatch (found ${targetFile.length()}, expected $expectedSize). Overwriting with new model...")
                targetFile.delete()
            }
        }

        Log.i(TAG, "Extracting model from assets to ${targetFile.absolutePath}...")

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
        val extDir = context.getExternalFilesDir("models")
        val extModel = if (extDir != null) File(extDir, TARGET_MODEL_NAME) else null
        if (extModel != null && extModel.exists() && extModel.length() > 50 * 1024 * 1024) {
            return true
        }
        val targetFile = File(File(context.filesDir, "models"), TARGET_MODEL_NAME)
        return targetFile.exists() && targetFile.length() > 10 * 1024 * 1024
    }

    fun getExtractedModelFile(context: Context): File {
        val extDir = context.getExternalFilesDir("models")
        val extModel = if (extDir != null) File(extDir, TARGET_MODEL_NAME) else null
        if (extModel != null && extModel.exists() && extModel.length() > 50 * 1024 * 1024) {
            return extModel
        }
        return File(File(context.filesDir, "models"), TARGET_MODEL_NAME)
    }
}
