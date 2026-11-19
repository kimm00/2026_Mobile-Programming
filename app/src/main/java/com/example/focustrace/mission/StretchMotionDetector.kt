package com.example.focustrace.mission

import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Encapsulates FSM (Finite State Machine) logic and timing for arm stretch detection (F-12 / T7).
 */
class StretchMotionDetector(
    private val targetCount: Int = 10,
    private val totalTimeMs: Long = 60000L,
    private val onStateChanged: (MissionState, Int, Long, Float) -> Unit
) {

    enum class MissionState {
        NOT_STARTED,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        SKIPPED
    }

    enum class FsmState {
        IDLE,
        EXTENDING,
        EXTENDED_HOLD,
        RETRACTING,
        COOLDOWN
    }

    var missionState = MissionState.NOT_STARTED
        private set
    var fsmState = FsmState.IDLE
        private set
    var count = 0
        private set

    private var endTime = 0L
    private var savedRemainingMs = totalTimeMs
    private var extendingStartTime = 0L
    private var holdStartTime = 0L
    private var holdConditionMet = false
    private var cooldownStartTime = 0L

    // Sensor thresholds
    private val highThreshold = 3.0f   // Extension detection threshold (m/s²)
    private val lowThreshold = 1.5f    // Stable hold threshold (m/s²)
    private val retractThreshold = 2.0f // Retraction detection threshold (m/s²)
    private val holdDurationMs = 500L  // Required hold duration (ms)
    private val cooldownMs = 600L      // Cooldown to prevent duplicate counts (ms)
    private val stateTimeoutMs = 2500L // FSM state timeout (ms)

    val remainingTimeMs: Long
        get() {
            return if (missionState == MissionState.IN_PROGRESS) {
                (endTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            } else {
                savedRemainingMs
            }
        }

    fun startMission() {
        if (missionState == MissionState.IN_PROGRESS) return

        count = 0
        missionState = MissionState.IN_PROGRESS
        fsmState = FsmState.IDLE
        holdConditionMet = false

        endTime = SystemClock.elapsedRealtime() + totalTimeMs
        savedRemainingMs = totalTimeMs

        onStateChanged(missionState, count, remainingTimeMs, 0f)
    }

    fun resetMission() {
        count = 0
        missionState = MissionState.NOT_STARTED
        fsmState = FsmState.IDLE
        savedRemainingMs = totalTimeMs
        holdConditionMet = false

        onStateChanged(missionState, count, remainingTimeMs, 0f)
    }

    fun skipMission() {
        missionState = MissionState.SKIPPED
        onStateChanged(missionState, count, remainingTimeMs, 0f)
    }

    fun checkTimer() {
        if (missionState != MissionState.IN_PROGRESS) return

        val now = SystemClock.elapsedRealtime()
        val remainingMs = (endTime - now).coerceAtLeast(0L)

        if (remainingMs <= 0L) {
            if (count >= targetCount) {
                triggerSuccess()
            } else {
                triggerFail()
            }
            return
        }

        onStateChanged(missionState, count, remainingMs, 0f)
    }

    private fun triggerSuccess() {
        missionState = MissionState.SUCCESS
        onStateChanged(missionState, count, 0L, 0f)
    }

    private fun triggerFail() {
        missionState = MissionState.FAILED
        onStateChanged(missionState, count, 0L, 0f)
    }

    fun processSensorEvent(x: Float, y: Float, z: Float) {
        if (missionState != MissionState.IN_PROGRESS) return

        val magnitude = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()

        if (now >= endTime) {
            if (count >= targetCount) triggerSuccess() else triggerFail()
            return
        }

        processFsm(magnitude, now)

        val remainingMs = (endTime - now).coerceAtLeast(0L)
        onStateChanged(missionState, count, remainingMs, magnitude)
    }

    private fun processFsm(magnitude: Float, now: Long) {
        when (fsmState) {
            FsmState.IDLE -> {
                if (magnitude >= highThreshold) {
                    fsmState = FsmState.EXTENDING
                    extendingStartTime = now
                }
            }
            FsmState.EXTENDING -> {
                if (magnitude < lowThreshold) {
                    fsmState = FsmState.EXTENDED_HOLD
                    holdStartTime = now
                    holdConditionMet = false
                } else if (now - extendingStartTime > stateTimeoutMs) {
                    fsmState = FsmState.IDLE
                }
            }
            FsmState.EXTENDED_HOLD -> {
                if (magnitude < lowThreshold) {
                    if (now - holdStartTime >= holdDurationMs) {
                        holdConditionMet = true
                    }
                }
                if (holdConditionMet && magnitude >= retractThreshold) {
                    fsmState = FsmState.RETRACTING
                    onArmStretchCompleted()
                } else if (now - holdStartTime > stateTimeoutMs) {
                    fsmState = FsmState.IDLE
                }
            }
            FsmState.RETRACTING -> {
                fsmState = FsmState.COOLDOWN
                cooldownStartTime = now
            }
            FsmState.COOLDOWN -> {
                if ((now - cooldownStartTime >= cooldownMs) && magnitude < lowThreshold) {
                    fsmState = FsmState.IDLE
                }
            }
        }
    }

    private fun onArmStretchCompleted() {
        count++
        if (count >= targetCount) {
            triggerSuccess()
        }
    }

    fun pause() {
        if (missionState == MissionState.IN_PROGRESS) {
            savedRemainingMs = (endTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }
    }

    fun resume() {
        if (missionState == MissionState.IN_PROGRESS) {
            endTime = SystemClock.elapsedRealtime() + savedRemainingMs
        }
    }
}
