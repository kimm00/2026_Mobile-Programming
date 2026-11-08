package com.example.focustrace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.db.entity.AppUsageEntity
import com.example.focustrace.data.db.entity.PhoneEventEntity
import com.example.focustrace.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionDao {

    @Insert
    abstract suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET endAt = :endAt, status = :status, stopReason = :stopReason, stopMemo = :stopMemo WHERE id = :sessionId")
    abstract suspend fun updateSessionEnd(
        sessionId: Long,
        endAt: Long,
        status: String,
        stopReason: String?,
        stopMemo: String?
    )

    @Query("UPDATE sessions SET focusMs = :focusMs, phoneUseMs = :phoneUseMs, checkCount = :checkCount, unlockOnlyCount = :unlockOnlyCount, longestFocusMs = :longestFocusMs WHERE id = :sessionId")
    abstract suspend fun updateSessionSummary(
        sessionId: Long,
        focusMs: Long,
        phoneUseMs: Long,
        checkCount: Int,
        unlockOnlyCount: Int,
        longestFocusMs: Long
    )

    @Insert
    abstract suspend fun insertPhoneEvent(event: PhoneEventEntity): Long

    @Insert
    abstract suspend fun insertAppUsages(usages: List<AppUsageEntity>)

    @Transaction
    open suspend fun insertEpisode(event: PhoneEventEntity, usages: List<AppUsageEntity>) {
        val eventId = insertPhoneEvent(event)
        val usagesWithEventId = usages.map { it.copy(eventId = eventId) }
        insertAppUsages(usagesWithEventId)
    }

    @Query("SELECT * FROM phone_events WHERE sessionId = :sessionId ORDER BY startAt ASC")
    abstract suspend fun getEvents(sessionId: Long): List<PhoneEventEntity>

    @Query("SELECT * FROM app_usages WHERE sessionId = :sessionId ORDER BY startAt ASC")
    abstract suspend fun getUsages(sessionId: Long): List<AppUsageEntity>

    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' ORDER BY startAt DESC LIMIT 1")
    abstract fun observeRunningSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' ORDER BY startAt DESC LIMIT 1")
    abstract suspend fun getRunningSession(): SessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    abstract fun observeSessionDetail(sessionId: Long): Flow<SessionDetail?>

    @Query("UPDATE sessions SET status = 'UNFINISHED', endAt = :endAt WHERE status = 'RUNNING'")
    abstract suspend fun markRunningAsUnfinished(endAt: Long)

    @Query("SELECT * FROM allowed_apps ORDER BY appLabel ASC")
    abstract fun observeAllowedApps(): Flow<List<AllowedAppEntity>>

    @Query("SELECT packageName FROM allowed_apps")
    abstract suspend fun getAllowedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllowedApp(app: AllowedAppEntity)

    @Query("DELETE FROM allowed_apps WHERE packageName = :packageName")
    abstract suspend fun deleteAllowedApp(packageName: String)

    @Query("""
    SELECT * FROM sessions
        WHERE startAt >= :startTime
          AND startAt < :endTime
          AND status != 'RUNNING'
        ORDER BY startAt DESC
    """)
    abstract suspend fun getSessionsByPeriod(
        startTime: Long,
        endTime: Long
    ): List<SessionEntity>

    @Query("""
    SELECT * FROM phone_events
        WHERE sessionId IN (:sessionIds)
        ORDER BY startAt ASC
    """)
    abstract suspend fun getEventsBySessionIds(
        sessionIds: List<Long>
    ): List<PhoneEventEntity>

    @Query("""
    SELECT * FROM app_usages
        WHERE sessionId IN (:sessionIds)
        ORDER BY startAt ASC
    """)
    abstract suspend fun getUsagesBySessionIds(
        sessionIds: List<Long>
    ): List<AppUsageEntity>
}
