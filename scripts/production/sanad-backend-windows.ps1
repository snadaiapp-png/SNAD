# ============================================================
# SANAD Platform — Backend Windows Service Installation
# ============================================================
# Installs the backend as a Windows Service using NSSM that:
#   - Starts automatically on boot
#   - Restarts automatically on crash
#   - Runs in the background (no PowerShell session needed)
#
# Usage (in PowerShell as Administrator):
#   Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
#   .\scripts\production\sanad-backend-windows.ps1
# ============================================================

$ErrorActionPreference = "Stop"

$InstallDir = "C:\sanad-platform"
$JarName = "sanad-platform.jar"
$ServiceName = "SanadBackend"
$LogDir = "C:\sanad-platform\logs"
$NssmUrl = "https://nssm.cc/release/nssm-2.24.zip"

Write-Host "=== SANAD Backend Windows Installation ===" -ForegroundColor Cyan

# 1. Check Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: Please run PowerShell as Administrator!" -ForegroundColor Red
    exit 1
}

# 2. Create directories
Write-Host "-> Creating directories..."
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# 3. Check if JAR exists
$JarPath = Join-Path $InstallDir $JarName
if (-not (Test-Path $JarPath)) {
    Write-Host "ERROR: $JarPath not found!" -ForegroundColor Red
    Write-Host "  Please copy the JAR file first to: $JarPath"
    exit 1
}

# 4. Download NSSM if not present
$NssmDir = "C:\nssm"
$NssmExe = "$NssmDir\nssm.exe"
if (-not (Test-Path $NssmExe)) {
    Write-Host "-> Downloading NSSM (Non-Sucking Service Manager)..."
    New-Item -ItemType Directory -Force -Path $NssmDir | Out-Null
    $ZipPath = "$NssmDir\nssm.zip"
    Invoke-WebRequest -Uri $NssmUrl -OutFile $ZipPath
    Expand-Archive -Path $ZipPath -DestinationPath $NssmDir -Force
    Copy-Item "$NssmDir\nssm-2.24\win64\nssm.exe" $NssmExe -Force
    Remove-Item $ZipPath
}

# 5. Install service
Write-Host "-> Installing Windows service: $ServiceName"
& $NssmExe install $ServiceName "C:\Program Files\Java\jdk-21\bin\java.exe" "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8 -jar $JarPath --spring.profiles.active=prod"

# 6. Configure service
& $NssmExe set $ServiceName AppDirectory $InstallDir
& $NssmExe set $ServiceName AppStdout "$LogDir\stdout.log"
& $NssmExe set $ServiceName AppStderr "$LogDir\stderr.log"
& $NssmExe set $ServiceName AppRotateFiles 1
& $NssmExe set $ServiceName AppRotateBytes 10485760
& $NssmExe set $ServiceName Start SERVICE_AUTO_START
& $NssmExe set $ServiceName AppExit Default Restart
& $NssmExe set $ServiceName AppRestartDelay 10000

# 7. Set environment variables
& $NssmExe set $ServiceName AppEnvironmentExtra "SPRING_PROFILES_ACTIVE=prod" "DATABASE_URL=jdbc:postgresql://localhost:5432/sanad" "DATABASE_USERNAME=sanad_backend" "DATABASE_PASSWORD=Snad@2026!Backend" "FLYWAY_ENABLED=true" "JPA_DDL_AUTO=validate" "SERVER_PORT=8080"

# 8. Start service
Write-Host "-> Starting service..."
Start-Service $ServiceName

# 9. Check status
Start-Sleep -Seconds 5
$svc = Get-Service $ServiceName
Write-Host ""
Write-Host "=== Service Status ===" -ForegroundColor Green
Write-Host "Name:    $($svc.Name)"
Write-Host "Status:  $($svc.Status)"
Write-Host "Startup: $($svc.StartType)"
Write-Host ""

Write-Host "=== Done ===" -ForegroundColor Green
Write-Host "Backend is now running as a Windows Service."
Write-Host "It will:"
Write-Host "  [OK] Start automatically on boot"
Write-Host "  [OK] Restart automatically on crash (after 10s)"
Write-Host "  [OK] Run in the background (no PowerShell needed)"
Write-Host ""
Write-Host "Commands:"
Write-Host "  Status:   Get-Service SanadBackend"
Write-Host "  Stop:     Stop-Service SanadBackend"
Write-Host "  Start:    Start-Service SanadBackend"
Write-Host "  Restart:  Restart-Service SanadBackend"
Write-Host "  Logs:     Get-Content C:\sanad-platform\logs\stdout.log -Tail 50 -Wait"
Write-Host "  Health:   Invoke-RestMethod http://localhost:8080/actuator/health"
