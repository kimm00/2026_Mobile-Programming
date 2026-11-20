# AI Agent Work Summary — FocusTrace (Exact Verbatim Questions)

This document contains the exact copy-pasted user questions and prompts along with the corresponding code changes made by the AI agent.

---

### 1.
- **Original User Question**:
  ```text
  refer to the Agents.md and below is the test cases, create a app that satisfy the flow of detection in agents.md and 6test cases -- 통과 기준: 다른 앱을 열면 패키지명이 정확히 나옴
  통과 기준: 화면을 꺼둔 채 1시간 이상 지나도 서비스가 살아 있음. 서비스 타입은 specialUse (dataSync는 Android 15에서 하루 6시간 제한). 실패 시 세션 종료 후 UsageEvents 일괄 조회로 대체
  통과 기준: 설정 화면 이동 → 허용 후 queryEvents로 이벤트가 읽힘
  통과 기준: 현재 앱을 주기적으로(예: 15초) 확인, 같은 비허용 앱 3분 넘게 쓰면 알림 / 허용 앱은 안 옴 / 1시간 세션 동안 배터리 소모가 크게 늘지 않음. T2·T3 통과 후 진행. 실패 시 F-13 제외
  통과 기준: 앱을 안 열고 잠금만 풀면 UNLOCK_ONLY로 분류됨
  통과 기준: 실행 중인 Service 안에서 registerReceiver로 등록, 잠금 해제할 때마다 로그가 찍힘 (Android 8부터 Manifest 등록은 안 옴)--
  ```
- **Fix**: Created `EpisodeAnalyzer.kt`, `FocusService.kt`, and test UI.

---

### 2.
- **Original User Question**:
  ```text
  This is a test branch (test/t1-t6-detection). Build a test-only app that follows the detection rules in AGENTS.md. First, show me your plan: which files you will create and where. Do not write code until I approve.
Requirements:
•
FocusService in detection/ (Foreground Service, specialUse, ongoing notification)
•
EpisodeAnalyzer in detection/ (pure Kotlin, AGENTS.md rules 3–7)
•
A test screen in ui/ with buttons: notification permission, usage access settings, battery optimization exemption, start/stop service, show/clear/share log
•
Write every event and episode result to a log file shown on the test screen
•
Log battery level every minute
•
Allowed apps for testing: the calculator apps (com.google.android.calculator, com.sec.android.app.popupcalculator)
•
Do not add new dependencies or change Gradle versions.
Pass criteria:
•
T1: Receiver is registered inside the running service with registerReceiver, and a log line appears on every unlock.
•
T2: After opening the usage access settings and allowing it, queryEvents returns events.
•
T3: When another app is opened, its exact package name appears in the log.
•
T4: Unlocking without opening an app is classified as UNLOCK_ONLY.
•
T5: With the screen off for over 1 hour, the service is still alive.
  ```
- **Fix**: Created implementation plan artifact and task list.

---

### 3.
- **Original User Question**:
  ```text
  Proceed with the approved implementation plan.
  ```
- **Fix**: Executed implementation plan (Manifest, EpisodeAnalyzer, FocusService, MainActivity).

---

### 4.
- **Original User Question**:
  ```text
  The app name is focustrace what is the wrong?
  ```
- **Fix**: Fixed `strings.xml` app name to `"FocusTrace"`, migrated test packages to `com.example.focustrace`.

---

### 5.
- **Original User Question**:
  ```text
  Now the app's logs are very hard to read and understand.., so make it clearly
  ```
- **Fix**: Revamped logging format with visual banners and emojis.

---

### 6.
- **Original User Question**:
  ```text
  Fix these issues in the detection test code:
  1. In EpisodeAnalyzer, use only ACTIVITY_RESUMED (1) and ACTIVITY_PAUSED (2).
     23 is ACTIVITY_STOPPED and 24 is ACTIVITY_DESTROYED, not foreground/background events.
  2. In FocusService, reset the unlock time after SCREEN_OFF, and ignore SCREEN_OFF when there was no unlock.
  3. Do not hard-code launcher packages. Resolve the default home app in FocusService
     and pass it, with this app's packageName, into EpisodeAnalyzer. Remove "com.example.tracefocus".
  4. Apply AGENTS.md rule 7: starting the service counts as an unlock, and episodes at
     service start/stop that contain only this app are not counted.
  5. Remove leftover reasoning comments and the duplicated isAuto/isCounted calculation.
  ```
- **Fix**: Refined event matching, unlock reset, dynamic home app resolution, rule 7 start unlock, and code cleanup.

---

### 7.
- **Original User Question**:
  ```text
  Under each episode result in the log, also print every raw ACTIVITY_RESUMED and
  ACTIVITY_PAUSED event used for the analysis, including excluded apps (this app,
  the home app, systemui). Show seconds from unlock, event type, and package name,
  and mark excluded packages with "(excluded)".
  ```
- **Fix**: Added raw events section under each episode result.

---

### 8.
- **Original User Question**:
  ```text
  T2 and T3 passed. Add refocus detection for T6 without changing EpisodeAnalyzer.
  First show your plan (files and classes). Do not write code until I approve.

  Behavior:
  - From unlock (or service start) until SCREEN_OFF, check the current foreground app every 15 s
    with queryEvents. Read only new events since the last check, with a few seconds of overlap
    because events can be written late. Stop checking on SCREEN_OFF.
  - Track how long the same app has been in the foreground continuously.
    Reset when another app comes to the foreground or the screen turns off.
  - If the same app is used for 3 minutes or more, and it is not allowed and not excluded
    (this app, home app, systemui), show one high-priority notification:
    "Back to focus?" / "You've been on <app name> for 3 min." Only once per continuous use.
  - Put the decision logic in a pure Kotlin class (e.g. RefocusTracker in detection/)
    so it can be unit tested. FocusService only feeds events and shows the notification.
  - Make the 3-minute limit a constant so I can set it to 30 s for a quick test.

  Logging (for the battery criterion):
  - Log every alert: package name and how long it was used.
  - Log battery level and the number of checks at service start, every minute, and at service stop.
  - Add a constant to turn the 15 s checking on/off, so I can compare two 1-hour sessions.
  ```
- **Fix**: Created implementation plan for `RefocusTracker`.

---

### 9.
- **Original User Question**:
  ```text
  make plan in korena
  ```
- **Fix**: Translated implementation plan into Korean.

---

### 10.
- **Original User Question**:
  ```text
  go
  ```
- **Fix**: Implemented `RefocusTracker.kt` and integrated periodic checks and notifications into `FocusService`.

---

### 11.
- **Original User Question**:
  ```text
  for the battery test between 15 second pooling checking and not, so make a buttom that turn on and off the 15 sec pooling
  ```
- **Fix**: Added toggle button (`Periodic: ON` / `Periodic: OFF`) in UI and service.

---

