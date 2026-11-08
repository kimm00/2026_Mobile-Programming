package com.example.focustrace.detection

import android.util.Log

interface EpisodeSink {
    suspend fun onEpisode(result: EpisodeAnalysisResult)
}

class LogcatEpisodeSink : EpisodeSink {
    override suspend fun onEpisode(result: EpisodeAnalysisResult) {
        Log.d(
            "FocusTrace_EpisodeSink",
            "kind=${result.kind}, usages=${result.appUsages.size}, " +
                    "shouldSave=${result.shouldSave}"
        )

        result.appUsages.forEach { usage ->
            Log.d(
                "FocusTrace_EpisodeSink",
                "App=${usage.packageName}, duration=${usage.durationMs}"
            )
        }
    }
}
