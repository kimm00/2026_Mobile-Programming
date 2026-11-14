package com.example.focustrace.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RefocusTrackerTest {

    private val limitMs = 3 * 60 * 1000L // 3 minutes = 180,000 ms
    private val allowedApps = setOf("com.google.android.calculator", "com.sec.android.app.popupcalculator")
    private val excludedApps = setOf("com.example.focustrace", "com.android.launcher", "com.android.systemui")

    @Test
    fun `1 YouTube for 3 min 10 s - one alert`() {
        val tracker = RefocusTracker(refocusLimitMs = limitMs, allowedApps = allowedApps, excludedApps = excludedApps)
        tracker.onUnlock(0L)
        val events = listOf(RawUsageEvent("com.google.android.youtube", 0L))
        val alert = tracker.processEvents(events, 190_000L) // 3 min 10 s

        assertNotNull(alert)
        assertEquals("com.google.android.youtube", alert?.packageName)
        assertEquals(190_000L, alert?.durationMs)
    }

    @Test
    fun `2 YouTube for 2 min 50 s - no alert`() {
        val tracker = RefocusTracker(refocusLimitMs = limitMs, allowedApps = allowedApps, excludedApps = excludedApps)
        tracker.onUnlock(0L)
        val events = listOf(RawUsageEvent("com.google.android.youtube", 0L))
        val alert = tracker.processEvents(events, 170_000L) // 2 min 50 s

        assertNull(alert)
    }

    @Test
    fun `3 YouTube 2 min, home 5 s, YouTube 2 min - no alert (reset)`() {
        val tracker = RefocusTracker(refocusLimitMs = limitMs, allowedApps = allowedApps, excludedApps = excludedApps)
        tracker.onUnlock(0L)

        // YouTube 0s to 2 min
        var alert = tracker.processEvents(listOf(RawUsageEvent("com.google.android.youtube", 0L)), 120_000L)
        assertNull(alert)

        // Home resumed at 2 min (120,000ms), checked at 2 min 5 s (125,000ms)
        alert = tracker.processEvents(listOf(RawUsageEvent("com.android.launcher", 120_000L)), 125_000L)
        assertNull(alert)

        // YouTube resumed again at 2 min 5 s (125,000ms), checked 2 min later at 4 min 5 s (245,000ms)
        alert = tracker.processEvents(listOf(RawUsageEvent("com.google.android.youtube", 125_000L)), 245_000L)
        assertNull(alert)
    }

    @Test
    fun `4 Calculator (allowed) for 5 min - no alert`() {
        val tracker = RefocusTracker(refocusLimitMs = limitMs, allowedApps = allowedApps, excludedApps = excludedApps)
        tracker.onUnlock(0L)
        val events = listOf(RawUsageEvent("com.google.android.calculator", 0L))
        val alert = tracker.processEvents(events, 300_000L) // 5 min

        assertNull(alert)
    }

    @Test
    fun `5 YouTube for 7 min - only one alert`() {
        val tracker = RefocusTracker(refocusLimitMs = limitMs, allowedApps = allowedApps, excludedApps = excludedApps)
        tracker.onUnlock(0L)
        val events = listOf(RawUsageEvent("com.google.android.youtube", 0L))

        // First check at 3 min 10 s -> Alert!
        val alert1 = tracker.processEvents(events, 190_000L)
        assertNotNull(alert1)

        // Second check at 4 min -> No alert
        val alert2 = tracker.processEvents(events, 240_000L)
        assertNull(alert2)

        // Third check at 7 min -> No alert (only one alert per continuous use)
        val alert3 = tracker.processEvents(events, 420_000L)
        assertNull(alert3)
    }
}
