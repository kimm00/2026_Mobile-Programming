# AGENTS.md — FocusTrace

Android app that records phone use during a focus session. **Kotlin + XML Views + Material 3** (no Compose).
Before coding, read `docs/schema.md` (data) or `DESIGN.md` (UI) when relevant.

## UI
- Build every screen with an XML layout in res/layout. Do not use Jetpack Compose or create views in Kotlin code.
- Access views with View Binding (as taught in class). Do not use findViewById.

## Architecture

### Packages
| Package | What goes here |
|---|---|
| `ui/` | Activities and screens, including the calendar screen |
| `data/db/` | Room entities, DAOs, `AppDatabase` |
| `data/repo/` | Repositories (the only way screens reach data) |
| `data/remote/` | Firestore client for the personal board |
| `data/model/` | Plain data classes for screens and reports (not Room entities) |
| `domain/` | Pure Kotlin calculations, e.g. `ReportCalculator` |
| `detection/` | `FocusService`, `EpisodeAnalyzer`, receivers (screen, earphone) |
| `ai/` | AI requests and responses |
| `widget/` | App widget |
| `tile/` | Quick Settings tile |
| `mission/` | Stretching mission |
| `sample/` | Sample data for development only |

### Rules
- Screens call Repository functions only, never a DAO or Firestore directly.
- Use coroutines for database and network work, and Flow for data that screens observe.
- `EpisodeAnalyzer` and everything in `domain/` are pure Kotlin (no Android imports).
- Changes to entities, DAOs, or `AppDatabase` need A's review (Room schema owner).

## Detection rules (tested on device — do not change without a new test)
1. Unlock = `ACTION_USER_PRESENT`, receiver registered with `ContextCompat.RECEIVER_EXPORTED` (NOT_EXPORTED does not receive it).
2. Episode = unlock → `ACTION_SCREEN_OFF`. Analyze 1.5 s after screen off, reading usage events from 1 s before unlock.
3. App time = its `ACTIVITY_RESUMED` → next app's resume, clipped to unlock time. Merge consecutive same app.
4. Exclude FocusTrace, home launcher, `com.android.systemui`.
5. App resumed within 0.5 s of unlock = auto. Auto app left within 2 s = pass (not counted). User-opened apps always count.
6. Kind: none counted → `UNLOCK_ONLY`; only allowed → `ALLOWED_ONLY`; else → `CHECK`.
7. Session start counts as unlock. Start/end episodes with only FocusTrace are not saved.
8. Foreground Service type `specialUse`, started only through `startSession` (START button, widget, or tile), never on its own.

## Session rules
- Only one session can be RUNNING. `startSession` closes any leftover RUNNING session as UNFINISHED.
- To end a session: stop FocusService, wait for RESULT_OK (last episode saved), then call `endSession`.
- The running session is the RUNNING row in the database, not a value held in memory.
- Screens get the repository with `context.sessionRepository` instead of creating one.
- FocusService follows the RUNNING session: it switches on START with a new id and resumes it after a restart.
- Opening the app while a session is RUNNING goes to that session, not to Prepare.

## Dependencies & secrets
- Allowed: AndroidX, Material 3, Room, WorkManager, Firebase Auth, Credential Manager (Google Sign-In), Firebase AI Logic, Firebase Firestore, MPAndroidChart, CameraX. Ask before adding others.
- New permissions (e.g. `BLUETOOTH_CONNECT` for F-17) change the shared manifest. Ask before adding them.
- Never commit or print keys, tokens, or passwords.

## Firestore
- Firestore stores personal board posts only, under `users/{uid}/posts`. Each user reads and writes only their own posts.
- Never upload session data or app usage data to Firestore.

## Features
Prompts refer to these IDs. Build only the feature the prompt names.
- F-01 Prepare & session: subject, target time, one-line plan → START; ongoing notification with remaining time; no pause
- F-02 Detection: unlock + app usage → `CHECK` / `ALLOWED_ONLY` / `UNLOCK_ONLY` (see Detection rules)
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
- F-15 Calendar: in-app CalendarView in Report that marks study days; tap a day to list its sessions
- F-16 Personal board: the user's own posts (e.g. study notes) in Firestore; create, read, update, delete; requires login
- F-17 Earphone detection: detect Bluetooth earphone (e.g. AirPods) connect/disconnect during a session and record it with the session