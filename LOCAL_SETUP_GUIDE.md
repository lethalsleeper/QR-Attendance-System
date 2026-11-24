# Beginner's Guide: Run QR Attendance 100% Free (Laptop Hotspot + 2 Phones)

The whole backend (logins, database, Cloud Functions) runs **on your laptop** using the
free Firebase Emulator Suite. The laptop shares its own Wi-Fi (**Mobile hotspot**), and two
phones connect to it. No cables during use, no Android emulator, no credit card, and no costs.

```
                    Laptop (start-emulators.bat running)
                    "fake Firebase": logins + database + Cloud Functions
                    Mobile hotspot ON  →  address 192.168.137.1
                       ▲                         ▲
            hotspot Wi-Fi                   hotspot Wi-Fi
                       │                         │
         ┌─────────────┴──────┐       ┌──────────┴───────────┐
         │ Teacher phone      │       │ Student phone        │
         │ shows the QR code  │ ◄──── │ scans the QR code    │
         └────────────────────┘ camera└──────────────────────┘
```

* **Parts A–E: once** (about 45 minutes in total).
* **Parts F–H: every time** you use the app.

Whenever a command is shown, use **Command Prompt**: press the Windows key, type `cmd`,
press Enter. Don't use PowerShell.

---

## Part A — Install two tools (one time)

### A1. Node.js
1. Go to <https://nodejs.org>, download the **LTS** version for Windows (`.msi`).
2. Run it and click **Next** on every screen.
3. Open a **new** Command Prompt and check it:
   ```
   node -v
   ```
   It should print something like `v22.x.x`.

### A2. Firebase CLI (runs the local "fake Firebase")
```
npm install -g firebase-tools@13
```
Then check it:
```
firebase --version
```
It should print `13.x.x`.

**Java** and the **Android SDK** are already installed on this laptop. **Android Studio is not
needed.** It's heavy for a 6 GB laptop. Install it later only if you want to read or edit the
code comfortably.

---

## Part B — Get your free `google-services.json` (one time)

The app needs this small file to start, even though everything runs locally.
Firebase's **Spark** plan is free forever. **Never click "Upgrade" / "Blaze".**

1. Open <https://console.firebase.google.com> and sign in with your Google account.
2. **Create a project** → name it e.g. `QR Attendance` → **Continue**.
3. Turn **Google Analytics off** → **Create project** → **Continue**.
4. Click the **Android** icon (or **+ Add app → Android**).
5. **Android package name**, typed exactly:
   ```
   com.example.qrattendance
   ```
   Leave the other fields empty → **Register app**.
6. Click **Download google-services.json**, then **Next → Next → Continue to console**.
7. Move the file into the project's `app` folder:
   `D:\Users\darkf\Documents\Android Projects\QRAttendanceSystem\app\google-services.json`
   The name must be exactly `google-services.json`. If Windows saved it as
   `google-services (1).json`, rename it.

You don't need to switch on Authentication or Firestore in the Firebase website.

---

## Part C — Allow the phones through the firewall (one time)

Windows Firewall blocks phones from reaching the laptop by default.

1. Double-click **`allow-hotspot-firewall.bat`**.
2. Windows asks *"Do you want to allow this app to make changes?"* → click **Yes**.
3. You should see several `[OK]` lines and `Done`.

This opens **only** the three ports the app needs (9099, 8080, 5001), and **only** for devices
on your laptop's hotspot. Other Wi-Fi networks you join (college, café) stay protected.
To undo it later, run `allow-hotspot-firewall.bat remove` in Command Prompt from the project
folder.

---

## Part D — Build the app (one time, and again after any code change)

1. Close Chrome and other heavy programs, because building uses a lot of memory.
2. Double-click **`build-app.bat`**. The first build downloads libraries and can take
   **10–20 minutes** on this laptop. Later builds take 1–3 minutes.
3. When it finishes, a folder window opens with **`QR-Attendance.apk`** selected. That's the
   app.

---

## Part E — Install the app on both phones (one time per build)

1. Copy `QR-Attendance.apk` to each phone, using whichever way is easiest:
   * a USB cable (choose **File transfer** on the phone and copy the file into **Download**),
   * Google Drive, Telegram "Saved Messages", or Quick Share/Nearby Share.
2. On the phone, open **Files → Downloads** and tap **QR-Attendance.apk**.
3. If asked, allow **"Install unknown apps"** for the Files app, then tap **Install**.
4. If **Play Protect** warns about an unknown app, tap **More details → Install anyway**.
   This only appears because you built the app yourself instead of downloading it from the
   Play Store.

USB debugging and Developer options are **not** needed.

---

## Part F — Start everything (every time)

1. **Laptop:** make sure it's connected to the internet (Wi-Fi or a cable). Windows needs
   this to switch the hotspot on. The phones themselves don't need internet for the app.
2. **Laptop:** **Settings → Network & Internet → Mobile hotspot** → switch it **On**.
   The network name and password are shown on that page.
   Tip: turn **Power saving** off there, otherwise Windows switches the hotspot off when no
   phone is connected.
3. **Both phones:** open Wi-Fi settings and connect to the laptop's hotspot name. If a phone
   says *"No internet, stay connected?"*, choose **Yes / Keep**.
4. **Laptop:** double-click **`start-emulators.bat`** and wait for **`All emulators ready!`**.
   Near the top it should say `Mobile hotspot .. ON - phones reach this laptop at
   192.168.137.1`. Keep the window open.
   (The first start downloads extra parts and takes a few minutes. Yellow warnings like
   "not currently authenticated" or "outdated firebase-functions" are normal.)
5. **First time only:** double-click **`approve-teacher.bat`**, type the teacher's email and
   press Enter. Only approved emails can register as teachers.

---

## Part G — Use the app

1. **Teacher phone:** open the app → **Register** → **Teacher** → enter the approved email
   and a password with at least 8 characters, including letters and numbers → **Register**.
2. **Student phone:** **Register** → **Student** → fill in a roll number → **Register**.
3. **Teacher:** **Start Session** → subject (e.g. `Data Structures`) → **5 min** → **Start**.
   The QR code and countdown appear.
4. **Student:** **Scan QR** → **Allow** the camera → point it at the teacher's screen.
   You'll see **"Attendance Marked Successfully!"**
5. **Teacher:** the student appears in the list instantly.
6. Scan again → **"Attendance Already Marked"**. After the timer ends → **"Session Expired"**.

Optional: open <http://localhost:4000> on the laptop to see the users and database live.

---

## Part H — Stop and save (every time)

1. Double-click **`stop-emulators.bat`**. It **saves** all accounts, sessions and attendance
   to the `emulator-data` folder, then stops the backend. Next time it all comes back.
2. Switch the Mobile hotspot off if you like.

⚠️ Just closing the black window does **not** save. Anything since the last save is lost.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| App says **"No internet connection"**, **"Unable to connect"** or keeps loading | Check: hotspot ON, phone connected to the **laptop's hotspot** (not another Wi-Fi), `start-emulators.bat` window open and ready, Part C done. Turning off the phone's **mobile data** can also help. |
| Mobile hotspot switch won't turn on | Connect the laptop to the internet first (Wi-Fi or cable), then try again. |
| Hotspot keeps switching itself off | Mobile hotspot settings → turn **Power saving** off. |
| Black window warns *"Your hotspot address is … but the app is set to …"* | Do what it says: change `firebaseEmulatorHost` in `gradle.properties`, run `build-app.bat`, reinstall on both phones. |
| Black window says **"port taken"** | Double-click `stop-emulators.bat`, then `start-emulators.bat` again. |
| App shows **"Firebase is not configured"** | `app\google-services.json` was missing when you built. Add it, run `build-app.bat`, reinstall. |
| **"This email is not approved for a teacher account"** | Run `approve-teacher.bat` with exactly that email, then register again. |
| **"We couldn't sign you in"** / profile not found | Data wasn't saved last time. Tap **Log out** and register again. |
| Phone says **"App not installed"** when updating | Uninstall the old QR Attendance app first, then install the new APK. |
| Build is very slow or fails with *"out of memory"* | Close other programs and run `build-app.bat` again. |
| `'firebase' is not recognized` | Close and reopen Command Prompt, or repeat step A2. |
| *"running scripts is disabled on this system"* | You're in PowerShell. Use **Command Prompt**. |
