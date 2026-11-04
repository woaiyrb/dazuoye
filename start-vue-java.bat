@echo off
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-vue-java.ps1"
if errorlevel 1 (
  echo.
  echo Preview startup failed. Check .local-dev\logs.
  pause
  exit /b 1
)
