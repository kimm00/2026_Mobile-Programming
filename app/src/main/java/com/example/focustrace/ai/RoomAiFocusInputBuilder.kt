package com.example.focustrace.ai

import com.example.focustrace.data.db.dao.SessionDao
import com.example.focustrace.data.db.entity.SessionEntity

/**
 * Builds [AiFocusInput] aggregated over the last 4 weeks from Room DB [SessionDao].
 *
 * Enforces strict privacy:
 * Contains ONLY weekly aggregate statistics and Early Stop memo texts.
 * Never includes package names, app labels, or phone event timestamps.
 */
object RoomAiFocusInputBuilder {

    suspend fun buildFromDao(
        dao: SessionDao,
        nowMs: Long = System.currentTimeMillis()
    ): AiFocusInput {
        val oneWeekMs = 7L * 24 * 60 * 60 * 1000
        val fourWeeksAgo = nowMs - (4 * oneWeekMs)

        val sessions = dao.getSessionsByPeriod(fourWeeksAgo, nowMs)
        return buildFromSessions(sessions, nowMs)
    }

    fun buildFromSessions(
        sessions: List<SessionEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): AiFocusInput {
        val oneWeekMs = 7L * 24 * 60 * 60 * 1000
        val fourWeeksAgo = nowMs - (4 * oneWeekMs)

        val filteredSessions = sessions.filter {
            it.startAt in fourWeeksAgo..nowMs && it.status != "RUNNING"
        }

        val weeklyStatsList = mutableListOf<WeeklyFocusStats>()

        for (i in 3 downTo 0) {
            val weekStart = nowMs - ((i + 1) * oneWeekMs)
            val weekEnd = nowMs - (i * oneWeekMs)
            val weekLabel = when (i) {
                0 -> "Week 4 (Current Week)"
                1 -> "Week 3 (1 Week Ago)"
                2 -> "Week 2 (2 Weeks Ago)"
                3 -> "Week 1 (3 Weeks Ago)"
                else -> "Week ${4 - i}"
            }

            val weekSessions = filteredSessions.filter { it.startAt in weekStart until weekEnd }

            val totalSessions = weekSessions.size
            val completedSessions = weekSessions.count { it.status == "COMPLETED" }
            val earlyStopSessions = weekSessions.count { it.status == "EARLY_STOP" }
            val totalFocusMins = weekSessions.sumOf { it.focusMs } / 60_000
            val totalPhoneUseMins = weekSessions.sumOf { it.phoneUseMs } / 60_000
            val checkCount = weekSessions.sumOf { it.checkCount }
            val longestFocusMins = (weekSessions.maxOfOrNull { it.longestFocusMs } ?: 0L) / 60_000

            weeklyStatsList.add(
                WeeklyFocusStats(
                    weekLabel = weekLabel,
                    totalSessions = totalSessions,
                    completedSessions = completedSessions,
                    earlyStopSessions = earlyStopSessions,
                    totalFocusMinutes = totalFocusMins,
                    totalPhoneUseMinutes = totalPhoneUseMins,
                    checkCount = checkCount,
                    longestFocusMinutes = longestFocusMins
                )
            )
        }

        // Per requirement, real Room DB AI requests include only weekly aggregate metrics
        // and omit early stop memo text.
        return AiFocusInput(
            weeklyStats = weeklyStatsList,
            earlyStopMemos = emptyList()
        )
    }
}
