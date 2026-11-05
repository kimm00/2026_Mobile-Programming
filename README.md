# FocusTrace

> **See when your focus breaks.**

FocusTrace is an Android app that tracks **when and how users interact with their phones during study sessions** and helps them improve their focus habits through personalized analysis.

Instead of simply blocking smartphone usage, FocusTrace follows a cycle of:

**Record → Recognize → Analyze → Improve**

---

## About FocusTrace

People often check their phones unconsciously while studying without realizing how frequently their focus is interrupted.

Unlike traditional focus apps that mainly provide timers or app blocking, FocusTrace records smartphone interactions **only during intentional focus sessions**.

Users can review when their focus was interrupted, understand their usage patterns, and receive AI-based recommendations for future study sessions.

---

## Main Features

### Login & Onboarding
- Sign in with Google or email (required before using the app)
- Step-by-step permission guide (usage access, notifications, camera, battery optimization)

### Focus Session
- Set a subject, a target focus time, and a one-line study plan
- Start a session from the app, the home-screen widget, or the Quick Settings tile
- No pause: a session ends either by completion or by Early Stop
- Detect phone checks in real time. A phone check is counted only when the user unlocks the phone and opens an app. Unlocking without opening an app, using only allowed apps, and using FocusTrace itself are recorded but not counted.
- Allow List: apps the user needs for studying are recorded but not counted as phone checks
- Refocus notification after using the same non-allowed app for more than 3 minutes\*
- Record completed and early-stopped sessions, with a reason and an optional memo for Early Stop

### Wrap-up
- Review session results and the Focus Timeline
- Write a short reflection
- Optionally take a completion photo and share the result
- Stretching mission after a completed session: stretch your arm 10 times, counted with the accelerometer (can be skipped)\*

\* Depends on the feasibility tests (T6, T7) on 11/6.

### Report
- View daily, weekly, and monthly statistics
- Track focus time, phone checks, app usage, and goal completion
- Analyze Early Stop frequency and reasons

### AI Focus Insight
- Analyze focus-session data with Gemini (Firebase AI Logic)
- Recommend an appropriate focus duration
- Provide personalized comments and feedback, including Early Stop patterns
- Show an AI recommendation card on the home screen
- Use a rule-based recommendation if the AI is unavailable
- Only aggregated statistics from the last 4 weeks and Early Stop memos are sent to the AI. App names and detailed usage records are not sent.

### App Widget
- Start a session directly from the home screen
- Show the remaining time and today's phone-check count

### Stretch Goals
- Weekly Report notification (WorkManager)
- Vibration feedback for each successful stretch

---

## Session Flow

**Prepare → Focus Session → Wrap-up (or Early Stop) → Report**

**Prepare**  
Set the subject, target time, and a one-line study plan.

**Focus Session**  
Tracking begins only when the user starts the session. A foreground service keeps running and shows the remaining time in a notification. Phone unlocks and app usage are recorded.

**Early Stop**  
If the user ends the session early, they choose a reason and can add a memo.

**Wrap-up**  
Review the Focus Timeline and session results, add a reflection, and optionally take a completion photo and share the result.

**Report**  
Review daily, weekly, and monthly statistics with AI insights.

---

## Tech Stack

| Category | Technology |
|---|---|
| Language / UI | Kotlin, XML Views (Fragment, RecyclerView) |
| Local Database | Room |
| Focus Tracking | Foreground Service (`specialUse`), BroadcastReceiver (`USER_PRESENT`), UsageStatsManager |
| Notifications | Ongoing session notification, refocus notification |
| Authentication | Firebase Auth (Google, email) |
| AI | Gemini via Firebase AI Logic |
| Camera | CameraX |
| Sensors | SensorManager (linear acceleration) |
| Visualization | MPAndroidChart |
| Home Screen & System UI | App Widget, Quick Settings Tile (TileService) |
| Background Tasks | WorkManager (stretch goal) |

---

## Development Environment

- Android Studio Quail 3 (2026.1.3)
- Android Gradle Plugin **9.3 or lower** (do not upgrade)
- Min SDK: 35 (Android 15)
- Gradle JDK: Android Studio's embedded JBR

---

## Team Roles

### A. Detection & Session — 김병찬
- Foreground Service and ongoing notification (remaining time)
- Unlock detection (`USER_PRESENT` receiver) and app-usage classification (UsageEvents)
- Allow List
- Refocus notification
- Battery optimization exemption request
- Room schema and DAO design, session start/end functions


### B. Results, Report & AI — 김도이
- Early Stop screen (reason + memo)
- Wrap-up screen (results, Focus Timeline, reflection)
- Daily / weekly / monthly reports and statistics (MPAndroidChart)
- AI recommendations and insights (Firebase AI Logic) with a rule-based fallback
- Home AI recommendation card
- Stretching mission (accelerometer)
- Sample data for the demo
- Stretch goals: Weekly Report (WorkManager), vibration feedback


### C. App Entry & UX — 김경윤
- Login (Google + email, Firebase Auth)
- Onboarding and permission guide (usage access, notifications, camera, battery optimization)
- Prepare screen (subject, target time, one-line plan)
- Completion photo (CameraX) and share intent
- App Widget (main entry point)
- Quick Settings tile
- Navigation, theme, and common UI
- Git rules and README

---

## Schedule

| Date | Milestone |
|---|---|
| 10/20 | Proposal presentation |
| 11/10 | Work-in-progress meeting |
| 11/29 | Feature freeze |
| 12/6 | Final presentation video, slides, and project submission |
| 12/8 | Team interview |

---

## Git Rules

### Branches

| Branch | Purpose | Example |
|---|---|---|
| `main` | Always builds and runs. **Never push directly.** | |
| `feature/<feature>` | Feature development | `feature/f01-prepare-screen` |
| `fix/<description>` | Bug fixes | `fix/tile-lock-screen` |
| `test/<test-id>` | Feasibility tests. **Never merged into `main`.** | `test/t8-tile` |

- One feature per branch. Keep branches small and merge them quickly.
- Reimplement anything learned in a `test/` branch inside a `feature/` branch.

### Commit Messages

Format: `type(scope): description`

- Types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`
- Example: `feat(F-01): add target time input to the prepare screen`
- **Commit small and often.** Pushing all of your code in a single day does not count as development history.

### Pull Requests

- Open a PR into `main` when your work is done.
- In the PR description, write what you did and how you tested it. If you used the agent, add the agent-log entry (e.g., `Agent log: W1 – Prepare screen`).
- The PR author merges their own PR.
- Use **"Create a merge commit"**. Do not squash, so each member's commit history is kept.
- Before merging, make sure the latest `main` builds.

### Do Not Change Without Team Agreement

- `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, and the AGP version
- If you need a new library, tell the team first and add it in a separate PR.

### Avoiding Conflicts

- Pull `main` before you start working.
- Tell the team before editing shared files such as `AndroidManifest.xml` or the Room schema.

---

## Agent Usage

- **Use Android Studio Agent Mode (Gemini in Android Studio) only.** Other agent IDEs and coding agents (e.g., Antigravity, Cursor, Claude Code, Copilot) are not allowed for project code.
- **Review every change before accepting it.** Each member must be able to explain any part of the code in the team interview.
- Never commit secrets (API keys, access tokens, keystore passwords).

### Team Files

| File | Location |
|---|---|
| Agent rules | `AGENTS.md` (project root) |
| Team skills | `.agents/skills/` |
| MCP settings (tokens removed) | `docs/agent-tools/mcp.redacted.json` |
| Files hidden from the agent | `.aiexclude` (`local.properties`, `google-services.json`, keystore files) |
| Design document | `DESIGN.md` |

- Make sure `.gitignore` does not exclude `AGENTS.md`, `.agents/`, or other agent rule files.
- Each member adds their own MCP tokens locally.

### Individual Agent Logs

- Each member writes a weekly log in `docs/agent-logs/<name>/agent-log-wN.md`.
- Record agent use per feature: goal, key prompts (in the language you used), and why each prompt worked or didn't.
- Edit only your own folder. Never copy a teammate's log.

| Log | Due |
|---|---|
| `agent-log-w1.md` | 11/13 |
| `agent-log-w2.md` | 11/20 |
| `agent-log-w3.md` | 11/27 |
| `agent-log-w4.md` | 12/4 |

---

**FocusTrace — See when your focus breaks.**
