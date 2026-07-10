param(
    [string]$BackupDirectory = "$env:USERPROFILE\SNAD-Backups"
)

$ErrorActionPreference = 'Stop'
$LocalDir = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $LocalDir '.env'
$ComposeFile = Join-Path $LocalDir 'docker-compose.local.yml'
$Timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$DumpName = "render-$Timestamp.dump"
$DumpPath = Join-Path $BackupDirectory $DumpName

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile"
}

$SecureUrl = Read-Host 'Paste the Render PostgreSQL external URL' -AsSecureString
$Ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureUrl)
try {
    $SourceDatabaseUrl = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Ptr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Ptr)
}

if ($SourceDatabaseUrl -notmatch '^postgres(ql)?://') {
    throw 'The Render URL must start with postgres:// or postgresql://'
}

New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null

Write-Host "[1/7] Exporting Render PostgreSQL to $DumpPath"
docker run --rm --env "SOURCE_DATABASE_URL=$SourceDatabaseUrl" --volume "${BackupDirectory}:/backup" postgres:16-alpine sh -ec "pg_dump \"`$SOURCE_DATABASE_URL\" --format=custom --no-owner --no-privileges --file=/backup/$DumpName"
$SourceDatabaseUrl = $null

Write-Host '[2/7] Verifying the dump structure and SHA-256'
docker run --rm --volume "${BackupDirectory}:/backup:ro" postgres:16-alpine pg_restore --list "/backup/$DumpName" *> $null
$Hash = Get-FileHash -Algorithm SHA256 -Path $DumpPath
"$($Hash.Hash.ToLower())  $DumpName" | Set-Content -Encoding ascii "$DumpPath.sha256"

$Confirmation = Read-Host 'Type RESTORE to replace the local target database objects'
if ($Confirmation -ne 'RESTORE') {
    Write-Host "Restore cancelled. Verified dump retained at $DumpPath"
    exit 0
}

Write-Host '[3/7] Starting the local PostgreSQL target'
docker compose --env-file $EnvFile -f $ComposeFile up -d postgres

Write-Host '[4/7] Copying the dump into the PostgreSQL container'
docker cp $DumpPath sanad-local-postgres:/tmp/render-migration.dump

Write-Host '[5/7] Terminating target connections'
docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = ''$POSTGRES_DB'' AND pid <> pg_backend_pid();"'

Write-Host '[6/7] Restoring Render data into the local PostgreSQL target'
docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres sh -ec 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges --exit-on-error /tmp/render-migration.dump'
docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres rm -f /tmp/render-migration.dump

Write-Host '[7/7] Running target integrity checks'
docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user, count(*) AS public_tables FROM pg_tables WHERE schemaname = ''public'';"'

Write-Host 'Migration rehearsal completed. Do not switch Vercel production to the local endpoint until application tests pass.'
