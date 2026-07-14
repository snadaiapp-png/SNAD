[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"
$deployDirectory = Join-Path $RepositoryRoot "deploy\self-hosted"
$logDirectory = Join-Path $RepositoryRoot "logs\self-hosted"
$logFile = Join-Path $logDirectory "watchdog.log"
$startScript = Join-Path $PSScriptRoot "start-snad-stack.ps1"

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Write-WatchdogLog {
    param([string]$Message)
    Add-Content -Path $logFile -Value ("{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message) -Encoding UTF8
}

function Test-BackendHealth {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 5
        return $health.status -eq "UP"
    }
    catch {
        return $false
    }
}

try {
    if (Test-BackendHealth) {
        exit 0
    }

    Write-WatchdogLog "Backend health check failed. Ensuring the stack is running."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $startScript -RepositoryRoot $RepositoryRoot

    if (Test-BackendHealth) {
        Write-WatchdogLog "Backend recovered after docker compose up."
        exit 0
    }

    Push-Location $deployDirectory
    try {
        Write-WatchdogLog "Backend is still unhealthy. Restarting backend and Cloudflare connector."
        & docker compose --env-file .env -f docker-compose.windows.yml restart backend cloudflared 2>&1 | Tee-Object -FilePath $logFile -Append
    }
    finally {
        Pop-Location
    }

    Start-Sleep -Seconds 45
    if (-not (Test-BackendHealth)) {
        throw "Backend remained unhealthy after restart."
    }

    Write-WatchdogLog "Backend recovered after container restart."
}
catch {
    Write-WatchdogLog ("Watchdog failure: {0}" -f $_.Exception.Message)
    exit 1
}
