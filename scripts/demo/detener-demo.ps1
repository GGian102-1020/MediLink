#requires -Version 5.1
param([switch]$BorrarDatos)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env.demo'
$compose = Join-Path $repoRoot 'docker-compose.demo.yml'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'No existe .env.demo. No hay un entorno demo configurado para detener.' }
Push-Location $repoRoot
try {
    $args = @('compose', '--env-file', $envFile, '-f', $compose, 'down')
    if ($BorrarDatos) {
        Write-Host 'ADVERTENCIA: esta opcion borra el volumen EXCLUSIVO de la demo y todos sus datos.'
        $confirm = Read-Host 'Escribi BORRAR-DEMO para confirmar'
        if ($confirm -cne 'BORRAR-DEMO') { throw 'Cancelado. No se borraron datos.' }
        $args += '-v'
    }
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw 'Docker no pudo detener la demo.' }
    Write-Host 'Demo detenida. Tambien podes cerrar ngrok con Ctrl+C en su ventana.'
    if ($BorrarDatos) { Write-Host 'Volumen demo borrado; .env.demo conservado para evitar cambios de claves imprevistos.' }
} finally { Pop-Location }
