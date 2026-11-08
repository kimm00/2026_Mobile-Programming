package com.example.focustrace.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

object UsageStatsHelper {

    @Suppress("DEPRECATION")
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @Suppress("MissingPermission")
    fun queryActivityResumedEvents(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long
    ): List<RawUsageEvent> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val usageEvents = usageStatsManager.queryEvents(startTimeMs, endTimeMs)
        val events = mutableListOf<RawUsageEvent>()
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                events.add(
                    RawUsageEvent(
                        packageName = event.packageName,
                        timestampMs = event.timeStamp
                    )
                )
            }
        }
        return events
    }

    fun getLauncherPackageNames(context: Context): Set<String> {
        val packageNames = mutableSetOf<String>()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
        for (info in resolveInfos) {
            info.activityInfo?.packageName?.let { packageNames.add(it) }
        }
        return packageNames
    }
}
