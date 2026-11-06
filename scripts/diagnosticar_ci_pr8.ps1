# Diagnostico reproducible de GitHub Actions para MediLink PR #8.
# PowerShell 5.1+ (Windows). No modifica Git ni la facturacion.
[CmdletBinding()]
param(
    [string]$Repository = 'GGian102-1020/MediLink',
    [long]$RunId = 42234404898,
    [switch]$ProbarFrontend,
    [switch]$ProbarBackend
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

function Ejecutar {
    param([string]$Programa, [string[]]$Argumentos)
    & $Programa @Argumentos
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo '$Programa $($Argumentos -join ' ')' (exit $LASTEXITCODE)."
    }
}

Write-Host "Repositorio: $Repository"
Write-Host "Run historico: https://github.com/$Repository/actions/runs/$RunId"
Write-Host 'Diagnostico confirmado el 2026-11-06: GitHub no inicio los jobs por bloqueo de facturacion.'
Write-Host 'La causa debe corregirse en Settings > Billing & licensing del propietario.'
Write-Host 'Un nuevo commit o un rerun NO corrigen una cuenta que sigue bloqueada.'
Write-Host ''

if (Get-Command gh -ErrorAction SilentlyContinue) {
    Write-Host 'Estado en GitHub CLI:'
    & gh run view $RunId -R $Repository
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'No se pudo consultar la ejecucion. Verificar gh auth status y permisos.'
    }
    Write-Host ''
} else {
    Write-Warning 'gh no esta disponible en PATH; se omite la consulta remota.'
}

if ($ProbarFrontend) {
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'Falta npm. Instalar Node.js 22 antes de probar el frontend.'
    }
    Write-Host 'Verificando frontend (npm ci reemplaza node_modules local)...'
    Push-Location (Join-Path $raiz 'frontend')
    try {
        Ejecutar 'npm.cmd' @('ci')
        Ejecutar 'npm.cmd' @('run', 'typecheck')
        Ejecutar 'npm.cmd' @('test')
        Ejecutar 'npm.cmd' @('run', 'build')
    } finally {
        Pop-Location
    }
    Write-Host 'Frontend: typecheck, tests y build OK.'
}

if ($ProbarBackend) {
    if ($env:MEDLINK_TEST_DB_URL) {
        throw 'MEDLINK_TEST_DB_URL esta definida. Por seguridad no se ejecutan pruebas sobre una base externa. Quitala y usa Testcontainers con Docker.'
    }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Falta Docker. Las pruebas PostgreSQL requieren Docker Desktop / Testcontainers.'
    }
    Ejecutar 'docker' @('info', '--format', '{{.ServerVersion}}')
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Falta Java 21.'
    }
    $maven = Join-Path $raiz 'backend\mvnw.cmd'
    if (-not (Test-Path $maven)) {
        throw "No se encuentra el wrapper Maven: $maven"
    }
    $schedulerAnterior = [Environment]::GetEnvironmentVariable('MEDLINK_RESERVAS_SCHEDULER_ENABLED', 'Process')
    $env:MEDLINK_RESERVAS_SCHEDULER_ENABLED = 'false'
    Write-Host 'Verificando backend con Maven y PostgreSQL efimero (Testcontainers)...'
    Push-Location (Join-Path $raiz 'backend')
    try {
        Ejecutar $maven @('-B', 'clean', 'verify')
    } finally {
        Pop-Location
        [Environment]::SetEnvironmentVariable('MEDLINK_RESERVAS_SCHEDULER_ENABLED', $schedulerAnterior, 'Process')
    }
    $python = if (Get-Command py -ErrorAction SilentlyContinue) { 'py' } elseif (Get-Command python -ErrorAction SilentlyContinue) { 'python' } else { $null }
    if (-not $python) { throw 'Falta Python 3 para validar los reportes PostgreSQL.' }
    Push-Location $raiz
    try {
        if ($python -eq 'py') { Ejecutar 'py' @('-3', 'scripts/verificar_tests_postgres.py') }
        else { Ejecutar 'python' @('scripts/verificar_tests_postgres.py') }
    } finally {
        Pop-Location
    }
    Write-Host 'Backend: Maven verify y suites PostgreSQL OK.'
}

if (-not ($ProbarFrontend -or $ProbarBackend)) {
    Write-Host 'Para ejecutar pruebas locales: .\scripts\diagnosticar_ci_pr8.ps1 -ProbarFrontend'
    Write-Host 'Con Docker Desktop activo: .\scripts\diagnosticar_ci_pr8.ps1 -ProbarBackend'
}
