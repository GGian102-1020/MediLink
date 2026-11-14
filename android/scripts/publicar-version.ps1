#requires -Version 5.1
param()
$ErrorActionPreference = 'Stop'

function Assert-Ok([string]$Message) {
    if ($LASTEXITCODE -ne 0) { throw $Message }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $repoRoot
$branch = (& git branch --show-current).Trim()
Assert-Ok 'No se pudo consultar la rama.'
if ($branch -ne 'develop-web') { throw "Estas en $branch; publica solamente desde develop-web." }
$origin = (& git remote get-url origin).Trim()
Assert-Ok 'No se pudo consultar origin.'
if ($origin -notmatch '(?i)github\.com[:/]GGian102-1020/MediLink(\.git)?$') {
    throw "origin no apunta a GGian102-1020/MediLink: $origin"
}
if (@(& git status --porcelain).Count -gt 0) { throw 'El arbol de trabajo no esta limpio. Hace commit antes.' }

& git fetch origin develop-web
Assert-Ok 'No se pudo consultar origin/develop-web.'
$localHead = (& git rev-parse HEAD).Trim()
$remoteHead = (& git rev-parse origin/develop-web).Trim()
if ($localHead -ne $remoteHead) {
    throw 'develop-web local y remoto no coinciden. Termina el push o integra cambios antes de publicar.'
}

$gradle = Get-Content -Raw (Join-Path $repoRoot 'android\app\build.gradle.kts')
$matchName = [regex]::Match($gradle, '(?m)^\s*versionName\s*=\s*"(\d+\.\d+\.\d+)"')
$matchCode = [regex]::Match($gradle, '(?m)^\s*versionCode\s*=\s*(\d+)')
if (-not $matchName.Success -or -not $matchCode.Success) {
    throw 'Defini versionName semver y versionCode numerico en android/app/build.gradle.kts.'
}
$version = $matchName.Groups[1].Value
$tag = "android-v$version"
Write-Host "Preparado para publicar $tag (versionCode $($matchCode.Groups[1].Value))."
Write-Host 'Este comando dispara GitHub Actions y crea una release PUBLICA si las pruebas pasan.'

$repo = 'GGian102-1020/MediLink'
$secretNames = @(& gh secret list -R $repo --json name --jq '.[].name')
Assert-Ok 'No se pudo consultar los secretos de GitHub. Comproba gh auth status.'
foreach ($name in @('MEDILINK_RELEASE_KEYSTORE_BASE64', 'MEDILINK_RELEASE_STORE_PASSWORD',
                    'MEDILINK_RELEASE_KEY_ALIAS', 'MEDILINK_RELEASE_KEY_PASSWORD')) {
    if ($secretNames -notcontains $name) { throw "Falta el secreto $name. Ejecuta configurar-publicacion-github.ps1." }
}
$api = (& gh variable get MEDILINK_API_BASE_URL -R $repo).Trim()
Assert-Ok 'Falta la variable MEDILINK_API_BASE_URL.'
if (-not $api.StartsWith('https://') -or -not $api.EndsWith('/')) {
    throw 'MEDILINK_API_BASE_URL debe ser HTTPS y terminar en /.'
}
try {
    $health = Invoke-WebRequest -UseBasicParsing -Uri ($api + 'api/health') -TimeoutSec 20
    if ($health.StatusCode -ne 200) { throw "HTTP $($health.StatusCode)" }
} catch { throw "La API no responde desde este equipo: $_" }
Write-Host 'OK: firma registrada y backend HTTPS accesible.'

# git ls-remote devuelve 0 incluso sin coincidencias: comprobar su salida.
$existing = & git ls-remote --tags origin "refs/tags/$tag"
Assert-Ok 'No se pudo verificar el tag.'
if ($existing) { throw "El tag $tag ya existe en origin." }
if (& git tag --list $tag) { throw "El tag $tag ya existe localmente." }

$confirmation = Read-Host 'Escribi PUBLICAR para crear y subir el tag'
if ($confirmation -cne 'PUBLICAR') { throw 'Cancelado; no se genero ningun tag.' }
& git tag -a $tag -m "MediLink Android $version"
Assert-Ok 'No se pudo crear el tag.'
& git push origin "refs/tags/$tag"
Assert-Ok 'No se pudo subir el tag. Revisar que no exista un tag remoto.'
Write-Host "Tag $tag publicado. GitHub Actions compilara, firmara y publicara la release si pasan las comprobaciones."
Write-Host "Ver ejecuciones: https://github.com/GGian102-1020/MediLink/actions"
