# ============================================================
# SANAD — Connect Local Backend to Frontend/Vercel (Windows)
# ============================================================
# Verifies the local Spring Boot backend, starts or reuses an
# ngrok HTTPS tunnel, verifies the public endpoint, configures
# the local Next.js BFF, and updates the GitHub monitoring URL
# when GitHub CLI is installed and authenticated.
# ============================================================
[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$BackendPort = 8080,

    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,

    [string]$Repository = "snadaiapp-png/SNAD",

    [switch]$SkipGitHubVariableUpdate
)

$ErrorActionPreference = "Stop"
$LocalBaseUrl = "http://127.0.0.1:$BackendPort"
$HealthPath = "/actuator/health"
$NgrokApiUrl = "http://127.0.0.1:4040/api/tunnels"
$WebEnvFile = Join-Path $RepositoryRoot "apps\web\.env.local"

function Test-SanadHealth {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [hashtable]$Headers = @{}
    )

    try {
        $response = Invoke-RestMethod `
            -Uri ($BaseUrl.TrimEnd("/") + $HealthPath) `
            -Headers $Headers `
            -TimeoutSec 20
        return $response.status -eq "UP"
    }
    catch {
        return $false
    }
}

function Get-NgrokPublicUrl {
    try {
        $tunnels = Invoke-RestMethod -Uri $NgrokApiUrl -TimeoutSec 5
        $httpsTunnel = $tunnels.tunnels |
            Where-Object { $_.public_url -like "https://*" } |
            Select-Object -First 1
        return $httpsTunnel.public_url
    }
    catch {
        return $null
    }
}

function Set-LocalWebEnvironment {
    New-Item -ItemType Directory -Force -Path (Split-Path $WebEnvFile) | Out-Null

    $preserved = @()
    if (Test-Path $WebEnvFile) {
        $preserved = Get-Content $WebEnvFile | Where-Object {
            $_ -notmatch '^BACKEND_API_BASE_URL=' -and
            $_ -notmatch '^BACKEND_REQUEST_TIMEOUT_MS=' -and
            $_ -notmatch '^NEXT_PUBLIC_API_BASE_URL='
        }
    }

    $content = @($preserved) + @(
        "BACKEND_API_BASE_URL=$LocalBaseUrl",
        "BACKEND_REQUEST_TIMEOUT_MS=15000"
    )
    Set-Content -Path $WebEnvFile -Value $content -Encoding UTF8
}

Write-Host "[1/5] Checking local SANAD backend..." -ForegroundColor Cyan
if (-not (Test-SanadHealth -BaseUrl $LocalBaseUrl)) {
    Write-Host "ERROR: Backend is not healthy at $LocalBaseUrl$HealthPath" -ForegroundColor Red
    Write-Host "Start it first with scripts\windows\start-sanad-production.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "  Local backend: UP" -ForegroundColor Green

Write-Host "[2/5] Checking ngrok tunnel..." -ForegroundColor Cyan
$PublicUrl = Get-NgrokPublicUrl
if (-not $PublicUrl) {
    $ngrok = Get-Command ngrok -ErrorAction SilentlyContinue
    if (-not $ngrok) {
        Write-Host "ERROR: ngrok is not installed or not available in PATH." -ForegroundColor Red
        Write-Host "Install ngrok, authenticate it once, then run this script again." -ForegroundColor Yellow
        exit 1
    }

    $LogDir = Join-Path $RepositoryRoot ".runtime"
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    Start-Process `
        -FilePath $ngrok.Source `
        -ArgumentList @("http", "$BackendPort", "--log", "stdout") `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $LogDir "ngrok.stdout.log") `
        -RedirectStandardError (Join-Path $LogDir "ngrok.stderr.log") | Out-Null

    for ($attempt = 1; $attempt -le 20; $attempt++) {
        Start-Sleep -Seconds 1
        $PublicUrl = Get-NgrokPublicUrl
        if ($PublicUrl) { break }
    }
}

if (-not $PublicUrl) {
    Write-Host "ERROR: ngrok did not expose an HTTPS tunnel." -ForegroundColor Red
    exit 1
}
Write-Host "  Public tunnel: $PublicUrl" -ForegroundColor Green

Write-Host "[3/5] Verifying public backend health..." -ForegroundColor Cyan
$TunnelHeaders = @{ "ngrok-skip-browser-warning" = "any-value" }
if (-not (Test-SanadHealth -BaseUrl $PublicUrl -Headers $TunnelHeaders)) {
    Write-Host "ERROR: Public tunnel cannot reach the local backend." -ForegroundColor Red
    exit 1
}
Write-Host "  Public backend: UP" -ForegroundColor Green

Write-Host "[4/5] Configuring local Next.js BFF..." -ForegroundColor Cyan
Set-LocalWebEnvironment
Write-Host "  Updated: $WebEnvFile" -ForegroundColor Green

Write-Host "[5/5] Updating GitHub monitoring target..." -ForegroundColor Cyan
if (-not $SkipGitHubVariableUpdate) {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if ($gh) {
        try {
            & $gh.Source variable set PRODUCTION_BASE_URL --body $PublicUrl --repo $Repository
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  GitHub PRODUCTION_BASE_URL updated." -ForegroundColor Green
            }
            else {
                Write-Host "  GitHub CLI could not update the variable." -ForegroundColor Yellow
            }
        }
        catch {
            Write-Host "  GitHub variable update skipped: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
    else {
        Write-Host "  GitHub CLI not found; update PRODUCTION_BASE_URL manually." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "LOCAL CONNECTION READY" -ForegroundColor Green
Write-Host "Local frontend backend URL : $LocalBaseUrl" -ForegroundColor White
Write-Host "Vercel BACKEND_API_BASE_URL: $PublicUrl" -ForegroundColor White
Write-Host "GitHub PRODUCTION_BASE_URL : $PublicUrl" -ForegroundColor White
Write-Host ""
Write-Host "Required Vercel action:" -ForegroundColor Cyan
Write-Host "Set BACKEND_API_BASE_URL to the value above for Production, then redeploy snad-app." -ForegroundColor White
