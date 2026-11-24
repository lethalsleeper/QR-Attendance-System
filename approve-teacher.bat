@echo off
rem ==========================================================================
rem  Approves an email address for a TEACHER account on the local emulators.
rem  Usage: double-click and type the email, or:  approve-teacher.bat teacher@college.edu
rem  The emulators (start-emulators.bat) must be running.
rem ==========================================================================
setlocal EnableExtensions
cd /d "%~dp0"

set "EMAIL=%~1"
if not defined EMAIL set /p "EMAIL=Teacher's email address: "
if not defined EMAIL (
  echo [X] No email entered.
  goto :end
)

set "PROJECT_ID="
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "(Get-Content -Raw 'app\google-services.json' | ConvertFrom-Json).project_info.project_id"`) do set "PROJECT_ID=%%i"
if not defined PROJECT_ID (
  echo [X] Could not read project_id from app\google-services.json.
  goto :end
)

rem Talk to the LOCAL Firestore emulator, never the real cloud database.
set FIRESTORE_EMULATOR_HOST=127.0.0.1:8080
pushd functions
node scripts\invite-teacher.js "%EMAIL%" --project %PROJECT_ID%
set "RESULT=%ERRORLEVEL%"
popd

if not "%RESULT%"=="0" (
  echo [X] Failed. Make sure start-emulators.bat is running and shows "All emulators ready!".
) else (
  echo This person can now register in the app as a Teacher using that email.
)

:end
echo.
pause
