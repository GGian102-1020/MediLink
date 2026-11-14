# Descarga del Gradle Wrapper 8.13 desde el tag oficial de GitHub. SHA-256 publicado por Gradle.
# Ejecutar desde cualquier directorio: & "...\android\scripts\bootstrap-wrapper.ps1"
$ErrorActionPreference = 'Stop'
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$destino = Join-Path $androidRoot 'gradle\wrapper\gradle-wrapper.jar'
$shaEsperado = '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
if (-not (Test-Path $destino)) {
    # El JAR no se distribuye como archivo descargable en services.gradle.org.
    # Lo obtenemos del tag v8.13.0 del repositorio oficial de Gradle.
    $url = 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar'
    $temporal = "$destino.download"
    try {
        Invoke-WebRequest -Uri $url -OutFile $temporal -UseBasicParsing
        $shaDescarga = (Get-FileHash -Algorithm SHA256 -Path $temporal).Hash.ToLowerInvariant()
        if ($shaDescarga -ne $shaEsperado) {
            throw "SHA-256 incorrecto del wrapper descargado. No ejecutarlo. Esperado: $shaEsperado; actual: $shaDescarga"
        }
        Move-Item -LiteralPath $temporal -Destination $destino
    }
    finally {
        if (Test-Path $temporal) { Remove-Item -LiteralPath $temporal -Force }
    }
}
$shaActual = (Get-FileHash -Algorithm SHA256 -Path $destino).Hash.ToLowerInvariant()
if ($shaActual -ne $shaEsperado) {
    throw "SHA-256 incorrecto del wrapper. No ejecutarlo. Esperado: $shaEsperado; actual: $shaActual"
}
Write-Host 'Gradle Wrapper 8.13 verificado.' -ForegroundColor Green
Write-Host "Archivo: $destino"
