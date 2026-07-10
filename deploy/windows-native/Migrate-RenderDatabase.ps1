[CmdletBinding()]
param(
    [string]$Root = 'C:\SANAD'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$PgBin = 'C:\Program Files\PostgreSQL\16\bin'
$PgDump = Join-Path $PgBin 'pg_dump.exe'
$PgRestore = Join-Path $PgBin 'pg_restore.exe'
$Psql = Join-Path $PgBin 'psql.exe'
$BackupDir = Join-Path $Root 'backups'

foreach ($path in @($PgDump, $PgRestore, $Psql)) {
    if (-not (Test-Path $path)) {
        throw "Missing PostgreSQL tool: $path"
    }
}

New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dumpPath = Join-Path $BackupDir "render-$timestamp.dump"

$sourceSecure = Read-Host 'Paste the Render External Database URL' -AsSecureString
$sourcePointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sourceSecure)
$localSecure = Read-Host 'Enter the local postgres administrator password' -AsSecureString
$localPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($localSecure)

$sourceUrl = $null
$localPassword = $null

try {
    $sourceUrl = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($sourcePointer)
    $localPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($localPointer)

    if ($sourceUrl -notmatch '^postgres(?:ql)?://') {
        throw 'The Render source URL must start with postgres:// or postgresql://.'
    }

    Write-Host "[1/6] Exporting Render PostgreSQL to $dumpPath"
    & $PgDump `
        --format=custom `
        --no-owner `
        --no-privileges `
        --file=$dumpPath `
        $sourceUrl

    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $dumpPath)) {
        throw 'Render database export failed.'
    }

    Write-Host '[2/6] Verifying dump structure and checksum'
    & $PgRestore --list $dumpPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'The exported dump is not readable by pg_restore.'
    }
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $dumpPath
    "$($hash.Hash)  $($hash.Path)" | Set-Content -LiteralPath "$dumpPath.sha256" -Encoding ASCII

    $confirmation = Read-Host 'Type RESTORE to replace objects in the local SANAD database'
    if ($confirmation -cne 'RESTORE') {
        Write-Host "Restore cancelled. Verified dump retained at $dumpPath"
        exit 0
    }

    $env:PGPASSWORD = $localPassword

    Write-Host '[3/6] Terminating active connections to local database'
    & $Psql -X -w -v ON_ERROR_STOP=1 -U postgres -h 127.0.0.1 -p 5432 -d postgres `
        -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='sanad' AND pid <> pg_backend_pid();"
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to terminate active SANAD database connections.'
    }

    Write-Host '[4/6] Restoring Render data into local PostgreSQL'
    & $PgRestore `
        --host=127.0.0.1 `
        --port=5432 `
        --username=postgres `
        --dbname=sanad `
        --role=sanad_app `
        --clean `
        --if-exists `
        --no-owner `
        --no-privileges `
        --exit-on-error `
        $dumpPath

    if ($LASTEXITCODE -ne 0) {
        throw 'Local database restore failed.'
    }

    Write-Host '[5/6] Restoring database and schema ownership'
    & $Psql -X -w -v ON_ERROR_STOP=1 -U postgres -h 127.0.0.1 -p 5432 -d postgres `
        -c "ALTER DATABASE sanad OWNER TO sanad_app;"
    & $Psql -X -w -v ON_ERROR_STOP=1 -U postgres -h 127.0.0.1 -p 5432 -d sanad `
        -c "ALTER SCHEMA public OWNER TO sanad_app; GRANT ALL ON SCHEMA public TO sanad_app;"
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to restore SANAD ownership after import.'
    }

    Write-Host '[6/6] Running target integrity checks'
    & $Psql -X -w -v ON_ERROR_STOP=1 -U postgres -h 127.0.0.1 -p 5432 -d sanad `
        -c "SELECT current_database(), count(*) AS public_tables FROM pg_tables WHERE schemaname='public';"
    if ($LASTEXITCODE -ne 0) {
        throw 'Post-restore integrity check failed.'
    }

    Write-Host "DATABASE MIGRATION: SUCCESS`nDump: $dumpPath" -ForegroundColor Green
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

    if ($sourcePointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($sourcePointer)
    }
    if ($localPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($localPointer)
    }

    $sourceUrl = $null
    $localPassword = $null
    $sourceSecure = $null
    $localSecure = $null
}
