[CmdletBinding()]
param(
    [string]$Root = 'C:\SANAD'
)

$ErrorActionPreference = 'Stop'
$PidPath = Join-Path $Root 'run\sanad.pid'

if (-not (Test-Path $PidPath)) {
    Write-Host 'SANAD is not running (PID file not found).'
    exit 0
}

$pidValue = (Get-Content -LiteralPath $PidPath -ErrorAction SilentlyContinue | Select-Object -First 1)
if ($pidValue -notmatch '^\d+$') {
    Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
    Write-Host 'Removed invalid SANAD PID file.'
    exit 0
}

$process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
if (-not $process) {
    Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
    Write-Host 'SANAD process was already stopped.'
    exit 0
}

Stop-Process -Id $process.Id -Force
$process.WaitForExit()
Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
Write-Host "SANAD STOPPED (PID=$($process.Id))" -ForegroundColor Yellow
