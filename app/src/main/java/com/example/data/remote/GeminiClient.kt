package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ChatMessagePayload(
    val role: String, // "user" or "model"
    val text: String,
    val imageBase64: String? = null
)

class GeminiClient {

    companion object {
        private const val TAG = "GeminiClient"
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        
        private const val SYSTEM_INSTRUCTION =
            "You are 'My AI Assistant', a highly intelligent, empathetic, and knowledgeable AI assistant inspired by ChatGPT. " +
            "You provide accurate, insightful, and clearly structured answers. " +
            "You fluently understand and respond in English, Hindi (हिन्दी), and Hinglish (Hindi written in English letters). " +
            "If the user speaks Hindi, reply in natural Hindi. If they write in Hinglish (e.g. 'Mujhe ek summary do'), reply in warm, natural Hinglish. " +
            "If they write in English, reply in English. " +
            "Use clear Markdown formatting with headers, lists, and code blocks with language tags when appropriate. " +
            "When analyzing images, provide detailed observations and answers."
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNullOrBlank() || key == "MY_GEMINI_API_KEY") {
                ""
            } else {
                key
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun streamGenerateContent(
        history: List<ChatMessagePayload>,
        currentMessage: String,
        currentImageBitmap: Bitmap? = null,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel.")
            )
        }

        val url = "$BASE_URL/$MODEL_NAME:streamGenerateContent?alt=sse&key=$apiKey"
        val requestJson = buildRequestBody(history, currentMessage, currentImageBitmap)

        try {
            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API error code ${response.code}: $errBody")
                val parsedMsg = parseErrorMessage(errBody, response.code)
                return@withContext Result.failure(Exception(parsedMsg))
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(Exception("Empty response body received from Gemini API"))

            val accumulatedText = StringBuilder()

            responseBody.byteStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val rawLine = line?.trim() ?: continue
                    if (!rawLine.startsWith("data:")) continue
                    val jsonPayload = rawLine.removePrefix("data:").trim()
                    if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                    try {
                        val chunkObj = JSONObject(jsonPayload)
                        val candidates = chunkObj.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                for (p in 0 until parts.length()) {
                                    val part = parts.getJSONObject(p)
                                    val textChunk = part.optString("text", "")
                                    if (textChunk.isNotEmpty()) {
                                        accumulatedText.append(textChunk)
                                        withContext(Dispatchers.Main) {
                                            onChunk(textChunk)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing SSE chunk: ${e.message}")
                    }
                }
            }

            val finalResult = accumulatedText.toString()
            if (finalResult.isBlank()) {
                Result.failure(Exception("No response was returned by the AI model. Please try again."))
            } else {
                Result.success(finalResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call Gemini API", e)
            Result.failure(e)
        }
    }

    private fun buildRequestBody(
        history: List<ChatMessagePayload>,
        currentMessage: String,
        currentImageBitmap: Bitmap?
    ): JSONObject {
        val root = JSONObject()

        // System Instruction
        val systemInstructionObj = JSONObject()
        val sysParts = JSONArray()
        val sysPart = JSONObject().put("text", SYSTEM_INSTRUCTION)
        sysParts.put(sysPart)
        systemInstructionObj.put("parts", sysParts)
        root.put("systemInstruction", systemInstructionObj)

        // Contents (Turns)
        val contentsArray = JSONArray()

        // Include last up to 8 conversation history turns for context
        val contextTurns = history.takeLast(8)
        for (item in contextTurns) {
            val turn = JSONObject()
            val role = if (item.role == "model" || item.role == "assistant") "model" else "user"
            turn.put("role", role)
            val parts = JSONArray()
            val textPart = JSONObject().put("text", item.text)
            parts.put(textPart)

            if (!item.imageBase64.isNullOrEmpty() && role == "user") {
                val inlineData = JSONObject()
                    .put("mimeType", "image/jpeg")
                    .put("data", item.imageBase64)
                val imgPart = JSONObject().put("inlineData", inlineData)
                parts.put(imgPart)
            }
            turn.put("parts", parts)
            contentsArray.put(turn)
        }

        // Current turn
        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()
        currentParts.put(JSONObject().put("text", currentMessage))

        if (currentImageBitmap != null) {
            val base64Image = bitmapToBase64(currentImageBitmap)
            val inlineData = JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", base64Image)
            currentParts.put(JSONObject().put("inlineData", inlineData))
        }
        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)

        root.put("contents", contentsArray)

        // Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.7)
        genConfig.put("topP", 0.95)
        root.put("generationConfig", genConfig)

        return root
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDimension = 1024
        val width = bitmap.width
        val height = bitmap.height
        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val (newW, newH) = if (ratio > 1f) {
                maxDimension to (maxDimension / ratio).toInt()
            } else {
                (maxDimension * ratio).toInt() to maxDimension
            }
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun parseErrorMessage(errBody: String, code: Int): String {
        return try {
            val json = JSONObject(errBody)
            val error = json.optJSONObject("error")
            val message = error?.optString("message")
            if (!message.isNullOrBlank()) {
                when {
                    code == 400 && message.contains("API key not valid", ignoreCase = true) ->
                        "Invalid Gemini API key. Please check your key in the AI Studio Secrets panel."
                    code == 429 ->
                        "Rate limit reached or quota exceeded. Please wait a moment and try again."
                    code == 403 ->
                        "API key permission denied. Ensure Gemini API is enabled for your Google Cloud project."
                    else -> message
                }
            } else {
                "HTTP $code: Unable to connect to Gemini API."
            }
        } catch (e: Exception) {
            "HTTP $code: Server communication error."
        }
    }
}
