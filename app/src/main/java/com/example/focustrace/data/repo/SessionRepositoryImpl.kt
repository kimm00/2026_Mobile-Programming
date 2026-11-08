package com.example.focustrace.data.repo

import android.content.Context
import com.example.focustrace.data.db.dao.SessionDao
import com.example.focustrace.data.db.dao.SessionDetail
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.db.entity.SessionEntity
import com.example.focustrace.detection.FocusService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SessionRepositoryImpl(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {

    override suspend fun startSession(
        subject: String,
        plan: String?,
        goalMinutes: Int,
        suggestedMinutes: Int?,
        acceptedAiSuggestion: Boolean,
    ): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            subject = subject,
            plan = plan,
            goalMinutes = goalMinutes,
            startAt = now,
            endAt = null,
            status = SessionStatus.RUNNING.name,
            stopReason = null,
            stopMemo = null,
            reflection = null,
            photoUri = null,
            suggestedMinutes = suggestedMinutes,
            acceptedAiSuggestion = acceptedAiSuggestion,
            isSample = false,
            focusMs = 0L,
            phoneUseMs = 0L,
            checkCount = 0,
            unlockOnlyCount = 0,
            longestFocusMs = 0L,
        )
        val newId = sessionDao.insertSession(session)
        FocusService.startService(context, newId, goalMinutes)
        newId
    }

    override suspend fun endSession(
        sessionId: Long,
        status: SessionStatus,
        stopReason: StopReason?,
        stopMemo: String?,
    ) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        sessionDao.updateSessionEnd(
            sessionId = sessionId,
            endAt = now,
            status = status.name,
            stopReason = stopReason?.name,
            stopMemo = stopMemo,
        )
        FocusService.stopService(context)
    }

    override fun observeRunningSession(): Flow<SessionEntity?> {
        return sessionDao.observeRunningSession()
    }

    override fun observeSession(sessionId: Long): Flow<SessionDetail?> {
        return sessionDao.observeSessionDetail(sessionId)
    }

    override suspend fun recoverUnfinishedSessions() = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        sessionDao.markRunningAsUnfinished(now)
    }

    override fun observeAllowedApps(): Flow<List<AllowedAppEntity>> {
        return sessionDao.observeAllowedApps()
    }

    override suspend fun setAllowed(
        packageName: String,
        appLabel: String,
        allowed: Boolean,
    ) = withContext(ioDispatcher) {
        if (allowed) {
            val app = AllowedAppEntity(
                packageName = packageName,
                appLabel = appLabel,
                addedAt = System.currentTimeMillis(),
            )
            sessionDao.insertAllowedApp(app)
        } else {
            sessionDao.deleteAllowedApp(packageName)
        }
    }
}
