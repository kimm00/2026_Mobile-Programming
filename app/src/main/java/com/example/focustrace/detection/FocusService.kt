package com.example.focustrace.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.focustrace.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FocusService : Service() {

    companion object {
        const val CHANNEL_ID = "FocusServiceChannel"
        const val REFOCUS_CHANNEL_ID = "RefocusAlertChannel"
        const val NOTIFICATION_ID = 1001
        const val REFOCUS_NOTIFICATION_ID = 2001

        // Configuration constants for testing/comparison
        var isPeriodicCheckEnabled: Boolean = true
        const val ALERT_THRESHOLD_MS = 3 * 60 * 1000L // 3 minutes (can be set to 30_000L for quick test)

        var instance: FocusService? = null
            private set
    }

    private var lastUnlockTime: Long = 0L
    private var homePackageName: String? = null
    private var checkCount: Int = 0
    private var lastCheckTime: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var episodeAnalyzer: EpisodeAnalyzer
    private lateinit var refocusTracker: RefocusTracker
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val now = System.currentTimeMillis()
            val timeStr = dateFormat.format(Date(now))

            when (action) {
                Intent.ACTION_USER_PRESENT -> {
                    lastUnlockTime = now
                    lastCheckTime = now
                    val logMsg = """
                        |
                        |==================================================
                        |🔓 [UNLOCK] $timeStr
                        |==================================================
                        |-> ACTION_USER_PRESENT received. Unlock recorded.
                        |--------------------------------------------------
                    """.trimMargin() + "\n"
                    appendLog(logMsg)
                    updateNotification("Session active - Unlocked at $timeStr")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    refocusTracker.onScreenOff()

                    if (lastUnlockTime == 0L) {
                        appendLog("[$timeStr] 🌙 SCREEN_OFF ignored (no prior unlock recorded).\n")
                        return
                    }

                    val unlockT = lastUnlockTime
                    val screenOffT = now
                    
                    lastUnlockTime = 0L

                    val durationSinceUnlock = (screenOffT - unlockT) / 1000
                    val logMsg = """
                        |
                        |==================================================
                        |🌙 [SCREEN OFF] $timeStr
                        |==================================================
                        |-> Screen turned off (Active session duration: ${durationSinceUnlock}s).
                        |-> Analyzing episode in 1.5s...
                        |--------------------------------------------------
                    """.trimMargin() + "\n"
                    appendLog(logMsg)

                    handler.postDelayed({
                        analyzeAndLogEpisode(unlockT, screenOffT)
                    }, 1500)
                }
            }
        }
    }

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            if (isPeriodicCheckEnabled) {
                performPeriodicCheck()
            }
            handler.postDelayed(this, 15_000) // every 15s
        }
    }

    private val batteryRunnable = object : Runnable {
        override fun run() {
            logBatteryAndChecks("MINUTE STATUS")
            handler.postDelayed(this, 60_000) // every minute
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val currentTime = System.currentTimeMillis()
        lastUnlockTime = currentTime
        lastCheckTime = currentTime

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        homePackageName = resolvedHome?.activityInfo?.packageName

        episodeAnalyzer = EpisodeAnalyzer(
            thisPackageName = packageName,
            homePackageName = homePackageName
        )

        refocusTracker = RefocusTracker(
            thisPackageName = packageName,
            homePackageName = homePackageName,
            alertThresholdMs = ALERT_THRESHOLD_MS
        )

        createNotificationChannels()
        
        val notification = createNotification("Focus Service is running (specialUse)")
        startForeground(NOTIFICATION_ID, notification)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)

        val startTime = dateFormat.format(Date())
        appendLog("""
            |
            |==================================================
            |🚀 [SERVICE STARTED] $startTime
            |==================================================
            |-> FocusService started with type `specialUse`.
            |-> Periodic Check Enabled: $isPeriodicCheckEnabled (every 15s)
            |-> Alert Threshold: ${ALERT_THRESHOLD_MS / 1000}s
            |-> Home Package: $homePackageName
            |--------------------------------------------------
        """.trimMargin() + "\n")

        logBatteryAndChecks("SERVICE START")

        handler.postDelayed(periodicCheckRunnable, 15_000)
        handler.postDelayed(batteryRunnable, 60_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        handler.removeCallbacks(periodicCheckRunnable)
        handler.removeCallbacks(batteryRunnable)

        logBatteryAndChecks("SERVICE STOP")

        val stopTime = dateFormat.format(Date())
        appendLog("""
            |
            |==================================================
            |🛑 [SERVICE STOPPED] $stopTime
            |==================================================
            |--------------------------------------------------
        """.trimMargin() + "\n")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Focus Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            manager?.createNotificationChannel(serviceChannel)

            val refocusChannel = NotificationChannel(
                REFOCUS_CHANNEL_ID,
                "Refocus Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts when using non-allowed apps too long"
            }
            manager?.createNotificationChannel(refocusChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("FocusTrace Test Service")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("FocusTrace Test Service")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun logBatteryAndChecks(tag: String) {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val timeStr = dateFormat.format(Date())
        appendLog("🔋 [$tag] $timeStr -> Battery: $level% | Total Checks: $checkCount\n")
    }

    private fun performPeriodicCheck() {
        checkCount++
        val now = System.currentTimeMillis()
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

        // Read new events since last check with 5 seconds overlap
        val startTime = if (lastCheckTime > 5000L) lastCheckTime - 5000L else lastCheckTime
        val endTime = now
        lastCheckTime = now

        val events = mutableListOf<RawUsageEvent>()
        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                events.add(RawUsageEvent(
                    packageName = event.packageName ?: "",
                    eventType = event.eventType,
                    timeStamp = event.timeStamp
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val alert = refocusTracker.processEvents(events, now)
        if (alert != null) {
            triggerRefocusAlert(alert)
        }
    }

    private fun triggerRefocusAlert(alert: RefocusAlert) {
        val timeStr = dateFormat.format(Date())
        val usedMinutesSec = "${alert.usedMs / 1000}s"
        
        // Log alert: package name and how long it was used
        appendLog("""
            |
            |⚠️ --------------------------------------------------
            |⚠️ [REFOCUS ALERT TRIGGERED] $timeStr
            |⚠️ --------------------------------------------------
            |• Package : ${alert.packageName}
            |• Used For: $usedMinutesSec (${alert.usedMs}ms)
            |--------------------------------------------------\n
        """.trimMargin() + "\n")

        // Show high-priority notification: "Back to focus?" / "You've been on <app name> for 3 min."
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val thresholdMin = ALERT_THRESHOLD_MS / 60_000
        val thresholdSec = (ALERT_THRESHOLD_MS % 60_000) / 1000
        val thresholdStr = if (thresholdMin > 0) "$thresholdMin min" else "$thresholdSec sec"

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, REFOCUS_CHANNEL_ID)
                .setContentTitle("Back to focus?")
                .setContentText("You've been on ${alert.packageName} for $thresholdStr.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_HIGH)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Back to focus?")
                .setContentText("You've been on ${alert.packageName} for $thresholdStr.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_HIGH)
                .build()
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(REFOCUS_NOTIFICATION_ID, notification)
    }

    private fun analyzeAndLogEpisode(unlockTime: Long, screenOffTime: Long) {
        val timeStr = dateFormat.format(Date())
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            appendLog("❌ [ANALYSIS ERROR] $timeStr -> UsageStatsManager is unavailable.\n")
            return
        }

        val startTime = unlockTime - 1000
        val endTime = screenOffTime + 1000
        val events = mutableListOf<RawUsageEvent>()

        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                events.add(RawUsageEvent(
                    packageName = event.packageName ?: "",
                    eventType = event.eventType,
                    timeStamp = event.timeStamp
                ))
            }
        } catch (e: Exception) {
            appendLog("❌ [QUERY ERROR] $timeStr -> queryEvents failed: ${e.message}\n")
            return
        }

        val result = episodeAnalyzer.analyzeEpisode(unlockTime, screenOffTime, events)
        
        val sb = StringBuilder()
        sb.append("\n")
        sb.append("--------------------------------------------------\n")
        sb.append("📊 [EPISODE RESULT] $timeStr\n")
        sb.append("--------------------------------------------------\n")
        sb.append("• Classification Kind : ${result.kind}\n")
        sb.append("• Unlock Time        : ${dateFormat.format(Date(result.startAt))}\n")
        sb.append("• Screen Off Time    : ${dateFormat.format(Date(result.endAt))}\n")
        sb.append("• Total Raw Events   : ${events.size}\n")
        
        if (result.usages.isEmpty()) {
            sb.append("• App Usages         : (None detected or all filtered out)\n")
        } else {
            sb.append("• App Usages Summary :\n")
            result.usages.forEachIndexed { index, usage ->
                val statusIcon = if (usage.isCounted) "🔴 [COUNTED]" else "🟢 [PASSED]"
                val typeIcon = if (usage.isAllowed) "✅ [ALLOWED]" else "⚠️ [NON-ALLOWED]"
                val autoTag = if (usage.isAuto) " (Auto)" else ""
                sb.append("  ${index + 1}. ${usage.packageName}\n")
                sb.append("     - Status  : $statusIcon $typeIcon$autoTag\n")
                sb.append("     - Duration: ${usage.durationMs} ms (${usage.durationMs / 1000.0}s)\n")
            }
        }

        sb.append("• Raw Events (Resumed/Paused):\n")
        val filteredRawEvents = events.filter { it.eventType == EpisodeAnalyzer.EVENT_RESUMED || it.eventType == EpisodeAnalyzer.EVENT_PAUSED }
            .sortedBy { it.timeStamp }
        if (filteredRawEvents.isEmpty()) {
            sb.append("  (No RESUMED or PAUSED events found)\n")
        } else {
            filteredRawEvents.forEach { event ->
                val secFromUnlock = (event.timeStamp - unlockTime) / 1000.0
                val typeName = if (event.eventType == EpisodeAnalyzer.EVENT_RESUMED) "RESUMED" else "PAUSED"
                val isExcluded = event.packageName == packageName || event.packageName == homePackageName || event.packageName == "com.android.systemui"
                val excludedTag = if (isExcluded) " (excluded)" else ""
                sb.append(String.format(Locale.getDefault(), "  - [%+.1fs] %s : %s%s\n", secFromUnlock, typeName, event.packageName, excludedTag))
            }
        }

        sb.append("--------------------------------------------------\n\n")
        appendLog(sb.toString())
    }

    private fun appendLog(msg: String) {
        try {
            val logFile = File(filesDir, "focus_test_log.txt")
            logFile.appendText(msg)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
