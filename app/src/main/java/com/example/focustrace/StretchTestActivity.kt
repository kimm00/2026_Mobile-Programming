package com.example.focustrace

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.sqrt

/**
 * T7 prototype for stretch detection using an FSM.
 * Detects 10 repetitions within a 60-second mission.
 */
class StretchTestActivity : Activity(), SensorEventListener {

    // Mission progress states
    enum class MissionState {
        NOT_STARTED, // Before mission starts
        IN_PROGRESS, // Mission in progress
        SUCCESS,     // Completed 10 repetitions within 60 seconds
        FAILED       // Time limit exceeded
    }

    // FSM states (Candidate B algorithm)
    enum class FsmState {
        IDLE,          // Waiting for movement
        EXTENDING,     // Extending arm (acceleration >= 3.0 m/s²)
        EXTENDED_HOLD, // Holding extended position (acceleration < 1.5 m/s² for 500 ms)
        RETRACTING,    // Retracting arm (movement detected after hold)
        COOLDOWN       // Preventing duplicate counts
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var resetButton: Button

    // Mission and sensor control variables
    private var missionState = MissionState.NOT_STARTED
    private var fsmState = FsmState.IDLE
    private var count = 0
    private val targetCount = 10

    // Timing variables (based on SystemClock.elapsedRealtime)
    private val totalTimeMs = 60000L // 60 seconds
    private var endTime = 0L
    private var savedRemainingMs = totalTimeMs

    // FSM timestamps and condition flags
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

    // Main-thread timer handler
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimerRunnable = object : Runnable {
        override fun run() {
            checkTimerAndUpdateUi()
            if (missionState == MissionState.IN_PROGRESS) {
                handler.postDelayed(this, 100L)  // Check time and update UI every 100 ms
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore mission progress after Activity recreation
        if (savedInstanceState != null) {
            count = savedInstanceState.getInt("count", 0)
            missionState = MissionState.valueOf(
                savedInstanceState.getString("mission_state", MissionState.NOT_STARTED.name)
            )
            fsmState = FsmState.valueOf(
                savedInstanceState.getString("fsm_state", FsmState.IDLE.name)
            )
            savedRemainingMs = savedInstanceState.getLong("remaining_ms", totalTimeMs)

            if (missionState == MissionState.IN_PROGRESS) {
                // Recalculate the end time based on the remaining time
                endTime = SystemClock.elapsedRealtime() + savedRemainingMs
            }
        }

        setupLayout()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    /**
     * Create the UI layout programmatically.
     */
    private fun setupLayout() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        timerText = TextView(this).apply {
            textSize = 36f
            gravity = Gravity.CENTER
            text = formatTime(savedRemainingMs)
        }

        statusText = TextView(this).apply {
            textSize = 18f
            setPadding(0, 30, 0, 30)
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        startButton = Button(this).apply {
            text = "START"
            textSize = 18f
            setOnClickListener { startMission() }
        }

        resetButton = Button(this).apply {
            text = "RESET"
            textSize = 18f
            setOnClickListener { resetMission() }
        }

        buttonLayout.addView(startButton)
        buttonLayout.addView(resetButton)

        rootLayout.addView(timerText)
        rootLayout.addView(statusText)
        rootLayout.addView(buttonLayout)

        setContentView(rootLayout)

        updateUi(if (missionState == MissionState.IN_PROGRESS) (endTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L) else savedRemainingMs)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("count", count)
        outState.putString("mission_state", missionState.name)
        outState.putString("fsm_state", fsmState.name)

        val remainingMs = if (missionState == MissionState.IN_PROGRESS) {
            (endTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            savedRemainingMs
        }
        outState.putLong("remaining_ms", remainingMs)

        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            statusText.text = "Linear acceleration sensor unavailable"
        }

        if (missionState == MissionState.IN_PROGRESS) {
            handler.post(updateTimerRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(updateTimerRunnable)

        if (missionState == MissionState.IN_PROGRESS) {
            savedRemainingMs = (endTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }
    }

    /**
     * Start the mission.
     */
    private fun startMission() {
        if (missionState == MissionState.IN_PROGRESS) return

        count = 0
        missionState = MissionState.IN_PROGRESS
        fsmState = FsmState.IDLE
        holdConditionMet = false

        endTime = SystemClock.elapsedRealtime() + totalTimeMs
        savedRemainingMs = totalTimeMs

        handler.removeCallbacks(updateTimerRunnable)
        handler.post(updateTimerRunnable)

        updateUi(totalTimeMs)
    }

    /**
     * Reset the mission.
     */
    private fun resetMission() {
        handler.removeCallbacks(updateTimerRunnable)

        count = 0
        missionState = MissionState.NOT_STARTED
        fsmState = FsmState.IDLE
        savedRemainingMs = totalTimeMs
        holdConditionMet = false

        updateUi(totalTimeMs)
    }

    /**
     * Check timer expiration and update the UI.
     */
    private fun checkTimerAndUpdateUi() {
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

        updateUi(remainingMs)
    }

    /**
     * Handle mission success.
     */
    private fun triggerSuccess() {
        missionState = MissionState.SUCCESS
        handler.removeCallbacks(updateTimerRunnable)
        val remainingMs = (endTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        updateUi(remainingMs)
    }

    /**
     * Handle mission failure.
     */
    private fun triggerFail() {
        missionState = MissionState.FAILED
        handler.removeCallbacks(updateTimerRunnable)
        updateUi(0L)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()

        // Process sensor events and count repetitions only during an active mission
        if (missionState == MissionState.IN_PROGRESS) {
            // Check whether the time limit has expired
            if (now >= endTime) {
                if (count >= targetCount) triggerSuccess() else triggerFail()
                return
            }

            processFsm(magnitude, now)
        }

        // Update real-time acceleration and mission status
        val remainingMs = if (missionState == MissionState.IN_PROGRESS) {
            (endTime - now).coerceAtLeast(0L)
        } else {
            savedRemainingMs
        }
        updateUi(remainingMs, magnitude)
    }

    // Detect a stretch through movement, hold, and return phases
    private fun processFsm(magnitude: Float, now: Long) {
        when (fsmState) {
            FsmState.IDLE -> {
                // 1. Start extension: switch to EXTENDING when acceleration reaches 3.0 m/s²
                if (magnitude >= highThreshold) {
                    fsmState = FsmState.EXTENDING
                    extendingStartTime = now
                }
            }

            FsmState.EXTENDING -> {
                // 2. After extension: switch to EXTENDED_HOLD when acceleration drops below 1.5 m/s²
                if (magnitude < lowThreshold) {
                    fsmState = FsmState.EXTENDED_HOLD
                    holdStartTime = now
                    holdConditionMet = false
                } else if (now - extendingStartTime > stateTimeoutMs) {
                    // Reset on timeout
                    fsmState = FsmState.IDLE
                }
            }

            FsmState.EXTENDED_HOLD -> {

                // 3. Detect a 0.5-second hold: check whether acceleration remains below 1.5 m/s²
                if (magnitude < lowThreshold) {
                    if (now - holdStartTime >= holdDurationMs) {
                        holdConditionMet = true
                    }
                }

                // After the 0.5-second hold, switch to RETRACTING when acceleration reaches 2.0 m/s²
                if (holdConditionMet && magnitude >= retractThreshold) {
                    fsmState = FsmState.RETRACTING
                    onArmStretchCompleted()
                } else if (now - holdStartTime > stateTimeoutMs) {
                    // Reset on timeout
                    fsmState = FsmState.IDLE
                }
            }

            FsmState.RETRACTING -> {
                // Switch to COOLDOWN after detecting the retraction movement
                fsmState = FsmState.COOLDOWN
                cooldownStartTime = now
            }

            FsmState.COOLDOWN -> {
                // 4. Prevent duplicate counts: return to IDLE after 600 ms when acceleration stabilizes
                if ((now - cooldownStartTime >= cooldownMs) && magnitude < lowThreshold) {
                    fsmState = FsmState.IDLE
                }
            }
        }
    }

    /**
     * Called when one arm stretch repetition is completed.
     */
    private fun onArmStretchCompleted() {
        count++
        if (count >= targetCount) {
            triggerSuccess()
        }
    }

    /**
     * Update the UI.
     */
    private fun updateUi(remainingMs: Long, currentMagnitude: Float = 0.0f) {
        timerText.text = formatTime(remainingMs)

        val stateMsg = when (missionState) {
            MissionState.NOT_STARTED -> "Press START to begin mission"
            MissionState.IN_PROGRESS -> "Mission in Progress..."
            MissionState.SUCCESS -> "MISSION SUCCESS! 🎉"
            MissionState.FAILED -> "MISSION FAILED ❌ (Time Limit Exceeded)"
        }

        startButton.isEnabled = (missionState != MissionState.IN_PROGRESS)

        statusText.text = """
            Result: $stateMsg

            Count: $count / $targetCount
            FSM State: $fsmState

            Acceleration: %.2f m/s²
            (High Thresh: %.1f, Low Thresh: %.1f)
        """.trimIndent().format(
            currentMagnitude,
            highThreshold,
            lowThreshold
        )
    }

    /**
     * Convert milliseconds to mm:ss format.
     */
    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
