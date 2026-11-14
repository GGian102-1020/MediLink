#requires -Version 5.1
<#
Carga SOLO catalogo y stock ficticios, sin usuarios ni contrasenas de ejemplo.
Es idempotente, y se conecta a la base aislada medilink-demo.
#>
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env.demo'
$compose = Join-Path $repoRoot 'docker-compose.demo.yml'
$seed = Join-Path $PSScriptRoot 'catalogo-ficticio.sql'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Primero ejecuta iniciar-demo.ps1.' }
Push-Location $repoRoot
try {
    $sql = Get-Content -LiteralPath $seed -Raw -Encoding ASCII
    $sql | & docker compose --env-file $envFile -f $compose exec -T db psql -X -v ON_ERROR_STOP=1 -U medlink -d medlink
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo cargar el catalogo demo. Revisa los logs de PostgreSQL.' }
    Write-Host 'OK: farmacias, medicamentos y stock de prueba cargados en la BD aislada.'
    Write-Host 'No se crearon cuentas con claves conocidas. Podes registrar usuarios de prueba desde Android.'
} finally { Pop-Location }
