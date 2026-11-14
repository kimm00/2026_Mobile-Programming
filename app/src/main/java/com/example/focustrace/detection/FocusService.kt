package com.example.focustrace.detection

import android.Manifest
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
import com.example.focustrace.R
import com.example.focustrace.data.db.entity.RefocusAlertEntity
import com.example.focustrace.ui.SessionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    private var refocusTracker: RefocusTracker? = null
    private var refocusMonitoringJob: Job? = null
    private var activeAlertId: Long? = null
    private var activeAlertPackageName: String? = null
    private var activeAlertFiredAt: Long = 0L
    private var lastRefocusCheckTimeMs: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isStopping) return

            val now = System.currentTimeMillis()

            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    lastUnlockTimeMs = now
                    startRefocusMonitoring(now)
                    Log.d(TAG, "USER_PRESENT: $now")
                }

                Intent.ACTION_SCREEN_OFF -> {
                    stopRefocusMonitoring(now)
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
        if (intent == null || intent.action == null) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

            serviceScope.launch {
                val dao = (applicationContext as FocusTraceApp).database.sessionDao()
                val runningSession = dao.getRunningSession()

                if (runningSession != null) {
                    Log.d(
                        TAG,
                        "Null intent restart: resuming running session id=${runningSession.id}, goalMinutes=${runningSession.goalMinutes} (previous sessionId=$sessionId)"
                    )
                    startOrSwitchSession(
                        newSessionId = runningSession.id,
                        newGoalMinutes = runningSession.goalMinutes,
                        isResume = true
                    )
                } else {
                    Log.d(
                        TAG,
                        "Null intent restart: no running session in DB, stopping service (sessionId=$sessionId)"
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }

            return START_STICKY
        }

        when (intent.action) {
            ACTION_START_SESSION -> {
                val requestedId = intent.getLongExtra(
                    EXTRA_SESSION_ID,
                    -1L
                )

                if (requestedId <= 0L) {
                    Log.e(TAG, "Invalid session ID: $requestedId")
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_STICKY
                }

                val requestedGoalMinutes = intent.getIntExtra(EXTRA_GOAL_MINUTES, 0)

                if (sessionId > 0L) {
                    if (requestedId == sessionId) {
                        Log.d(
                            TAG,
                            "START while already running: same sessionId=$sessionId, keeping running"
                        )
                        return START_STICKY
                    } else {
                        Log.d(
                            TAG,
                            "START while running: switching session from oldSessionId=$sessionId to newSessionId=$requestedId"
                        )
                        startOrSwitchSession(
                            newSessionId = requestedId,
                            newGoalMinutes = requestedGoalMinutes,
                            isResume = false
                        )
                        return START_STICKY
                    }
                }

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )

                startOrSwitchSession(
                    newSessionId = requestedId,
                    newGoalMinutes = requestedGoalMinutes,
                    isResume = false
                )
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
                    Log.w(
                        TAG,
                        "Stop requested for session $requestedId, but current session is $sessionId"
                    )
                    receiver?.send(RESULT_ERROR, Bundle())
                    return START_STICKY
                }

                if (sessionId <= 0L) {
                    Log.d(
                        TAG,
                        "Stop requested when service not running a valid session (sessionId=$sessionId)"
                    )
                    receiver?.send(RESULT_OK, Bundle())
                    stopSelf()
                    return START_STICKY
                }
                if (isStopping) {
                    Log.w(
                        TAG,
                        "Stop requested while already stopping (sessionId=$sessionId)"
                    )
                    receiver?.send(RESULT_ERROR, Bundle())
                    return START_STICKY
                }

                Log.d(TAG, "Stopping session id=$sessionId")
                isStopping = true
                unregisterScreenReceiver()

                val finalUnlockTime = lastUnlockTimeMs
                val finalEndTime = System.currentTimeMillis()
                val finalStartFlag = isSessionStartEpisode

                stopRefocusMonitoring(finalEndTime)

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
                        Log.d(TAG, "All episodes saved for session id=$sessionId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save episodes for session id=$sessionId", e)
                        receiver?.send(RESULT_ERROR, Bundle())
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startOrSwitchSession(
        newSessionId: Long,
        newGoalMinutes: Int,
        isResume: Boolean
    ) {
        sessionId = newSessionId
        goalMinutes = newGoalMinutes
        isStopping = false
        val now = System.currentTimeMillis()
        lastUnlockTimeMs = now
        isSessionStartEpisode = true

        stopRefocusMonitoring(now)

        val dao = (applicationContext as FocusTraceApp).database.sessionDao()

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

            val excludedApps = setOfNotNull(packageName, homePackageName, "com.android.systemui")
            refocusTracker = RefocusTracker(
                refocusLimitMs = REFOCUS_LIMIT_MS,
                allowedApps = allowedApps,
                excludedApps = excludedApps
            )

            registerScreenReceiver()
            updateNotification()

            startRefocusMonitoring(now)

            if (isResume) {
                Log.d(TAG, "Resumed session id=$sessionId, goalMinutes=$goalMinutes")
            } else {
                Log.d(TAG, "Started session id=$sessionId, goalMinutes=$goalMinutes")
            }
        }
    }

    private fun closeActiveRefocusAlert(leftAtMs: Long) {
        val alertId = activeAlertId ?: return
        activeAlertId = null
        activeAlertPackageName = null
        activeAlertFiredAt = 0L
        serviceScope.launch {
            val dao = (applicationContext as FocusTraceApp).database.sessionDao()
            dao.updateRefocusAlertLeftAt(alertId, leftAtMs)
        }
    }

    private fun stopRefocusMonitoring(leftAtMs: Long) {
        refocusMonitoringJob?.cancel()
        refocusMonitoringJob = null
        closeActiveRefocusAlert(leftAtMs)
        refocusTracker?.onScreenOff()
    }

    private fun startRefocusMonitoring(unlockTimeMs: Long) {
        refocusMonitoringJob?.cancel()
        lastRefocusCheckTimeMs = unlockTimeMs

        val tracker = refocusTracker ?: return
        tracker.onUnlock(unlockTimeMs)

        refocusMonitoringJob = serviceScope.launch {
            val dao = (applicationContext as FocusTraceApp).database.sessionDao()

            while (isActive) {
                delay(REFOCUS_CHECK_INTERVAL_MS)
                if (!isActive) break

                val now = System.currentTimeMillis()
                val queryStart = (lastRefocusCheckTimeMs - REFOCUS_OVERLAP_MS).coerceAtLeast(0L)
                val events = UsageStatsHelper.queryActivityResumedEvents(
                    context = applicationContext,
                    startTimeMs = queryStart,
                    endTimeMs = now
                )
                lastRefocusCheckTimeMs = now

                val alertPkg = activeAlertPackageName
                if (activeAlertId != null && alertPkg != null) {
                    val leaveEvent = events.firstOrNull {
                        it.packageName != alertPkg && it.timestampMs >= activeAlertFiredAt
                    }
                    if (leaveEvent != null) {
                        closeActiveRefocusAlert(leaveEvent.timestampMs)
                    }
                }

                val alert = tracker.processEvents(events, now)
                if (alert != null && sessionId > 0L) {
                    val alertEntity = RefocusAlertEntity(
                        sessionId = sessionId,
                        packageName = alert.packageName,
                        firedAt = now,
                        usedMsBeforeAlert = alert.durationMs,
                        leftAt = null
                    )
                    val insertedId = dao.insertRefocusAlert(alertEntity)
                    activeAlertId = insertedId
                    activeAlertPackageName = alert.packageName
                    activeAlertFiredAt = now

                    showRefocusNotification(alert.packageName)
                }
            }
        }
    }

    private fun showRefocusNotification(packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission not granted, skipping refocus notification")
                return
            }
        }

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        val intent = Intent(this, SessionActivity::class.java).apply {
            putExtra(SessionActivity.EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, REFOCUS_CHANNEL_ID)
            .setContentTitle("Back to focus?")
            .setContentText("You've been on $appLabel for 3 min.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            ?.notify(REFOCUS_NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
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
        stopRefocusMonitoring(System.currentTimeMillis())
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return

        val sessionChannel = NotificationChannel(
            CHANNEL_ID,
            "FocusTrace Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Phone usage monitoring"
        }

        val refocusChannel = NotificationChannel(
            REFOCUS_CHANNEL_ID,
            "Refocus Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Refocus alerts for non-allowed app usage"
        }

        notificationManager.createNotificationChannel(sessionChannel)
        notificationManager.createNotificationChannel(refocusChannel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, SessionActivity::class.java).apply {
            putExtra(SessionActivity.EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
        private const val REFOCUS_CHANNEL_ID = "refocus_alert_channel"
        private const val REFOCUS_NOTIFICATION_ID = 2002
        private const val REFOCUS_CHECK_INTERVAL_MS = 15_000L
        private const val REFOCUS_LIMIT_MS = 3 * 60 * 1000L
        private const val REFOCUS_OVERLAP_MS = 5_000L
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
