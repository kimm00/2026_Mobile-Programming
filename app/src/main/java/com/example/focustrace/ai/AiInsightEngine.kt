package com.example.focustrace.ai

/**
 * Pure Kotlin AI engine responsible for:
 * 1. Validating input data and determining if baseline focus metrics are sufficient.
 * 2. Generating privacy-filtered prompt payload (ensuring NO app names or phone logs).
 * 3. Validating and parsing Gemini structured JSON output.
 * 4. Executing deterministic rule-based fallback logic.
 */
object AiInsightEngine {

    private const val MIN_REQUIRED_FOCUS_MINUTES = 30L

    /**
     * Checks if the 4-week input has sufficient data.
     * Returns null if sufficient, or a fallback [AiInsightResult] if insufficient.
     */
    fun validateInput(input: AiFocusInput): AiInsightResult? {
        val totalSessions = input.weeklyStats.sumOf { it.totalSessions }
        val totalFocusMins = input.weeklyStats.sumOf { it.totalFocusMinutes }

        if (totalSessions == 0 || totalFocusMins < MIN_REQUIRED_FOCUS_MINUTES) {
            return generateRuleFallback(
                input = input,
                reason = "Insufficient historical focus data (< 30 minutes total focus time)"
            )
        }
        return null
    }

    /**
     * Builds a prompt for Gemini containing strictly aggregated metrics and Early Stop memos.
     * Guaranteed privacy: Contains NO app names, package names, or event logs.
     */
    fun buildPrompt(input: AiFocusInput): String {
        val statsText = input.weeklyStats.joinToString(separator = "\n") { week ->
            "- ${week.weekLabel}: ${week.totalSessions} sessions (${week.completedSessions} completed, ${week.earlyStopSessions} early stops), " +
                    "${week.totalFocusMinutes} mins focused, ${week.totalPhoneUseMinutes} mins phone use, " +
                    "${week.checkCount} phone checks, longest session: ${week.longestFocusMinutes} mins"
        }

        val memoText = if (input.earlyStopMemos.isEmpty()) {
            "None recorded."
        } else {
            input.earlyStopMemos.joinToString(separator = "\n") { memo ->
                if (memo.memo.isNotBlank()) {
                    "- Reason: [${memo.reason}], User Note: \"${memo.memo}\""
                } else {
                    "- Reason: [${memo.reason}]"
                }
            }
        }

        return """
            You are an expert focus & productivity coach analyzing a user's recent phone usage & focus stats.

            [PRIVACY NOTICE & INPUT DATA]
            Aggregated 4-Week Focus Statistics:
            $statsText

            Early Stop Reasons & Notes:
            $memoText

            [TASK INSTRUCTIONS]
            Analyze both the weekly focus statistics and the early stop reasons & notes together to suggest concrete actions for future sessions.
            1. Recommend an optimal focus session duration in minutes (integer between 15 and 120).
            2. Provide exactly 2 or 3 concise, highly actionable focus insights or advice points:
               - If fatigue or long target durations repeat, suggest session length adjustments or scheduled stretch breaks.
               - If external distractions, noise, urgent tasks, or phone urges repeat, suggest study environment adjustments or phone placement strategies.
               - If user notes mention specific contexts (e.g. under 'OTHER' or custom memos like noise or calls), incorporate those contextual triggers into actionable advice.

            [STRICT JSON OUTPUT REQUIREMENT]
            Respond ONLY with a valid JSON object matching the following structure:
            {
              "recommendedDurationMinutes": 45,
              "insights": [
                "Concise actionable insight 1...",
                "Concise actionable insight 2...",
                "Concise actionable insight 3..."
              ]
            }
        """.trimIndent()
    }

    /**
     * Parses and validates raw JSON text returned by the model.
     * Enforces schema rules:
     * - recommendedDurationMinutes in 15..120
     * - insights count in 2..3 non-blank strings
     * Throws IllegalArgumentException on invalid schema/content.
     */
    fun parseAndValidateResponse(jsonString: String, modelName: String = "gemini-3.5-flash-lite"): AiInsightResult {
        val cleanJson = jsonString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val durationMatch = Regex(""""recommendedDurationMinutes"\s*:\s*(\d+)""").find(cleanJson)
            ?: throw IllegalArgumentException("Missing or invalid 'recommendedDurationMinutes' field")
        val duration = durationMatch.groupValues[1].toIntOrNull()
            ?: throw IllegalArgumentException("Duration is not a valid integer")

        if (duration !in 15..120) {
            throw IllegalArgumentException("Recommended duration ($duration mins) is out of range (15..120)")
        }

        val insightsArrayMatch = Regex(""""insights"\s*:\s*\[([\s\S]*?)]""").find(cleanJson)
            ?: throw IllegalArgumentException("Missing 'insights' array field")
        val rawInsightsContent = insightsArrayMatch.groupValues[1]

        val insightMatches = Regex("""\s*"([^"\\]*(?:\\.[^"\\]*)*)"\s*""").findAll(rawInsightsContent)
        val insights = insightMatches.map { match ->
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .trim()
        }.filter { it.isNotBlank() }.toList()

        if (insights.size !in 2..3) {
            throw IllegalArgumentException("Expected 2 to 3 insights, but parsed ${insights.size}")
        }

        return AiInsightResult(
            recommendedDurationMinutes = duration,
            insights = insights,
            source = "AI",
            modelName = modelName,
            statusMessage = "Generated successfully via AI ($modelName)"
        )
    }

    /**
     * Deterministic rule-based engine used when API call fails, data is malformed, or data is insufficient.
     */
    fun generateRuleFallback(input: AiFocusInput, reason: String? = null): AiInsightResult {
        val totalSessions = input.weeklyStats.sumOf { it.totalSessions }
        val totalCompleted = input.weeklyStats.sumOf { it.completedSessions }
        val totalFocusMins = input.weeklyStats.sumOf { it.totalFocusMinutes }
        val totalChecks = input.weeklyStats.sumOf { it.checkCount }

        val completionRate = if (totalSessions > 0) totalCompleted.toDouble() / totalSessions else 0.0
        val checksPerHour = if (totalFocusMins > 0) (totalChecks.toDouble() / (totalFocusMins / 60.0)) else 0.0

        val (recommendedMinutes, insights) = when {
            totalSessions == 0 || totalFocusMins < MIN_REQUIRED_FOCUS_MINUTES -> {
                25 to listOf(
                    "Focus data is limited. Start with standard 25-minute sessions to build consistency.",
                    "Set a single clear goal before launching each focus block.",
                    "Keep your phone in another room or face-down during early sessions."
                )
            }
            completionRate >= 0.8 && checksPerHour < 3.0 -> {
                50 to listOf(
                    "High completion rate (${(completionRate * 100).toInt()}%). Consider extending target session duration to 50 minutes.",
                    "Low phone check frequency indicates strong deep focus habits.",
                    "Schedule a 10-minute stretch break after long sessions to sustain high energy."
                )
            }
            completionRate < 0.5 || checksPerHour > 5.0 -> {
                25 to listOf(
                    "Frequent phone checks ($totalChecks checks across recent weeks). Try 25-minute Pomodoro intervals.",
                    "Review Early Stop notes to eliminate primary distraction triggers.",
                    "Use the study allow-list to maintain focus on required learning tools."
                )
            }
            else -> {
                35 to listOf(
                    "Based on your recent 4-week average, a 35-minute focus session is your sweet spot.",
                    "Take brief planned breaks between sessions to reduce early stops from fatigue.",
                    "Track session notes to identify your peak focus hours of the day."
                )
            }
        }

        val statusMsg = if (reason != null) "Fallback active: $reason" else "Fallback active (Rule Engine)"

        return AiInsightResult(
            recommendedDurationMinutes = recommendedMinutes,
            insights = insights,
            source = "RULE_FALLBACK",
            statusMessage = statusMsg
        )
    }
}
