@echo off
chcp 65001 >nul
setlocal

rem ===== homework auto-grading dev environment stopper =====
rem Only the services owned by this project are stopped. Do not kill every
rem node.exe on the machine: that would also terminate Codex or another app.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-local.ps1"
exit /b %errorlevel%
