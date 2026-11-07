package com.example.focustrace.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeAnalyzerTest {

    private val defaultExcluded = setOf("com.example.focustrace", "com.android.launcher", "com.android.systemui")
    private val analyzer = EpisodeAnalyzer(
        thisPackageName = "com.example.focustrace",
        homePackageName = "com.android.launcher"
    )

    @Test
    fun `browser RESUMED 0_0, home RESUMED 5_0, browser PAUSED 5_6 - duration is 5_0s not 5_6s`() {
        val unlockTime = 0L
        val screenOffTime = 10000L

        val events = listOf(
            RawUsageEvent("com.sec.android.app.sbrowser", 0L, EpisodeAnalyzer.EVENT_RESUMED),
            RawUsageEvent("com.android.launcher", 5000L, EpisodeAnalyzer.EVENT_RESUMED),
            RawUsageEvent("com.sec.android.app.sbrowser", 5600L, EpisodeAnalyzer.EVENT_PAUSED)
        )

        val input = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = events,
            allowedApps = emptySet(),
            excludedApps = defaultExcluded
        )

        val result = analyzer.analyze(input)

        assertEquals(1, result.appUsages.size)
        val usage = result.appUsages[0]
        assertEquals("com.sec.android.app.sbrowser", usage.packageName)
        assertEquals(0L, usage.startAt)
        assertEquals(5000L, usage.durationMs) // 5.0 s (not 5.6 s)
    }

    @Test
    fun `browser 0-5, home 5-7, browser 7-12 - one browser entry of 10s`() {
        val unlockTime = 0L
        val screenOffTime = 12000L

        val events = listOf(
            RawUsageEvent("com.sec.android.app.sbrowser", 0L, EpisodeAnalyzer.EVENT_RESUMED),
            RawUsageEvent("com.android.launcher", 5000L, EpisodeAnalyzer.EVENT_RESUMED),
            RawUsageEvent("com.sec.android.app.sbrowser", 7000L, EpisodeAnalyzer.EVENT_RESUMED)
        )

        val input = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = events,
            allowedApps = emptySet(),
            excludedApps = defaultExcluded
        )

        val result = analyzer.analyze(input)

        assertEquals(1, result.appUsages.size)
        val usage = result.appUsages[0]
        assertEquals("com.sec.android.app.sbrowser", usage.packageName)
        assertEquals(0L, usage.startAt)
        assertEquals(10000L, usage.durationMs) // (5-0) + (12-7) = 10.0 s
    }

    @Test
    fun `rule 3 and 4 - clips to unlock time, merges consecutive same app, excludes system apps`() {
        val unlockTime = 1000L
        val screenOffTime = 10000L

        val events = listOf(
            RawUsageEvent("com.example.focustrace", 800L),
            RawUsageEvent("com.some.otherapp", 900L),
            RawUsageEvent("com.some.otherapp", 3000L),
            RawUsageEvent("com.android.launcher", 6000L)
        )

        val input = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = events,
            allowedApps = emptySet(),
            excludedApps = defaultExcluded
        )

        val result = analyzer.analyze(input)

        assertEquals(1, result.appUsages.size)
        val usage = result.appUsages[0]
        assertEquals("com.some.otherapp", usage.packageName)
        assertEquals(1000L, usage.startAt) // Clipped from 900 to 1000
        assertEquals(5000L, usage.durationMs) // Ends at 6000 (when launcher resumed), 6000 - 1000 = 5000ms
        assertTrue(usage.isAuto) // 900L <= 1000L + 500L
        assertTrue(usage.isCounted) // 5000ms > 2000ms
    }

    @Test
    fun `app usage ends when excluded app resumes`() {
        val unlockTime = 1000L
        val screenOffTime = 10000L

        val events = listOf(
            RawUsageEvent("com.user.app", 2000L),
            RawUsageEvent("com.android.launcher", 6000L) // Excluded app resumed at 6000L
        )

        val input = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = events,
            allowedApps = emptySet(),
            excludedApps = defaultExcluded
        )

        val result = analyzer.analyze(input)

        assertEquals(1, result.appUsages.size)
        val usage = result.appUsages[0]
        assertEquals("com.user.app", usage.packageName)
        assertEquals(2000L, usage.startAt)
        assertEquals(4000L, usage.durationMs) // 6000ms - 2000ms = 4000ms (ends when launcher resumes at 6000L)
    }

    @Test
    fun `same app separated by excluded app is merged into single app usage`() {
        val unlockTime = 1000L
        val screenOffTime = 15000L

        val events = listOf(
            RawUsageEvent("com.sec.android.app.sbrowser", 1000L),
            RawUsageEvent("com.android.launcher", 6056L), // Excluded
            RawUsageEvent("com.sec.android.app.sbrowser", 9000L)
        )

        val input = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = events,
            allowedApps = emptySet(),
            excludedApps = defaultExcluded
        )

        val result = analyzer.analyze(input)

        assertEquals(1, result.appUsages.size)
        val usage = result.appUsages[0]
        assertEquals("com.sec.android.app.sbrowser", usage.packageName)
        assertEquals(1000L, usage.startAt)
        // 5056ms (first run) + 6000ms (second run 9000..15000) = 11056ms
        assertEquals(11056L, usage.durationMs)
    }

    @Test
    fun `rule 5 - auto app left within 2 seconds is not counted`() {
        val unlockTime = 1000L
        val screenOffTime = 5000L

        val events = listOf(
            RawUsageEvent("com.auto.app", 1100L), // <= 1500ms -> isAuto
            RawUsageEvent("com.user.app", 2500L) // > 1500ms -> not auto
        )

        val input = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = events,
            allowedApps = emptySet(),
            excludedApps = defaultExcluded
        )

        val result = analyzer.analyze(input)

        assertEquals(2, result.appUsages.size)

        val autoApp = result.appUsages[0]
        assertEquals("com.auto.app", autoApp.packageName)
        assertTrue(autoApp.isAuto)
        assertEquals(1400L, autoApp.durationMs) // 2500 - 1100 = 1400ms <= 2000ms
        assertFalse(autoApp.isCounted)

        val userApp = result.appUsages[1]
        assertEquals("com.user.app", userApp.packageName)
        assertFalse(userApp.isAuto)
        assertTrue(userApp.isCounted)
    }

    @Test
    fun `rule 6 - kind determination`() {
        val unlockTime = 1000L
        val screenOffTime = 10000L

        // Case 1: UNLOCK_ONLY (auto app left within 2s -> duration = 1400ms <= 2000ms -> not counted)
        val inputUnlockOnly = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = 2400L,
            rawEvents = listOf(RawUsageEvent("com.auto.app", 1100L)),
            allowedApps = emptySet(),
            excludedApps = defaultExcluded
        )
        val result1 = analyzer.analyze(inputUnlockOnly)
        assertEquals(EpisodeAnalyzer.KIND_UNLOCK_ONLY, result1.kind)

        // Case 2: ALLOWED_ONLY (all counted apps are allowed)
        val inputAllowedOnly = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = listOf(RawUsageEvent("com.study.app", 2000L)),
            allowedApps = setOf("com.study.app"),
            excludedApps = defaultExcluded
        )
        val result2 = analyzer.analyze(inputAllowedOnly)
        assertEquals(EpisodeAnalyzer.KIND_ALLOWED_ONLY, result2.kind)

        // Case 3: CHECK (at least one non-allowed app counted)
        val inputCheck = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = listOf(
                RawUsageEvent("com.study.app", 2000L),
                RawUsageEvent("com.game.app", 5000L)
            ),
            allowedApps = setOf("com.study.app"),
            excludedApps = defaultExcluded
        )
        val result3 = analyzer.analyze(inputCheck)
        assertEquals(EpisodeAnalyzer.KIND_CHECK, result3.kind)
    }

    @Test
    fun `rule 7 - session start or end episodes with only FocusTrace are not saved`() {
        val unlockTime = 1000L
        val screenOffTime = 5000L

        // Session start episode with only FocusTrace (excluded)
        val inputStartOnlySelf = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = listOf(RawUsageEvent("com.example.focustrace", 1000L)),
            allowedApps = emptySet(),
            excludedApps = defaultExcluded,
            isSessionStartEpisode = true
        )
        val resultStart = analyzer.analyze(inputStartOnlySelf)
        assertFalse(resultStart.shouldSave)

        // Mid-session episode with only FocusTrace IS saved
        val inputMidOnlySelf = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = listOf(RawUsageEvent("com.example.focustrace", 1000L)),
            allowedApps = emptySet(),
            excludedApps = defaultExcluded,
            isSessionStartEpisode = false,
            isSessionEndEpisode = false
        )
        val resultMid = analyzer.analyze(inputMidOnlySelf)
        assertTrue(resultMid.shouldSave)
    }

    @Test
    fun `rule 7 - end episode with auto app for 0_4s is not saved because no usage is counted`() {
        val unlockTime = 1000L
        val screenOffTime = 5000L

        // sbrowser resumed at 1100L (auto), left at 1500L (duration 400ms <= 2000ms -> isCounted = false)
        // followed by FocusTrace at 1500L (excluded)
        val events = listOf(
            RawUsageEvent("com.sec.android.app.sbrowser", 1100L),
            RawUsageEvent("com.example.focustrace", 1500L)
        )

        val inputEndWithAutoApp = EpisodeInput(
            unlockTimeMs = unlockTime,
            screenOffTimeMs = screenOffTime,
            rawEvents = events,
            allowedApps = emptySet(),
            excludedApps = defaultExcluded,
            isSessionEndEpisode = true
        )

        val result = analyzer.analyze(inputEndWithAutoApp)

        assertEquals(EpisodeAnalyzer.KIND_UNLOCK_ONLY, result.kind)
        assertFalse(result.shouldSave)
    }
}
