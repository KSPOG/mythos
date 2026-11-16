@echo off
setlocal
cd /d "%~dp0"
title Build MYTHOS
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-mythos.ps1"
if errorlevel 1 (
  echo.
  echo MYTHOS build failed. The server will not be given an unverified plugin JAR.
  pause
  exit /b 1
)
echo.
echo MYTHOS plugin build completed.
pause
