# ============================================================
# SANAD — Build and Install Current Backend JAR (Windows)
# ============================================================
# Builds the current repository source, verifies that the resulting JAR
# contains the Control Plane bootstrap controller, safely replaces the
# installed JAR, restarts the backend, and restores the watchdog task.
# ============================================================
[CmdletBinding()]
param(
    [string]$RepositoryRoot = "C:\Users\SNADA\Desktop\SNAD",
    [string]$InstallDir = "C:\sanad-platform",
    [string]$WatchdogTaskName = "SANAD AutoStart Watchdog"
)

$ErrorActionPreference = "Stop"
$BackendProject = Join-Path $RepositoryRoot "apps\sanad-platform"
$MavenWrapper = Join-Path $BackendProject "mvnw.cmd"
$InstalledJar = Join-Path $InstallDir "sanad-platform.jar"
$PidFile = Join-Path $InstallDir "sanad-backend.pid"
$StartScript = Join-Path $RepositoryRoot "scripts\windows\start-sanad-production.ps1"
$BackupDir = Join-Path $InstallDir "backups"
$RequiredJarEntry = "BOOT-INF/classes/com/sanad/platform/internal/bootstrap/api/ControlPlaneBootstrapController.class"

function Stop-InstalledBackend {
    if (-not (Test-Path $PidFile)) { return }

    $backendPid = Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($backendPid -and $backendPid -match '^\d+$') {
        $process = Get-Process -Id ([int]$backendPid) -ErrorAction SilentlyContinue
        if ($process) {
            if ($process.ProcessName -notin @("java", "javaw")) {
                throw "PID file points to unexpected process '$($process.ProcessName)'; refusing to stop it."
            }
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(15000)
        }
    }

    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}

function Wait-ForHealth {
    for ($attempt = 1; $attempt -le 45; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 5
            if ($health.status -eq "UP") { return }
        }
        catch { }
        Start-Sleep -Seconds 2
    }
    throw "Updated backend did not become healthy within 90 seconds."
}

foreach ($requiredPath in @($BackendProject, $MavenWrapper, $InstallDir, $StartScript)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Required path not found: $requiredPath"
    }
}

Write-Host "[1/6] Building current backend source..." -ForegroundColor Cyan
Push-Location $BackendProject
try {
    & $MavenWrapper clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host "[2/6] Locating and verifying generated JAR..." -ForegroundColor Cyan
$BuiltJar = Get-ChildItem (Join-Path $BackendProject "target\sanad-platform-*.jar") -File |
    Where-Object { $_.Name -notlike "*.original" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $BuiltJar) {
    throw "No generated SANAD backend JAR was found under $BackendProject\target."
}

$JarCommand = Get-Command jar.exe -ErrorAction SilentlyContinue
if (-not $JarCommand) {
    throw "jar.exe was not found in PATH. Install or expose a full JDK, not only a JRE."
}

$jarEntries = & $JarCommand.Source tf $BuiltJar.FullName
if ($LASTEXITCODE -ne 0 -or $jarEntries -notcontains $RequiredJarEntry) {
    throw "Generated JAR does not contain the required Control Plane bootstrap controller."
}
Write-Host "  Verified: $RequiredJarEntry" -ForegroundColor Green

$WatchdogWasRunning = $false
$task = Get-ScheduledTask -TaskName $WatchdogTaskName -ErrorAction SilentlyContinue
if ($task) {
    $WatchdogWasRunning = $task.State -eq "Running"
    Stop-ScheduledTask -TaskName $WatchdogTaskName -ErrorAction SilentlyContinue
}

try {
    Write-Host "[3/6] Stopping installed backend..." -ForegroundColor Cyan
    Stop-InstalledBackend

    Write-Host "[4/6] Backing up and installing current JAR..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
    if (Test-Path $InstalledJar) {
        $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
        Copy-Item $InstalledJar (Join-Path $BackupDir "sanad-platform-$timestamp.jar") -Force
    }
    Copy-Item $BuiltJar.FullName $InstalledJar -Force

    Write-Host "[5/6] Starting updated backend..." -ForegroundColor Cyan
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $StartScript
    if ($LASTEXITCODE -ne 0) {
        throw "Backend start script failed with exit code $LASTEXITCODE."
    }
    Wait-ForHealth

    Write-Host "[6/6] Installation verification complete." -ForegroundColor Cyan
    Write-Host "UPDATED BACKEND JAR INSTALLED AND HEALTHY" -ForegroundColor Green
    Write-Host "Source JAR: $($BuiltJar.FullName)" -ForegroundColor White
    Write-Host "Installed JAR: $InstalledJar" -ForegroundColor White
}
finally {
    if ($task -and $WatchdogWasRunning) {
        Start-ScheduledTask -TaskName $WatchdogTaskName -ErrorAction SilentlyContinue
    }
}
