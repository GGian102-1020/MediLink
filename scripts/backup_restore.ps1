[CmdletBinding(DefaultParameterSetName = 'Live')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Fixture')][switch]$Fixture,
    [Parameter(Mandatory, ParameterSetName = 'Live')][string]$SourceContainer,
    [Parameter(Mandatory, ParameterSetName = 'Live')][string]$Database,
    [Parameter(Mandatory, ParameterSetName = 'Live')][string]$User,
    [Parameter(Mandatory)][string]$BackupPath,
    [Parameter(Mandatory)][string]$EvidencePath,
    [ValidateRange(1, 300)][int]$ReadyTimeoutSeconds = 60
)

# Complete backup + isolated restore. No shell redirection of binary dumps.
# Only resources with names generated in this invocation are removed in finally.
$ErrorActionPreference = 'Stop'
function Invoke-Docker {
    param([string[]]$Arguments)
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker failed ($($Arguments[0])): $output" }
    return $output
}
function Wait-Postgres {
    # The official image first starts a socket-only temporary server, then stops
    # it before exec'ing the permanent server. A socket probe can pass too early.
    $timer = [Diagnostics.Stopwatch]::StartNew()
    do {
        & docker exec $SourceContainer pg_isready -h 127.0.0.1 -p 5432 -t 1 -U $User -d $Database *> $null
        if ($LASTEXITCODE -eq 0) {
            # Also verify the socket/database/authentication used by the backup.
            # Keep local socket authentication: TCP here is only the startup gate.
            $probe = & docker exec --env PGCONNECT_TIMEOUT=2 $SourceContainer psql -X -w -qAt `
                -v ON_ERROR_STOP=1 -U $User -d $Database -c 'SELECT 1;' 2>&1
            if ($LASTEXITCODE -eq 0 -and "$probe".Trim() -eq '1') {
                Write-Output "PostgreSQL ready: permanent TCP server and SQL connection verified ($SourceContainer)."
                return
            }
        }
        Start-Sleep -Milliseconds 200
    } while ($timer.Elapsed.TotalSeconds -lt $ReadyTimeoutSeconds)
    throw "PostgreSQL readiness timeout after ${ReadyTimeoutSeconds}s: permanent TCP server and SQL connection required ($SourceContainer)."
}
function Schema-Fingerprint([string]$TargetDatabase) {
    $schema = Invoke-Docker @('exec', $SourceContainer, 'pg_dump', '-U', $User, '-d', $TargetDatabase,
        '--schema-only', '--no-owner', '--no-privileges')
    # pg_dump/restore can add redundant parentheses to AND expressions in CHECKs.
    # PostgreSQL's pretty deparser canonicalizes them without discarding operators.
    $checks = Invoke-Docker @('exec', $SourceContainer, 'psql', '-X', '-qAt', '-U', $User, '-d', $TargetDatabase,
        '-c', "SELECT conrelid::regclass::text || '|' || conname || '|' || pg_get_constraintdef(oid,true) FROM pg_constraint WHERE contype='c' AND connamespace='public'::regnamespace ORDER BY conrelid::regclass::text,conname;")
    $normalized = (($schema | Where-Object {
        $_ -notmatch '^\\(un)?restrict ' -and $_ -notmatch '^\s+CONSTRAINT .* CHECK '
    }) + $checks) -join "`n"
    $script:schemas[$TargetDatabase] = $normalized
    $bytes = [Text.Encoding]::UTF8.GetBytes($normalized)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
}

$repo = Split-Path -Parent $PSScriptRoot
$backupFull = [IO.Path]::GetFullPath($BackupPath)
$evidenceFull = [IO.Path]::GetFullPath($EvidencePath)
foreach ($destination in @($backupFull, $evidenceFull)) {
    if (Test-Path -LiteralPath $destination) { throw "Refusing to overwrite existing file: $destination" }
    $parent = Split-Path -Parent $destination
    if (!(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
}
$suffix = [guid]::NewGuid().ToString('N')
$restoreDatabase = "medlink_restore_$suffix"
$remoteDump = "/tmp/medlink-$suffix.dump"
$remoteSql = "/tmp/medlink-check-$suffix.sql"
$createdContainer = $false
$createdDatabase = $false
$started = Get-Date
$script:schemas = @{}
try {
    if ($Fixture) {
        $SourceContainer = "medlink-backup-test-$suffix"
        $Database = 'medlink_backup_source'
        $User = 'postgres'
        Invoke-Docker @('run', '--detach', '--name', $SourceContainer, '--label', 'medlink.purpose=backup-test',
            '-e', "POSTGRES_DB=$Database", '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', 'postgres:16-alpine') | Out-Null
        $createdContainer = $true
    }
    Wait-Postgres
    if ($Fixture) {
        foreach ($sql in @('database/01_medlink_esquema.sql', 'database/02_datos_prueba.sql',
            'backend/src/main/resources/db/migration/V2__extension_busqueda_catalogo.sql',
            'backend/src/main/resources/db/migration/V3__indice_consulta_auditoria.sql', 'scripts/verificar_seed.sql')) {
            Invoke-Docker @('cp', (Join-Path $repo $sql), "${SourceContainer}:/tmp/setup.sql") | Out-Null
            Invoke-Docker @('exec', $SourceContainer, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                '-U', $User, '-d', $Database, '-f', '/tmp/setup.sql') | Out-Null
        }
    }
    Invoke-Docker @('cp', (Join-Path $repo 'scripts/verificar_restauracion.sql'), "${SourceContainer}:$remoteSql") | Out-Null
    $sourceRows = @(Invoke-Docker @('exec', $SourceContainer, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
        '-U', $User, '-d', $Database, '-f', $remoteSql))
    $sourceSchema = Schema-Fingerprint $Database
    Invoke-Docker @('exec', $SourceContainer, 'pg_dump', '-Fc', '-U', $User, '-d', $Database, '-f', $remoteDump) | Out-Null
    Invoke-Docker @('cp', "${SourceContainer}:$remoteDump", $backupFull) | Out-Null
    Invoke-Docker @('exec', $SourceContainer, 'createdb', '-U', $User, $restoreDatabase) | Out-Null
    $createdDatabase = $true
    Invoke-Docker @('exec', $SourceContainer, 'pg_restore', '--exit-on-error', '--single-transaction',
        '--no-owner', '--no-privileges', '-U', $User, '-d', $restoreDatabase, $remoteDump) | Out-Null
    $restoredRows = @(Invoke-Docker @('exec', $SourceContainer, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
        '-U', $User, '-d', $restoreDatabase, '-f', $remoteSql))
    $restoredSchema = Schema-Fingerprint $restoreDatabase
    if ($sourceSchema -ne $restoredSchema) {
        $difference = Compare-Object ($script:schemas[$Database] -split "`n") ($script:schemas[$restoreDatabase] -split "`n")
        throw "Restored schema differs from source schema: $($difference | Select-Object -First 20 | ConvertTo-Json -Compress)"
    }
    if (@(Compare-Object $sourceRows $restoredRows).Count -ne 0) { throw 'Restored row counts or checksums differ.' }
    $version = (Invoke-Docker @('exec', $SourceContainer, 'psql', '-X', '-At', '-U', $User, '-d', $Database,
        '-c', 'SHOW server_version;')) -join ''
    $lines = @('# Evidencia de backup/restauración', '', "Fecha: $($started.ToString('o'))",
        "PostgreSQL: $version", "Modo: $(if ($Fixture) {'fixture aislada con scripts 01/02 + V2/V3'} else {'base indicada por el operador'})",
        "Contenedor: $SourceContainer", "Origen: $Database", "Destino descartable: $restoreDatabase",
        'Readiness: servidor definitivo TCP 127.0.0.1:5432 y SELECT 1 por la conexión local de backup.',
        'Comandos: pg_dump -Fc; createdb; pg_restore --exit-on-error --single-transaction --no-owner --no-privileges.',
        "SHA256 del esquema normalizado: $sourceSchema", 'Resultado: PASS (esquema, conteos y checksums coinciden).',
        "Backup: $backupFull", '', 'Tabla | Registros | MD5 del contenido ordenado', '--- | ---: | ---') + $restoredRows
    $lines | Set-Content -LiteralPath $evidenceFull -Encoding utf8
    Write-Output "PASS: complete restore verified; $($restoredRows.Count) tables; evidence: $evidenceFull"
} finally {
    if ($createdDatabase -and !$Fixture) {
        # restoreDatabase is generated locally; never accepts a caller-provided database name.
        Invoke-Docker @('exec', $SourceContainer, 'dropdb', '-U', $User, $restoreDatabase) | Out-Null
    }
    if ($createdContainer) {
        Invoke-Docker @('rm', '-f', '-v', $SourceContainer) | Out-Null
    } elseif ($SourceContainer) {
        Invoke-Docker @('exec', $SourceContainer, 'rm', '-f', $remoteDump, $remoteSql) | Out-Null
    }
}
