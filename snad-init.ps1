# ============================================================
# SANAD Platform — Windows PowerShell bootstrap script
# ------------------------------------------------------------
# Usage:
#   .\snad-init.ps1
#
# Prerequisites:
#   - PowerShell 5.1+ (Windows 10/11 ships with 5.1)
#   - Git for Windows (https://git-scm.com/download/win)
#   - OpenJDK 21 (Temurin recommended: https://adoptium.net/)
#   - Apache Maven 3.9+ (https://maven.apache.org/download.cgi)
#   - Node.js 24.x LTS (https://nodejs.org/)
#   - Docker Desktop (https://www.docker.com/products/docker-desktop/)
#     — only required for running the production compose stack
#       or the Testcontainers-based integration tests
#
# This script:
#   1. Verifies all prerequisites are installed and on PATH.
#   2. Copies .env.example to .env if .env does not exist.
#   3. Installs backend Maven dependencies (cached).
#   4. Installs frontend npm dependencies (cached).
#   5. Runs a quick build sanity check.
#
# It does NOT start any services. Use:
#   - apps/sanad-platform: `mvn spring-boot:run` (local H2 profile)
#   - apps/web:            `npm run dev`
#   - Full stack:          `docker compose -f apps/sanad-platform/docker-compose.prod.yml --env-file .env up -d --build`
# ============================================================

# Stop on first error. PowerShell uses $ErrorActionPreference.
$ErrorActionPreference = "Stop"

function Test-Command {
    param([string]$Name)
    return [bool](Get-Command -Name $Name -ErrorAction SilentlyContinue)
}

function Assert-Version {
    param(
        [string]$Label,
        [string]$Command,
        [string]$MinVersion,
        [scriptblock]$ExtractVersion
    )
    if (-not (Test-Command $Command)) {
        Write-Error "PREREQ MISSING: $Label not found on PATH. Install $Command and re-run."
        exit 1
    }
    $raw = & $Command --version 2>&1 | Out-String
    $version = & $ExtractVersion $raw
    if ([string]::IsNullOrWhiteSpace($version)) {
        Write-Warning "Could not parse $Label version. Continuing."
        return
    }
    if ([version]$version -lt [version]$MinVersion) {
        Write-Error "$Label version $version is older than required $MinVersion. Upgrade and re-run."
        exit 1
    }
    Write-Host "OK   $Label $version"
}

Write-Host "=========================================="
Write-Host "  SANAD Platform - bootstrap (Windows)"
Write-Host "=========================================="
Write-Host ""

# --- 1. Prerequisites --------------------------------------------------
Write-Host "[1/5] Verifying prerequisites..."
Assert-Version -Label "Git"      -Command "git"  -MinVersion "2.40.0" -ExtractVersion { param($s) ($s -split "`n")[0] -replace 'git version ','' }
Assert-Version -Label "Java"     -Command "java" -MinVersion "21.0.0" -ExtractVersion { param($s) if ($s -match 'version "?(?<v>\d+(\.\d+)*)"?') { $matches['v'] } }
Assert-Version -Label "Maven"    -Command "mvn"  -MinVersion "3.9.0"  -ExtractVersion { param($s) if ($s -match 'Apache Maven (?<v>\d+(\.\d+)+)') { $matches['v'] } }
Assert-Version -Label "Node.js"  -Command "node" -MinVersion "24.0.0" -ExtractVersion { param($s) ($s -split "`n")[0] -replace 'v','' }
Assert-Version -Label "npm"      -Command "npm"  -MinVersion "10.0.0" -ExtractVersion { param($s) ($s -split "`n")[0] }
Write-Host ""

# --- 2. .env file ------------------------------------------------------
Write-Host "[2/5] Ensuring .env file exists..."
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot
if (-not (Test-Path ".env")) {
    if (Test-Path ".env.example") {
        Copy-Item ".env.example" ".env"
        Write-Host "OK   Created .env from .env.example"
        Write-Host "     Edit .env to set DATABASE_PASSWORD, JWT_SECRET, and other secrets before running."
    } else {
        Write-Warning ".env.example not found. Skipping .env creation."
    }
} else {
    Write-Host "OK   .env already exists (left untouched)"
}
Write-Host ""

# --- 3. Backend dependencies ------------------------------------------
Write-Host "[3/5] Installing backend Maven dependencies (this may take a few minutes on first run)..."
Push-Location "apps/sanad-platform"
& mvn -B -ntp -q dependency:go-offline -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven dependency resolution failed (exit $LASTEXITCODE)."
    exit 1
}
Write-Host "OK   Backend dependencies resolved"
Pop-Location
Write-Host ""

# --- 4. Frontend dependencies -----------------------------------------
Write-Host "[4/5] Installing frontend npm dependencies (this may take a few minutes on first run)..."
Push-Location "apps/web"
& npm ci --no-audit --no-fund
if ($LASTEXITCODE -ne 0) {
    Write-Error "npm ci failed (exit $LASTEXITCODE)."
    exit 1
}
Write-Host "OK   Frontend dependencies installed"
Pop-Location
Write-Host ""

# --- 5. Sanity build ---------------------------------------------------
Write-Host "[5/5] Sanity build check..."
Push-Location "apps/sanad-platform"
& mvn -B -ntp -q clean compile -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Error "Backend compile failed (exit $LASTEXITCODE)."
    exit 1
}
Pop-Location

Push-Location "apps/web"
& npm run lint 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Frontend lint reported issues (exit $LASTEXITCODE). Run 'npm run lint' to review."
} else {
    Write-Host "OK   Frontend lint clean"
}
Pop-Location

Write-Host ""
Write-Host "=========================================="
Write-Host "  SANAD bootstrap complete"
Write-Host "=========================================="
Write-Host ""
Write-Host "Next steps:"
Write-Host "  Backend (local H2):   cd apps/sanad-platform ; mvn spring-boot:run -Dspring-boot.run.profiles=local"
Write-Host "  Frontend (dev):       cd apps/web ; npm run dev"
Write-Host "  Full stack (Docker):  docker compose -f apps/sanad-platform/docker-compose.prod.yml --env-file .env up -d --build"
Write-Host ""
Write-Host "  Health check:         curl http://localhost:8080/actuator/health"
Write-Host "  Frontend:             http://localhost:3000"
Write-Host ""
