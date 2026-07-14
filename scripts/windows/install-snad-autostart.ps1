[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^https://')]
    [string]$PublicBackendUrl,

    [string]$VercelOrigins = "https://snad-app.vercel.app",

    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,

    [switch]$ForceEnvironmentRegeneration
)

$ErrorActionPreference = "Stop"

$deployDirectory = Join-Path $RepositoryRoot "deploy\self-hosted"
$environmentFile = Join-Path $deployDirectory ".env"
$startScript = Join-Path $PSScriptRoot "start-snad-stack.ps1"
$watchdogScript = Join-Path $PSScriptRoot "snad-watchdog.ps1"
$startupTaskName = "SNAD Stack Startup"
$watchdogTaskName = "SNAD Stack Watchdog"
$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name

function Assert-Administrator {
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Run PowerShell as Administrator to install the scheduled tasks and protect the environment file."
    }
}

function New-RandomBase64 {
    param([int]$ByteCount)
    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function ConvertFrom-SecureStringPlainText {
    param([Security.SecureString]$SecureValue)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

Assert-Administrator

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker Desktop is required. Install it, enable WSL 2 integration, then rerun this installer."
}

if (-not (Test-Path $deployDirectory)) {
    throw "Deployment directory not found: $deployDirectory"
}
if (-not (Test-Path $startScript) -or -not (Test-Path $watchdogScript)) {
    throw "SNAD Windows runtime scripts are missing from the repository."
}

if ((Test-Path $environmentFile) -and -not $ForceEnvironmentRegeneration) {
    Write-Host "Reusing the existing protected environment file: $environmentFile"
}
else {
    $secureTunnelToken = Read-Host "Paste the Cloudflare Tunnel token (input is hidden)" -AsSecureString
    $tunnelToken = ConvertFrom-SecureStringPlainText $secureTunnelToken
    if ([string]::IsNullOrWhiteSpace($tunnelToken)) {
        throw "Cloudflare Tunnel token cannot be empty."
    }

    $postgresPassword = (New-RandomBase64 36).Replace("+", "A").Replace("/", "B").TrimEnd("=")
    $jwtSecret = New-RandomBase64 64
    $crmEncryptionKey = New-RandomBase64 32
    $publicBackendUrlNormalized = $PublicBackendUrl.TrimEnd("/")

    $environmentContent = @"
POSTGRES_DB=sanad
POSTGRES_USER=sanad
POSTGRES_PASSWORD=$postgresPassword
JWT_SECRET=$jwtSecret
CRM_CUSTOM_FIELD_ENCRYPTION_KEY=$crmEncryptionKey
CLOUDFLARE_TUNNEL_TOKEN=$tunnelToken
PUBLIC_BACKEND_URL=$publicBackendUrlNormalized
SANAD_CORS_ALLOWED_ORIGINS=$VercelOrigins
APPLICATION_BASE_URL=https://snad-app.vercel.app
COOKIE_SAME_SITE=none
COOKIE_DOMAIN=
BOOTSTRAP_ENABLED=false
SECURITY_NOTIFICATION_PROVIDER=disabled
JAVA_OPTS=-Xms256m -Xmx1024m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8
"@

    Set-Content -Path $environmentFile -Value $environmentContent -Encoding UTF8 -NoNewline

    & icacls.exe $environmentFile /inheritance:r /grant:r "$($currentIdentity):(F)" "SYSTEM:(F)" "BUILTIN\Administrators:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to protect the environment file ACL."
    }

    $tunnelToken = $null
    $secureTunnelToken.Dispose()
    Write-Host "Created and protected: $environmentFile"
}

$startupActionArguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$startScript`" -RepositoryRoot `"$RepositoryRoot`""
$startupAction = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $startupActionArguments
$startupTrigger = New-ScheduledTaskTrigger -AtLogOn -User $currentIdentity
$startupPrincipal = New-ScheduledTaskPrincipal -UserId $currentIdentity -LogonType Interactive -RunLevel Highest
$startupSettings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -RestartCount 10 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 20)

Register-ScheduledTask `
    -TaskName $startupTaskName `
    -Action $startupAction `
    -Trigger $startupTrigger `
    -Principal $startupPrincipal `
    -Settings $startupSettings `
    -Description "Starts PostgreSQL, SNAD backend, and Cloudflare Tunnel after Windows sign-in." `
    -Force | Out-Null

$watchdogActionArguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$watchdogScript`" -RepositoryRoot `"$RepositoryRoot`""
$watchdogAction = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $watchdogActionArguments
$watchdogTrigger = New-ScheduledTaskTrigger `
    -Once `
    -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes 2)
$watchdogPrincipal = New-ScheduledTaskPrincipal -UserId $currentIdentity -LogonType Interactive -RunLevel Highest
$watchdogSettings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10)

Register-ScheduledTask `
    -TaskName $watchdogTaskName `
    -Action $watchdogAction `
    -Trigger $watchdogTrigger `
    -Principal $watchdogPrincipal `
    -Settings $watchdogSettings `
    -Description "Checks SNAD backend every two minutes and restores the Docker stack when unhealthy." `
    -Force | Out-Null

Write-Host "Building and starting the persistent SNAD stack..."
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $startScript -RepositoryRoot $RepositoryRoot -Build
if ($LASTEXITCODE -ne 0) {
    throw "Initial SNAD stack startup failed. Review logs\selfhosted\startup.log"
}

try {
    $externalHealth = Invoke-RestMethod -Uri ($PublicBackendUrl.TrimEnd("/") + "/actuator/health") -TimeoutSec 15
    if ($externalHealth.status -eq "UP") {
        Write-Host "External Cloudflare endpoint is UP: $PublicBackendUrl"
    }
    else {
        Write-Warning "The public endpoint responded but did not report UP. Verify the Cloudflare public-hostname service URL is http://backend:8080."
    }
}
catch {
    Write-Warning "The local stack is installed, but the public endpoint is not reachable yet. Verify Cloudflare DNS/public-hostname configuration for $PublicBackendUrl."
}

Write-Host "Installation complete."
Write-Host "Startup task : $startupTaskName"
Write-Host "Watchdog task: $watchdogTaskName"
Write-Host "Local health  : http://127.0.0.1:8080/actuator/health"
Write-Host "Public health : $($PublicBackendUrl.TrimEnd('/'))/actuator/health"
