package com.example.focustrace.detection

/**
 * Result object when a refocus alert condition is met.
 */
data class RefocusAlert(
    val packageName: String,
    val durationMs: Long
)

/**
 * Pure Kotlin tracker for continuous app usage during focus sessions (F-13 / T6 refocus detection).
 */
class RefocusTracker(
    val refocusLimitMs: Long = 3 * 60 * 1000L,
    val allowedApps: Set<String> = emptySet(),
    val excludedApps: Set<String> = emptySet()
) {
    private var currentUnlockTimeMs: Long = 0L
    private var currentPackage: String? = null
    private var currentSegmentStartMs: Long = 0L
    private var currentResumeTimeMs: Long = 0L
    private var alertFiredForCurrentRun: Boolean = false

    fun onUnlock(unlockTimeMs: Long) {
        currentUnlockTimeMs = unlockTimeMs
        if (currentPackage != null) {
            currentSegmentStartMs = maxOf(currentResumeTimeMs, unlockTimeMs)
            alertFiredForCurrentRun = false
        }
    }

    fun onScreenOff() {
        currentPackage = null
        currentSegmentStartMs = 0L
        currentResumeTimeMs = 0L
        alertFiredForCurrentRun = false
    }

    fun processEvents(events: List<RawUsageEvent>, currentTimeMs: Long): RefocusAlert? {
        for (event in events) {
            if (event.eventType == 1) { // ACTIVITY_RESUMED
                if (event.packageName != currentPackage) {
                    currentPackage = event.packageName
                    currentResumeTimeMs = event.timestampMs
                    currentSegmentStartMs = maxOf(event.timestampMs, currentUnlockTimeMs)
                    alertFiredForCurrentRun = false
                }
            }
        }

        val pkg = currentPackage ?: return null
        if ((pkg in excludedApps || pkg in allowedApps)) {
            return null
        }

        if (alertFiredForCurrentRun) {
            return null
        }

        val duration = currentTimeMs - currentSegmentStartMs
        if (duration >= refocusLimitMs) {
            alertFiredForCurrentRun = true
            return RefocusAlert(packageName = pkg, durationMs = duration)
        }

        return null
    }
}
