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
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.focustrace.FocusTraceApp
import com.example.focustrace.MainActivity
import com.example.focustrace.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FocusService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope =
        CoroutineScope(Dispatchers.IO + serviceJob)

    private val analysisMutex = Mutex()
    private val pendingAnalyses = mutableListOf<Job>()

    private var sessionId = -1L
    private var goalMinutes = 0
    private var lastUnlockTimeMs = 0L
    private var isSessionStartEpisode = true
    private var homePackageName: String? = null
    private var isReceiverRegistered = false
    private var isStopping = false

    private var allowedApps: Set<String> = emptySet()

    private lateinit var episodeAnalyzer: EpisodeAnalyzer
    private lateinit var episodeSink: EpisodeSink

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isStopping) return

            val now = System.currentTimeMillis()

            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    lastUnlockTimeMs = now
                    Log.d(TAG, "USER_PRESENT: $now")
                }

                Intent.ACTION_SCREEN_OFF -> {
                    if (lastUnlockTimeMs == 0L) return

                    val unlockTime = lastUnlockTimeMs
                    val screenOffTime = now
                    val startFlag = isSessionStartEpisode

                    lastUnlockTimeMs = 0L
                    isSessionStartEpisode = false

                    val job = serviceScope.launch {
                        delay(ANALYSIS_DELAY_MS)

                        analysisMutex.withLock {
                            performEpisodeAnalysis(
                                unlockTime,
                                screenOffTime,
                                startFlag
                            )
                        }
                    }

                    synchronized(pendingAnalyses) {
                        pendingAnalyses.add(job)
                    }

                    job.invokeOnCompletion {
                        synchronized(pendingAnalyses) {
                            pendingAnalyses.remove(job)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)

        val resolvedHome = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        homePackageName = resolvedHome?.activityInfo?.packageName

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START_SESSION -> {
                val requestedId = intent.getLongExtra(
                    EXTRA_SESSION_ID,
                    -1L
                )

                if (requestedId <= 0L) {
                    Log.e(TAG, "Invalid session ID")
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (sessionId > 0L) {
                    Log.w(TAG, "Service already running")
                    return START_NOT_STICKY
                }

                sessionId = requestedId
                goalMinutes = intent.getIntExtra(EXTRA_GOAL_MINUTES, 0)
                isStopping = false

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )

                val dao = (applicationContext as FocusTraceApp).database
                    .sessionDao()

                episodeSink = RoomEpisodeSink(
                    sessionDao = dao,
                    sessionId = sessionId,
                    scope = serviceScope,
                    packageManager = packageManager
                )

                serviceScope.launch {
                    allowedApps = dao.getAllowedPackages().toSet()

                    episodeAnalyzer = EpisodeAnalyzer(
                        thisPackageName = packageName,
                        homePackageName = homePackageName,
                        allowedPackages = allowedApps
                    )

                    lastUnlockTimeMs = System.currentTimeMillis()
                    isSessionStartEpisode = true

                    registerScreenReceiver()

                    Log.d(TAG, "Started session=$sessionId, goalMinutes=$goalMinutes")
                }
            }

            ACTION_STOP_SESSION -> {
                val requestedId = intent.getLongExtra(
                    EXTRA_SESSION_ID,
                    -1L
                )

                val receiver = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(
                        EXTRA_RESULT_RECEIVER,
                        ResultReceiver::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
                }

                if (requestedId > 0L && requestedId != sessionId) {
                    receiver?.send(RESULT_ERROR, Bundle())
                    return START_NOT_STICKY
                }

                if (sessionId <= 0L || isStopping) {
                    receiver?.send(RESULT_ERROR, Bundle())
                    return START_NOT_STICKY
                }

                isStopping = true
                unregisterScreenReceiver()

                val finalUnlockTime = lastUnlockTimeMs
                val finalEndTime = System.currentTimeMillis()
                val finalStartFlag = isSessionStartEpisode

                lastUnlockTimeMs = 0L

                serviceScope.launch {
                    try {
                        val jobs = synchronized(pendingAnalyses) {
                            pendingAnalyses.toList()
                        }

                        jobs.forEach { it.join() }

                        analysisMutex.withLock {
                            if (finalUnlockTime != 0L) {
                                performEpisodeAnalysis(
                                    unlockTimeMs = finalUnlockTime,
                                    screenOffTimeMs = finalEndTime,
                                    isStartEpisode = finalStartFlag,
                                    isEndEpisode = true
                                )
                            }
                        }

                        receiver?.send(RESULT_OK, Bundle())
                        Log.d(TAG, "All episodes saved")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save episodes", e)
                        receiver?.send(RESULT_ERROR, Bundle())
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun performEpisodeAnalysis(
        unlockTimeMs: Long,
        screenOffTimeMs: Long,
        isStartEpisode: Boolean,
        isEndEpisode: Boolean = false
    ) {
        val rawEvents = UsageStatsHelper.queryActivityResumedEvents(
            context = applicationContext,
            startTimeMs = (unlockTimeMs - 1000L).coerceAtLeast(0L),
            endTimeMs = screenOffTimeMs
        )

        val result = episodeAnalyzer.analyze(
            EpisodeInput(
                unlockTimeMs = unlockTimeMs,
                screenOffTimeMs = screenOffTimeMs,
                rawEvents = rawEvents,
                allowedApps = allowedApps,
                isSessionStartEpisode = isStartEpisode,
                isSessionEndEpisode = isEndEpisode
            )
        )

        if (result.shouldSave) {
            episodeSink.onEpisode(result)

            Log.d(
                TAG,
                "Saved episode: ${result.kind}, " +
                        "apps=${result.appUsages.size}"
            )
        }
    }

    private fun registerScreenReceiver() {
        if (isReceiverRegistered) return

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

    private fun unregisterScreenReceiver() {
        if (!isReceiverRegistered) return

        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver unregister failed", e)
        }

        isReceiverRegistered = false
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FocusTrace Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Phone usage monitoring"
        }

        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusTrace Session Active")
            .setContentText("Monitoring phone usage")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START_SESSION =
            "com.example.focustrace.action.START_SESSION"

        const val ACTION_STOP_SESSION =
            "com.example.focustrace.action.STOP_SESSION"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_GOAL_MINUTES = "extra_goal_minutes"
        const val EXTRA_RESULT_RECEIVER = "result_receiver"

        const val RESULT_OK = 1
        const val RESULT_ERROR = 0

        private const val CHANNEL_ID = "focus_trace_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ANALYSIS_DELAY_MS = 1500L
        private const val TAG = "FocusTrace_Service"

        fun startService(context: Context, sessionId: Long, goalMinutes: Int = 0) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = ACTION_START_SESSION
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_GOAL_MINUTES, goalMinutes)
            }

            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = ACTION_STOP_SESSION
            }

            context.startService(intent)
        }

        fun stopService(
            context: Context,
            sessionId: Long,
            receiver: ResultReceiver
        ) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = ACTION_STOP_SESSION
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
            }

            context.startService(intent)
        }
    }
}
