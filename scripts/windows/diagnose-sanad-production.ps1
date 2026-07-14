# SANAD Platform — Diagnostics
Write-Host "=== SANAD Platform Diagnostics ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "[1] PostgreSQL Service:" -ForegroundColor Yellow
$pg = Get-Service "postgresql*" -ErrorAction SilentlyContinue
if ($pg) { Write-Host "  Status: $($pg.Status), Name: $($pg.Name)" -ForegroundColor Green }
else { Write-Host "  NOT FOUND" -ForegroundColor Red }
Write-Host ""
Write-Host "[2] Port 8080:" -ForegroundColor Yellow
$port = netstat -an | Select-String ":8080.*LISTEN"
if ($port) { Write-Host "  LISTENING" -ForegroundColor Green }
else { Write-Host "  NOT LISTENING" -ForegroundColor Red }
Write-Host ""
Write-Host "[3] Port 5432:" -ForegroundColor Yellow
$pgPort = netstat -an | Select-String ":5432.*LISTEN"
if ($pgPort) { Write-Host "  LISTENING" -ForegroundColor Green }
else { Write-Host "  NOT LISTENING" -ForegroundColor Red }
Write-Host ""
Write-Host "[4] Backend Health:" -ForegroundColor Yellow
try {
    $h = Invoke-RestMethod "http://localhost:8080/actuator/health" -TimeoutSec 5
    Write-Host "  Status: $($h.status)" -ForegroundColor Green
    Write-Host "  DB: $($h.components.db.status)" -ForegroundColor White
} catch {
    Write-Host "  UNREACHABLE: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""
Write-Host "[5] Environment:" -ForegroundColor Yellow
$envVars = @("DATABASE_URL","DATABASE_USERNAME","SPRING_PROFILES_ACTIVE","SANAD_CONTROL_PLANE_TENANT_ID")
foreach ($v in $envVars) {
    $val = (Get-Item "Env:$v" -ErrorAction SilentlyContinue).Value
    if ($val) { Write-Host "  ${v}: SET" -ForegroundColor Green }
    else { Write-Host "  ${v}: NOT SET" -ForegroundColor Red }
}
Write-Host ""
Write-Host "[6] Last 10 stderr lines:" -ForegroundColor Yellow
$errLog = "C:\sanad-platform\logs\stderr.log"
if (Test-Path $errLog) { Get-Content $errLog -Tail 10 }
else { Write-Host "  No stderr log found" -ForegroundColor Gray }
