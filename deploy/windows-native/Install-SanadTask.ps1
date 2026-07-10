[CmdletBinding()]
param(
    [string]$Root = 'C:\SANAD'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script from PowerShell as Administrator.'
}

$StartScript = Join-Path $Root 'scripts\Start-Sanad.ps1'
$EnvPath = Join-Path $Root 'config\sanad.env'

if (-not (Test-Path $StartScript)) {
    throw "Missing startup script: $StartScript"
}
if (-not (Test-Path $EnvPath)) {
    throw "Missing environment file: $EnvPath"
}

# Restrict the secrets file to SYSTEM and local Administrators.
& icacls.exe $EnvPath /inheritance:r /grant:r 'SYSTEM:(R)' 'Administrators:(F)' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to protect the SANAD environment file ACL.'
}

$PowerShell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$Arguments = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$StartScript`" -Root `"$Root`""

$Action = New-ScheduledTaskAction -Execute $PowerShell -Argument $Arguments
$Trigger = New-ScheduledTaskTrigger -AtStartup
$Settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1) `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 2)

Register-ScheduledTask `
    -TaskName 'SANAD Backend' `
    -Description 'Starts the SANAD Spring Boot backend on Windows startup.' `
    -Action $Action `
    -Trigger $Trigger `
    -Settings $Settings `
    -User 'SYSTEM' `
    -RunLevel Highest `
    -Force | Out-Null

Write-Host 'SANAD scheduled task installed successfully.' -ForegroundColor Green
Write-Host 'Run manually with: Start-ScheduledTask -TaskName "SANAD Backend"'
