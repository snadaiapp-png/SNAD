# SANAD Platform — Status Check
$PidFile = "C:\sanad-platform\sanad-backend.pid"
Write-Host "=== SANAD Backend Status ===" -ForegroundColor Cyan
if (Test-Path $PidFile) {
    $pid = Get-Content $PidFile
    $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Host "  Process: RUNNING (PID: $pid)" -ForegroundColor Green
    } else {
        Write-Host "  Process: NOT RUNNING (stale PID file)" -ForegroundColor Red
    }
} else {
    Write-Host "  Process: NOT RUNNING" -ForegroundColor Red
}
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 5
    Write-Host "  Health: $($health.status)" -ForegroundColor $(if ($health.status -eq "UP") {"Green"} else {"Yellow"})
    Write-Host "  Database: $($health.components.db.status)" -ForegroundColor White
} catch {
    Write-Host "  Health: UNREACHABLE" -ForegroundColor Red
}
