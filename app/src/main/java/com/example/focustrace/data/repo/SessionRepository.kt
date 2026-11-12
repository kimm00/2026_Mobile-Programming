package com.example.focustrace.data.repo

import com.example.focustrace.data.db.dao.SessionDetail
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

enum class SessionStatus {
    RUNNING,
    COMPLETED,
    EARLY_STOP,
    UNFINISHED,
}

enum class StopReason {
    DISTRACTED,
    TIRED,
    INTERRUPTED,
    TASK_COMPLETED,
    OTHER,
}

interface SessionRepository {

    /** Called by PrepareActivity to insert a new session and start tracking. */
    suspend fun startSession(
        subject: String,
        plan: String?,
        goalMinutes: Int,
        suggestedMinutes: Int? = null,
        acceptedAiSuggestion: Boolean = false,
    ): Long

    /** Called by SessionActivity, EarlyStopActivity, or FocusService to mark a session ended. */
    suspend fun endSession(
        sessionId: Long,
        status: SessionStatus,
        stopReason: StopReason? = null,
        stopMemo: String? = null,
    )

    /** Called by SessionActivity and App Widget to observe the currently active RUNNING session. */
    fun observeRunningSession(): Flow<SessionEntity?>

    /** Called by FinishActivity and ReportActivity to observe a session with its detailed phone events and app usages. */
    fun observeSession(sessionId: Long): Flow<SessionDetail?>

    /** Called on app launch to close any leftover RUNNING sessions as UNFINISHED. */
    suspend fun recoverUnfinishedSessions()

    /** Called by AllowList screen and PrepareActivity to observe the user-allowed study apps list. */
    fun observeAllowedApps(): Flow<List<AllowedAppEntity>>

    /** Called by AllowList screen to add or remove an app from the allowed study apps list. */
    suspend fun setAllowed(packageName: String, appLabel: String, allowed: Boolean)

    /** Called by JournalActivity and AI Insights to query session history without accessing DAO directly. */
    suspend fun getSessionsByPeriod(
        startTime: Long = 0L,
        endTime: Long = Long.MAX_VALUE
    ): List<SessionEntity>

    /** Called by JournalActivity to update reflection notes for a session. */
    suspend fun updateSessionReflection(sessionId: Long, reflection: String?)
}
