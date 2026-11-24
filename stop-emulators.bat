@echo off
rem ==========================================================================
rem  Saves all emulator data (accounts, sessions, attendance) to emulator-data\
rem  and then stops the local Firebase emulators.
rem ==========================================================================
setlocal EnableExtensions
cd /d "%~dp0"

set "PROJECT_ID="
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "(Get-Content -Raw 'app\google-services.json' | ConvertFrom-Json).project_info.project_id"`) do set "PROJECT_ID=%%i"
if not defined PROJECT_ID (
  echo [X] Could not read project_id from app\google-services.json.
  goto :end
)

echo Saving your data to emulator-data\ ...
call firebase emulators:export emulator-data --project %PROJECT_ID% --force
if errorlevel 1 (
  echo [!] Data could not be saved - were the emulators running?
) else (
  echo Data saved.
)

echo Stopping the emulators...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and ($_.CommandLine -like '*cloud-firestore-emulator*' -or $_.CommandLine -like '*firebase-tools*') } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
echo Done. You can close the emulator window.

:end
echo.
pause
