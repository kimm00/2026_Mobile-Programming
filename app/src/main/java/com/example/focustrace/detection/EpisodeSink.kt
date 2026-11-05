package com.example.focustrace.detection

import android.util.Log

/**
 * Interface for receiving episode analysis results.
 */
interface EpisodeSink {
    fun onEpisode(result: EpisodeAnalysisResult)
}

/**
 * Logcat implementation of EpisodeSink for F-02.
 */
class LogcatEpisodeSink : EpisodeSink {
    override fun onEpisode(result: EpisodeAnalysisResult) {
        Log.d(TAG, "onEpisode: kind=${result.kind}, startAt=${result.startAt}, endAt=${result.endAt}, usagesCount=${result.appUsages.size}, shouldSave=${result.shouldSave}")
        for (usage in result.appUsages) {
            Log.d(TAG, "   AppUsage: ${usage.packageName}, durationMs=${usage.durationMs}, isAllowed=${usage.isAllowed}, isAuto=${usage.isAuto}, isCounted=${usage.isCounted}")
        }
    }

    companion object {
        private const val TAG = "FocusTrace_EpisodeSink"
    }
}
