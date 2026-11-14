#requires -Version 5.1
param(
    [Parameter(Mandatory = $true)]
    [string]$ApiBaseUrl,
    [string]$KeyStorePath = (Join-Path $HOME 'Documents\MediLink-private\medilink-release.p12')
)
$ErrorActionPreference = 'Stop'
$repo = 'GGian102-1020/MediLink'

function Assert-ExternalSuccess([string]$Message) {
    if ($LASTEXITCODE -ne 0) { throw $Message }
}

function Send-GhSecret([string]$Name, [string]$Value) {
    # Alimentamos stdin SIN salto de linea; la clave no aparece como argumento,
    # en el historial de comandos, ni en archivos de texto temporales.
    $exe = (Get-Command 'gh.exe' -ErrorAction Stop).Source
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $exe
    $info.Arguments = "secret set $Name -R $repo"
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.CreateNoWindow = $true
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $info
    if (-not $proc.Start()) { throw "No se pudo iniciar GitHub CLI para $Name" }
    try {
        $proc.StandardInput.Write($Value)
        $proc.StandardInput.Close()
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $proc.StandardError.ReadToEnd()
        $proc.WaitForExit()
        if ($proc.ExitCode -ne 0) { throw "Error al subir el secreto $Name. $stderr" }
    } finally {
        $proc.Dispose()
    }
    Write-Host "OK: secreto $Name configurado."
}

$uri = $null
if (-not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$uri) -or
    $uri.Scheme -ne 'https' -or
    -not $ApiBaseUrl.EndsWith('/') -or
    $uri.Host -notmatch '\.' -or
    $uri.Host -match '(\.invalid|\.example|\.test|\.local|\.localhost)$' -or
    $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw 'ApiBaseUrl debe ser HTTPS con dominio publico y terminar en / (ej.: https://api.midominio.com/).'
}

& gh auth status
Assert-ExternalSuccess 'Primero inicia sesion con gh auth login.'

# Sin un backend accesible no es util distribuir la app.
try {
    $health = Invoke-WebRequest -Uri ($ApiBaseUrl + 'api/health') -UseBasicParsing -TimeoutSec 20
    if ($health.StatusCode -ne 200) { throw "Respuesta HTTP $($health.StatusCode)" }
} catch {
    throw "La API no responde en $($ApiBaseUrl)api/health. No se configuro la publicacion: $_"
}

if (-not (Test-Path -LiteralPath $KeyStorePath)) {
    $parent = Split-Path -Parent $KeyStorePath
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Write-Host 'Se creara un keystore privado fuera del repositorio.'
    Write-Host 'Elegi una contrasena fuerte y GUARDALA junto con una copia de seguridad del archivo.'
    & keytool -genkeypair -keystore $KeyStorePath -storetype PKCS12 `
      -alias medilink-release -keyalg RSA -keysize 3072 -validity 10000 `
      -dname 'CN=MediLink, OU=Android, O=MediLink, L=Buenos Aires, ST=Buenos Aires, C=AR'
    Assert-ExternalSuccess 'Fallo keytool: comprobar Java 17 y los permisos de carpeta.'
} else {
    Write-Host "Se reutilizara el keystore existente: $KeyStorePath"
    Write-Host 'NO generes otra clave para actualizaciones: Android requiere la firma anterior.'
}

$secret = Read-Host 'Reingresa la contrasena del keystore (NO se muestra)' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    if ([string]::IsNullOrWhiteSpace($plain)) { throw 'Contrasena vacia.' }

    # En PKCS12 generado por keytool, la contrasena de la clave coincide con la del almacén.
    # Validar el contenido sin pasar la contrasena en la linea de comandos.
    # keytool vuelve a solicitarla en la consola.
    & keytool -list -keystore $KeyStorePath -storetype PKCS12 -alias medilink-release > $null
    Assert-ExternalSuccess 'No se pudo abrir el keystore. Verifica la contrasena y el alias medilink-release.'

    $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeyStorePath))
    Send-GhSecret 'MEDILINK_RELEASE_KEYSTORE_BASE64' $b64
    Send-GhSecret 'MEDILINK_RELEASE_STORE_PASSWORD' $plain
    Send-GhSecret 'MEDILINK_RELEASE_KEY_ALIAS' 'medilink-release'
    Send-GhSecret 'MEDILINK_RELEASE_KEY_PASSWORD' $plain
    $plain = $null
    $b64 = $null
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}

& gh variable set MEDILINK_API_BASE_URL -R $repo --body $ApiBaseUrl
Assert-ExternalSuccess 'No se pudo configurar MEDILINK_API_BASE_URL.'
Write-Host "Configuracion lista. Respaldar en lugar seguro el keystore: $KeyStorePath"
Write-Host 'No hagas un release hasta probar Android contra la API desplegada.'
