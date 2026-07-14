[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [switch]$RemoveDatabaseVolume,
    [switch]$RemoveEnvironmentFile
)

$ErrorActionPreference = "Stop"
$deployDirectory = Join-Path $RepositoryRoot "deploy\self-hosted"
$startupTaskName = "SNAD Stack Startup"
$watchdogTaskName = "SNAD Stack Watchdog"

Unregister-ScheduledTask -TaskName $startupTaskName -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName $watchdogTaskName -Confirm:$false -ErrorAction SilentlyContinue

Push-Location $deployDirectory
try {
    $arguments = @("compose", "--env-file", ".env", "-f", "docker-compose.windows.yml", "down", "--remove-orphans")
    if ($RemoveDatabaseVolume) {
        $arguments += "--volumes"
    }
    & docker @arguments
}
finally {
    Pop-Location
}

if ($RemoveEnvironmentFile) {
    Remove-Item (Join-Path $deployDirectory ".env") -Force -ErrorAction SilentlyContinue
}

Write-Host "SNAD automatic startup was removed."
if (-not $RemoveDatabaseVolume) {
    Write-Host "The PostgreSQL volume was preserved. Use -RemoveDatabaseVolume only when permanent data deletion is intended."
}
