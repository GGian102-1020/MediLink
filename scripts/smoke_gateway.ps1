# Ejecutar desde la raiz, tras docker compose --profile api up -d --build.
# Solo demo local: el certificado TLS es de la CA interna de Caddy.
param([string]$BaseUrl = 'https://localhost:8443')
$ErrorActionPreference = 'Stop'

function Invoke-DemoGet([string]$Path) {
    $result = & curl.exe -ksS --fail "$BaseUrl$Path"
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo acceder a $BaseUrl$Path (curl exit $LASTEXITCODE)"
    }
    return ($result -join "`n")
}

$api = Invoke-DemoGet '/api/medicamentos?page=0&size=1' | ConvertFrom-Json
if ($null -eq $api.PSObject.Properties['content']) {
    throw 'Respuesta API invalida: no es PageDTO'
}
Write-Host 'PASS: API devuelve PageDTO JSON'
foreach ($route in @('/', '/ingresar', '/mis-reservas', '/admin/usuarios')) {
    $page = Invoke-DemoGet $route
    if ($page -notmatch 'id="root"') {
        throw "La SPA React no esta disponible en $route"
    }
    Write-Host "PASS: $route"
}
Write-Host 'PASS: gateway HTTPS, API JSON y rutas SPA directas'
