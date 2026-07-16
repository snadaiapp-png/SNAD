# ============================================================
# SANAD — Connect Local Backend to Frontend/Vercel (Windows)
# ============================================================
# Verifies the local Spring Boot backend, starts or reuses an
# ngrok HTTPS tunnel, verifies the public endpoint, configures
# the local Next.js BFF, and updates GitHub/Vercel targets when
# their CLIs are installed and authenticated.
# ============================================================
[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$BackendPort = 8080,

    [string]$RepositoryRoot = "",

    [string]$Repository = "snadaiapp-png/SNAD",

    [string]$VercelOrgId = "team_kzO2MiiSbpoP0gWXojwUFSvR",

    [string]$VercelProjectId = "prj_WM5fbCPCycdogZQaWFnLKDgb5bA9",

    [switch]$SkipGitHubVariableUpdate,

    [switch]$SkipVercelVariableUpdate,

    [switch]$DeployVercelProduction
)

$ErrorActionPreference = "Stop"

# $PSScriptRoot is not guaranteed to be populated while default parameter
# expressions are being bound in Windows PowerShell 5.1. Resolve the repository
# root only after the script body starts.
if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
        throw "Unable to resolve the script directory. Pass -RepositoryRoot explicitly."
    }
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}
else {
    $RepositoryRoot = (Resolve-Path $RepositoryRoot).Path
}

$LocalBaseUrl = "http://127.0.0.1:$BackendPort"
$HealthPath = "/actuator/health"
$NgrokApiUrl = "http://127.0.0.1:4040/api/tunnels"
$WebDir = Join-Path $RepositoryRoot "apps\web"
$WebEnvFile = Join-Path $WebDir ".env.local"

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

function Set-VercelProductionVariable {
    param(
        [Parameter(Mandatory = $true)][System.Management.Automation.CommandInfo]$VercelCommand,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $Value | & $VercelCommand.Source env update $Name production --yes
    if ($LASTEXITCODE -eq 0) { return $true }

    # The variable may not exist yet. Add/overwrite it without an interactive
    # confirmation while keeping the value out of command-line arguments.
    $Value | & $VercelCommand.Source env add $Name production --force
    return $LASTEXITCODE -eq 0
}

Write-Host "[1/6] Checking local SANAD backend..." -ForegroundColor Cyan
if (-not (Test-SanadHealth -BaseUrl $LocalBaseUrl)) {
    Write-Host "ERROR: Backend is not healthy at $LocalBaseUrl$HealthPath" -ForegroundColor Red
    Write-Host "Start it first with scripts\windows\start-sanad-production.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "  Local backend: UP" -ForegroundColor Green

Write-Host "[2/6] Checking ngrok tunnel..." -ForegroundColor Cyan
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

Write-Host "[3/6] Verifying public backend health..." -ForegroundColor Cyan
$TunnelHeaders = @{ "ngrok-skip-browser-warning" = "any-value" }
if (-not (Test-SanadHealth -BaseUrl $PublicUrl -Headers $TunnelHeaders)) {
    Write-Host "ERROR: Public tunnel cannot reach the local backend." -ForegroundColor Red
    exit 1
}
Write-Host "  Public backend: UP" -ForegroundColor Green

Write-Host "[4/6] Configuring local Next.js BFF..." -ForegroundColor Cyan
Set-LocalWebEnvironment
Write-Host "  Updated: $WebEnvFile" -ForegroundColor Green

Write-Host "[5/6] Updating GitHub monitoring target..." -ForegroundColor Cyan
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

Write-Host "[6/6] Updating Vercel production connection..." -ForegroundColor Cyan
$VercelConfigured = $false
if (-not $SkipVercelVariableUpdate) {
    $vercel = Get-Command vercel -ErrorAction SilentlyContinue
    if ($vercel) {
        $previousOrgId = $env:VERCEL_ORG_ID
        $previousProjectId = $env:VERCEL_PROJECT_ID
        try {
            $env:VERCEL_ORG_ID = $VercelOrgId
            $env:VERCEL_PROJECT_ID = $VercelProjectId

            # The Vercel project Root Directory is already configured as
            # apps/web. Run the CLI from the repository root; running it from
            # apps/web would incorrectly resolve apps/web/apps/web.
            Push-Location $RepositoryRoot
            try {
                $baseUpdated = Set-VercelProductionVariable `
                    -VercelCommand $vercel `
                    -Name "BACKEND_API_BASE_URL" `
                    -Value $PublicUrl
                $timeoutUpdated = Set-VercelProductionVariable `
                    -VercelCommand $vercel `
                    -Name "BACKEND_REQUEST_TIMEOUT_MS" `
                    -Value "15000"

                if ($baseUpdated -and $timeoutUpdated) {
                    $VercelConfigured = $true
                    Write-Host "  Vercel production variables updated." -ForegroundColor Green

                    if ($DeployVercelProduction) {
                        Write-Host "  Deploying repository source to Vercel production..." -ForegroundColor Cyan
                        & $vercel.Source deploy --prod --yes
                        if ($LASTEXITCODE -ne 0) {
                            throw "Vercel production deployment failed."
                        }
                        Write-Host "  Vercel production deployment completed." -ForegroundColor Green
                    }
                }
                else {
                    Write-Host "  Vercel CLI could not update one or more variables." -ForegroundColor Yellow
                }
            }
            finally {
                Pop-Location
            }
        }
        catch {
            Write-Host "  Vercel update skipped: $($_.Exception.Message)" -ForegroundColor Yellow
        }
        finally {
            $env:VERCEL_ORG_ID = $previousOrgId
            $env:VERCEL_PROJECT_ID = $previousProjectId
        }
    }
    else {
        Write-Host "  Vercel CLI not found; update production variables manually." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "LOCAL CONNECTION READY" -ForegroundColor Green
Write-Host "Local frontend backend URL : $LocalBaseUrl" -ForegroundColor White
Write-Host "Public backend tunnel URL  : $PublicUrl" -ForegroundColor White
Write-Host "GitHub PRODUCTION_BASE_URL : $PublicUrl" -ForegroundColor White
Write-Host "Vercel BACKEND_API_BASE_URL: $PublicUrl" -ForegroundColor White
Write-Host "Vercel timeout             : 15000" -ForegroundColor White
Write-Host ""

if (-not $VercelConfigured) {
    Write-Host "Required Vercel action:" -ForegroundColor Cyan
    Write-Host "Set BACKEND_API_BASE_URL=$PublicUrl and BACKEND_REQUEST_TIMEOUT_MS=15000 for Production, then redeploy snad-app." -ForegroundColor White
}
elseif (-not $DeployVercelProduction) {
    Write-Host "Vercel variables are ready. Merge/push a deployment or rerun with -DeployVercelProduction." -ForegroundColor Cyan
}
