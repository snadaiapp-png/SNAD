@echo off
REM ============================================================
REM SANAD Platform — Backend Quick Start (Windows)
REM ============================================================
REM Starts the backend in the background using Java Web Start.
REM For permanent installation, use sanad-backend-windows.ps1
REM ============================================================

setlocal

set INSTALL_DIR=C:\sanad-platform
set JAR_NAME=sanad-platform.jar
set LOG_DIR=C:\sanad-platform\logs

REM Create log directory
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM Check if JAR exists
if not exist "%INSTALL_DIR%\%JAR_NAME%" (
    echo ERROR: %INSTALL_DIR%\%JAR_NAME% not found!
    echo Please copy the JAR file first.
    pause
    exit /b 1
)

REM Check if already running
tasklist /fi "WINDOWTITLE eq SANAD Backend" 2>nul | find /i "SANAD Backend" >nul
if %ERRORLEVEL% equ 0 (
    echo SANAD Backend is already running.
    pause
    exit /b 0
)

REM Start in background
echo Starting SANAD Backend...
start "SANAD Backend" /MIN cmd /c "java -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8 -jar %INSTALL_DIR%\%JAR_NAME% --spring.profiles.active=prod > %LOG_DIR%\stdout.log 2> %LOG_DIR%\stderr.log"

REM Wait for startup
timeout /t 10 /nobreak >nul

REM Check health
echo Checking health...
curl -s http://localhost:8080/actuator/health
echo.

echo.
echo SANAD Backend started in background.
echo Logs: %LOG_DIR%\stdout.log
echo.
echo To stop: taskkill /fi "WINDOWTITLE eq SANAD Backend"
pause
