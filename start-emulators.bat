@echo off
rem ==========================================================================
rem  Starts the free local Firebase backend (Auth + Firestore + Cloud Functions)
rem  for the QR Attendance app. Keep this window open while you use the app.
rem  Stop it with stop-emulators.bat (that also SAVES your data).
rem ==========================================================================
setlocal EnableExtensions
cd /d "%~dp0"
title QR Attendance - Firebase Emulators

where node >nul 2>nul
if errorlevel 1 (
  echo [X] Node.js was not found. Install it from https://nodejs.org - see LOCAL_SETUP_GUIDE.md, Part A.
  goto :fail
)
where firebase >nul 2>nul
if errorlevel 1 (
  echo [X] The Firebase CLI was not found. Open Command Prompt and run:  npm install -g firebase-tools@13
  goto :fail
)
where java >nul 2>nul
if errorlevel 1 (
  echo [X] Java was not found. The Firestore emulator needs Java 11 or newer.
  goto :fail
)
if not exist "app\google-services.json" (
  echo [X] app\google-services.json is missing - see LOCAL_SETUP_GUIDE.md, Part B.
  goto :fail
)

set "PROJECT_ID="
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "(Get-Content -Raw 'app\google-services.json' | ConvertFrom-Json).project_info.project_id"`) do set "PROJECT_ID=%%i"
if not defined PROJECT_ID (
  echo [X] Could not read project_id from app\google-services.json.
  goto :fail
)

if not exist "functions\node_modules\" (
  echo Installing Cloud Functions packages - first run only, this takes a minute...
  pushd functions
  call npm install --no-audit --no-fund
  popd
  if not exist "functions\node_modules\" goto :fail
)

rem Windows sometimes leaves the Firestore emulator running after the last session.
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*cloud-firestore-emulator*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>nul

rem Give the Functions emulator more time to load the code on the first (slow) start.
set FUNCTIONS_DISCOVERY_TIMEOUT=60

set "IMPORT_ARG="
if exist "emulator-data\firebase-export-metadata.json" set "IMPORT_ARG=--import=emulator-data"

rem --- Mobile hotspot check: the phones reach this laptop through the hotspot.
set "HOTSPOT_IP="
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -like '192.168.137.*' } | Select-Object -First 1).IPAddress"`) do set "HOTSPOT_IP=%%i"
set "APP_HOST="
for /f "tokens=2 delims==" %%i in ('findstr /b /c:"firebaseEmulatorHost=" gradle.properties') do set "APP_HOST=%%i"

echo.
echo  Project ......... %PROJECT_ID%
echo  Emulator UI ..... http://localhost:4000
if defined IMPORT_ARG echo  Saved data ...... loading accounts and sessions from emulator-data\
if not defined HOTSPOT_IP (
  echo.
  echo  [!] Mobile hotspot looks OFF. Opening its settings now - switch it ON and
  echo      connect both phones to it. The emulators keep starting meanwhile.
  start "" ms-settings:network-mobilehotspot
) else if not "%HOTSPOT_IP%"=="%APP_HOST%" (
  echo.
  echo  [!] Your hotspot address is %HOTSPOT_IP% but the app is set to %APP_HOST%.
  echo      Put firebaseEmulatorHost=%HOTSPOT_IP% in gradle.properties, run
  echo      build-app.bat and reinstall the app on both phones.
) else (
  echo  Mobile hotspot .. ON - phones reach this laptop at %HOTSPOT_IP%
)
echo.
echo  Wait for "All emulators ready!" and leave this window open.
echo  To stop AND save your data, double-click stop-emulators.bat.
echo.

call firebase emulators:start --project %PROJECT_ID% %IMPORT_ARG%
echo.
echo Emulators stopped.
pause
exit /b 0

:fail
echo.
pause
exit /b 1
