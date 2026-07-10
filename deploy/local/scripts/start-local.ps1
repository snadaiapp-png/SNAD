$ErrorActionPreference = 'Stop'

$LocalDir = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $LocalDir '.env'
$ComposeFile = Join-Path $LocalDir 'docker-compose.local.yml'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI was not found. Install and start Docker Desktop first.'
}

try {
    docker info *> $null
} catch {
    throw 'Docker Desktop is not running or the Docker engine is unavailable.'
}

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile. Copy .env.local.example to .env and fill all secrets."
}

$Placeholders = Select-String -Path $EnvFile -Pattern 'REPLACE_WITH_' -SimpleMatch:$false
if ($Placeholders) {
    $Lines = ($Placeholders | ForEach-Object { $_.LineNumber }) -join ', '
    throw "Unresolved placeholders exist in .env at line(s): $Lines"
}

Write-Host '[1/5] Validating Docker Compose configuration...'
docker compose --env-file $EnvFile -f $ComposeFile config --quiet

Write-Host '[2/5] Building the SANAD backend image...'
docker compose --env-file $EnvFile -f $ComposeFile build --pull backend

Write-Host '[3/5] Starting PostgreSQL, backend, and Cloudflare Tunnel...'
docker compose --env-file $EnvFile -f $ComposeFile up -d

Write-Host '[4/5] Waiting for the local backend health endpoint...'
$Healthy = $false
for ($Attempt = 1; $Attempt -le 60; $Attempt++) {
    try {
        $Health = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/actuator/health' -TimeoutSec 5
        if ($Health.status -eq 'UP') {
            $Healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 5
    }
}

if (-not $Healthy) {
    docker compose --env-file $EnvFile -f $ComposeFile ps
    docker compose --env-file $EnvFile -f $ComposeFile logs --tail 150 backend
    throw 'The backend did not become healthy. Review the backend logs above.'
}

Write-Host '[5/5] Verifying the Cloudflare connector...'
docker compose --env-file $EnvFile -f $ComposeFile ps
$TunnelLogs = docker compose --env-file $EnvFile -f $ComposeFile logs --tail 80 cloudflared
$TunnelLogs | Write-Host

Write-Host ''
Write-Host 'SANAD local backend is healthy at http://127.0.0.1:18080/actuator/health'
Write-Host 'Complete the Published application route in Cloudflare using Service URL: http://backend:8080'
