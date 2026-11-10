# AGENTS.md — FocusTrace

Android app that records phone use during a focus session. **Kotlin + XML Views + Material 3** (no Compose).
Before coding, read `docs/schema.md` (data) or `DESIGN.md` (UI) when relevant.

## UI
- Build every screen with an XML layout in res/layout. Do not use Jetpack Compose or create views in Kotlin code.
- Access views with View Binding (as taught in class). Do not use findViewById.

## Architecture
- Packages: `ui/`, `data/db/`, `data/repo/`, `detection/`, `ai/`, `widget/`, `tile/`, `mission/`
- Screens use the session API in `docs/schema.md` through Repositories, never DAOs. Coroutines for DB/network, Flow for data.
- `EpisodeAnalyzer` is pure Kotlin (no Android imports).

## Detection rules (tested on device — do not change without a new test)
1. Unlock = `ACTION_USER_PRESENT`, receiver registered with `ContextCompat.RECEIVER_EXPORTED` (NOT_EXPORTED does not receive it).
2. Episode = unlock → `ACTION_SCREEN_OFF`. Analyze 1.5 s after screen off, reading usage events from 1 s before unlock.
3. App time = its `ACTIVITY_RESUMED` → next app's resume, clipped to unlock time. Merge consecutive same app.
4. Exclude FocusTrace, home launcher, `com.android.systemui`.
5. App resumed within 0.5 s of unlock = auto. Auto app left within 2 s = pass (not counted). User-opened apps always count.
6. Kind: none counted → `UNLOCK_ONLY`; only allowed → `ALLOWED_ONLY`; else → `CHECK`.
7. Session start counts as unlock. Start/end episodes with only FocusTrace are not saved.
8. Foreground Service type `specialUse`, started only from the START button.

## Session rules
- Only one session can be RUNNING. startSession closes any leftover RUNNING session as UNFINISHED.
- To end a session: stop FocusService, wait for RESULT_OK (last episode saved), then call endSession.
- The running session is the RUNNING row in the database, not a value held in memory.
- Screens get the repository with context.sessionRepository instead of creating one.

## Dependencies & secrets
- Allowed: AndroidX, Material 3, Room, WorkManager, Firebase Auth, Firebase AI Logic, MPAndroidChart, CameraX. Ask before adding others.
- Never commit or print keys, tokens, or passwords.

## Features
Prompts refer to these IDs. Build only the feature the prompt names.
- F-01 Prepare & session: subject, target time, one-line plan → START; ongoing notification with remaining time; no pause
- F-02 Detection: unlock + app usage → `CHECK` / `ALLOWED_ONLY` / `UNLOCK_ONLY` (rules below)
- F-03 Allow List: user picks study apps; recorded but not counted
- F-04 Early Stop: reason + optional memo
- F-05 Wrap-up & Report: session result, Focus Timeline, reflection; daily/weekly/monthly stats (MPAndroidChart)
- F-06 AI Insight: Gemini via Firebase AI Logic, rule-based fallback, home recommendation card
- F-08 Completion photo (CameraX) + share intent
- F-09 Weekly Report (WorkManager) — stretch goal
- F-10 App Widget: start session, remaining time, today's phone checks
- F-11 Vibration on each stretch — stretch goal
- F-12 Stretching mission: 10 arm stretches with the accelerometer, skippable
- F-13 Refocus alert: same non-allowed app over 3 min → one notification
- F-14 Quick Settings tile: start a session
