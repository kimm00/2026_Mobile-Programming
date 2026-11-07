package com.example.focustrace

import com.example.focustrace.data.model.DailyFocusStat
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.model.ReportSession
import com.example.focustrace.domain.ReportCalculator
import com.example.focustrace.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class ReportCalculatorTest {

    private lateinit var calculator: ReportCalculator

    @Before
    fun setUp() {
        calculator = ReportCalculator(zoneId = SampleData.TEST_ZONE_ID)
    }

    @Test
    fun testDayReport_CalculatesCorrectMetrics() {
        // Day 1: 2026-03-01 00:00 to 2026-03-02 00:00
        val dayStart = SampleData.getEpochMs(0, 0, 0)
        val dayEnd = SampleData.getEpochMs(1, 0, 0)

        val report = calculator.calculateReport(
            periodType = PeriodType.DAY,
            periodStart = dayStart,
            periodEnd = dayEnd,
            sessions = SampleData.SESSIONS,
            phoneEvents = SampleData.PHONE_EVENTS,
            appUsages = SampleData.APP_USAGES
        )

        val summary = report.summary

        // Day 1: Session 1 (50m focus, 2 checks, COMPLETED) + Session 2 (40m focus, 1 check, EARLY_STOP TIRED)
        val expectedFocusMs = (50 + 40) * 60 * 1000L // 5,400,000 ms (1.5 hours)
        assertEquals(expectedFocusMs, summary.totalFocusMs)
        assertEquals(3, summary.totalCheckCount)
        assertEquals(2, summary.totalFinishedSessionCount)
        assertEquals(1, summary.completedSessionCount)
        assertEquals(50.0, summary.goalCompletionRate, 0.001)

        // Checks per hour = (3 checks / 1.5 hours) = 2.0
        assertEquals(2.0, summary.checksPerFocusHour, 0.001)

        // Longest focus period = 30 minutes (Session 1)
        val expectedLongest = 30 * 60 * 1000L
        assertEquals(expectedLongest, summary.longestFocusMs)

        // Early stop reasons: 1 TIRED
        assertEquals(1, report.earlyStopReasons.size)
        assertEquals("TIRED", report.earlyStopReasons[0].reasonCode)
        assertEquals(1, report.earlyStopReasons[0].count)
    }

    @Test
    fun testWeekReport_CalculatesCorrectMetricsAndDailyGrouping() {
        // Week 1: 2026-03-01 00:00 to 2026-03-08 00:00 (7 days)
        val weekStart = SampleData.getEpochMs(0, 0, 0)
        val weekEnd = SampleData.getEpochMs(7, 0, 0)

        val report = calculator.calculateReport(
            periodType = PeriodType.WEEK,
            periodStart = weekStart,
            periodEnd = weekEnd,
            sessions = SampleData.SESSIONS,
            phoneEvents = SampleData.PHONE_EVENTS,
            appUsages = SampleData.APP_USAGES
        )

        val summary = report.summary

        // Sessions 1, 2, 3, 4, 5 included (Session 6 RUNNING is excluded)
        val expectedFocusMs = 275 * 60 * 1000L
        assertEquals(expectedFocusMs, summary.totalFocusMs)
        assertEquals(9, summary.totalCheckCount)
        assertEquals(5, summary.totalFinishedSessionCount)
        assertEquals(3, summary.completedSessionCount)
        assertEquals(60.0, summary.goalCompletionRate, 0.001)

        // Daily breakdown should have 7 entries
        assertEquals(7, report.dailyStats.size)

        // Day 1 (2026-03-01): 90 min
        assertEquals(LocalDate.of(2026, 3, 1), report.dailyStats[0].date)
        assertEquals(90 * 60 * 1000L, report.dailyStats[0].focusMs)

        // Day 2 (2026-03-02): 110 min
        assertEquals(LocalDate.of(2026, 3, 2), report.dailyStats[1].date)
        assertEquals(110 * 60 * 1000L, report.dailyStats[1].focusMs)

        // Day 3 (2026-03-03): 20 min
        assertEquals(LocalDate.of(2026, 3, 3), report.dailyStats[2].date)
        assertEquals(20 * 60 * 1000L, report.dailyStats[2].focusMs)

        // Day 4 (2026-03-04): 55 min (Cross-midnight session starting 23:30 on Day 4)
        assertEquals(LocalDate.of(2026, 3, 4), report.dailyStats[3].date)
        assertEquals(55 * 60 * 1000L, report.dailyStats[3].focusMs)

        // Early stop reasons: TIRED, INTERRUPTED
        val reasonCodes = report.earlyStopReasons.map { it.reasonCode }.toSet()
        assertTrue(reasonCodes.contains("TIRED"))
        assertTrue(reasonCodes.contains("INTERRUPTED"))
    }

    @Test
    fun testMonthReport_CalculatesCorrectMetricsAnd6CalendarWeeks() {
        // March 2026: 2026-03-01 00:00 to 2026-04-01 00:00 (31 days)
        val monthStart = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, SampleData.TEST_ZONE_ID).toInstant().toEpochMilli()
        val monthEnd = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, SampleData.TEST_ZONE_ID).toInstant().toEpochMilli()

        val report = calculator.calculateReport(
            periodType = PeriodType.MONTH,
            periodStart = monthStart,
            periodEnd = monthEnd,
            sessions = SampleData.SESSIONS,
            phoneEvents = SampleData.PHONE_EVENTS,
            appUsages = SampleData.APP_USAGES
        )

        val summary = report.summary
        assertEquals(7, summary.totalFinishedSessionCount)
        assertEquals(4, summary.completedSessionCount)

        // March 1, 2026 is Sunday -> 6 calendar week bins (Sun March 1, Mon-Sun March 2-8, Mon-Sun March 9-15, Mon-Sun March 16-22, Mon-Sun March 23-29, Mon-Tue March 30-31)
        assertEquals(6, report.weeklyStats.size)
        assertEquals("Week 1", report.weeklyStats[0].weekLabel)
        assertEquals(LocalDate.of(2026, 3, 1), report.weeklyStats[0].startDate)
        assertEquals(LocalDate.of(2026, 3, 1), report.weeklyStats[0].endDate)

        assertEquals("Week 2", report.weeklyStats[1].weekLabel)
        assertEquals(LocalDate.of(2026, 3, 2), report.weeklyStats[1].startDate)
        assertEquals(LocalDate.of(2026, 3, 8), report.weeklyStats[1].endDate)

        assertEquals("Week 6", report.weeklyStats[5].weekLabel)
        assertEquals(LocalDate.of(2026, 3, 30), report.weeklyStats[5].startDate)
        assertEquals(LocalDate.of(2026, 3, 31), report.weeklyStats[5].endDate)

        // Early stop reasons: TIRED (1), INTERRUPTED (1), DISTRACTED (1)
        assertEquals(3, report.earlyStopReasons.size)
        val reasons = report.earlyStopReasons.map { it.reasonCode }.toSet()
        assertTrue(reasons.contains("TIRED"))
        assertTrue(reasons.contains("INTERRUPTED"))
        assertTrue(reasons.contains("DISTRACTED"))
    }

    @Test
    fun testCalendarWeeks_Spans4WeeksForFebruary2021() {
        // Feb 1, 2021 is Monday. Feb 28, 2021 is Sunday.
        val days = (1..28).map { day ->
            DailyFocusStat(date = LocalDate.of(2021, 2, day), focusMs = 60 * 60 * 1000L, checkCount = 1)
        }
        val weeklyStats = calculator.calculateWeeklyStats(days)

        // Must yield exactly 4 weeks
        assertEquals(4, weeklyStats.size)
        assertEquals(LocalDate.of(2021, 2, 1), weeklyStats[0].startDate)
        assertEquals(LocalDate.of(2021, 2, 7), weeklyStats[0].endDate)
        assertEquals(LocalDate.of(2021, 2, 22), weeklyStats[3].startDate)
        assertEquals(LocalDate.of(2021, 2, 28), weeklyStats[3].endDate)
    }

    @Test
    fun testCalendarWeeks_Spans5WeeksForMay2026() {
        // May 1, 2026 is Friday. May 31, 2026 is Sunday.
        val days = (1..31).map { day ->
            DailyFocusStat(date = LocalDate.of(2026, 5, day), focusMs = 60 * 60 * 1000L, checkCount = 1)
        }
        val weeklyStats = calculator.calculateWeeklyStats(days)

        // Must yield exactly 5 weeks
        assertEquals(5, weeklyStats.size)
        assertEquals(LocalDate.of(2026, 5, 1), weeklyStats[0].startDate)
        assertEquals(LocalDate.of(2026, 5, 3), weeklyStats[0].endDate)
        assertEquals(LocalDate.of(2026, 5, 25), weeklyStats[4].startDate)
        assertEquals(LocalDate.of(2026, 5, 31), weeklyStats[4].endDate)
    }

    @Test
    fun testEmptyDataset_HandlesGracefullyWithoutExceptions() {
        val dayStart = SampleData.getEpochMs(0, 0, 0)
        val dayEnd = SampleData.getEpochMs(1, 0, 0)

        val report = calculator.calculateReport(
            periodType = PeriodType.DAY,
            periodStart = dayStart,
            periodEnd = dayEnd,
            sessions = emptyList(),
            phoneEvents = emptyList(),
            appUsages = emptyList()
        )

        val summary = report.summary

        assertEquals(0L, summary.totalFocusMs)
        assertEquals(0, summary.totalCheckCount)
        assertEquals(0.0, summary.checksPerFocusHour, 0.001)
        assertEquals(0.0, summary.goalCompletionRate, 0.001)
        assertEquals(0L, summary.longestFocusMs)
        assertTrue(report.earlyStopReasons.isEmpty())
        assertTrue(report.topAppUsages.isEmpty())
    }

    @Test
    fun testZeroFocusDuration_HandlesDivisionByZeroSafely() {
        val zeroFocusSession = ReportSession(
            id = 100L,
            subject = "Zero Focus Session",
            plan = null,
            goalMinutes = 30,
            startAt = SampleData.getEpochMs(0, 10, 0),
            endAt = SampleData.getEpochMs(0, 10, 5),
            status = "EARLY_STOP",
            stopReason = "OTHER",
            focusMs = 0L,
            phoneUseMs = 5000L,
            checkCount = 3,
            longestFocusMs = 0L
        )

        val summary = calculator.calculateSummary(listOf(zeroFocusSession))

        assertEquals(0L, summary.totalFocusMs)
        assertEquals(3, summary.totalCheckCount)
        assertEquals(0.0, summary.checksPerFocusHour, 0.001)
    }
}
