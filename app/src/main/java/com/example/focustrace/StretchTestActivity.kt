package com.example.focustrace

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.focustrace.mission.StretchMotionDetector
import java.util.Locale

/**
 * T7 prototype for stretch detection using StretchMotionDetector.
 */
class StretchTestActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var resetButton: Button

    private val detector = StretchMotionDetector(
        targetCount = 10,
        totalTimeMs = 60000L
    ) { state, count, remainingMs, magnitude ->
        updateUi(state, count, remainingMs, magnitude)
    }

    // Main-thread timer handler
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimerRunnable = object : Runnable {
        override fun run() {
            detector.checkTimer()
            if (detector.missionState == StretchMotionDetector.MissionState.IN_PROGRESS) {
                handler.postDelayed(this, 100L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLayout()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun setupLayout() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        timerText = TextView(this).apply {
            textSize = 36f
            gravity = Gravity.CENTER
            text = "01:00"
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
            setOnClickListener {
                detector.startMission()
                handler.removeCallbacks(updateTimerRunnable)
                handler.post(updateTimerRunnable)
            }
        }

        resetButton = Button(this).apply {
            text = "RESET"
            textSize = 18f
            setOnClickListener {
                detector.resetMission()
                handler.removeCallbacks(updateTimerRunnable)
                updateUi(detector.missionState, detector.count, detector.remainingTimeMs, 0f)
            }
        }

        buttonLayout.addView(startButton)
        buttonLayout.addView(resetButton)

        rootLayout.addView(timerText)
        rootLayout.addView(statusText)
        rootLayout.addView(buttonLayout)

        setContentView(rootLayout)
        updateUi(detector.missionState, detector.count, detector.remainingTimeMs, 0f)
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            statusText.text = "Linear acceleration sensor unavailable"
        }

        detector.resume()
        if (detector.missionState == StretchMotionDetector.MissionState.IN_PROGRESS) {
            handler.removeCallbacks(updateTimerRunnable)
            handler.post(updateTimerRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(updateTimerRunnable)
        detector.pause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        detector.processSensorEvent(event.values[0], event.values[1], event.values[2])
    }

    private fun updateUi(
        state: StretchMotionDetector.MissionState,
        count: Int,
        remainingMs: Long,
        magnitude: Float
    ) {
        timerText.text = formatTime(remainingMs)

        val stateMsg = when (state) {
            StretchMotionDetector.MissionState.NOT_STARTED -> "Press START to begin mission"
            StretchMotionDetector.MissionState.IN_PROGRESS -> "Mission in Progress..."
            StretchMotionDetector.MissionState.SUCCESS -> "MISSION SUCCESS! 🎉"
            StretchMotionDetector.MissionState.FAILED -> "MISSION FAILED ❌ (Time Limit Exceeded)"
            StretchMotionDetector.MissionState.SKIPPED -> "MISSION SKIPPED"
        }

        startButton.isEnabled = (state != StretchMotionDetector.MissionState.IN_PROGRESS)

        statusText.text = """
            Result: $stateMsg

            Count: $count / 10
            Acceleration: %.2f m/s²
        """.trimIndent().format(magnitude)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
