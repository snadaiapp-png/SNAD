# SANAD Render Configuration Script
# Run this entire block in PowerShell

$RENDER_API_KEY = Read-Host "Enter Render API Key"
$RENDER_SERVICE_ID = "srv-d8ragqkm0tmc73bviqq0"

$headers = @{
    "Authorization" = "Bearer $RENDER_API_KEY"
    "Content-Type" = "application/json"
    "Accept" = "application/json"
}
$baseUrl = "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars"

function Set-RenderVar($key, $value) {
    $bodyObj = @(@{key=$key; value=$value})
    $body = $bodyObj | ConvertTo-Json -Compress -Depth 5
    if ($body -notmatch '^\[') { $body = "[$body]" }
    try {
        $resp = Invoke-RestMethod -Uri $baseUrl -Method PUT -Headers $headers -Body $body
        Write-Host "  OK: $key" -ForegroundColor Green
    } catch {
        Write-Host "  FAIL: $key ($($_.Exception.Message))" -ForegroundColor Red
    }
}

Set-RenderVar "SPRING_DATASOURCE_URL" "jdbc:postgresql://aws-1-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require"
Set-RenderVar "SPRING_DATASOURCE_USERNAME" "postgres.eyoupksefusnslvdnflt"
Set-RenderVar "SPRING_DATASOURCE_DRIVER_CLASS_NAME" "org.postgresql.Driver"

Write-Host "=== Triggering Deploy ===" -ForegroundColor Yellow
$body = @{clearCache = $false} | ConvertTo-Json -Compress
try {
    $deployResp = Invoke-RestMethod -Uri "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys" -Method POST -Headers $headers -Body $body
    Write-Host "  OK: Deploy $($deployResp.id)" -ForegroundColor Green
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
}

$RENDER_API_KEY = $null
[System.GC]::Collect()
