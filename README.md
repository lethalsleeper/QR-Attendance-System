# QR Attendance System

An Android app that replaces paper roll-calls. A **teacher** starts a session and shows a
time-limited QR code; **students** scan it with their phones; a **Cloud Function** validates
the scan on the server and records attendance in **Cloud Firestore**; the teacher sees each
student appear on screen in real time.

```
Teacher taps "Start Session"  ──►  createSession (Cloud Function)
                                     · session id, 256-bit token, server-time expiry
Teacher screen shows QR {sessionId, token} + countdown
Student scans QR (CameraX + ML Kit) ──►  markAttendance (Cloud Function)
                                     · auth + role + token + expiry + duplicate checks
                                     · Firestore transaction writes the record
Teacher's live list updates  ◄──  Firestore snapshot listener → Flow → StateFlow → Compose
```

---

## 1. Features

| Area | What is implemented |
|---|---|
| **Auth** | Email/password login & registration, input validation, password visibility toggle, duplicate-email handling, loading/error states, role-based navigation, logout that clears the back stack |
| **Role security** | Roles are assigned by the server. Teacher accounts require an administrator-created invite (`teacherInvites/{email}`); a modified APK cannot self-promote |
| **Teacher** | Dashboard (greeting, profile, stats, session history with date/time/count/status, empty state), Start Session (subject + 2/5/10/15 min), Active Session (ZXing QR, countdown, live attendance list with names / roll numbers / times, "End session" with confirmation, "Session Expired" state that hides the QR but keeps the list), screen kept awake while live, tablet two-pane layout |
| **Student** | Dashboard (greeting, roll number, Scan QR, attendance %, attended/conducted, per-subject breakdown, recent history), QR Scanner (CameraX preview, ML Kit QR detection, runtime permission incl. "Don't ask again" → Settings, torch toggle, one-scan-at-a-time, success/failure panels, Scan Again), full Attendance History grouped by month |
| **Backend** | 4 callable Cloud Functions + 1 scheduled function, Firestore transactions, constant-time token comparison, server-time expiry, duplicate prevention by document id, rollback on failed registration |
| **Security rules** | Least-privilege Firestore rules; no client can write sessions or attendance; tokens readable only by the owning teacher |
| **UX** | Material 3 design system (palette, typography, shapes, buttons, text fields, cards, loaders, error banners, empty states, dialogs, status pills), light + dark theme, edge-to-edge, offline banner, smooth navigation transitions |
| **Tests** | 65 Android JVM unit tests · 28 backend unit tests · 43 backend emulator tests (functions + security rules) |

---

## 2. Technology stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin (K2) | 2.0.21 |
| Build | Android Gradle Plugin / Gradle (Kotlin DSL + version catalog) | 8.7.3 / 8.10.2 |
| UI | Jetpack Compose BOM / Material 3 | 2024.12.01 / 1.3.x |
| Navigation | Navigation Compose (type-safe routes, kotlinx.serialization) | 2.8.5 |
| DI | Hilt (KSP) | 2.52 |
| Async | Kotlin Coroutines, Flow, StateFlow | 1.9.0 |
| Lifecycle | lifecycle-runtime-compose (`collectAsStateWithLifecycle`) | 2.8.7 |
| Backend SDKs | Firebase BoM (Auth, Firestore, Functions) | 33.7.0 |
| QR generation | ZXing core | 3.5.3 |
| QR scanning | CameraX + ML Kit Barcode Scanning (bundled model) | 1.4.1 / 17.3.0 |
| Cloud Functions | Node.js 20, firebase-functions (2nd gen), firebase-admin | 6.x / 12.x |
| Tests | JUnit4, kotlinx-coroutines-test, MockK, Turbine, Node test runner, @firebase/rules-unit-testing | |

**SDK levels:** `minSdk 26` (Android 8.0 — ~97 % of active devices; gives native `java.time`
and adaptive icons without desugaring), `targetSdk 35`, `compileSdk 35`, Java/Kotlin target 17.

---

## 3. Architecture

Clean MVVM with the repository pattern. Composables only render state and forward user
intents; they never touch Firebase.

```
presentation  (Compose screens ─► ViewModels exposing StateFlow<UiState> + one-off event Flows)
     │
domain        (models, repository interfaces, use cases: MarkAttendance, StartSession,
     │         QrPayloadCodec, InputValidator, SessionExpiryPolicy — pure Kotlin)
     │
data          (repository implementations, Firestore/Functions data sources, mappers,
               error mapping, snapshot-listener → Flow adapters)
```

* **Real-time pipeline:** `addSnapshotListener` → `callbackFlow` (listener removed in
  `awaitClose`) → repository `Flow` → ViewModel `combine`/`stateIn(WhileSubscribed(5s))` →
  `collectAsStateWithLifecycle()`. Listeners stop 5 s after a screen leaves the foreground.
* **Errors:** every Firebase/network exception is mapped to a user-friendly `AppError`
  (`FirebaseErrorMapper`, `FunctionResponseMapper`). Flows close with `AppException` and
  ViewModels convert them into UI state — nothing crashes the app.
* **Role-aware navigation:** teacher/student destinations are wrapped in `RoleGuard`. A
  student who reaches a teacher route (or vice versa) sees "Access denied" with a button to
  their own dashboard. Data access is enforced separately by the rules and functions.
* **Clock skew:** each function response carries `serverTime`; `ServerSyncedTimeProvider`
  corrects the countdown for a wrong device clock. Expiry itself is always decided by the server.

### Project structure

```
QRAttendanceSystem/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/ (theme, adaptive launcher icon, network security config)
│       │   └── java/com/example/qrattendance/
│       │       ├── QRAttendanceApp.kt, MainActivity.kt
│       │       ├── data/
│       │       │   ├── mapper/      FirestoreMappers, FunctionResponseMapper, FirebaseErrorMapper
│       │       │   ├── remote/      FirestoreSchema, CloudFunctionsDataSource
│       │       │   └── repository/  Auth/Session/AttendanceRepositoryImpl, snapshot→Flow helpers
│       │       ├── domain/
│       │       │   ├── model/       User, Session, Attendance, AppError/AppResult, SessionExpiryPolicy
│       │       │   ├── repository/  AuthRepository, SessionRepository, AttendanceRepository
│       │       │   └── usecase/     MarkAttendance, StartSession, QrPayloadCodec, InputValidator
│       │       ├── presentation/
│       │       │   ├── auth/        Splash, Login, Register (+ ViewModels)
│       │       │   ├── teacher/     TeacherDashboard, StartSession, ActiveSession (+ ViewModels)
│       │       │   ├── student/     StudentDashboard (+ ViewModel)
│       │       │   ├── scanner/     ScannerScreen/ViewModel, QrCameraPreview, QrCodeAnalyzer, ScannerOverlay
│       │       │   ├── attendance/  AttendanceHistory screen/ViewModel, history item
│       │       │   ├── components/  design-system components
│       │       │   ├── navigation/  Routes, AppNavHost, RoleGuard, AppViewModel
│       │       │   └── theme/       Color, Type, Shape/Spacing, Theme (light + dark)
│       │       ├── di/              FirebaseModule, RepositoryModule, DispatchersModule
│       │       └── utils/           NetworkMonitor, TimeProvider, QrCodeGenerator (ZXing), DateTimeUtils …
│       ├── debug/res/xml/          cleartext allowed for the Firebase Emulator (debug only)
│       └── test/                   JVM unit tests
├── functions/
│   ├── index.js                    function exports (region us-central1)
│   ├── src/validation.js           pure validation + attendance decision logic
│   ├── src/handlers.js             callable handlers (dependency-injected)
│   ├── src/stats.js                attendance statistics
│   ├── scripts/invite-teacher.js   admin tool to approve teacher emails
│   └── test/{unit,emulator}/       backend tests
├── firestore.rules
├── firestore.indexes.json
├── firebase.json                   deploy + emulator configuration
├── gradle/libs.versions.toml
├── settings.gradle.kts, build.gradle.kts, gradle.properties
└── README.md
```

---

## 4. Security model

### 4.1 Role provisioning (why a modified APK can't become a teacher)

1. The app never writes `users/{uid}`; Firestore rules deny `create` on profiles and deny any
   update to `role`, `email` or `rollNumber`.
2. Registration goes through the **`registerUser`** callable function. It creates the Firebase
   Auth account with the Admin SDK and writes the profile in a transaction.
3. If the requested role is `teacher`, the function requires a document
   **`teacherInvites/{lowercase-email}`** with `used: false`. That collection is invisible to
   clients (rules deny all access); only an administrator can create it (Firebase console or
   `functions/scripts/invite-teacher.js`). The invite is marked `used` in the same transaction.
4. Any other role value (e.g. `"admin"`) is rejected. If the profile write fails, the Auth
   account is deleted again so the email can be reused.

**Admin workflow:** add `teacherInvites/teacher@college.edu` → teacher registers in the app →
role `teacher`. To promote/demote an existing account, edit `role` in `users/{uid}` in the
console (clients cannot).

### 4.2 How the rules and Cloud Functions work together

| Operation | Who | Path |
|---|---|---|
| Create account / assign role | `registerUser` (Admin SDK) | rules deny client writes |
| Create session, generate token & expiry | `createSession` (Admin SDK) | rules deny client `create` |
| Read own session (incl. token) | owning teacher | rule: `isTeacher() && teacherId == auth.uid` |
| End own session early | owning teacher | rule allows only `status: active→expired` + `endedAt == request.time` |
| Write attendance | `markAttendance` (Admin SDK) | rules deny **all** client writes |
| Read live attendance list | owning teacher | rule checks parent session's `teacherId` |
| Read own history | student | `users/{uid}/attendance` readable by owner only |
| Everything else | — | denied by the catch-all rule |

`markAttendance` checks, in order: authenticated → payload format → caller's profile exists
and `role == student` → session exists → token matches (constant-time) → not already marked →
not expired (**server time**, `now >= expiresAt` or `status != active`). It then, in **one
Firestore transaction**, creates `sessions/{id}/attendance/{studentUid}` and
`users/{studentUid}/attendance/{sessionId}` and increments `attendanceCount`. Because the
attendance document id is the student's UID, concurrent duplicate requests are serialised by
the transaction and the loser gets `ALREADY_MARKED`. The app shows success **only** after the
function returns.

The session token is generated on the server, never returned by `createSession`, never logged,
and readable only by the teacher who owns the session. Students only ever see it inside the QR.

---

## 5. Firestore data model

```
users/{uid}
  uid, name, email, role ("teacher"|"student"), rollNumber (string|null), createdAt (server ts)
users/{uid}/attendance/{sessionId}            ← student history (written by markAttendance)
  sessionId, subject, subjectKey, teacherId, teacherName, sessionCreatedAt, markedAt, status

sessions/{sessionId}                          ← auto id (written by createSession)
  sessionId, teacherId, teacherName, subject, subjectKey (lower-case),
  createdAt, expiresAt (server time), token (256-bit base64url), status ("active"|"expired"),
  durationMinutes, attendanceCount, endedAt?
sessions/{sessionId}/attendance/{studentUid}  ← one doc per student (written by markAttendance)
  studentId, studentName, rollNumber, markedAt, status ("present")

teacherInvites/{lowercase email}              ← admin only
  used (bool), usedBy?, usedAt?
```

All timestamps are Firestore `Timestamp`s from the server (`FieldValue.serverTimestamp()` for
profiles; the function's server clock for session and attendance times so the value returned to
the app is identical to the stored one). Composite indexes are in `firestore.indexes.json`.

---

## 6. Firebase setup

You need: a Google account, Node.js 20+, Java 17+ (for emulators) and the Firebase CLI
(`npm install -g firebase-tools`, then `firebase login`).

1. **Create a Firebase project** – <https://console.firebase.google.com> → *Add project*.
   Google Analytics is not needed.
2. **Add the Android app** – *Project overview → Add app → Android*.
   * Package name: **`com.example.qrattendance`** (must match `applicationId`).
   * App nickname: anything. SHA-1: not required (email/password auth).
3. **Download `google-services.json`** from the last step of that wizard (or later from
   *Project settings → Your apps*).
4. **Place it at `app/google-services.json`** (next to `app/build.gradle.kts`). It is git-ignored.
   Without it the project still compiles, but the app shows "Firebase is not configured".
5. **Enable Email/Password sign-in** – *Build → Authentication → Get started → Sign-in method →
   Email/Password → Enable → Save*.
6. **Create Cloud Firestore** – *Build → Firestore Database → Create database* → choose a
   location near you → start in **production mode** (never test mode; our rules replace the
   defaults in step 7).
7. **Link the CLI and deploy rules + indexes** (from the project root):
   ```bash
   firebase use --add
   ```
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes
   ```
   Index builds take a few minutes; until then the teacher dashboard shows a "missing index" error.
8. **Deploy Cloud Functions** – Cloud Functions (2nd gen) and the scheduled function require the
   **Blaze (pay-as-you-go)** plan: *Usage and billing → Modify plan*. A classroom demo stays far
   inside the free quota. Then:
   ```bash
   cd functions && npm install && cd ..
   ```
   ```bash
   firebase deploy --only functions
   ```
   Region is `us-central1` in **two places** that must match: `REGION` in `functions/index.js`
   and `FUNCTIONS_REGION` in `app/build.gradle.kts`.
9. **Environment configuration** – no secrets or environment variables are required. The only
   project-specific settings are:
   * **Approve teachers** before they register (choose one):
     * Console: Firestore → *Start collection* `teacherInvites` → Document ID = the teacher's
       email in lower case → field `used` (boolean) = `false`.
     * CLI (needs `gcloud auth application-default login` or `GOOGLE_APPLICATION_CREDENTIALS`):
       ```bash
       cd functions && node scripts/invite-teacher.js teacher@college.edu --project YOUR_PROJECT_ID
       ```
   * Optional hardening: enable **Firebase App Check** (Play Integrity) for the app, then set
     `enforceAppCheck: true` in `functions/index.js` and redeploy.
   * If your organisation policy blocks public Cloud Run invokers, allow `allUsers` to invoke the
     callable functions (they still require a Firebase ID token internally).

---

## 7. Build and run

Requirements: **Android Studio Ladybug (2024.2) or newer** (bundled JDK 17/21), Android SDK 35.

1. *File → Open* → select the `QRAttendanceSystem` folder. Let Gradle sync (Android Studio
   creates `local.properties` with your SDK path).
2. Make sure `app/google-services.json` is present (section 6).
3. Connect a phone with USB debugging (or start an emulator with a camera) → **Run ▶ app**.

Command line:
```bash
./gradlew assembleDebug
```
```bash
./gradlew installDebug
```
(Windows: `gradlew.bat …`). APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Demo flow:** approve a teacher email → register the teacher in the app → register a student
on a second phone → teacher *Start Session* → student *Scan QR* → watch the student appear live.

### Running entirely on the Firebase Emulator Suite (free, no Blaze plan)

**Step-by-step beginner instructions: [LOCAL_SETUP_GUIDE.md](LOCAL_SETUP_GUIDE.md).** The
default setup is two physical phones on the laptop's Windows Mobile Hotspot. The project is
already configured for it (`useFirebaseEmulator=true`, `firebaseEmulatorHost=192.168.137.1` in
`gradle.properties`) and includes Windows helper scripts:

| Script | Does |
|---|---|
| `allow-hotspot-firewall.bat` | One-time, admin: allows TCP 9099/8080/5001 from the hotspot subnet 192.168.137.0/24 only (`remove` undoes it) |
| `build-app.bat` | Builds the debug APK and copies it to `QR-Attendance.apk` for installing on the phones |
| `start-emulators.bat` | Checks the hotspot, then starts Auth + Firestore + Functions emulators for the project id in `google-services.json`, restoring saved data |
| `approve-teacher.bat` | Adds a `teacherInvites/{email}` entry in the local Firestore |
| `stop-emulators.bat` | Exports all data to `emulator-data/`, then stops the emulators |

To use real cloud Firebase instead, set `useFirebaseEmulator=false`. The emulator switch exists
only in debug builds; release builds always use production Firebase over HTTPS.

---

## 8. Testing

### Automated tests

| Suite | Command | Count | Covers |
|---|---|---|---|
| Android JVM unit tests | `./gradlew testDebugUnitTest` | 65 | QR payload parsing, expiry logic & countdown, input validation, Firestore/Function mappers, error mapping, repository (duplicate / expired / invalid / network handling), MarkAttendance use case, Scanner, Login, Register and StartSession ViewModels |
| Backend unit tests | `cd functions && npm run test:unit` | 28 | registration/session/payload validation, token generation & comparison, expiry boundary, attendance decision order, statistics |
| Backend emulator tests | `cd functions && npm run test:emulator` | 71 total (adds 43) | every `markAttendance` path incl. concurrent duplicates, server-time expiry, token from another session, role checks, `createSession`, `registerUser` (duplicate email, teacher invite, role tampering), stats, scheduled expiry, **and the security rules** (profile/role tampering, token secrecy, attendance writes, session tampering) |

`npm run test:emulator` needs the Firebase CLI on the PATH and Java 11+; it starts the Auth and
Firestore emulators under the offline project id `demo-qr-attendance`. On Windows the Firestore
emulator JVM occasionally keeps running after the tests finish; if the next run reports
"port taken", stop the leftover `java … cloud-firestore-emulator` process (Task Manager).

### Manual test checklist

| # | Scenario | Expected result |
|---|---|---|
| 1 | Register a **teacher** whose email has an invite | Lands on Teacher Dashboard; invite shows `used: true` |
| 1b | Register a teacher **without** an invite | "This email is not approved for a teacher account…"; no account created |
| 2 | Register a **student** (with roll number) | Lands on Student Dashboard showing the roll number |
| 2b | Register again with the same email | "An account with this email already exists…" |
| 3 | Teacher logs out and logs in | Teacher Dashboard with session history |
| 4 | Student logs in with a wrong password, then the right one | Error message, then Student Dashboard |
| 5 | Teacher → Start Session → subject + 2 min → Start | Active Session screen, status Live |
| 6 | QR code is visible with a countdown | QR rendered, timer counts down, progress bar shrinks |
| 7 | Student → Scan QR → grant camera → point at QR | "Verifying attendance…" then the scanner pauses |
| 8 | Backend confirms | "Attendance Marked Successfully!", subject, date & time |
| 9 | Teacher screen (no refresh) | Student appears instantly with roll number and time; count increments |
| 10 | Student scans the same QR again | "Attendance Already Marked" |
| 11 | Wait for the countdown to end, scan with a new student | Teacher sees "Session Expired" (QR hidden, list kept); student sees "QR Code Expired" |
| 12 | Scan any other QR (a URL, Wi-Fi code) | "Invalid QR Code" without a network call |
| 13 | Student dashboard / View all | History shows the session; attended count and % update |
| 14 | Teacher dashboard | Past sessions with date, time, present count and Ended status; tap to view the final list |
| 15 | Logout (both roles) | Confirmation dialog → Login; Back does not return to the dashboard |
| + | Deny camera permission twice | "Camera permission needed" → *Open Settings*; returning after granting starts the camera |
| + | Airplane mode, then scan | "Unable to connect. Please try again."; nothing recorded |
| + | Teacher kills the app during a session, reopens | Session still listed as Live until expiry; tapping it shows the QR again |
| + | "End" during a live session | Confirmation → QR disappears immediately, scans rejected |

---

## 9. Edge cases and how they are handled

| Case | Handling |
|---|---|
| No internet | Offline banner; scans fail fast with "Unable to connect"; Firestore serves cached data |
| Auth failure / wrong password | Mapped Firebase exceptions → friendly messages |
| Duplicate email | `EMAIL_EXISTS` from `registerUser` → "account already exists" |
| Camera permission denied / permanently denied | Rationale screen with *Grant* or *Open Settings*; re-checked on resume |
| Camera unavailable / in use | "Camera unavailable" screen with retry |
| Invalid / foreign QR | Rejected locally by `QrPayloadCodec`, and again by the server |
| Expired QR / expires while scanning | Server compares with server time → "QR Code Expired" |
| Session does not exist | "Session Not Found" |
| Token mismatch | "Invalid QR Code" |
| Same QR scanned repeatedly | Only one request while validating; same code ignored for 2.5 s after a result; server returns "Already Marked" |
| Student on teacher screen / teacher on student screen | `RoleGuard` → "Access denied"; rules block the data anyway |
| Firestore permission denied | Mapped to "You don't have permission…" |
| Teacher closes the app during a session | Session keeps running on the server; scheduled `expireSessions` marks it expired; dashboard computes status from `expiresAt` |
| Student closes the app after scanning | The callable completes server-side; history shows it on next launch; a rescan says "Already Marked" |
| Network failure during submission | Reported as a network error; success is never shown without the server's confirmation |
| Missing user profile | Splash/guard show "Couldn't load your profile" with *Retry* and *Log out* |
| Firebase initialisation failure | `FirebaseInitializer` detects it; a "Firebase is not configured" screen is shown instead of crashing |

---

## 10. Known limitations

* **QR sharing:** a student could photograph the QR and send it to an absent friend during the
  validity window. Short durations (2–5 min) reduce this; production systems add rotating
  tokens (e.g. every 20 s), location checks or device binding.
* **No enrolment model:** "sessions conducted" counts only sessions of subjects (same teacher)
  the student has attended at least once since registering. A course roster would make the
  percentage exact for every enrolled subject.
* **Blaze plan required** to deploy Cloud Functions (the Emulator Suite is free).
* **App Check is off by default**, so the callable endpoints can be called by any client with a
  valid Firebase ID token (all authorisation is still enforced server-side).
* Student names in attendance records are denormalised at scan time and don't change if a
  profile name is edited later. The app doesn't include a profile-edit screen (rules allow
  name-only edits).
* Roll-number uniqueness is not enforced; there is no password-reset or email-verification UI.
* `com.example.qrattendance` is fine for a college project but cannot be published on Google
  Play; change `applicationId`/`namespace` (and re-register the Firebase app) before publishing.
* String literals are in Kotlin rather than `strings.xml`, so the UI is English-only.
