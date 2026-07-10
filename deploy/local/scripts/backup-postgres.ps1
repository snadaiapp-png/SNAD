param(
    [string]$BackupDirectory = "$env:USERPROFILE\SNAD-Backups",
    [int]$RetentionDays = 7
)

$ErrorActionPreference = 'Stop'
$LocalDir = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $LocalDir '.env'
$ComposeFile = Join-Path $LocalDir 'docker-compose.local.yml'
$Timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$FileName = "sanad-$Timestamp.dump"
$Destination = Join-Path $BackupDirectory $FileName

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile"
}

New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null

Write-Host "Creating PostgreSQL backup: $Destination"
docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres sh -ec 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges --file=/tmp/sanad-backup.dump'
docker cp sanad-local-postgres:/tmp/sanad-backup.dump $Destination
docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres rm -f /tmp/sanad-backup.dump

if (-not (Test-Path $Destination)) {
    throw 'Backup file was not created.'
}

$Hash = Get-FileHash -Algorithm SHA256 -Path $Destination
"$($Hash.Hash.ToLower())  $FileName" | Set-Content -Encoding ascii "$Destination.sha256"

Get-ChildItem -Path $BackupDirectory -Filter 'sanad-*.dump' |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
    ForEach-Object {
        Remove-Item -Force $_.FullName
        if (Test-Path "$($_.FullName).sha256") {
            Remove-Item -Force "$($_.FullName).sha256"
        }
    }

Write-Host "Backup completed: $Destination"
Write-Host "SHA256: $($Hash.Hash.ToLower())"
