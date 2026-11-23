# Compila un APK de MediLink que se conecta al backend corriendo en ESTA PC
# a traves de la red WiFi (sin hosting). El celular debe estar en la misma red.
#
# Uso (desde cualquier carpeta):
#   & "...\MediLink\android\scripts\build-apk-lan.ps1"
#   & "...\build-apk-lan.ps1" -Ip 192.168.1.50      # forzar una IP
#   & "...\build-apk-lan.ps1" -SinBackend           # solo compilar
param(
    [string]$Ip,
    [int]$Puerto = 18081,
    [switch]$SinBackend
)
$ErrorActionPreference = 'Stop'
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path (Join-Path $androidRoot '..')).Path

# 1) IP de la PC en la red local (la interfaz que tiene puerta de enlace).
if (-not $Ip) {
    $cfg = Get-NetIPConfiguration |
        Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
        Sort-Object { if ($_.InterfaceAlias -match 'Wi-?Fi|WLAN|Inal') { 0 } else { 1 } } |
        Select-Object -First 1
    if (-not $cfg) { throw 'No encontre una red activa. Pasa la IP con -Ip 192.168.x.x' }
    $Ip = ($cfg.IPv4Address | Select-Object -First 1).IPAddress
    Write-Host "Red: $($cfg.InterfaceAlias)  ->  IP de esta PC: $Ip" -ForegroundColor Cyan
}
$apiUrl = "http://${Ip}:${Puerto}/"

# 2) Backend en Docker, publicado en la red local.
if (-not $SinBackend) {
    Push-Location $repoRoot
    try {
        if (-not (Test-Path .\.env)) { throw 'Falta .env en la raiz de MediLink (ver .env.example).' }
        # Docker y Gradle escriben progreso en stderr: no tratarlo como error (PowerShell 5.1).
        $ErrorActionPreference = 'Continue'
        docker compose -f .\docker-compose.yml -f .\android\compose.lan.yml --profile api up -d --build 2>&1 | ForEach-Object { "$_" }
        $ErrorActionPreference = 'Stop'
        if ($LASTEXITCODE -ne 0) { throw 'docker compose fallo.' }
    } finally { Pop-Location }
    Write-Host 'Esperando al backend...'
    $ok = $false
    for ($i = 0; $i -lt 40 -and -not $ok; $i++) {
        try { Invoke-RestMethod "${apiUrl}api/health" -TimeoutSec 3 | Out-Null; $ok = $true }
        catch { Start-Sleep -Seconds 3 }
    }
    if ($ok) { Write-Host "Backend OK en ${apiUrl}api/health" -ForegroundColor Green }
    else { Write-Warning "El backend no respondio en ${apiUrl}api/health. Revisa: docker compose logs backend" }
}

# 3) Compilar la variante LAN. Gradle 8.13 soporta JDK 17-24: si JAVA_HOME es mas nuevo, buscar uno compatible.
function Get-JavaMajor([string]$home_) {
    $exe = Join-Path $home_ 'bin\java.exe'
    if (-not (Test-Path $exe)) { return 0 }
    $ErrorActionPreference = 'Continue'
    $line = & $exe -version 2>&1 | ForEach-Object { "$_" } | Select-Object -First 1
    if ($line -match '"(\d+)') { return [int]$Matches[1] } else { return 0 }
}
$javaHomeOriginal = $env:JAVA_HOME
$major = if ($env:JAVA_HOME) { Get-JavaMajor $env:JAVA_HOME } else { 0 }
if ($major -lt 17 -or $major -gt 24) {
    $candidatos = @("$env:ProgramFiles\Android\Android Studio\jbr") +
        (Get-ChildItem "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object FullName)
    $jdk = $candidatos | Where-Object { $m = Get-JavaMajor $_; $m -ge 17 -and $m -le 24 } | Select-Object -First 1
    if (-not $jdk) { throw "Gradle 8.13 necesita JDK 17-24 (JAVA_HOME actual: Java $major). Instala JDK 21." }
    Write-Host "Usando JDK compatible: $jdk" -ForegroundColor Cyan
    $env:JAVA_HOME = $jdk
}
$sdkDefault = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if (-not $env:ANDROID_HOME -and -not (Test-Path (Join-Path $androidRoot 'local.properties')) -and (Test-Path $sdkDefault)) {
    $env:ANDROID_HOME = $sdkDefault
}
Push-Location $androidRoot
try {
    $env:MEDILINK_LAN_API_URL = $apiUrl
    $ErrorActionPreference = 'Continue'
    .\gradlew.bat :app:assembleLan 2>&1 | ForEach-Object { "$_" }
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'La compilacion fallo.' }
} finally {
    Remove-Item Env:\MEDILINK_LAN_API_URL -ErrorAction SilentlyContinue
    $env:JAVA_HOME = $javaHomeOriginal
    Pop-Location
}

# 4) Copiar el APK a MediLink\dist
$apk = Get-ChildItem (Join-Path $androidRoot 'app\build\outputs\apk\lan') -Filter *.apk | Select-Object -First 1
$dist = Join-Path $repoRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$destino = Join-Path $dist 'MediLink-lan.apk'
Copy-Item $apk.FullName $destino -Force
Write-Host ''
Write-Host "APK listo: $destino" -ForegroundColor Green
Write-Host "La app usara: $apiUrl"
Write-Host ''
Write-Host 'Si el celular no conecta, habilita el puerto en el Firewall (PowerShell como administrador):'
Write-Host "  New-NetFirewallRule -DisplayName 'MediLink backend LAN' -Direction Inbound -Protocol TCP -LocalPort $Puerto -Action Allow -Profile Private"
Write-Host 'y verifica que la red WiFi de Windows este como "Privada".'
