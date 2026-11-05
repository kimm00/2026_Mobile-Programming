package com.example.focustrace.detection

data class RawUsageEvent(
    val packageName: String,
    val eventType: Int,
    val timeStamp: Long
)

data class AppUsageInfo(
    val packageName: String,
    val startAt: Long,
    val durationMs: Long,
    val isAllowed: Boolean,
    val isAuto: Boolean,
    val isCounted: Boolean
)

data class EpisodeResult(
    val startAt: Long,
    val endAt: Long,
    val kind: String,
    val usages: List<AppUsageInfo>
)

class EpisodeAnalyzer(
    private val thisPackageName: String,
    private val homePackageName: String?,
    private val allowedPackages: Set<String> = setOf(
        "com.google.android.calculator",
        "com.sec.android.app.popupcalculator",
        "com.sec.android.app.calculator"
    )
) {
    companion object {
        const val EVENT_RESUMED = 1
        const val EVENT_PAUSED = 2
    }

    private val excludedPackages: Set<String> = buildSet {
        add(thisPackageName)
        add("com.android.systemui")
        if (!homePackageName.isNullOrBlank()) {
            add(homePackageName)
        }
    }

    fun analyzeEpisode(
        unlockTime: Long,
        screenOffTime: Long,
        events: List<RawUsageEvent>
    ): EpisodeResult {
        val sortedEvents = events.filter { it.timeStamp >= (unlockTime - 1000) && it.timeStamp <= screenOffTime }
            .sortedBy { it.timeStamp }

        val activeIntervals = mutableListOf<Pair<String, Pair<Long, Long>>>()
        
        var currentPkg: String? = null
        var currentStart: Long = unlockTime

        for (event in sortedEvents) {
            if (excludedPackages.contains(event.packageName)) continue

            if (event.eventType == EVENT_RESUMED) {
                if (currentPkg != null && currentPkg != event.packageName) {
                    val endT = event.timeStamp
                    if (endT > currentStart) {
                        activeIntervals.add(currentPkg to (currentStart to endT))
                    }
                }
                currentPkg = event.packageName
                currentStart = maxOf(event.timeStamp, unlockTime)
            } else if (event.eventType == EVENT_PAUSED) {
                if (currentPkg == event.packageName) {
                    val endT = event.timeStamp
                    if (endT > currentStart) {
                        activeIntervals.add(currentPkg to (currentStart to endT))
                    }
                    currentPkg = null
                    currentStart = endT
                }
            }
        }

        if (currentPkg != null && screenOffTime > currentStart) {
            activeIntervals.add(currentPkg to (currentStart to screenOffTime))
        }

        val rawUsages = mutableListOf<AppUsageInfo>()
        var lastPkg: String? = null
        var segStart: Long = 0
        var segDuration: Long = 0

        for ((pkg, interval) in activeIntervals) {
            val clippedStart = maxOf(interval.first, unlockTime)
            val clippedEnd = minOf(interval.second, screenOffTime)
            if (clippedEnd <= clippedStart) continue

            val duration = clippedEnd - clippedStart
            if (lastPkg == pkg) {
                segDuration += duration
            } else {
                if (lastPkg != null) {
                    rawUsages.add(createUsageInfo(lastPkg, segStart, segDuration, unlockTime))
                }
                lastPkg = pkg
                segStart = clippedStart
                segDuration = duration
            }
        }
        if (lastPkg != null) {
            rawUsages.add(createUsageInfo(lastPkg, segStart, segDuration, unlockTime))
        }

        val processedUsages = rawUsages.map { usage ->
            createUsageInfo(usage.packageName, usage.startAt, usage.durationMs, unlockTime)
        }

        val countedUsages = processedUsages.filter { it.isCounted }
        val kind = when {
            countedUsages.isEmpty() -> "UNLOCK_ONLY"
            countedUsages.all { it.isAllowed } -> "ALLOWED_ONLY"
            else -> "CHECK"
        }

        return EpisodeResult(
            startAt = unlockTime,
            endAt = screenOffTime,
            kind = kind,
            usages = processedUsages
        )
    }

    private fun createUsageInfo(packageName: String, startAt: Long, durationMs: Long, unlockTime: Long): AppUsageInfo {
        val isAllowed = allowedPackages.contains(packageName)
        val timeSinceUnlock = startAt - unlockTime
        val isAuto = timeSinceUnlock >= 0 && timeSinceUnlock <= 500
        val isCounted = !(isAuto && durationMs <= 2000)
        return AppUsageInfo(
            packageName = packageName,
            startAt = startAt,
            durationMs = durationMs,
            isAllowed = isAllowed,
            isAuto = isAuto,
            isCounted = isCounted
        )
    }
}
