package com.example.focustrace.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig

/**
 * Interface for abstracting generative AI calls.
 * Allows pure unit testing without Firebase runtime initialization.
 */
interface FirebaseAiClient {
    suspend fun generateContent(prompt: String, modelName: String): String
}

/**
 * Production client implementation wrapping Firebase AI Logic SDK.
 * Configures structured JSON output (`application/json`).
 */
class DefaultFirebaseAiClient : FirebaseAiClient {
    private companion object {
        private const val TAG = "FirebaseAiClient"
    }

    override suspend fun generateContent(prompt: String, modelName: String): String {
        val app = FirebaseApp.getInstance()
        val projectId = app.options.projectId
        val backend = GenerativeBackend.googleAI()

        safeLogD(
            TAG,
            "Executing Gemini request: [App: ${app.name}, ProjectId: $projectId, Backend: GoogleAI (Gemini Developer API), Model: $modelName]"
        )

        val aiInstance = Firebase.ai(app = app, backend = backend)
        val generativeModel = aiInstance.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
        val response = generativeModel.generateContent(prompt)
        return response.text ?: throw IllegalStateException("Empty response body from Firebase AI model")
    }

    private fun safeLogD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }
}
