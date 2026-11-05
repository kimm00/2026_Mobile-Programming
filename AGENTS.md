# AGENTS.md — FocusTrace

Android app that records phone use during a focus session. **Kotlin + XML Views + Material 3** (no Compose).
Before coding, read `docs/schema.md` (data) or `DESIGN.md` (UI) when relevant.

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

## Dependencies & secrets
- Allowed: AndroidX, Material 3, Room, WorkManager, Firebase Auth, Firebase AI Logic, MPAndroidChart, CameraX. Ask before adding others.
- Never commit or print keys, tokens, or passwords.

