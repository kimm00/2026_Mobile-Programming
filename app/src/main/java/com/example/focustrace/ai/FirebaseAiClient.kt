package com.example.focustrace.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Interface for abstracting generative AI calls.
 * Allows pure unit testing without Firebase runtime initialization.
 */
interface FirebaseAiClient {
    suspend fun generateContent(prompt: String, modelName: String): String
    suspend fun testMinimalCall(
        modelName: String = "gemini-3.5-flash-lite",
        prompt: String = "Reply with OK"
    ): String
}

/**
 * Production client implementation communicating with the local proxy server.
 * The server forwards requests to Gemini Developer API using the server-side GEMINI_API_KEY.
 */
class DefaultFirebaseAiClient(
    private val serverUrl: String = DEFAULT_SERVER_URL
) : FirebaseAiClient {

    companion object {
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080/api/ai/generate"
        private const val TAG = "FirebaseAiClient"
        private const val TEST_TAG = "AiTest"
    }

    override suspend fun generateContent(prompt: String, modelName: String): String = withContext(Dispatchers.IO) {
        safeLogD(TAG, "Executing AI request via proxy server [$serverUrl, Model: $modelName]")
        val responseText = executeHttpPost(prompt, modelName)
        if (responseText.isBlank()) {
            throw IllegalStateException("Empty response body from proxy server")
        }
        responseText
    }

    override suspend fun testMinimalCall(modelName: String, prompt: String): String = withContext(Dispatchers.IO) {
        safeLogD(TEST_TAG, "Executing minimal call test via proxy server [$serverUrl, Model: $modelName, Prompt: '$prompt']")
        try {
            val responseText = executeHttpPost(prompt, modelName)
            if (responseText.isBlank()) {
                throw IllegalStateException("Empty response body from proxy server")
            }
            safeLogD(TEST_TAG, "Minimal call SUCCESS [Model: $modelName]: $responseText")
            responseText
        } catch (e: Exception) {
            val exceptionClass = e.javaClass.name
            val errorMsg = e.message ?: "No detailed error message"
            safeLogE(TEST_TAG, "Minimal call FAILED [Model: $modelName] Exception: $exceptionClass | Message: $errorMsg", e)
            throw e
        }
    }

    private fun executeHttpPost(prompt: String, modelName: String): String {
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("modelName", modelName)
        }.toString()

        val url = URL(serverUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        conn.outputStream.use { os ->
            os.write(payload.toByteArray(Charsets.UTF_8))
        }

        val statusCode = conn.responseCode
        if (statusCode !in 200..299) {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
            val errorMsg = try {
                JSONObject(errorText).optString("error", errorText)
            } catch (_: Exception) {
                errorText
            }
            throw IllegalStateException("Proxy server error (HTTP $statusCode): $errorMsg")
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(responseBody)
        return responseJson.optString("text")
    }

    private fun safeLogD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun safeLogE(tag: String, msg: String, throwable: Throwable? = null) {
        try {
            Log.e(tag, msg, throwable)
        } catch (_: Throwable) {
            System.err.println("[$tag] $msg: ${throwable?.message}")
        }
    }
}
