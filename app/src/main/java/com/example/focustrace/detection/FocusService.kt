package com.example.focustrace.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.focustrace.MainActivity
import com.example.focustrace.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground Service (specialUse) for phone usage detection during a focus session.
 */
class FocusService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var lastUnlockTimeMs: Long = 0L
    private var isSessionStartEpisode: Boolean = true
    private var homePackageName: String? = null
    private var isReceiverRegistered: Boolean = false

    var episodeSink: EpisodeSink = LogcatEpisodeSink()
    private var allowedApps: Set<String> = emptySet()
    private var batteryJob: Job? = null

    private var refocusJob: Job? = null
    private val seenEvents = mutableSetOf<RawUsageEvent>()
    private var refocusCheckCount: Int = 0
    private lateinit var refocusTracker: RefocusTracker

    private lateinit var episodeAnalyzer: EpisodeAnalyzer

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val now = System.currentTimeMillis()

            when (action) {
                Intent.ACTION_USER_PRESENT -> {
                    lastUnlockTimeMs = now
                    Log.d(TAG, "USER_PRESENT received at $lastUnlockTimeMs")
                    TestLogger.log(applicationContext, "UNLOCK (USER_PRESENT) at $lastUnlockTimeMs")
                    refocusTracker.onUnlock(lastUnlockTimeMs)
                    startRefocusLoop()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    stopRefocusLoop()
                    refocusTracker.onScreenOff()

                    if (lastUnlockTimeMs == 0L) {
                        Log.d(TAG, "SCREEN_OFF ignored (no prior unlock recorded)")
                        TestLogger.log(applicationContext, "SCREEN_OFF ignored (no prior unlock recorded)")
                        return
                    }

                    val unlockT = lastUnlockTimeMs
                    val screenOffT = now
                    val startEpisodeFlag = isSessionStartEpisode
                    isSessionStartEpisode = false
                    lastUnlockTimeMs = 0L

                    Log.d(TAG, "SCREEN_OFF received at $screenOffT. Scheduling analysis in 1.5s.")
                    TestLogger.log(applicationContext, "SCREEN_OFF at $screenOffT (unlock was at $unlockT)")

                    serviceScope.launch {
                        delay(ANALYSIS_DELAY_MS)
                        performEpisodeAnalysis(unlockT, screenOffT, startEpisodeFlag)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        episodeSink = FileEpisodeSink(applicationContext)

        val currentTime = System.currentTimeMillis()
        lastUnlockTimeMs = currentTime

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        homePackageName = resolvedHome?.activityInfo?.packageName

        val testAllowedApps = setOf(
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.android.calculator2",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.deskclock"
        )
        val excludedApps = setOf(
            packageName,
            homePackageName ?: "",
            "com.android.systemui"
        )

        refocusTracker = RefocusTracker(
            refocusLimitMs = REFOCUS_LIMIT_MS,
            allowedApps = testAllowedApps,
            excludedApps = excludedApps
        )

        episodeAnalyzer = EpisodeAnalyzer(
            thisPackageName = packageName,
            homePackageName = homePackageName,
            allowedPackages = allowedApps
        )

        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SESSION

        when (action) {
            ACTION_START_SESSION -> {
                val notification = buildNotification()
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )

                lastUnlockTimeMs = System.currentTimeMillis()
                isSessionStartEpisode = true

                registerScreenReceiver()
                TestLogger.log(applicationContext, "SERVICE_START at $lastUnlockTimeMs")
                startBatteryLogging()

                refocusTracker.onUnlock(lastUnlockTimeMs)
                startRefocusLoop()

                Log.d(TAG, "FocusService started. Unlock time initialized to $lastUnlockTimeMs")
            }
            ACTION_STOP_SESSION -> {
                stopSessionInternal()
            }
        }

        return START_STICKY
    }

    private fun startRefocusLoop() {
        if (!isRefocusCheckEnabled) return
        refocusJob?.cancel()
        refocusJob = serviceScope.launch {
            while (true) {
                delay(REFOCUS_CHECK_INTERVAL_MS)
                performRefocusCheck()
            }
        }
    }

    private fun stopRefocusLoop() {
        refocusJob?.cancel()
        refocusJob = null
    }

    private fun performRefocusCheck() {
        val startCheckMs = System.currentTimeMillis()
        val startTime = (startCheckMs - 20_000L).coerceAtLeast(0L)
        val rawEvents = UsageStatsHelper.queryActivityResumedEvents(
            context = applicationContext,
            startTimeMs = startTime,
            endTimeMs = startCheckMs
        )

        val newEvents = rawEvents.filter { seenEvents.add(it) }
        val alert = refocusTracker.processEvents(newEvents, startCheckMs)

        val endCheckMs = System.currentTimeMillis()
        val checkDurationMs = endCheckMs - startCheckMs
        refocusCheckCount++

        TestLogger.log(applicationContext, "REFOCUS_CHECK took ${checkDurationMs}ms")

        if (alert != null) {
            TestLogger.log(
                applicationContext,
                "REFOCUS_ALERT: app=${alert.packageName}, duration=${alert.durationMs}ms"
            )
            showRefocusNotification(alert.packageName, alert.durationMs)
        }
    }

    private fun showRefocusNotification(packageNameStr: String, durationMs: Long) {
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageNameStr, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageNameStr
        }

        val durationMin = (durationMs / 60000L).coerceAtLeast(1L)
        val contentText = "You've been on $appName for $durationMin min."

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, REFOCUS_CHANNEL_ID)
            .setContentTitle("Back to focus?")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(REFOCUS_NOTIFICATION_ID, notification)
    }

    private fun startBatteryLogging() {
        batteryJob?.cancel()
        batteryJob = serviceScope.launch {
            while (true) {
                logBatteryLevel()
                delay(60_000L)
            }
        }
    }

    private fun stopBatteryLogging() {
        batteryJob?.cancel()
        batteryJob = null
    }

    private fun logBatteryLevel() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, filter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (scale > 0) (level * 100 / scale) else -1
            val checks = refocusCheckCount
            refocusCheckCount = 0
            TestLogger.log(applicationContext, "BATTERY_LEVEL: $pct% ($level/$scale) | refocus_checks: $checks")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery level", e)
        }
    }

    private fun registerScreenReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister screen receiver", e)
            }
            isReceiverRegistered = false
        }
    }

    private fun performEpisodeAnalysis(
        unlockTimeMs: Long,
        screenOffTimeMs: Long,
        isStartEpisode: Boolean
    ) {
        val startTime = (unlockTimeMs - 1000L).coerceAtLeast(0L)
        val rawEvents = UsageStatsHelper.queryActivityResumedEvents(
            context = applicationContext,
            startTimeMs = startTime,
            endTimeMs = screenOffTimeMs
        )

        val input = EpisodeInput(
            unlockTimeMs = unlockTimeMs,
            screenOffTimeMs = screenOffTimeMs,
            rawEvents = rawEvents,
            allowedApps = allowedApps,
            isSessionStartEpisode = isStartEpisode,
            isSessionEndEpisode = false
        )

        val result = episodeAnalyzer.analyze(input)
        if (result.shouldSave) {
            episodeSink.onEpisode(result)
        } else {
            Log.d(TAG, "Episode skipped (shouldSave = false)")
            TestLogger.log(applicationContext, "EPISODE SKIPPED (shouldSave = false)")
        }
    }

    private fun stopSessionInternal() {
        stopRefocusLoop()
        refocusTracker.onScreenOff()
        stopBatteryLogging()
        unregisterScreenReceiver()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        TestLogger.log(applicationContext, "SERVICE_STOP")
        Log.d(TAG, "FocusService stopped.")
    }

    override fun onDestroy() {
        serviceInstance = null
        stopRefocusLoop()
        refocusTracker.onScreenOff()
        stopBatteryLogging()
        unregisterScreenReceiver()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Trace Session Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for phone usage detection during focus sessions"
            }
            manager?.createNotificationChannel(channel)

            val refocusChannel = NotificationChannel(
                REFOCUS_CHANNEL_ID,
                "FocusTrace Refocus Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alert notifications when user stays on non-allowed apps for too long"
            }
            manager?.createNotificationChannel(refocusChannel)
        }
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusTrace Session Active")
            .setContentText("Monitoring phone usage during session...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START_SESSION = "com.example.focustrace.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.example.focustrace.action.STOP_SESSION"

        const val REFOCUS_LIMIT_MS = 3 * 60 * 1000L
        
        @Volatile
        var isRefocusCheckEnabled: Boolean = true

        @Volatile
        private var serviceInstance: FocusService? = null

        private const val REFOCUS_CHECK_INTERVAL_MS = 15_000L

        private const val CHANNEL_ID = "focus_trace_service_channel"
        private const val REFOCUS_CHANNEL_ID = "focus_trace_refocus_channel"
        private const val NOTIFICATION_ID = 1001
        private const val REFOCUS_NOTIFICATION_ID = 1002
        private const val ANALYSIS_DELAY_MS = 1500L
        private const val TAG = "FocusTrace_Service"

        fun startService(context: Context) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = ACTION_START_SESSION
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = ACTION_STOP_SESSION
            }
            context.startService(intent)
        }

        fun toggleRefocusPolling(context: Context): Boolean {
            val newState = !isRefocusCheckEnabled
            isRefocusCheckEnabled = newState
            TestLogger.log(context, "REFOCUS_POLLING_TOGGLE: ${if (newState) "ENABLED (ON)" else "DISABLED (OFF)"}")
            val instance = serviceInstance
            if (instance != null) {
                if (newState) {
                    instance.startRefocusLoop()
                } else {
                    instance.stopRefocusLoop()
                }
            }
            return newState
        }
    }
}
