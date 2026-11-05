package com.example.focustrace.detection

data class RefocusAlert(
    val packageName: String,
    val usedMs: Long,
    val firedAt: Long
)

class RefocusTracker(
    private val thisPackageName: String,
    private val homePackageName: String?,
    private val allowedPackages: Set<String> = setOf(
        "com.google.android.calculator",
        "com.sec.android.app.popupcalculator",
        "com.sec.android.app.calculator"
    ),
    private val alertThresholdMs: Long = 3 * 60 * 1000L // 3 minutes default
) {
    private val excludedPackages: Set<String> = buildSet {
        add(thisPackageName)
        add("com.android.systemui")
        if (!homePackageName.isNullOrBlank()) {
            add(homePackageName)
        }
    }

    private var currentForegroundPkg: String? = null
    private var usageStartTimestamp: Long = 0L
    private var alertAlreadyFiredForCurrentSession: Boolean = false

    fun onScreenOff() {
        reset()
    }

    fun reset() {
        currentForegroundPkg = null
        usageStartTimestamp = 0L
        alertAlreadyFiredForCurrentSession = false
    }

    fun processEvents(events: List<RawUsageEvent>, currentTime: Long): RefocusAlert? {
        val sorted = events.sortedBy { it.timeStamp }
        
        for (event in sorted) {
            val pkg = event.packageName
            if (event.eventType == EpisodeAnalyzer.EVENT_RESUMED) {
                if (currentForegroundPkg != pkg) {
                    currentForegroundPkg = pkg
                    usageStartTimestamp = event.timeStamp
                    alertAlreadyFiredForCurrentSession = false
                }
            } else if (event.eventType == EpisodeAnalyzer.EVENT_PAUSED) {
                if (currentForegroundPkg == pkg) {
                    currentForegroundPkg = null
                    usageStartTimestamp = 0L
                    alertAlreadyFiredForCurrentSession = false
                }
            }
        }

        val pkg = currentForegroundPkg
        if (pkg != null && usageStartTimestamp > 0L) {
            val isExcluded = excludedPackages.contains(pkg)
            val isAllowed = allowedPackages.contains(pkg)

            if (!isExcluded && !isAllowed) {
                val durationMs = currentTime - usageStartTimestamp
                if (durationMs >= alertThresholdMs && !alertAlreadyFiredForCurrentSession) {
                    alertAlreadyFiredForCurrentSession = true
                    return RefocusAlert(
                        packageName = pkg,
                        usedMs = durationMs,
                        firedAt = currentTime
                    )
                }
            }
        }

        return null
    }
}
