package com.example.focustrace

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.db.entity.AppUsageEntity
import com.example.focustrace.data.db.entity.PhoneEventEntity
import com.example.focustrace.data.db.entity.SessionEntity
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.RoomReportRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RoomReportRepositoryFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomReportRepository
    private val zoneId = ZoneId.systemDefault()

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = RoomReportRepository(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testObserveReport_ReactsToSessionsEventsAndUsagesChanges() = runBlocking {
        val start = 1000L
        val end = 999999999999L

        val flow = repository.observeReport(PeriodType.DAY, start, end, zoneId)

        // 1. Initial emission should be empty
        val initial = flow.first()
        assertEquals(0, initial.sessions.size)

        // 2. Insert session
        val sessionId = db.sessionDao().insertSession(
            SessionEntity(
                subject = "Math",
                plan = "Study algebra",
                goalMinutes = 30,
                startAt = 5000L,
                status = "COMPLETED"
            )
        )

        // Insert phone event
        val eventId = db.sessionDao().insertPhoneEvent(
            PhoneEventEntity(
                sessionId = sessionId,
                startAt = 5100L,
                endAt = 5200L,
                kind = "CHECK"
            )
        )

        // Insert app usage
        db.sessionDao().insertAppUsages(
            listOf(
                AppUsageEntity(
                    eventId = eventId,
                    sessionId = sessionId,
                    packageName = "com.example.app",
                    appLabel = "TestApp",
                    startAt = 5150L,
                    durationMs = 1000L,
                    isAllowed = false,
                    isAuto = false,
                    isCounted = true
                )
            )
        )

        // Await until report has sessions and app usages
        val updatedReport = withTimeout(5000L) {
            flow.first { it.sessions.isNotEmpty() && it.topAppUsages.isNotEmpty() }
        }

        assertEquals(1, updatedReport.sessions.size)
        assertEquals("Math", updatedReport.sessions[0].subject)
        assertEquals(1, updatedReport.topAppUsages.size)
        assertEquals("TestApp", updatedReport.topAppUsages[0].appLabel)

        // 3. Test deletion
        db.sessionDao().deleteSession(sessionId)

        val finalReport = withTimeout(5000L) {
            flow.first { it.sessions.isEmpty() }
        }
        assertEquals(0, finalReport.sessions.size)
    }
}
