@echo off
rem ==========================================================================
rem  ONE-TIME SETUP: lets phones on this laptop's Mobile hotspot reach the
rem  local Firebase emulators (Windows Firewall blocks them by default).
rem  Opens only ports 9099, 8080 and 5001, and only for the hotspot network.
rem  Windows will ask for administrator permission - click "Yes".
rem  To undo:  allow-hotspot-firewall.bat remove
rem ==========================================================================
setlocal EnableExtensions
cd /d "%~dp0"

net session >nul 2>&1
if errorlevel 1 (
  echo Asking Windows for administrator permission...
  if /i "%~1"=="remove" (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList 'remove' -Verb RunAs"
  ) else (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  )
  exit /b
)

if /i "%~1"=="remove" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\hotspot-firewall.ps1" -Remove
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\hotspot-firewall.ps1"
)
echo.
pause
