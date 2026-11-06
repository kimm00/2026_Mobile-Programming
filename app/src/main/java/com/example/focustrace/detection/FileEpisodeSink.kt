package com.example.focustrace.detection

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe logger utility for recording test events to a file.
 */
object TestLogger {
    private const val LOG_FILE_NAME = "focus_trace_test.log"

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    @Synchronized
    fun log(context: Context, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] $message\n"
        try {
            getLogFile(context).appendText(logLine)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun readLog(context: Context): String {
        val file = getLogFile(context)
        return if ((file.exists() && file.length() > 0)) {
            file.readText()
        } else {
            "Log file is empty."
        }
    }

    @Synchronized
    fun clearLog(context: Context) {
        val file = getLogFile(context)
        if (file.exists()) {
            file.writeText("")
        }
    }
}

/**
 * Implementation of [EpisodeSink] that appends episode analysis results to a log file.
 */
class FileEpisodeSink(private val context: Context) : EpisodeSink {
    override fun onEpisode(result: EpisodeAnalysisResult) {
        val sb = StringBuilder()
        val durationSec = String.format(Locale.US, "%.1f", (result.endAt - result.startAt) / 1000.0)
        sb.append("EPISODE: kind=${result.kind}, duration=${durationSec}s, startAt=${result.startAt}, endAt=${result.endAt}, shouldSave=${result.shouldSave}\n")
        
        if (result.appUsages.isEmpty()) {
            sb.append("   (No app usages recorded)")
        } else {
            result.appUsages.forEachIndexed { index, usage ->
                val usageSec = String.format(Locale.US, "%.1f", usage.durationMs / 1000.0)
                sb.append("   [$index] ${usage.packageName} | dur=${usage.durationMs}ms (${usageSec}s) | allowed=${usage.isAllowed} | auto=${usage.isAuto} | counted=${usage.isCounted}")
                if (index < result.appUsages.size - 1) {
                    sb.append("\n")
                }
            }
        }
        TestLogger.log(context, sb.toString())
    }
}
