@echo off
setlocal
cd /d "%~dp0"
title MYTHOS Server Installer
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-server.ps1"
if errorlevel 1 (
  echo.
  echo Installation did not complete successfully. Review the error above.
  pause
  exit /b 1
)
echo.
echo Installation finished. Use start.bat to launch MYTHOS.
pause
