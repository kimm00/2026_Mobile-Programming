package com.example.focustrace.detection

/**
 * Represents a raw app usage event (e.g. ACTIVITY_RESUMED) captured from UsageStatsManager.
 */
data class RawUsageEvent(
    val packageName: String,
    val timestampMs: Long,
    val eventType: Int = 1
)

/**
 * Input parameter block for [EpisodeAnalyzer.analyze].
 */
data class EpisodeInput(
    val unlockTimeMs: Long,
    val screenOffTimeMs: Long,
    val rawEvents: List<RawUsageEvent>,
    val allowedApps: Set<String> = emptySet(),
    val excludedApps: Set<String> = emptySet(),
    val isSessionStartEpisode: Boolean = false,
    val isSessionEndEpisode: Boolean = false
)

/**
 * Analyzed usage segment of a specific app inside an episode.
 */
data class AnalyzedAppUsage(
    val packageName: String,
    val startAt: Long,
    val durationMs: Long,
    val isAllowed: Boolean,
    val isAuto: Boolean,
    val isCounted: Boolean
)

/**
 * Result of episode analysis (unlock -> screen off).
 */
data class EpisodeAnalysisResult(
    val startAt: Long,
    val endAt: Long,
    val kind: String,
    val appUsages: List<AnalyzedAppUsage>,
    val shouldSave: Boolean
)
