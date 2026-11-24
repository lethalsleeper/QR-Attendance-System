@echo off
rem ==========================================================================
rem  Builds the Android app and puts it in this folder as QR-Attendance.apk.
rem  Copy that file to both phones and install it. No Android Studio needed.
rem  Run it again after changing the code or gradle.properties.
rem ==========================================================================
setlocal EnableExtensions
cd /d "%~dp0"

if not exist "app\google-services.json" (
  echo [X] app\google-services.json is missing - see LOCAL_SETUP_GUIDE.md, Part B.
  goto :fail
)
where java >nul 2>nul
if errorlevel 1 (
  echo [X] Java was not found. Install Java 17 or newer.
  goto :fail
)

echo Building the app. The first build downloads libraries and can take 10+ minutes;
echo later builds take 1-3 minutes. Close other heavy programs to make it faster.
echo.
call gradlew.bat assembleDebug --console=plain
set "BUILD_RESULT=%ERRORLEVEL%"

rem Free the memory used by the build tools straight away.
call gradlew.bat --stop >nul 2>nul

if not "%BUILD_RESULT%"=="0" (
  echo.
  echo [X] The build failed. Scroll up to see the first error message.
  goto :fail
)

copy /y "app\build\outputs\apk\debug\app-debug.apk" "QR-Attendance.apk" >nul
echo.
echo [OK] Created QR-Attendance.apk in this folder.
echo      Copy it to both phones and tap it to install - see LOCAL_SETUP_GUIDE.md, Part E.
explorer /select,"%~dp0QR-Attendance.apk"
echo.
pause
exit /b 0

:fail
echo.
pause
exit /b 1
