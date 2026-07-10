[CmdletBinding()]
param(
    [string]$Root = 'C:\SANAD',
    [int]$HealthTimeoutSeconds = 900
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$AppDir = Join-Path $Root 'app'
$ConfigDir = Join-Path $Root 'config'
$LogDir = Join-Path $Root 'logs'
$RunDir = Join-Path $Root 'run'
$JarPath = Join-Path $AppDir 'sanad-backend.jar'
$EnvPath = Join-Path $ConfigDir 'sanad.env'
$PidPath = Join-Path $RunDir 'sanad.pid'
$StdoutPath = Join-Path $LogDir 'sanad-out.log'
$StderrPath = Join-Path $LogDir 'sanad-error.log'

foreach ($directory in @($AppDir, $ConfigDir, $LogDir, $RunDir)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

if (-not (Test-Path $JarPath)) {
    throw "Missing backend JAR: $JarPath"
}
if (-not (Test-Path $EnvPath)) {
    throw "Missing environment file: $EnvPath"
}

function Import-SanadEnvironment {
    param([Parameter(Mandatory)][string]$Path)

    foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }

        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            throw "Invalid environment line: $rawLine"
        }

        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1)

        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "Invalid environment variable name: $name"
        }

        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

Import-SanadEnvironment -Path $EnvPath

$required = @(
    'DATABASE_URL',
    'DATABASE_USERNAME',
    'DATABASE_PASSWORD',
    'JWT_SECRET',
    'SANAD_CORS_ALLOWED_ORIGINS'
)

foreach ($name in $required) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable is empty: $name"
    }
    if ($value -match 'REPLACE_WITH') {
        throw "Resolve placeholder before startup: $name"
    }
}

$env:SPRING_PROFILES_ACTIVE = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'prod' }
$env:SERVER_PORT = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8080' }
$env:SERVER_ADDRESS = '127.0.0.1'
$env:DATABASE_POOL_MAX = if ($env:DATABASE_POOL_MAX) { $env:DATABASE_POOL_MAX } else { '3' }
$env:DATABASE_POOL_MIN = if ($env:DATABASE_POOL_MIN) { $env:DATABASE_POOL_MIN } else { '1' }

if ($env:DATABASE_URL -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):5432/sanad(?:\?.*)?$') {
    throw 'DATABASE_URL must target the local SANAD database on 127.0.0.1:5432.'
}

$postgresService = Get-Service -Name 'postgresql-x64-16' -ErrorAction SilentlyContinue
if (-not $postgresService) {
    throw 'PostgreSQL service postgresql-x64-16 was not found.'
}
if ($postgresService.Status -ne 'Running') {
    Start-Service -Name 'postgresql-x64-16'
    $postgresService.WaitForStatus('Running', [TimeSpan]::FromSeconds(60))
}

if (Test-Path $PidPath) {
    $existingPid = (Get-Content $PidPath -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($existingPid -match '^\d+$') {
        $existing = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
        if ($existing) {
            Write-Host "SANAD is already running with PID $existingPid."
            exit 0
        }
    }
    Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
}

$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaCommand) {
    $JavaPath = $javaCommand.Source
} else {
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        throw 'Java was not found in PATH and JAVA_HOME is empty.'
    }
    $JavaPath = Join-Path $javaHome 'bin\java.exe'
}
if (-not (Test-Path $JavaPath)) {
    throw "Java executable not found: $JavaPath"
}

$javaOptions = if ($env:SANAD_JAVA_OPTS) {
    $env:SANAD_JAVA_OPTS -split '\s+'
} else {
    @(
        '-Xms128m',
        '-Xmx768m',
        '-XX:+UseSerialGC',
        '-XX:MaxMetaspaceSize=192m',
        '-XX:MaxDirectMemorySize=64m',
        '-Xss512k',
        '-XX:TieredStopAtLevel=1',
        '-XX:+ExitOnOutOfMemoryError',
        '-Dfile.encoding=UTF-8'
    )
}

$arguments = @($javaOptions) + @('-jar', $JarPath)

$process = Start-Process `
    -FilePath $JavaPath `
    -ArgumentList $arguments `
    -WorkingDirectory $AppDir `
    -RedirectStandardOutput $StdoutPath `
    -RedirectStandardError $StderrPath `
    -WindowStyle Hidden `
    -PassThru

Set-Content -LiteralPath $PidPath -Value $process.Id -Encoding ASCII

$healthUrl = "http://127.0.0.1:$($env:SERVER_PORT)/actuator/health"
$deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5

    $running = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
    if (-not $running) {
        $tail = if (Test-Path $StderrPath) { Get-Content $StderrPath -Tail 80 } else { @() }
        Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
        throw "SANAD exited during startup.`n$($tail -join [Environment]::NewLine)"
    }

    try {
        $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 10
        if ($response.status -eq 'UP') {
            Write-Host "SANAD STARTUP: SUCCESS (PID=$($process.Id), health=UP)" -ForegroundColor Green
            exit 0
        }
    } catch {
        # Startup can take several minutes on the low-resource host.
    }
}

Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
throw "SANAD health check did not become UP within $HealthTimeoutSeconds seconds. Review $StdoutPath and $StderrPath."
