#requires -Version 5.1
<#
Devuelve exclusivamente la URL HTTPS publica por stdout para permitir:
$api = & .\scripts\demo\verificar-tunel.ps1
Los mensajes de diagnostico se muestran mediante Write-Host.
El agente ngrok debe estar ejecutandose en otra terminal: ngrok http <puerto-demo>
#>
$ErrorActionPreference = 'Stop'
$hostPort = [int](& (Join-Path $PSScriptRoot 'puerto-demo.ps1'))
try {
    $local = Invoke-WebRequest -Uri "http://127.0.0.1:$hostPort/api/health" -UseBasicParsing -TimeoutSec 8
    if ($local.StatusCode -ne 200 -or $local.Content -notmatch 'MediLink API funcionando') {
        throw 'Respuesta incorrecta del backend local.'
    }
} catch { throw "El backend no responde correctamente en localhost:$hostPort. Ejecuta iniciar-demo.ps1. $_" }

try { $info = Invoke-RestMethod -Uri 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 8 }
catch { throw "No se encontro ngrok local en el puerto 4040. Abre otra terminal y ejecuta: ngrok http $hostPort" }

$candidates = @($info.tunnels | Where-Object {
    $_.public_url -like 'https://*' -and
    ([string]$_.config.addr) -match ('(^|:)' + $hostPort + '(/|$)')
})
if ($candidates.Count -ne 1) {
    throw "No se encontro UN unico tunel HTTPS hacia el puerto $hostPort. Revisa ngrok http $hostPort."
}
$api = ([string]$candidates[0].public_url).TrimEnd('/') + '/'
$uri = [Uri]$api
if ($uri.Scheme -ne 'https' -or $uri.Host -notmatch '\.') {
    throw 'Ngrok no devolvio una URL HTTPS valida.'
}

try {
    # El header evita confundir con una pagina HTML de aviso del plan gratuito.
    $response = Invoke-WebRequest -Uri ($api + 'api/health') -UseBasicParsing -TimeoutSec 20 -Headers @{
        'ngrok-skip-browser-warning' = 'true'
        'User-Agent' = 'MediLink-Demo/1.0'
    }
    if ($response.StatusCode -ne 200 -or $response.Content -notmatch 'MediLink API funcionando') {
        throw 'La respuesta HTTPS no corresponde a la API de MediLink.'
    }
} catch { throw "El tunel existe pero la API no respondio correctamente desde HTTPS: $_" }

Write-Host 'OK: backend local y URL HTTPS ngrok comprobados.'
Write-Host "Endpoint de demostracion: $api"
Write-Host 'Este backend solo estara disponible mientras Docker, ngrok y tu PC sigan encendidos.'
Write-Output $api
