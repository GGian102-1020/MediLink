#requires -Version 5.1
<#
Devuelve SOLO el puerto publicado en Windows. Nunca imprime secretos de .env.demo.
Si el archivo fue creado antes de este cambio, usa 18081 como valor heredado.
#>
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot '.env.demo'
$port = 18081
if (Test-Path -LiteralPath $envFile) {
    $found = @(Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^MEDILINK_DEMO_HOST_PORT=' })
    if ($found.Count -gt 1) { throw 'MEDILINK_DEMO_HOST_PORT esta repetido en .env.demo.' }
    if ($found.Count -eq 1) {
        $raw = ($found[0] -split '=', 2)[1].Trim()
        $parsed = 0
        if (-not [int]::TryParse($raw, [ref]$parsed) -or $parsed -lt 1024 -or $parsed -gt 65535) {
            throw 'MEDILINK_DEMO_HOST_PORT debe ser un puerto numerico entre 1024 y 65535.'
        }
        $port = $parsed
    }
}
Write-Output $port
