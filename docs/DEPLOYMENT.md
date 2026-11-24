# Deploying to Real (Cloud) Firebase

This guide puts the backend on Google's Firebase servers so the app works anywhere with
internet, without a laptop running emulators.

> **Just want to run the project for free?** Use the
> [local setup guide](../LOCAL_SETUP_GUIDE.md) instead: everything runs on the Firebase
> Emulator Suite on your own computer, no billing account needed.

---

## 1. Firebase setup

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
   inside the free quota; setting a **budget alert** is recommended. Then:
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

## 2. Point the app at the cloud

The debug build talks to the local emulators by default. In `gradle.properties`, set:

```properties
useFirebaseEmulator=false
```

Release builds always use the real Firebase project over HTTPS, whatever this setting says.

---

## 3. Build and run

Requirements: **Android Studio Ladybug (2024.2) or newer**, Android SDK 35, and a Gradle JDK
between 17 and 23 (if Android Studio reports an incompatible Gradle JVM, choose JVM 21).

1. *File → Open* → select the `QRAttendanceSystem` folder. Let Gradle sync (Android Studio
   creates `local.properties` with your SDK path). Decline any offer to upgrade the Android
   Gradle Plugin.
2. Make sure `app/google-services.json` is present (step 4 above).
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
