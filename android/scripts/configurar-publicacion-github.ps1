#requires -Version 5.1
param(
    [string]$ApiBaseUrl = '',
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

# La URL es OPCIONAL para la primera descarga de la facultad.
# Si hay URL, solo se valida el formato, NO se obliga a tener un backend en linea.
if (-not [string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
    $uri = $null
    if (-not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -ne 'https' -or
        -not $ApiBaseUrl.EndsWith('/') -or
        $uri.Host -notmatch '\.' -or
        $uri.Host -match '(\.invalid|\.example|\.test|\.local|\.localhost)$' -or
        $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
        throw 'ApiBaseUrl, si se indica, debe ser HTTPS y terminar en /. Sin backend, no pases -ApiBaseUrl.'
    }
}

& gh auth status
Assert-ExternalSuccess 'Primero inicia sesion con gh auth login.'
$visibility = (& gh repo view $repo --json visibility --jq '.visibility').Trim()
Assert-ExternalSuccess 'No se pudo consultar la visibilidad del repositorio.'
if ($visibility -ne 'PUBLIC') {
    throw 'El repositorio debe ser PUBLIC para que cualquiera pueda descargar el APK sin iniciar sesion.'
}

# keytool viene con Java/JDK y normalmente con Android Studio: no se instala ngrok.
function Get-KeytoolPath {
    $tool = Get-Command 'keytool.exe' -ErrorAction SilentlyContinue
    if ($tool) { return $tool.Source }
    $roots = @($env:JAVA_HOME)
    if ($env:ProgramFiles) { $roots += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr') }
    if ($env:LOCALAPPDATA) { $roots += (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr') }
    foreach ($root in $roots) {
        if (-not $root) { continue }
        $path = Join-Path $root 'bin\keytool.exe'
        if (Test-Path -LiteralPath $path) { return $path }
    }
    throw 'No se encuentra keytool de Java. Abri Android Studio o configura JAVA_HOME apuntando a su JDK y ejecuta de nuevo.'
}
$keytool = Get-KeytoolPath

if (-not (Test-Path -LiteralPath $KeyStorePath)) {
    $parent = Split-Path -Parent $KeyStorePath
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Write-Host 'Se creara un keystore privado fuera del repositorio.'
    Write-Host 'Elegi una contrasena fuerte y GUARDALA junto con una copia de seguridad del archivo.'
    & $keytool -genkeypair -keystore $KeyStorePath -storetype PKCS12 `
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

    # Verificar la contrasena que se enviara a GitHub sin exponerla como argumento
    # de proceso. Java keytool acepta -storepass:env NOMBRE_VARIABLE.
    # En PKCS12 creado por keytool, storepass y keypass coinciden.
    $env:MEDILINK_TEMP_KEYSTORE_PASSWORD = $plain
    try {
        & $keytool -list -keystore $KeyStorePath -storetype PKCS12 -alias medilink-release `
          '-storepass:env' MEDILINK_TEMP_KEYSTORE_PASSWORD > $null
        Assert-ExternalSuccess 'La contrasena o el alias del keystore no coinciden.'
    } finally {
        Remove-Item Env:MEDILINK_TEMP_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    }

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

# Con ApiBaseUrl vacia, eliminar cualquier enlace viejo a un servidor de demo
# para que GitHub Actions compile con el destino de reserva .invalid.
$variableNames = @(& gh variable list -R $repo --json name --jq '.[].name')
Assert-ExternalSuccess 'No se pudo consultar las variables de GitHub.'
if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
    if ($variableNames -contains 'MEDILINK_API_BASE_URL') {
        & gh variable delete MEDILINK_API_BASE_URL -R $repo
        Assert-ExternalSuccess 'No se pudo quitar la API de demo anterior de GitHub.'
        Write-Host 'API anterior eliminada: el APK se publicara en modo SIN servidor.'
    }
} else {
    & gh variable set MEDILINK_API_BASE_URL -R $repo --body $ApiBaseUrl
    Assert-ExternalSuccess 'No se pudo configurar MEDILINK_API_BASE_URL.'
}
Write-Host "Firma lista. Guarda en lugar seguro el keystore: $KeyStorePath"
Write-Host 'Ya podes publicar un APK firmado para instalar desde el QR, sin ngrok ni dominio.'
Write-Host 'Las funciones online necesitaran un backend en otra etapa.'
