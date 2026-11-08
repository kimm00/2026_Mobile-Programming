package com.example.focustrace.data.repo

import com.example.focustrace.data.db.dao.SessionDao
import com.example.focustrace.data.db.dao.SessionDetail
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

class RoomSessionRepository(
    private val dao: SessionDao
) : SessionRepository {

    override suspend fun startSession(
        subject: String,
        plan: String?,
        goalMinutes: Int,
        suggestedMinutes: Int?,
        acceptedAiSuggestion: Boolean
    ): Long {
        require(goalMinutes > 0) {
            "goalMinutes must be positive"
        }

        val session = SessionEntity(
            subject = subject,
            plan = plan,
            goalMinutes = goalMinutes,
            startAt = System.currentTimeMillis(),
            status = SessionStatus.RUNNING.name,
            suggestedMinutes = suggestedMinutes,
            acceptedAiSuggestion = acceptedAiSuggestion
        )

        return dao.insertSession(session)
    }

    override suspend fun endSession(
        sessionId: Long,
        status: SessionStatus,
        stopReason: StopReason?,
        stopMemo: String?
    ) {
        require(status != SessionStatus.RUNNING) {
            "Cannot end a session with RUNNING status"
        }

        dao.updateSessionEnd(
            sessionId = sessionId,
            endAt = System.currentTimeMillis(),
            status = status.name,
            stopReason = stopReason?.name,
            stopMemo = stopMemo
        )
    }

    override fun observeRunningSession(): Flow<SessionEntity?> {
        return dao.observeRunningSession()
    }

    override fun observeSession(sessionId: Long): Flow<SessionDetail?> {
        return dao.observeSessionDetail(sessionId)
    }

    override suspend fun recoverUnfinishedSessions() {
        dao.markRunningAsUnfinished(System.currentTimeMillis())
    }

    override fun observeAllowedApps(): Flow<List<AllowedAppEntity>> {
        return dao.observeAllowedApps()
    }

    override suspend fun setAllowed(
        packageName: String,
        appLabel: String,
        allowed: Boolean
    ) {
        if (allowed) {
            dao.insertAllowedApp(
                AllowedAppEntity(
                    packageName = packageName,
                    appLabel = appLabel,
                    addedAt = System.currentTimeMillis()
                )
            )
        } else {
            dao.deleteAllowedApp(packageName)
        }
    }
}
