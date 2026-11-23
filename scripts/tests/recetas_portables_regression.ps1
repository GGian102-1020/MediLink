param([string]$Paquete)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $Paquete) { $Paquete = Join-Path $repo 'recetas-demo-pdf/portables' }
$fixtures = @(Get-Content -LiteralPath (Join-Path $Paquete 'recetas.json') -Raw -Encoding UTF8 | ConvertFrom-Json)
$importSql = Get-Content -LiteralPath (Join-Path $Paquete 'registrar-recetas.sql') -Raw -Encoding UTF8
$suffix = [guid]::NewGuid().ToString('N').Substring(0,10)
$testDb = 'medifind-portability-db-' + $suffix
$testApi = 'medifind-portability-api-' + $suffix
$network = ((docker inspect medlink-db | ConvertFrom-Json)[0].NetworkSettings.Networks.PSObject.Properties | Select-Object -First 1).Name
if (-not $network) { throw 'Iniciá primero los servicios locales de MediFind.' }
function SqlLiteral([string]$value) { "'" + $value.Replace("'","''") + "'" }
function DbSql([string]$sql) {
    $sql | & docker exec -i $testDb psql -v ON_ERROR_STOP=1 -U postgres -d portability -At
    if ($LASTEXITCODE -ne 0) { throw 'Falló el SQL de la prueba aislada.' }
}
try {
    & docker run --rm -d --name $testDb --network $network -e POSTGRES_DB=portability -e POSTGRES_PASSWORD=portability-test-only postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear PostgreSQL para la prueba.' }
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        & docker exec $testDb pg_isready -U postgres -d portability 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    & docker run --rm -d --name $testApi --network $network -e "DB_URL=jdbc:postgresql://${testDb}:5432/portability" `
        -e DB_USER=postgres -e DB_PASSWORD=portability-test-only -e JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= medilink-backend | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la API de prueba.' }
    $migrated=$false
    for ($attempt=0; $attempt -lt 45; $attempt++) {
        $table=DbSql "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='recuperacion_contrasena';"
        if ($table -eq '1') { $migrated=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $migrated) { throw 'La API de prueba no completó las migraciones.' }
    $catalogs=@($fixtures | Group-Object medicamentoCatalogo,concentracionCatalogo,formaFarmaceutica,unidadExpendio,cantidadEnvase | ForEach-Object { $_.Group[0] })
    $catalogSql='BEGIN;'
    $index=0
    foreach ($catalog in $catalogs) {
        $index++; $catalogId=9000+$index
        $catalogSql += "`nINSERT INTO medicamento(id_medicamento,nombre) VALUES($catalogId,$(SqlLiteral $catalog.medicamentoCatalogo));"
        $catalogSql += "`nINSERT INTO presentacion_medicamento(id_presentacion,id_medicamento,concentracion_texto,forma_farmaceutica,cantidad_envase,unidad_expendio,condicion_dispensa) VALUES($catalogId,$catalogId,$(SqlLiteral $catalog.concentracionCatalogo),$(SqlLiteral $catalog.formaFarmaceutica),$($catalog.cantidadEnvase),$(SqlLiteral $catalog.unidadExpendio),'BAJO_RECETA');"
    }
    DbSql ($catalogSql + "`nCOMMIT;") | Out-Null
    DbSql $importSql | Out-Null
    $firstCount=[int](DbSql 'SELECT count(*) FROM receta_demo;')
    $remapped=[int](DbSql 'SELECT count(*) FROM receta_demo WHERE id_presentacion>9000;')
    DbSql $importSql | Out-Null
    $secondCount=[int](DbSql 'SELECT count(*) FROM receta_demo;')
    if ($firstCount -ne $fixtures.Count -or $remapped -ne $fixtures.Count -or $secondCount -ne $fixtures.Count) { throw 'El paquete no se importó correctamente o duplicó registros.' }
    [pscustomobject]@{Recetas=$firstCount;CatalogoConOtrosIds=$true;ReimportacionSinDuplicados=$true;BaseLocalPrincipalSinCambios=$true} | ConvertTo-Json -Compress
} finally {
    # Solo estos dos contenedores efímeros de la prueba; no se tocan servicios ni volúmenes del proyecto.
    & docker stop -t 1 $testApi 2>$null | Out-Null
    & docker stop -t 1 $testDb 2>$null | Out-Null
}
