@echo off
setlocal
cd /d "%~dp0"
title MYTHOS Minecraft Server
set "SERVER_DIR=%~dp0server"
if not exist "%SERVER_DIR%\run.bat" (
  echo MYTHOS server is not installed yet.
  echo Run install.bat first.
  pause
  exit /b 1
)
if not exist "%SERVER_DIR%\mods\Mythos.jar" (
  echo MYTHOS plugin JAR is missing.
  echo Run build.bat to compile and install the plugin, then retry.
  pause
  exit /b 1
)
java -version >nul 2>&1
if errorlevel 1 (
  echo Java was not found. Install a 64-bit Java 21 runtime and add it to PATH.
  pause
  exit /b 1
)
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~V"
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
if not defined JAVA_MAJOR set "JAVA_MAJOR=0"
if not "%JAVA_MAJOR%"=="21" (
  echo Java 21 is required. Detected: %JAVA_VERSION%
  pause
  exit /b 1
)
if not exist "%SERVER_DIR%\eula.txt" echo eula=false>"%SERVER_DIR%\eula.txt"
findstr /i /c:"eula=true" "%SERVER_DIR%\eula.txt" >nul
if errorlevel 1 (
  echo Mojang's Minecraft EULA has not been accepted.
  echo Read https://www.minecraft.net/eula
  echo If you agree, edit server\eula.txt and change eula=false to eula=true.
  pause
  exit /b 1
)
cd /d "%SERVER_DIR%"
call run.bat nogui
set "EXIT_CODE=%ERRORLEVEL%"
echo.
echo MYTHOS server stopped with exit code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
