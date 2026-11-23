param(
    [string]$AndroidSdk,
    [string]$Avd = 'Pixel_10_Pro',
    [switch]$SinEmulador,
    [switch]$SinCompilar
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$compose = @('compose','-f',(Join-Path $repo 'docker-compose.yml'),'-f',(Join-Path $repo 'android/compose.dev.yml'),'--profile','api')
if (-not (Test-Path (Join-Path $repo '.env'))) { throw 'Falta .env. Copiá .env.example y configurá JWT_SECRET_BASE64 y las opciones de escaneo antes de iniciar.' }
& docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Output 'Iniciando Docker Desktop...'
    & docker desktop start
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar Docker Desktop.' }
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        & docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 2
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker todavía no está disponible.' }
}
$up = @('up','-d')
if (-not $SinCompilar) { $up += '--build' }
& docker @compose @up
if ($LASTEXITCODE -ne 0) { throw 'No se pudo levantar el entorno. Revisá el error de Docker.' }
$ready = $false
for ($attempt=0; $attempt -lt 30; $attempt++) {
    try {
        Invoke-RestMethod 'http://127.0.0.1:18081/api/health' -TimeoutSec 3 | Out-Null
        Invoke-RestMethod 'http://127.0.0.1:5173/api/health' -TimeoutSec 3 | Out-Null
        $ready = $true; break
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $ready) { throw 'Los servicios iniciaron pero todavía no responden. Consultá docker compose logs backend frontend.' }
Write-Output 'API, base y dashboard funcionando. Dashboard: http://127.0.0.1:5173/panel'
if (-not $AndroidSdk) {
    $localProperties = Join-Path $repo 'android/local.properties'
    $sdkLine = if (Test-Path $localProperties) { Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1 }
    $AndroidSdk = if ($sdkLine) { $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') } else { Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
}
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
if (-not (Test-Path $adb)) { Write-Output 'SDK Android no encontrado. El dashboard está listo; indicá -AndroidSdk para conectar un celular.'; return }
$connected = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device\s*$' })
if (-not $SinEmulador -and $connected.Count -eq 0) {
    $emulator = Join-Path $AndroidSdk 'emulator/emulator.exe'
    if (-not (Test-Path $emulator) -or $Avd -notin @(& $emulator -list-avds)) { throw "No se encontró el emulador $Avd. Crealo en Android Studio o conectá un celular con depuración USB." }
    Write-Output "Iniciando $Avd..."
    Start-Process -FilePath $emulator -ArgumentList '-avd',$Avd -WindowStyle Hidden
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        $connected = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device\s*$' })
        if ($connected.Count -gt 0) { break }
        Start-Sleep -Seconds 2
    }
}
foreach ($entry in $connected) {
    $serial = ($entry -split '\s+')[0]
    & $adb -s $serial reverse tcp:18081 tcp:18081
    if ($LASTEXITCODE -ne 0) { throw "No se pudo conectar $serial a la API local." }
    & $adb -s $serial shell am start -n ar.edu.uade.medilink.android.debug/ar.edu.uade.medilink.android.MainActivity
    if ($LASTEXITCODE -ne 0) { Write-Output "La app debug todavía no está instalada en $serial. Compilala e instalala desde Android Studio." }
}
if ($connected.Count -eq 0) { Write-Output 'No hay un celular conectado. Conectalo por USB y repetí el script.' }
Write-Output 'Los servicios quedan en Docker; cerrar esta consola no apaga el dashboard.'
