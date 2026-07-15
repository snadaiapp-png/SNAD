# SANAD Platform — Stop Backend
$PidFile = "C:\sanad-platform\sanad-backend.pid"
if (Test-Path $PidFile) {
    $pid = Get-Content $PidFile
    $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
    if ($proc) {
        Stop-Process -Id $pid -Force
        Write-Host "SANAD Backend stopped (PID: $pid)" -ForegroundColor Green
    } else {
        Write-Host "Process $pid not running" -ForegroundColor Yellow
    }
    Remove-Item $PidFile -Force
} else {
    Write-Host "No PID file found — backend may not be running" -ForegroundColor Yellow
}
