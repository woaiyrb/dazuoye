@echo off
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
rem ===== mark dev environment launcher (one click) =====
rem DeepTutor -> MySQL -> Redis -> API -> Gateway -> Web -> browser
rem All logic lives in scripts\start-local.ps1.
rem Pass -NoBrowser to start everything without opening a browser.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-local.ps1" %*
if errorlevel 1 (
  echo.
  echo Launcher failed - see the output above.
  pause
  exit /b 1
)
