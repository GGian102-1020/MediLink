#requires -Version 5.1
param(
    [ValidateRange(0,65535)][int]$Puerto = 0
)
<#
Levanta PostgreSQL + API de prueba en volumen aislado. NO inicia ngrok.
.env.demo se genera solo en el primer arranque, se excluye de Git y nunca
se sobreescribe en sus claves (el puerto puede cambiar sin tocar las claves).
#>
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env.demo'
$compose = Join-Path $repoRoot 'docker-compose.demo.yml'

function New-RandomBytes([int]$Count) {
    $bytes = New-Object byte[] $Count
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ,$bytes
}

function Get-DemoValue([string]$Name) {
    $found = @(Get-Content -LiteralPath $envFile | Where-Object { $_ -match "^$([regex]::Escape($Name))=" })
    if ($found.Count -ne 1) { throw "Falta $Name o esta repetido en .env.demo. No se inicio Docker." }
    return ($found[0] -split '=', 2)[1].Trim()
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Instala y abre Docker Desktop antes de iniciar la demo.'
}
& docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop no esta iniciado o no responde.' }
& docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Se requiere Docker Compose v2.' }

if (-not (Test-Path -LiteralPath $envFile)) {
    $password = [BitConverter]::ToString((New-RandomBytes 24)).Replace('-', '').ToLowerInvariant()
    $jwt = [Convert]::ToBase64String((New-RandomBytes 48))
    $values = @(
        '# Exclusivo para demo; NO subir a Git ni compartir.',
        "MEDILINK_DEMO_DB_PASSWORD=$password",
        "MEDILINK_DEMO_JWT_SECRET_BASE64=$jwt",
        "MEDILINK_DEMO_HOST_PORT=$(if ($Puerto -gt 0) { $Puerto } else { 18081 })"
    )
    [System.IO.File]::WriteAllLines($envFile, [string[]]$values, [System.Text.Encoding]::ASCII)
    Write-Host 'OK: .env.demo generado con credenciales aleatorias (fuera de Git).'
} else {
    Write-Host 'OK: reutilizando .env.demo. No se modificaron las credenciales.'
}

# El puerto es independiente de las claves de DB/JWT: se puede cambiar sin
# recrear el volumen ni alterar .env.demo por completo.
if ($Puerto -gt 0 -and $Puerto -lt 1024) { throw 'El puerto debe estar entre 1024 y 65535.' }
if ($Puerto -gt 0) {
    $currentPort = & (Join-Path $PSScriptRoot 'puerto-demo.ps1')
    if ([int]$currentPort -ne $Puerto) {
        $lines = @(Get-Content -LiteralPath $envFile)
        $portLines = @($lines | Where-Object { $_ -match '^MEDILINK_DEMO_HOST_PORT=' })
        if ($portLines.Count -gt 1) { throw 'MEDILINK_DEMO_HOST_PORT esta repetido en .env.demo.' }
        if ($portLines.Count -eq 1) {
            $lines = @($lines | ForEach-Object {
                if ($_ -match '^MEDILINK_DEMO_HOST_PORT=') { "MEDILINK_DEMO_HOST_PORT=$Puerto" } else { $_ }
            })
        } else {
            $lines += "MEDILINK_DEMO_HOST_PORT=$Puerto"
        }
        [System.IO.File]::WriteAllLines($envFile, [string[]]$lines, [System.Text.Encoding]::ASCII)
        Write-Host "Puerto de demo actualizado a $Puerto. Se conservaron las credenciales."
    }
}
$hostPort = [int](& (Join-Path $PSScriptRoot 'puerto-demo.ps1'))
if ($env:MEDILINK_DEMO_HOST_PORT -and $env:MEDILINK_DEMO_HOST_PORT -ne [string]$hostPort) {
    throw 'MEDILINK_DEMO_HOST_PORT ya esta definida en la terminal con otro valor. Eliminala de la sesion antes de iniciar Docker.'
}

$dbPass = Get-DemoValue 'MEDILINK_DEMO_DB_PASSWORD'
$jwtBase64 = Get-DemoValue 'MEDILINK_DEMO_JWT_SECRET_BASE64'
if ($dbPass.Length -lt 32 -or $dbPass -eq 'medlink123') {
    throw 'La contrasena de la BD demo debe tener 32 caracteres aleatorios como minimo.'
}
try { $decodedJwt = [Convert]::FromBase64String($jwtBase64) }
catch { throw 'MEDILINK_DEMO_JWT_SECRET_BASE64 no es Base64 valido.' }
if ($decodedJwt.Length -lt 32) { throw 'La clave JWT demo debe tener al menos 32 bytes.' }

Push-Location $repoRoot
try {
    Write-Host 'Iniciando base aislada y backend. El primer build puede descargar Maven/Docker.'
    & docker compose --env-file $envFile -f $compose up -d --build db backend
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose fallo en el puerto $hostPort. Si esta ocupado, intenta .\scripts\demo\iniciar-demo.ps1 -Puerto 18082 (u otro libre). No se borraron datos."
    }
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try {
            $health = Invoke-WebRequest -Uri "http://127.0.0.1:$hostPort/api/health" -UseBasicParsing -TimeoutSec 4
            if ($health.StatusCode -eq 200 -and $health.Content -match 'MediLink API funcionando') {
                $ready = $true
                break
            }
        } catch { }
        Start-Sleep -Seconds 3
    }
    if (-not $ready) {
        throw "La API no respondio correctamente en $hostPort. Verifica los logs del backend; el volumen existente NO fue borrado."
    }
    Write-Host "OK: API local http://127.0.0.1:$hostPort/api/health"
    Write-Host "Ahora, en otra ventana de PowerShell, ejecuta: ngrok http $hostPort"
    Write-Host 'Deja esa ventana abierta durante la demostracion.'
} finally { Pop-Location }
