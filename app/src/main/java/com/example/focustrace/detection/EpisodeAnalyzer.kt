package com.example.focustrace.detection

/**
 * Pure Kotlin episode analyzer implementing AGENTS.md detection rules 3–7.
 *
 * Rule 3: App time = its ACTIVITY_RESUMED -> next app's resume, clipped to unlock time.
 *         An app's time ends when the next app is resumed, even if that app is excluded.
 *         Merge consecutive same app (after excluding FocusTrace/home/systemui).
 * Rule 4: Exclude FocusTrace, home launcher, com.android.systemui.
 * Rule 5: App resumed within 0.5 s of unlock = auto.
 *         Auto app left within 2 s = pass (not counted). User-opened apps always count.
 * Rule 6: Kind: none counted -> UNLOCK_ONLY; only allowed -> ALLOWED_ONLY; else -> CHECK.
 * Rule 7: Session start counts as unlock. Start/end episodes with only FocusTrace are not saved.
 */
class EpisodeAnalyzer(
    private val thisPackageName: String = "",
    private val homePackageName: String? = null,
    private val allowedPackages: Set<String> = emptySet()
) {

    private val defaultExcludedPackages: Set<String> = buildSet {
        if (thisPackageName.isNotBlank()) add(thisPackageName)
        add("com.android.systemui")
        if (!homePackageName.isNullOrBlank()) add(homePackageName)
    }

    fun analyze(input: EpisodeInput): EpisodeAnalysisResult {
        val unlockTime = input.unlockTimeMs
        val screenOffTime = input.screenOffTimeMs
        val effectiveAllowed = if (input.allowedApps.isNotEmpty()) input.allowedApps else allowedPackages
        val effectiveExcluded = defaultExcludedPackages + input.excludedApps

        if (screenOffTime <= unlockTime) {
            val isStartOrEnd = input.isSessionStartEpisode || input.isSessionEndEpisode
            return EpisodeAnalysisResult(
                startAt = unlockTime,
                endAt = maxOf(unlockTime, screenOffTime),
                kind = KIND_UNLOCK_ONLY,
                appUsages = emptyList(),
                shouldSave = !isStartOrEnd
            )
        }

        // Filter events: RESUMED events within window [unlockTime - 1000ms, screenOffTime]
        val resumedEvents = input.rawEvents
            .filter { it.eventType == EVENT_RESUMED && it.timestampMs in (unlockTime - 1000L)..screenOffTime }
            .sortedBy { it.timestampMs }

        // Form raw intervals for each resumed event.
        // An app's time ends when the NEXT app is resumed (even if that next app is excluded).
        val rawIntervals = mutableListOf<RawAppInterval>()

        for (i in resumedEvents.indices) {
            val event = resumedEvents[i]
            val startTime = event.timestampMs
            val nextTime = if (i + 1 < resumedEvents.size) {
                resumedEvents[i + 1].timestampMs
            } else {
                screenOffTime
            }

            val effectiveStart = maxOf(startTime, unlockTime)
            val effectiveEnd = minOf(nextTime, screenOffTime)

            if (effectiveEnd > effectiveStart) {
                rawIntervals.add(
                    RawAppInterval(
                        packageName = event.packageName,
                        resumedAt = startTime,
                        startAt = effectiveStart,
                        endAt = effectiveEnd
                    )
                )
            }
        }

        // Rule 4: Exclude intervals where package is in effectiveExcluded FIRST
        val filteredIntervals = rawIntervals.filter { interval ->
            interval.packageName !in effectiveExcluded
        }

        // Rule 3: Merge consecutive same non-excluded app intervals
        val mergedIntervals = mutableListOf<RawAppInterval>()
        for (interval in filteredIntervals) {
            val last = mergedIntervals.lastOrNull()
            if (last != null && last.packageName == interval.packageName) {
                val addedDuration = interval.endAt - interval.startAt
                mergedIntervals[mergedIntervals.lastIndex] = RawAppInterval(
                    packageName = last.packageName,
                    resumedAt = last.resumedAt,
                    startAt = last.startAt,
                    endAt = last.endAt + addedDuration
                )
            } else {
                mergedIntervals.add(interval)
            }
        }

        // Rule 5: Auto app and pass check
        val appUsages = mergedIntervals.map { interval ->
            val durationMs = interval.endAt - interval.startAt
            // App resumed within 0.5 s (500 ms) of unlock = auto
            val isAuto = interval.resumedAt <= unlockTime + 500L
            // Auto app left within 2 s (2000 ms) = pass (not counted). User-opened apps always count.
            val isCounted = if (isAuto) {
                durationMs > 2000L
            } else {
                true
            }
            val isAllowed = interval.packageName in effectiveAllowed

            AnalyzedAppUsage(
                packageName = interval.packageName,
                startAt = interval.startAt,
                durationMs = durationMs,
                isAllowed = isAllowed,
                isAuto = isAuto,
                isCounted = isCounted
            )
        }

        // Rule 6: Kind determination
        val countedUsages = appUsages.filter { it.isCounted }
        val kind = when {
            countedUsages.isEmpty() -> KIND_UNLOCK_ONLY
            countedUsages.all { it.isAllowed } -> KIND_ALLOWED_ONLY
            else -> KIND_CHECK
        }

        // Rule 7: Start/end episodes with no user app usages are not saved
        val isStartOrEnd = input.isSessionStartEpisode || input.isSessionEndEpisode
        val hasUserAppUsages = appUsages.isNotEmpty()
        val shouldSave = if (isStartOrEnd && !hasUserAppUsages) {
            false
        } else {
            true
        }

        return EpisodeAnalysisResult(
            startAt = unlockTime,
            endAt = screenOffTime,
            kind = kind,
            appUsages = appUsages,
            shouldSave = shouldSave
        )
    }

    private data class RawAppInterval(
        val packageName: String,
        val resumedAt: Long,
        val startAt: Long,
        val endAt: Long
    )

    companion object {
        const val EVENT_RESUMED = 1
        const val EVENT_PAUSED = 2

        const val KIND_CHECK = "CHECK"
        const val KIND_ALLOWED_ONLY = "ALLOWED_ONLY"
        const val KIND_UNLOCK_ONLY = "UNLOCK_ONLY"
    }
}
