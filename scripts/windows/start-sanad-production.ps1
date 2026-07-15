# ============================================================
# SANAD Platform — Production Start Script (Windows)
# ============================================================
# Starts the backend on port 8080 with production settings.
# Does NOT reset passwords, create databases, or kill other Java processes.
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$InstallDir = "C:\sanad-platform"
$JarName = "sanad-platform.jar"
$LogDir = "$InstallDir\logs"
$PidFile = "$InstallDir\sanad-backend.pid"

# 1. Check PostgreSQL service
Write-Host "[1/6] Checking PostgreSQL..." -ForegroundColor Cyan
$pgService = Get-Service -Name "postgresql*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($pgService -and $pgService.Status -ne "Running") {
    Write-Host "  Starting PostgreSQL service..." -ForegroundColor Yellow
    Start-Service $pgService.Name
}
if (-not $pgService) {
    Write-Host "  WARNING: PostgreSQL service not found. Ensure PostgreSQL is running." -ForegroundColor Yellow
}
Write-Host "  PostgreSQL: OK" -ForegroundColor Green

# 2. Check JAR exists
$JarPath = Join-Path $InstallDir $JarName
if (-not (Test-Path $JarPath)) {
    Write-Host "ERROR: $JarPath not found!" -ForegroundColor Red
    Write-Host "Build first: cd apps/sanad-platform; mvn clean package -DskipTests"
    exit 1
}

# 3. Read environment from .env.local (not tracked in Git)
$EnvFile = "$InstallDir\.env.local"
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match "^([^#][^=]+)=(.*)$") {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim()
            Set-Item -Path "Env:$key" -Value $val
        }
    }
    Write-Host "[2/6] Environment loaded from .env.local" -ForegroundColor Green
} else {
    Write-Host "[2/6] WARNING: .env.local not found at $EnvFile" -ForegroundColor Yellow
    Write-Host "  Create it with: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD,"
    Write-Host "  SANAD_CONTROL_PLANE_TENANT_ID, JWT_SECRET, etc."
}

# 4. Validate critical env vars
$required = @("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD")
foreach ($var in $required) {
    if (-not (Get-Item "Env:$var" -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: $var is not set!" -ForegroundColor Red
        exit 1
    }
}
Write-Host "  Environment: OK" -ForegroundColor Green

# 5. Check if already running
if (Test-Path $PidFile) {
    $oldPid = Get-Content $PidFile
    $proc = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Host "SANAD Backend is already running (PID: $oldPid)" -ForegroundColor Yellow
        exit 0
    }
}

# 6. Start backend
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Write-Host "[3/6] Starting SANAD Backend..." -ForegroundColor Cyan
$proc = Start-Process -FilePath "java" `
    -ArgumentList "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-Dfile.encoding=UTF-8", `
                  "-jar", $JarPath, "--spring.profiles.active=prod", `
                  "--server.address=0.0.0.0", "--server.port=8080" `
    -WindowStyle Hidden `
    -RedirectStandardOutput "$LogDir\stdout.log" `
    -RedirectStandardError "$LogDir\stderr.log" `
    -PassThru

$proc.Id | Out-File -FilePath $PidFile -Encoding ASCII
Write-Host "  PID: $($proc.Id)" -ForegroundColor Green

# 7. Wait for health
Write-Host "[4/6] Waiting for health endpoint..." -ForegroundColor Cyan
for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Seconds 5
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 5
        if ($health.status -eq "UP") {
            Write-Host "[5/6] Backend is UP!" -ForegroundColor Green
            Write-Host "[6/6] Health: $($health.status)" -ForegroundColor Green
            Write-Host ""
            Write-Host "SANAD Backend started successfully." -ForegroundColor Green
            Write-Host "  URL: http://localhost:8080" -ForegroundColor White
            Write-Host "  PID: $($proc.Id)" -ForegroundColor White
            Write-Host "  Logs: $LogDir" -ForegroundColor White
            exit 0
        }
    } catch {
        Write-Host "  Attempt $i : $($health.status)..." -ForegroundColor Gray
    }
    if ($proc.HasExited) {
        Write-Host "ERROR: Backend process exited prematurely!" -ForegroundColor Red
        Get-Content "$LogDir\stderr.log" -Tail 30
        exit 1
    }
}
Write-Host "ERROR: Backend did not become healthy within 150s" -ForegroundColor Red
Get-Content "$LogDir\stderr.log" -Tail 30
exit 1
