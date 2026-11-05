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

    private lateinit var episodeAnalyzer: EpisodeAnalyzer

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val now = System.currentTimeMillis()

            when (action) {
                Intent.ACTION_USER_PRESENT -> {
                    lastUnlockTimeMs = now
                    Log.d(TAG, "USER_PRESENT received at $lastUnlockTimeMs")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (lastUnlockTimeMs == 0L) {
                        Log.d(TAG, "SCREEN_OFF ignored (no prior unlock recorded)")
                        return
                    }

                    val unlockT = lastUnlockTimeMs
                    val screenOffT = now
                    val startEpisodeFlag = isSessionStartEpisode
                    isSessionStartEpisode = false
                    lastUnlockTimeMs = 0L

                    Log.d(TAG, "SCREEN_OFF received at $screenOffT. Scheduling analysis in 1.5s.")

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
        val currentTime = System.currentTimeMillis()
        lastUnlockTimeMs = currentTime

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        homePackageName = resolvedHome?.activityInfo?.packageName

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
                Log.d(TAG, "FocusService started. Unlock time initialized to $lastUnlockTimeMs")
            }
            ACTION_STOP_SESSION -> {
                stopSessionInternal()
            }
        }

        return START_STICKY
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
        }
    }

    private fun stopSessionInternal() {
        unregisterScreenReceiver()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "FocusService stopped.")
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Trace Session Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for phone usage detection during focus sessions"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
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

        private const val CHANNEL_ID = "focus_trace_service_channel"
        private const val NOTIFICATION_ID = 1001
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
    }
}
