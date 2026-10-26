[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)

# Deterministic integration regression. Owns only a UUID-named disposable container.
# Pauses the official entrypoint while its temporary socket-only server is running.
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite regression output: $output" }
New-Item -ItemType Directory -Path $output -Force | Out-Null
$container = 'medlink-backup-regression-' + [guid]::NewGuid().ToString('N')
$created = $false
$results = [Collections.Generic.List[string]]::new()
function Checked-Docker {
    param([string[]]$Arguments)
    $result = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Regression Docker failed ($($Arguments[0])): $result" }
    return $result
}
try {
    Checked-Docker @('create', '--name', $container, '--label', 'medlink.purpose=backup-regression',
        '-e', 'POSTGRES_DB=medlink_backup_source', '-e', 'POSTGRES_HOST_AUTH_METHOD=trust',
        'postgres:16-alpine') | Out-Null
    $created = $true
    $gate = @'
#!/bin/sh
touch /tmp/medlink-init-paused
while [ ! -f /tmp/medlink-init-release ]; do sleep 0.1; done
'@
    $gatePath = Join-Path $output '99_pause.sh'
    [IO.File]::WriteAllText($gatePath, $gate.Replace("`r", '') + "`n")
    $files = [ordered]@{
        'database/01_medlink_esquema.sql' = '01_schema.sql'
        'database/02_datos_prueba.sql' = '02_seed.sql'
        'backend/src/main/resources/db/migration/V2__extension_busqueda_catalogo.sql' = '03_search.sql'
        'backend/src/main/resources/db/migration/V3__indice_consulta_auditoria.sql' = '04_audit.sql'
    }
    foreach ($file in $files.GetEnumerator()) {
        Checked-Docker @('cp', (Join-Path $repo $file.Key), "${container}:/docker-entrypoint-initdb.d/$($file.Value)") | Out-Null
    }
    Checked-Docker @('cp', $gatePath, "${container}:/docker-entrypoint-initdb.d/99_pause.sh") | Out-Null
    Checked-Docker @('start', $container) | Out-Null
    $paused = $false
    for ($i=0; $i -lt 120; $i++) {
        & docker exec $container test -f /tmp/medlink-init-paused *> $null
        if ($LASTEXITCODE -eq 0) { $paused=$true; break }
        Start-Sleep -Milliseconds 250
    }
    if (!$paused) { throw 'Official PostgreSQL entrypoint did not reach the temporary-server gate.' }
    Checked-Docker @('exec', $container, 'pg_isready', '-U', 'postgres', '-d', 'medlink_backup_source') | Out-Null
    & docker exec $container pg_isready -h 127.0.0.1 -p 5432 -U postgres -d medlink_backup_source *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Temporary server unexpectedly accepted TCP connections.' }
    $results.Add('PASS: old socket-only pg_isready is successful while the permanent TCP server is absent.')

    $prematureDump = Join-Path $output 'premature.dump'
    $prematureEvidence = Join-Path $output 'premature.md'
    $timedOut = $false
    try {
        & (Join-Path $repo 'scripts/backup_restore.ps1') -SourceContainer $container `
            -Database medlink_backup_source -User postgres -ReadyTimeoutSeconds 2 `
            -BackupPath $prematureDump -EvidencePath $prematureEvidence
    } catch {
        if ($_.Exception.Message -notlike '*PostgreSQL readiness timeout*') { throw }
        $timedOut = $true
    }
    if (!$timedOut -or (Test-Path -LiteralPath $prematureDump) -or (Test-Path -LiteralPath $prematureEvidence)) {
        throw 'Backup proceeded before permanent PostgreSQL startup.'
    }
    $temporaryDatabases = Checked-Docker @('exec', $container, 'psql', '-X', '-qAt', '-U', 'postgres',
        '-d', 'medlink_backup_source', '-c', "SELECT count(*) FROM pg_database WHERE datname LIKE 'medlink_restore_%';")
    if ("$temporaryDatabases".Trim() -ne '0') { throw 'Premature restore database was created.' }
    $results.Add('PASS: corrected readiness times out against the temporary server; no dump, success evidence or restore database is created.')

    # Reproduce precisely the missing-socket interval during temporary shutdown.
    Checked-Docker @('exec', '--user', 'postgres', $container, 'pg_ctl', '-D', '/var/lib/postgresql/data',
        '-m', 'fast', '-w', 'stop') | Out-Null
    $failure = & docker exec $container psql -X -w -qAt -U postgres -d medlink_backup_source -c 'SELECT 1;' 2>&1
    if ($LASTEXITCODE -eq 0 -or "$failure" -notlike '*No such file or directory*' -or "$failure" -notlike '*.s.PGSQL.5432*') {
        throw "Expected missing-socket error was not reproduced: $failure"
    }
    $results.Add('PASS: reproduced psql missing /var/run/postgresql/.s.PGSQL.5432 during temporary-server shutdown.')
    Checked-Docker @('exec', '--user', 'postgres', $container, 'pg_ctl', '-D', '/var/lib/postgresql/data',
        '-l', '/tmp/medlink-regression-postgres.log', '-o', "-c listen_addresses=''", '-w', 'start') | Out-Null
    Checked-Docker @('exec', $container, 'touch', '/tmp/medlink-init-release') | Out-Null
    & (Join-Path $repo 'scripts/backup_restore.ps1') -SourceContainer $container `
        -Database medlink_backup_source -User postgres -BackupPath (Join-Path $output 'restored.dump') `
        -EvidencePath (Join-Path $output 'restore.md')
    if (!(Test-Path -LiteralPath (Join-Path $output 'restore.md'))) { throw 'Complete restore evidence is missing.' }
    $results.Add('PASS: after final startup, real pg_dump/pg_restore and all-table schema/count/checksum verification succeed.')
    $lines = @('# PostgreSQL readiness and backup regression', '',
        "PowerShell: $($PSVersionTable.PSVersion)", "OS: $([Runtime.InteropServices.RuntimeInformation]::OSDescription)",
        "Date: $([DateTimeOffset]::Now.ToString('o'))", '') + @($results)
    $lines | Set-Content -LiteralPath (Join-Path $output 'regression.md') -Encoding utf8
    $results | Write-Output
} finally {
    if ($created) { Checked-Docker @('rm', '-f', '-v', $container) | Out-Null }
}
