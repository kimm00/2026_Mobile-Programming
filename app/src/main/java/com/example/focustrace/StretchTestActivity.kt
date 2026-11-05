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
 * T7 스트레칭 미션 사전 검증 Activity
 *
 * 60초 제한시간 내 팔 뻗기(가속도 -> 0.5초 홀드 -> 접기) 10회 감지 테스트.
 * 후보 B (FSM 기반 알고리즘) 적용.
 */
class StretchTestActivity : Activity(), SensorEventListener {

    // 미션 진행 상태
    enum class MissionState {
        NOT_STARTED, // 시작 전
        IN_PROGRESS, // 진행 중
        SUCCESS,     // 성공 (60초 내 10회 달성)
        FAILED       // 실패 (60초 초과)
    }

    // FSM 상태 (후보 B 알고리즘)
    enum class FsmState {
        IDLE,          // 동작 대기
        EXTENDING,     // 팔 뻗는 중 (가속도 >= 3.0 m/s²)
        EXTENDED_HOLD, // 팔 뻗은 상태 유지 (가속도 < 1.5 m/s² 500ms 이상)
        RETRACTING,    // 팔 접는 중 (홀드 후 새로운 움직임 감지)
        COOLDOWN       // 중복 카운트 방지 불응기
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var resetButton: Button

    // 미션 및 센서 제어 변수
    private var missionState = MissionState.NOT_STARTED
    private var fsmState = FsmState.IDLE
    private var count = 0
    private val targetCount = 10

    // 시간 관련 변수 (SystemClock.elapsedRealtime 기준)
    private val totalTimeMs = 60000L // 60초
    private var endTime = 0L
    private var savedRemainingMs = totalTimeMs

    // FSM 타임스탬프 및 조건 변수
    private var extendingStartTime = 0L
    private var holdStartTime = 0L
    private var holdConditionMet = false
    private var cooldownStartTime = 0L

    // 센서 임계값 정의
    private val highThreshold = 3.0f   // 뻗기 감지 임계값 (m/s²)
    private val lowThreshold = 1.5f    // 홀드 안정 상태 임계값 (m/s²)
    private val retractThreshold = 2.0f// 접기 감지 임계값 (m/s²)
    private val holdDurationMs = 500L  // 홀드 유지 필요 시간 (ms)
    private val cooldownMs = 600L      // 중복 방지 쿨다운 (ms)
    private val stateTimeoutMs = 2500L // FSM 상태 타임아웃 (ms)

    // 메인 스레드 타이머 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimerRunnable = object : Runnable {
        override fun run() {
            checkTimerAndUpdateUi()
            if (missionState == MissionState.IN_PROGRESS) {
                handler.postDelayed(this, 100L) // 100ms 마다 UI 및 시간 체킹
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Activity 재생성 시 상태 복원
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
                // 남은 시간을 기준으로 종료 시점 재계산
                endTime = SystemClock.elapsedRealtime() + savedRemainingMs
            }
        }

        setupLayout()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    /**
     * UI 레이아웃 동적 생성
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
     * 미션 시작 처리
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
     * 미션 리셋 처리
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
     * 타이머 만료 여부 확인 및 UI 갱신
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
     * 미션 성공 처리
     */
    private fun triggerSuccess() {
        missionState = MissionState.SUCCESS
        handler.removeCallbacks(updateTimerRunnable)
        val remainingMs = (endTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        updateUi(remainingMs)
    }

    /**
     * 미션 실패 처리
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

        // 미션 진행 중일 때만 센서 FSM 및 카운트 동작 수행
        if (missionState == MissionState.IN_PROGRESS) {
            // 제한 시간 만료 체크
            if (now >= endTime) {
                if (count >= targetCount) triggerSuccess() else triggerFail()
                return
            }

            processFsm(magnitude, now)
        }

        // 실시간 가속도 및 상태 표시 업데이트
        val remainingMs = if (missionState == MissionState.IN_PROGRESS) {
            (endTime - now).coerceAtLeast(0L)
        } else {
            savedRemainingMs
        }
        updateUi(remainingMs, magnitude)
    }

    /**
     * FSM (후보 B) 스트레칭 동작 감지 로직
     */
    private fun processFsm(magnitude: Float, now: Long) {
        when (fsmState) {
            FsmState.IDLE -> {
                // 1. 뻗기 시작: 가속도가 3.0 m/s² 이상일 때 EXTENDING 전환
                if (magnitude >= highThreshold) {
                    fsmState = FsmState.EXTENDING
                    extendingStartTime = now
                }
            }

            FsmState.EXTENDING -> {
                // 2. 뻗은 후 정지: 가속도가 1.5 m/s² 미만으로 감소 시 EXTENDED_HOLD 전환
                if (magnitude < lowThreshold) {
                    fsmState = FsmState.EXTENDED_HOLD
                    holdStartTime = now
                    holdConditionMet = false
                } else if (now - extendingStartTime > stateTimeoutMs) {
                    // 타임아웃 리셋
                    fsmState = FsmState.IDLE
                }
            }

            FsmState.EXTENDED_HOLD -> {
                // 3. 0.5초 홀드 감지: 가속도가 계속 1.5 m/s² 미만으로 유지되는지 검사
                if (magnitude < lowThreshold) {
                    if (now - holdStartTime >= holdDurationMs) {
                        holdConditionMet = true
                    }
                }

                // 0.5초 홀드 충족 후 팔을 접는 움직임 (가속도 >= 2.0 m/s²) 감지 시 RETRACTING 전환
                if (holdConditionMet && magnitude >= retractThreshold) {
                    fsmState = FsmState.RETRACTING
                    onArmStretchCompleted()
                } else if (now - holdStartTime > stateTimeoutMs) {
                    // 타임아웃 리셋
                    fsmState = FsmState.IDLE
                }
            }

            FsmState.RETRACTING -> {
                // 팔 접는 동작 완료 후 쿨다운으로 전환
                fsmState = FsmState.COOLDOWN
                cooldownStartTime = now
            }

            FsmState.COOLDOWN -> {
                // 4. 중복 카운트 방지: 쿨다운 시간(600ms) 경과 후 가속도가 안정화되면 IDLE 복귀
                if ((now - cooldownStartTime >= cooldownMs) && magnitude < lowThreshold) {
                    fsmState = FsmState.IDLE
                }
            }
        }
    }

    /**
     * 팔 뻗기 1회 완료 시 호출
     */
    private fun onArmStretchCompleted() {
        count++
        if (count >= targetCount) {
            triggerSuccess()
        }
    }

    /**
     * UI 업데이트
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
     * milliseconds -> mm:ss 포맷 변환
     */
    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
