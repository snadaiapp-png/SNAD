# ============================================================
# SANAD — Secure Control Plane Admin Password Reset (Windows)
# ============================================================
# Temporarily enables the token-gated internal bootstrap endpoint,
# rotates the Control Plane admin password and role grants, disables
# bootstrap again, restarts the backend, and verifies local login.
# No password or bootstrap token is printed or committed.
# ============================================================
[CmdletBinding()]
param(
    [string]$InstallDir = "C:\sanad-platform",
    [string]$RepositoryRoot = "C:\Users\SNADA\Desktop\SNAD",
    [string]$AdminEmail = "cp-admin@sanad-control-plane.internal"
)

$ErrorActionPreference = "Stop"
$EnvFile = Join-Path $InstallDir ".env.local"
$PidFile = Join-Path $InstallDir "sanad-backend.pid"
$StartScript = Join-Path $RepositoryRoot "scripts\windows\start-sanad-production.ps1"
$LocalBaseUrl = "http://127.0.0.1:8080"
$BootstrapTimeoutSeconds = 180

function ConvertTo-PlainText {
    param([Parameter(Mandatory = $true)][Security.SecureString]$SecureValue)

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function New-UrlSafeToken {
    param([int]$ByteCount = 48)

    $bytes = New-Object byte[] $ByteCount
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }

    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Set-EnvValues {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Values,
        [string[]]$RemoveKeys = @()
    )

    $lines = @()
    if (Test-Path $EnvFile) {
        $lines = @(Get-Content $EnvFile)
    }

    $keysToReplace = @($Values.Keys) + @($RemoveKeys)
    $escapedKeys = $keysToReplace | ForEach-Object { [Regex]::Escape($_) }
    $pattern = if ($escapedKeys.Count -gt 0) {
        '^(' + ($escapedKeys -join '|') + ')='
    }
    else {
        '^$'
    }

    $preserved = $lines | Where-Object { $_ -notmatch $pattern }
    $newLines = @($preserved)

    foreach ($key in $Values.Keys | Sort-Object) {
        $newLines += "$key=$($Values[$key])"
    }

    Set-Content -Path $EnvFile -Value $newLines -Encoding UTF8
}

function Stop-SanadBackend {
    if (-not (Test-Path $PidFile)) {
        return
    }

    $backendPid = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($backendPid -and $backendPid -match '^\d+$') {
        $process = Get-Process -Id ([int]$backendPid) -ErrorAction SilentlyContinue
        if ($process) {
            if ($process.ProcessName -notin @('java', 'javaw')) {
                throw "PID file points to unexpected process '$($process.ProcessName)'; refusing to stop it."
            }

            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(15000)
        }
    }

    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}

function Start-SanadBackend {
    if (-not (Test-Path $StartScript)) {
        throw "Backend start script not found: $StartScript"
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $StartScript
    if ($LASTEXITCODE -ne 0) {
        throw "Backend start script failed with exit code $LASTEXITCODE."
    }
}

function Wait-ForBackendHealth {
    for ($attempt = 1; $attempt -le 45; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "$LocalBaseUrl/actuator/health" -TimeoutSec 5
            if ($health.status -eq "UP") {
                return
            }
        }
        catch {
            # Retry until the bounded timeout expires.
        }
        Start-Sleep -Seconds 2
    }

    throw "Backend did not become healthy within 90 seconds."
}

function Test-AdminLogin {
    param(
        [Parameter(Mandatory = $true)][string]$Email,
        [Parameter(Mandatory = $true)][string]$Password
    )

    try {
        $loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json -Compress
        $loginResponse = Invoke-RestMethod `
            -Method Post `
            -Uri "$LocalBaseUrl/api/v1/auth/login" `
            -ContentType "application/json" `
            -Body $loginBody `
            -TimeoutSec 30

        return -not [string]::IsNullOrWhiteSpace($loginResponse.accessToken)
    }
    catch {
        return $false
    }
}

function Wait-ForAdminLogin {
    param(
        [Parameter(Mandatory = $true)][string]$Email,
        [Parameter(Mandatory = $true)][string]$Password,
        [int]$Attempts = 12
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        if (Test-AdminLogin -Email $Email -Password $Password) {
            return $true
        }
        if ($attempt -lt $Attempts) {
            Start-Sleep -Seconds 5
        }
    }

    return $false
}

function Disable-BootstrapConfiguration {
    Set-EnvValues `
        -Values @{ CONTROL_PLANE_BOOTSTRAP_ENABLED = "false" } `
        -RemoveKeys @(
            "CONTROL_PLANE_BOOTSTRAP_TOKEN",
            "CONTROL_PLANE_ADMIN_PASSWORD",
            "CONTROL_PLANE_ADMIN_DISPLAY_NAME"
        )
}

if (-not (Test-Path $EnvFile)) {
    throw "Backend environment file not found: $EnvFile"
}
if (-not (Test-Path $StartScript)) {
    throw "Repository start script not found: $StartScript"
}

$tenantLine = Get-Content $EnvFile | Where-Object { $_ -match '^SANAD_CONTROL_PLANE_TENANT_ID=.+' } | Select-Object -First 1
if (-not $tenantLine) {
    throw "SANAD_CONTROL_PLANE_TENANT_ID is missing from $EnvFile"
}

Write-Host "SANAD Control Plane administrator reset" -ForegroundColor Cyan
Write-Host "Account: $AdminEmail" -ForegroundColor White
Write-Host "The new password must contain at least 12 characters." -ForegroundColor Yellow

$passwordSecure = Read-Host "New password" -AsSecureString
$passwordConfirmSecure = Read-Host "Confirm new password" -AsSecureString
$password = ConvertTo-PlainText $passwordSecure
$passwordConfirm = ConvertTo-PlainText $passwordConfirmSecure
$bootstrapConfigurationActive = $false
$resetCompleted = $false

try {
    if ($password.Length -lt 12) {
        throw "Password must contain at least 12 characters."
    }
    if ($password -cne $passwordConfirm) {
        throw "The two password entries do not match."
    }

    $bootstrapToken = New-UrlSafeToken

    Write-Host "[1/5] Enabling one-time bootstrap..." -ForegroundColor Cyan
    Set-EnvValues -Values @{
        CONTROL_PLANE_BOOTSTRAP_ENABLED = "true"
        CONTROL_PLANE_BOOTSTRAP_TOKEN = $bootstrapToken
        CONTROL_PLANE_ADMIN_EMAIL = $AdminEmail
        CONTROL_PLANE_ADMIN_PASSWORD = $password
        CONTROL_PLANE_ADMIN_DISPLAY_NAME = "SANAD Control Plane Admin"
    }
    $bootstrapConfigurationActive = $true

    Write-Host "[2/5] Restarting backend with bootstrap enabled..." -ForegroundColor Cyan
    Stop-SanadBackend
    Start-SanadBackend
    Wait-ForBackendHealth

    Write-Host "[3/5] Rotating administrator credentials and grants..." -ForegroundColor Cyan
    $bootstrapConfirmed = $false
    try {
        $bootstrapResponse = Invoke-RestMethod `
            -Method Post `
            -Uri "$LocalBaseUrl/api/v1/internal/control-plane/bootstrap-admin" `
            -Headers @{ "X-Control-Plane-Bootstrap-Token" = $bootstrapToken } `
            -ContentType "application/json" `
            -TimeoutSec $BootstrapTimeoutSeconds

        $bootstrapConfirmed = $bootstrapResponse.status -eq "ok" -and $bootstrapResponse.bootstrap -eq "complete"
        if (-not $bootstrapConfirmed) {
            throw "Bootstrap endpoint returned an unexpected response."
        }
    }
    catch {
        Write-Host "  Bootstrap request did not return normally; verifying whether the transaction completed..." -ForegroundColor Yellow
        $bootstrapConfirmed = Wait-ForAdminLogin -Email $AdminEmail -Password $password -Attempts 12
        if (-not $bootstrapConfirmed) {
            throw "Bootstrap could not be confirmed after the request failure: $($_.Exception.Message)"
        }
        Write-Host "  Credential rotation completed despite the HTTP timeout." -ForegroundColor Green
    }

    Write-Host "[4/5] Disabling bootstrap and removing temporary secrets..." -ForegroundColor Cyan
    Disable-BootstrapConfiguration
    Stop-SanadBackend
    Start-SanadBackend
    Wait-ForBackendHealth
    $bootstrapConfigurationActive = $false

    Write-Host "[5/5] Verifying login locally..." -ForegroundColor Cyan
    if (-not (Wait-ForAdminLogin -Email $AdminEmail -Password $password -Attempts 6)) {
        throw "Login verification did not return an access token."
    }

    $resetCompleted = $true
    Write-Host ""
    Write-Host "CONTROL PLANE ADMIN RESET COMPLETE" -ForegroundColor Green
    Write-Host "Email: $AdminEmail" -ForegroundColor White
    Write-Host "Local login verification: PASS" -ForegroundColor Green
    Write-Host "Bootstrap mode: DISABLED" -ForegroundColor Green
    Write-Host "You can now sign in through https://snad-app.vercel.app" -ForegroundColor White
}
finally {
    if ($bootstrapConfigurationActive) {
        Write-Host "Cleaning up temporary bootstrap configuration after an incomplete reset..." -ForegroundColor Yellow
        try {
            Disable-BootstrapConfiguration
            Stop-SanadBackend
            Start-SanadBackend
            Wait-ForBackendHealth
            Write-Host "Bootstrap mode was disabled and temporary secrets were removed." -ForegroundColor Green
        }
        catch {
            Write-Host "CRITICAL: automatic bootstrap cleanup failed: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host "Set CONTROL_PLANE_BOOTSTRAP_ENABLED=false in $EnvFile and restart the backend immediately." -ForegroundColor Red
        }
    }

    $password = $null
    $passwordConfirm = $null
    $bootstrapToken = $null
    [GC]::Collect()
}
