$ErrorActionPreference='Stop'
$folder=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path '.local-cloud/tools/render'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$release=Invoke-RestMethod 'https://api.github.com/repos/render-oss/cli/releases/latest'
$asset=@($release.assets | Where-Object name -match '^cli_[0-9.]+_windows_amd64\.zip$')
$checksums=@($release.assets | Where-Object name -match '^cli_[0-9.]+_SHA256SUMS$')
if($asset.Count -ne 1 -or $checksums.Count -ne 1){throw 'No se identificó una distribución oficial única de Render CLI'}
foreach($item in @($asset[0],$checksums[0])){if($item.browser_download_url -notlike 'https://github.com/render-oss/cli/releases/download/*'){throw 'Origen inesperado'}}
$zip=Join-Path $folder $asset[0].name
$checksumFile=Join-Path $folder $checksums[0].name
Invoke-WebRequest $asset[0].browser_download_url -OutFile $zip
Invoke-WebRequest $checksums[0].browser_download_url -OutFile $checksumFile
$line=@((Get-Content -LiteralPath $checksumFile) | Where-Object {$_ -match ([regex]::Escape($asset[0].name)+'\s*$')})
if($line.Count -ne 1){throw 'Checksum no identificado'}
$expected=($line[0] -split '\s+')[0]
if((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $expected){throw 'Checksum incorrecto'}
Expand-Archive -LiteralPath $zip -DestinationPath $folder -Force
Get-ChildItem -LiteralPath $folder -Recurse -Filter render.exe | Select-Object -ExpandProperty FullName
