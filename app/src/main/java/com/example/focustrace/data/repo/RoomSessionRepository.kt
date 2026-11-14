package com.example.focustrace.data.repo

import com.example.focustrace.data.db.dao.SessionDao
import com.example.focustrace.data.db.dao.SessionDetail
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.db.entity.AppUsageEntity
import com.example.focustrace.data.db.entity.DeviceStateEntity
import com.example.focustrace.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

class RoomSessionRepository(
    private val dao: SessionDao,
) : SessionRepository {

    override suspend fun startSession(
        subject: String,
        plan: String?,
        goalMinutes: Int,
        suggestedMinutes: Int?,
        acceptedAiSuggestion: Boolean,
    ): Long {
        require(goalMinutes > 0) {
            "goalMinutes must be positive"
        }

        dao.markRunningAsUnfinished(System.currentTimeMillis())

        val session = SessionEntity(
            subject = subject,
            plan = plan,
            goalMinutes = goalMinutes,
            startAt = System.currentTimeMillis(),
            status = SessionStatus.RUNNING.name,
            suggestedMinutes = suggestedMinutes,
            acceptedAiSuggestion = acceptedAiSuggestion,
        )

        return dao.insertSession(session)
    }

    override suspend fun endSession(
        sessionId: Long,
        status: SessionStatus,
        stopReason: StopReason?,
        stopMemo: String?,
        endAt: Long?,
    ) {
        require(status != SessionStatus.RUNNING) {
            "Cannot end a session with RUNNING status"
        }

        val session = dao.getSessionById(sessionId) ?: return

        if (session.status != SessionStatus.RUNNING.name) return

        val actualEndAt = endAt ?: System.currentTimeMillis()
        val elapsedMs = (actualEndAt - session.startAt).coerceAtLeast(0L)

        dao.closeOpenDeviceStates(sessionId, actualEndAt)

        val events = dao.getEvents(sessionId)
        val usages = dao.getUsages(sessionId)
        val deviceStates = dao.getDeviceStates(sessionId)

        val countedPhoneUsages = usages.filter {
            it.isCounted && !it.isAllowed
        }

        val mergedIntervals = mergeIntervals(
            countedPhoneUsages,
            session.startAt,
            actualEndAt,
        )

        val phoneUseMs = mergedIntervals.sumOf {
            it.second - it.first
        }

        val focusMs = (elapsedMs - phoneUseMs).coerceAtLeast(0L)

        val checkCount = events.count {
            it.kind == "CHECK"
        }

        val unlockOnlyCount = events.count {
            it.kind == "UNLOCK_ONLY"
        }

        val longestFocusMs = calculateLongestFocus(
            session.startAt,
            actualEndAt,
            mergedIntervals,
        )

        val earphoneMs = calculateDeviceStateMs(
            deviceStates,
            "EARPHONE",
            session.startAt,
            actualEndAt,
        )

        val faceDownMs = calculateDeviceStateMs(
            deviceStates,
            "FACE_DOWN",
            session.startAt,
            actualEndAt,
        )

        dao.finishSession(
            sessionId = sessionId,
            endAt = actualEndAt,
            status = status.name,
            stopReason = stopReason?.name,
            stopMemo = stopMemo,
            focusMs = focusMs,
            phoneUseMs = phoneUseMs,
            checkCount = checkCount,
            unlockOnlyCount = unlockOnlyCount,
            longestFocusMs = longestFocusMs,
            earphoneMs = earphoneMs,
            faceDownMs = faceDownMs,
        )
    }

    private fun calculateDeviceStateMs(
        states: List<DeviceStateEntity>,
        type: String,
        sessionStart: Long,
        sessionEnd: Long,
    ): Long {
        val matching = states.filter { it.type == type }
        val intervals = matching.mapNotNull { state ->
            val start = maxOf(state.startAt, sessionStart)
            val stateEnd = state.endAt ?: sessionEnd
            val end = minOf(stateEnd, sessionEnd)
            if (end > start) start to end else null
        }.sortedBy { it.first }

        if (intervals.isEmpty()) return 0L

        var mergedTotal = 0L
        var currentStart = intervals[0].first
        var currentEnd = intervals[0].second

        for (i in 1 until intervals.size) {
            val (start, end) = intervals[i]
            if (start <= currentEnd) {
                currentEnd = maxOf(currentEnd, end)
            } else {
                mergedTotal += (currentEnd - currentStart)
                currentStart = start
                currentEnd = end
            }
        }
        mergedTotal += (currentEnd - currentStart)
        return mergedTotal
    }

    private fun mergeIntervals(
        usages: List<AppUsageEntity>,
        sessionStart: Long,
        sessionEnd: Long,
    ): List<Pair<Long, Long>> {
        val intervals = usages.mapNotNull { usage ->
            val start = maxOf(usage.startAt, sessionStart)
            val end = minOf(
                usage.startAt + usage.durationMs,
                sessionEnd,
            )

            if (end > start) {
                start to end
            } else {
                null
            }
        }.sortedBy { it.first }

        if (intervals.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<Long, Long>>()
        var currentStart = intervals[0].first
        var currentEnd = intervals[0].second

        for (i in 1 until intervals.size) {
            val (start, end) = intervals[i]

            if (start <= currentEnd) {
                currentEnd = maxOf(currentEnd, end)
            } else {
                merged.add(currentStart to currentEnd)
                currentStart = start
                currentEnd = end
            }
        }

        merged.add(currentStart to currentEnd)
        return merged
    }

    private fun calculateLongestFocus(
        sessionStart: Long,
        sessionEnd: Long,
        phoneIntervals: List<Pair<Long, Long>>,
    ): Long {
        var previousEnd = sessionStart
        var longest = 0L

        for ((start, end) in phoneIntervals) {
            longest = maxOf(
                longest,
                (start - previousEnd).coerceAtLeast(0L),
            )
            previousEnd = maxOf(previousEnd, end)
        }

        longest = maxOf(
            longest,
            (sessionEnd - previousEnd).coerceAtLeast(0L),
        )

        return longest
    }

    override fun observeRunningSession(): Flow<SessionEntity?> {
        return dao.observeRunningSession()
    }

    override fun observeSession(
        sessionId: Long,
    ): Flow<SessionDetail?> {
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
        allowed: Boolean,
    ) {
        if (allowed) {
            dao.insertAllowedApp(
                AllowedAppEntity(
                    packageName = packageName,
                    appLabel = appLabel,
                    addedAt = System.currentTimeMillis(),
                )
            )
        } else {
            dao.deleteAllowedApp(packageName)
        }
    }

    override suspend fun getSessionsByPeriod(
        startTime: Long,
        endTime: Long,
    ): List<SessionEntity> {
        return dao.getSessionsByPeriod(startTime, endTime)
    }

    override suspend fun savePhoto(sessionId: Long, photoUri: String?) {
        dao.updateSessionPhoto(sessionId, photoUri)
    }

    override suspend fun updateSessionReflection(sessionId: Long, reflection: String?) {
        dao.updateSessionReflection(sessionId, reflection)
    }

    override suspend fun saveJournalPostId(sessionId: Long, postId: String?) {
        dao.updateJournalPostId(sessionId, postId)
    }
}
