package com.example.focustrace.ai

import android.util.Log

/**
 * Facade service orchestrating input validation, Firebase Gemini model requests,
 * output validation, and deterministic rule-based fallback.
 */
class AiInsightService(
    private val client: FirebaseAiClient = DefaultFirebaseAiClient(),
    private val modelName: String = DEFAULT_MODEL
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val TAG = "AiInsightService"
    }

    /**
     * Generates AI insights based on 4-week aggregate data and Early Stop memos.
     *
     * @param input Aggregated focus stats and memos (NO app names or event logs).
     * @param forceFallback If true, bypasses API calls to test rule fallback logic.
     * @return Validated [AiInsightResult].
     */
    suspend fun generateInsights(
        input: AiFocusInput,
        forceFallback: Boolean = false
    ): AiInsightResult {
        if (forceFallback) {
            return AiInsightEngine.generateRuleFallback(
                input = input,
                reason = "Simulated manual rule fallback"
            )
        }

        // 1. Input Validation: check if baseline data is sufficient (<30 mins total focus time)
        val insufficientResult = AiInsightEngine.validateInput(input)
        if (insufficientResult != null) {
            return insufficientResult
        }

        // 2. Build Privacy-Safe Prompt Payload
        val prompt = AiInsightEngine.buildPrompt(input)

        // 3. Execute Gemini Request with explicitly configured GoogleAI backend
        val rawResponseText = try {
            client.generateContent(prompt, modelName)
        } catch (e: Exception) {
            val exceptionType = e.javaClass.simpleName
            val errorMsg = e.message ?: "No detailed error message"
            safeLogE(TAG, "Gemini API request failed [Model: $modelName, Type: $exceptionType, Details: $errorMsg]", e)
            return AiInsightEngine.generateRuleFallback(
                input = input,
                reason = "Firebase AI service error ($modelName - $exceptionType): $errorMsg"
            )
        }

        // 4. Validate Structured Output
        return try {
            AiInsightEngine.parseAndValidateResponse(rawResponseText, modelName)
        } catch (e: Exception) {
            val parseErrorMsg = e.message ?: e.javaClass.simpleName
            safeLogE(TAG, "Response validation failed for model $modelName: $parseErrorMsg", e)
            return AiInsightEngine.generateRuleFallback(
                input = input,
                reason = "Malformed response validation failure: $parseErrorMsg"
            )
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
