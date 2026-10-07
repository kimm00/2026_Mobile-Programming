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

### Focus Session
- Set a subject and target focus time
- Start and end a focus session
- Track screen unlocks and phone usage automatically
- Record completed and early-stopped sessions
- Add a short study plan and reflection
- Take start/end verification photos

### Focus Timeline & Report
- Visualize when focus was interrupted during a session
- View daily, weekly, and monthly statistics
- Track focus time, phone checks, app usage, and goal completion
- Analyze Early Stop frequency and reasons

### AI Focus Insight
- Analyze aggregated focus-session data
- Recommend an appropriate focus duration
- Provide personalized comments and feedback
- Analyze Early Stop patterns and optional memos
- Use a rule-based recommendation if the AI API is unavailable

### Focus Battle
- Create a room and invite friends with a code
- Start focus sessions together
- View real-time **Focusing / Phone in Use** status
- Compare phone-check counts and completion results
- Keep detailed app usage private

---

## Session Flow

**Prepare → Focus Session → Wrap-up**

**Prepare**  
Set the subject, target time, study plan, and verification photo.

**Focus Session**  
Tracking begins when the user presses START. Phone unlocks, screen activity, and app usage are recorded.

**Wrap-up**  
Review the Focus Timeline and session results, add a reflection, and optionally share the result.

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| Local Database | Room |
| Focus Tracking | Foreground Service, BroadcastReceiver, UsageStatsManager |
| Authentication & Battle | Firebase Auth, Firebase Realtime Database |
| AI | LLM API, Retrofit |
| Camera | CameraX |
| Visualization | MPAndroidChart |
| Background Tasks | WorkManager |

---

## Team Roles

### A. Detection & Session
- Foreground Service
- Screen/unlock detection
- UsageStatsManager
- Session lifecycle
- Early Stop
- Room DB schema & DAO
- Notifications

**Owner: TBD**

### B. Report & AI
- Focus Timeline
- Daily / weekly / monthly reports
- Statistical aggregation
- AI recommendations
- Early Stop analysis
- LLM API integration
- Sample data for demo

**Owner: TBD**

### C. Social & UX
- Firebase Authentication
- Onboarding & permissions
- Focus Battle
- CameraX verification photos
- Prepare Session UI
- Navigation & common UI
- Git / PR management

**Owner: TBD**

---

## Development Plan

**Phase 1 — Core**  
Focus session → Detection → Room DB → Timeline & Report

**Phase 2 — AI**  
Focus data → AI analysis → Focus time recommendation & feedback

**Phase 3 — Focus Battle**  
Firebase → Room creation → Real-time status → Battle results

**Phase 4 — Additional Features**  
Weekly Report, App Widget, vibration feedback, sharing

---

## Team

| Member | Role |
|---|---|
| Member 1 | TBD |
| Member 2 | TBD |
| Member 3 | TBD |

---

**FocusTrace — See when your focus breaks.**