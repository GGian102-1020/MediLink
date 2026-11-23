param(
    [string]$Paquete,
    [string]$Repositorio = (Split-Path $PSScriptRoot -Parent),
    [string]$AndroidSdk,
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
if (-not (Test-Path (Join-Path $Repositorio 'docker-compose.yml'))) { $Repositorio = (Get-Location).Path }
if (-not (Test-Path (Join-Path $Repositorio 'docker-compose.yml'))) { throw 'Ejecutá este script desde la raíz de MediLink o indicá -Repositorio.' }
$Repositorio = (Resolve-Path $Repositorio).Path
if (-not $Paquete) { $Paquete = Join-Path $Repositorio 'recetas-demo-pdf/portables' }
$Paquete = (Resolve-Path $Paquete).Path
if ([IO.Path]::GetExtension($Paquete) -eq '.zip') {
    $expanded = Join-Path $Repositorio ('recetas-demo-pdf/portables-import-' + [guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $Paquete -DestinationPath $expanded
    $Paquete = $expanded
}
$sqlPath = Join-Path $Paquete 'registrar-recetas.sql'
$manifestPath = Join-Path $Paquete 'recetas.json'
if (-not (Test-Path $sqlPath) -or -not (Test-Path $manifestPath)) { throw 'El paquete debe contener registrar-recetas.sql y recetas.json.' }
$fixtures = @(Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json)
if ($fixtures.Count -eq 0) { throw 'El paquete no contiene recetas.' }
foreach ($fixture in $fixtures) {
    if ([IO.Path]::GetFileName($fixture.archivo) -ne $fixture.archivo -or [IO.Path]::GetExtension($fixture.archivo) -ne '.pdf') { throw 'El paquete contiene un nombre de PDF inválido.' }
    if (-not (Test-Path -LiteralPath (Join-Path $Paquete $fixture.archivo))) { throw "Falta el PDF $($fixture.archivo). No se importaron recetas." }
}
if ($fixtures | Where-Object { [datetime]$_.fechaVencimiento -lt (Get-Date).Date }) { throw 'El paquete contiene recetas vencidas. Generá otro con preparar-recetas-demo.ps1.' }
$compose = @('compose','-f',(Join-Path $Repositorio 'docker-compose.yml'),'-f',(Join-Path $Repositorio 'android/compose.dev.yml'))
Write-Output 'Registrando solamente las recetas ficticias del paquete. No se borran datos ni se reinician recetas utilizadas.'
Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 | & docker @compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) { throw 'No se importó el paquete. Revisá que la base tenga el catálogo de demostración y las migraciones aplicadas.' }
if (-not $AndroidSdk) {
    $localProperties = Join-Path $Repositorio 'android/local.properties'
    $sdkLine = if (Test-Path $localProperties) { Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1 }
    $AndroidSdk = if ($sdkLine) { $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') } else { Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
}
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
if (-not (Test-Path $adb)) { Write-Output "Recetas registradas. SDK no encontrado: los PDFs quedan en $Paquete"; return }
$devices = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device\s*$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($Serial) { $devices = @($devices | Where-Object { $_ -eq $Serial }) }
foreach ($device in $devices) {
    & $adb -s $device reverse tcp:18081 tcp:18081 | Out-Null
    $remote = '/sdcard/Download/MediFind-Recetas-Demo'
    & $adb -s $device shell mkdir -p $remote
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la carpeta de Descargas en $device" }
    foreach ($fixture in $fixtures) {
        $pdf = Join-Path $Paquete $fixture.archivo
        if (-not (Test-Path -LiteralPath $pdf)) { throw "Falta el archivo $($fixture.archivo)" }
        & $adb -s $device push $pdf ($remote + '/' + $fixture.archivo)
        if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar $($fixture.archivo) al celular." }
    }
    & $adb -s $device push (Join-Path $Paquete 'LEEME.txt') ($remote + '/LEEME.txt')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar la guía al celular.' }
    & $adb -s $device push (Join-Path $Paquete $fixtures[0].archivo) '/sdcard/Download/MediFind-Receta-Ficticia.pdf'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar la receta principal a Descargas.' }
    Write-Output "PDFs guardados en Descargas/MediFind-Recetas-Demo de $device."
}
if ($devices.Count -eq 0) { Write-Output "Recetas registradas. Conectá el celular y repetí el comando para copiar los PDFs. Paquete: $Paquete" }
