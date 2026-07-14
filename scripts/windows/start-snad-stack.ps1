[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$deployDirectory = Join-Path $RepositoryRoot "deploy\self-hosted"
$composeFile = Join-Path $deployDirectory "docker-compose.windows.yml"
$environmentFile = Join-Path $deployDirectory ".env"
$logDirectory = Join-Path $RepositoryRoot "logs\self-hosted"
$logFile = Join-Path $logDirectory "startup.log"

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Write-StackLog {
    param([string]$Message)
    $line = "{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    Write-Host $line
}

function Test-DockerEngine {
    try {
        & docker info *> $null
        return $LASTEXITCODE -eq 0
    }
    catch {
        return $false
    }
}

function Start-DockerDesktopIfAvailable {
    $candidatePaths = @(
        (Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"),
        (Join-Path ${env:ProgramFiles(x86)} "Docker\Docker\Docker Desktop.exe"),
        (Join-Path $env:LOCALAPPDATA "Docker\Docker Desktop.exe")
    ) | Where-Object { $_ -and (Test-Path $_) }

    if ($candidatePaths.Count -gt 0) {
        Write-StackLog "Docker Engine is unavailable. Starting Docker Desktop."
        Start-Process -FilePath $candidatePaths[0] -WindowStyle Minimized | Out-Null
    }
}

if (-not (Test-Path $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if (-not (Test-Path $environmentFile)) {
    throw "Environment file not found: $environmentFile. Run install-snad-autostart.ps1 first."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Install Docker Desktop and run this script again."
}

if (-not (Test-DockerEngine)) {
    Start-DockerDesktopIfAvailable
}

$dockerReady = $false
for ($attempt = 1; $attempt -le 90; $attempt++) {
    if (Test-DockerEngine) {
        $dockerReady = $true
        break
    }
    Start-Sleep -Seconds 5
}

if (-not $dockerReady) {
    throw "Docker Engine did not become ready within 7.5 minutes."
}

Push-Location $deployDirectory
try {
    $composeArguments = @("compose", "--env-file", ".env", "-f", "docker-compose.windows.yml", "up", "-d", "--remove-orphans")
    if ($Build) {
        $composeArguments += "--build"
    }

    Write-StackLog ("Starting SNAD stack{0}." -f $(if ($Build) { " with image rebuild" } else { "" }))
    & docker @composeArguments 2>&1 | Tee-Object -FilePath $logFile -Append
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$healthy = $false
for ($attempt = 1; $attempt -le 60; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 5
        if ($health.status -eq "UP") {
            $healthy = $true
            break
        }
    }
    catch {
        # The application may still be applying Flyway migrations.
    }
    Start-Sleep -Seconds 5
}

if (-not $healthy) {
    Push-Location $deployDirectory
    try {
        & docker compose --env-file .env -f docker-compose.windows.yml ps 2>&1 | Tee-Object -FilePath $logFile -Append
        & docker compose --env-file .env -f docker-compose.windows.yml logs --tail 120 backend 2>&1 | Tee-Object -FilePath $logFile -Append
    }
    finally {
        Pop-Location
    }
    throw "SNAD backend did not report UP within five minutes. Review $logFile"
}

Write-StackLog "SNAD backend is UP and the Cloudflare connector is managed by Docker restart policy."
