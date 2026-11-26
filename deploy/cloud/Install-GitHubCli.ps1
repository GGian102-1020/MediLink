$ErrorActionPreference='Stop'
$folder=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path '.local-cloud/tools/gh'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$release=Invoke-RestMethod 'https://api.github.com/repos/cli/cli/releases/latest'
$asset=@($release.assets | Where-Object name -match '^gh_[0-9.]+_windows_amd64\.zip$')
$checksums=@($release.assets | Where-Object name -match '^gh_[0-9.]+_checksums\.txt$')
if($asset.Count -ne 1 -or $checksums.Count -ne 1){throw 'No se identificó una distribución oficial única de GitHub CLI'}
foreach($item in @($asset[0],$checksums[0])){if($item.browser_download_url -notlike 'https://github.com/cli/cli/releases/download/*'){throw 'Origen de descarga inesperado'}}
$zip=Join-Path $folder $asset[0].name
Invoke-WebRequest $asset[0].browser_download_url -OutFile $zip
$checksumFile=Join-Path $folder $checksums[0].name
Invoke-WebRequest $checksums[0].browser_download_url -OutFile $checksumFile
$list=Get-Content -LiteralPath $checksumFile -Raw
$line=@($list -split "`n" | Where-Object {$_ -match ([regex]::Escape($asset[0].name)+'\s*$')})
if($line.Count -ne 1){throw 'No hay un checksum único para la descarga'}
$expected=($line[0] -split '\s+')[0]
if((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $expected){throw 'Checksum de GitHub CLI incorrecto'}
Expand-Archive -LiteralPath $zip -DestinationPath $folder -Force
Get-ChildItem $folder -Recurse -Filter gh.exe | Select-Object -ExpandProperty FullName
