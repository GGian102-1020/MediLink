param(
    [string]$Api = 'http://127.0.0.1:18081',
    [ValidateRange(1,100)][int]$CantidadPorPresentacion = 10,
    [ValidateRange(1,365)][int]$VigenciaDias = 90,
    [string]$CorreoAdmin = 'admin@medlink.test',
    [securestring]$ClaveAdmin,
    [string]$Destino
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($Api -notmatch '^http://(127\.0\.0\.1|localhost):[0-9]+/?$') { throw 'Este preparador está limitado a la API local de demostración.' }
$Api = $Api.TrimEnd('/')
if (-not $Destino) { $Destino = Join-Path $repo 'recetas-demo-pdf/portables' }
if ((Test-Path $Destino) -and (Get-ChildItem -LiteralPath $Destino -File)) { throw 'El destino ya contiene archivos. Usá otro -Destino para conservar el paquete anterior.' }
New-Item -ItemType Directory -Path $Destino -Force | Out-Null
$Destino = (Resolve-Path $Destino).Path
# Credencial ficticia pública del seed; una cuenta distinta se pasa por parámetros.
if (-not $ClaveAdmin) { $ClaveAdmin = ConvertTo-SecureString 'Password123!' -AsPlainText -Force }
$credential = [pscredential]::new($CorreoAdmin,$ClaveAdmin)
$login = Invoke-RestMethod "$Api/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{correo=$CorreoAdmin;contrasena=$credential.GetNetworkCredential().Password} | ConvertTo-Json)
$headers = @{Authorization='Bearer ' + $login.accessToken}
$specs = @(
    @{nombre='Amoxicilina';concentracion='250 mg/5 ml';slug='Amoxicilina-250mg'},
    @{nombre='Levotiroxina';concentracion='100 mcg';slug='Levotiroxina-100mcg'}
)
$files = @{}
$batches = @()
try {
    foreach ($spec in $specs) {
        $medications = Invoke-RestMethod "$Api/api/medicamentos?nombre=$([uri]::EscapeDataString($spec.nombre))"
        $medication = @($medications.content | Where-Object nombre -eq $spec.nombre)
        if ($medication.Count -ne 1) { throw "No se encontró el medicamento de demo $($spec.nombre)." }
        $presentations = Invoke-RestMethod "$Api/api/medicamentos/$($medication[0].id)/presentaciones"
        $presentation = @($presentations.content | Where-Object { $_.concentracionTexto -eq $spec.concentracion -and $_.condicionDispensa -eq 'BAJO_RECETA' })
        if ($presentation.Count -ne 1) { throw "No hay una única presentación de $($spec.nombre) $($spec.concentracion)." }
        $batch = Invoke-RestMethod "$Api/api/admin/recetas-demo/lotes" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{
            presentacionId=$presentation[0].id;cantidad=$CantidadPorPresentacion;fechaVencimiento=(Get-Date).AddDays($VigenciaDias).ToString('yyyy-MM-dd')
        } | ConvertTo-Json)
        $batches += [guid]$batch.lote
        $index = 0
        foreach ($recipe in $batch.recetas) {
            $index++
            $filename = 'MediFind-' + $spec.slug + '-' + $index.ToString('00') + '.pdf'
            Invoke-WebRequest "$Api/api/admin/recetas-demo/$($recipe.id)/pdf" -Headers $headers -OutFile (Join-Path $Destino $filename)
            $files[$recipe.codigoReceta] = $filename
        }
    }
} finally { Invoke-RestMethod "$Api/api/auth/logout" -Method Post -Headers $headers | Out-Null }
$batchSql = ($batches | ForEach-Object { "'" + $_.ToString() + "'::uuid" }) -join ','
$exportSql = @"
SELECT jsonb_agg(jsonb_build_object(
 'lote',d.lote,'codigo',d.codigo,'codigoVerificacion',d.codigo_verificacion,
 'fechaEmision',d.fecha_emision,'fechaVencimiento',d.fecha_vencimiento,
 'medicamento',d.medicamento,'concentracion',d.concentracion,'presentacion',d.presentacion,
 'medicamentoCatalogo',m.nombre,'concentracionCatalogo',p.concentracion_texto,
 'formaFarmaceutica',p.forma_farmaceutica,'unidadExpendio',p.unidad_expendio,'cantidadEnvase',p.cantidad_envase
) ORDER BY d.id_receta_demo)
FROM receta_demo d JOIN presentacion_medicamento p USING(id_presentacion) JOIN medicamento m USING(id_medicamento)
WHERE d.lote IN ($batchSql);
"@
$compose = @('compose','-f',(Join-Path $repo 'docker-compose.yml'),'-f',(Join-Path $repo 'android/compose.dev.yml'))
$export = $exportSql | & docker @compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron exportar las recetas ficticias recién generadas.' }
$fixtures = @((($export -join "`n") | ConvertFrom-Json) | ForEach-Object {
    $_ | Add-Member -NotePropertyName archivo -NotePropertyValue $files[$_.codigo] -PassThru
})
$json = ConvertTo-Json -InputObject $fixtures -Depth 5
$json | Set-Content -LiteralPath (Join-Path $Destino 'recetas.json') -Encoding UTF8
$delimiter = '$fixture_' + [guid]::NewGuid().ToString('N') + '$'
$sql = @'
-- Solo plantillas ficticias; no incluye cuentas, claves, titulares ni reservas.
-- Reimportar conserva las recetas ya asignadas o utilizadas y nunca reinicia saldos.
BEGIN;
DO $import$
DECLARE r jsonb; presentation_id bigint;
BEGIN
  FOR r IN SELECT value FROM jsonb_array_elements(__DELIMITER____JSON____DELIMITER__::jsonb) LOOP
    SELECT p.id_presentacion INTO STRICT presentation_id
    FROM presentacion_medicamento p JOIN medicamento m USING(id_medicamento)
    WHERE m.nombre = r->>'medicamentoCatalogo' AND p.concentracion_texto = r->>'concentracionCatalogo'
      AND p.forma_farmaceutica = r->>'formaFarmaceutica' AND p.unidad_expendio = r->>'unidadExpendio'
      AND p.cantidad_envase = (r->>'cantidadEnvase')::integer AND p.condicion_dispensa='BAJO_RECETA'
      AND p.estado='ACTIVA' AND m.estado='ACTIVO';
    INSERT INTO receta_demo(lote,codigo,codigo_verificacion,fecha_emision,fecha_vencimiento,fecha_creacion,
      id_presentacion,medicamento,concentracion,presentacion)
    VALUES((r->>'lote')::uuid,r->>'codigo',r->>'codigoVerificacion',(r->>'fechaEmision')::date,
      (r->>'fechaVencimiento')::date,CURRENT_TIMESTAMP,presentation_id,r->>'medicamento',r->>'concentracion',r->>'presentacion')
    ON CONFLICT(codigo) DO NOTHING;
    IF NOT EXISTS(SELECT 1 FROM receta_demo d WHERE d.codigo=r->>'codigo'
      AND d.codigo_verificacion=r->>'codigoVerificacion' AND d.id_presentacion=presentation_id
      AND d.fecha_emision=(r->>'fechaEmision')::date AND d.fecha_vencimiento=(r->>'fechaVencimiento')::date
      AND d.medicamento=r->>'medicamento' AND d.concentracion=r->>'concentracion' AND d.presentacion=r->>'presentacion') THEN
      RAISE EXCEPTION 'Ya existe una receta con el mismo código y otros datos. No se modificó el registro.';
    END IF;
  END LOOP;
END $import$;
COMMIT;
'@
$sql = $sql.Replace('__DELIMITER__',$delimiter).Replace('__JSON__',$json)
$sql | Set-Content -LiteralPath (Join-Path $Destino 'registrar-recetas.sql') -Encoding UTF8
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'instalar-recetas-demo.ps1') -Destination (Join-Path $Destino 'instalar-recetas-demo.ps1')
@"
RECETAS FICTICIAS MEDIFIND - PAQUETE PARA PRUEBAS
Sin validez médica. No incluye la clave de OpenAI ni datos de pacientes.

1. En cada computadora, usar la misma versión de MediLink con Docker, el catálogo de demo y la API iniciados.
2. Configurar la clave compartida de OpenAI únicamente en el .env local de cada PC. No copiarla al celular.
3. Desde la raíz de MediLink ejecutar:
   powershell -File RUTA\instalar-recetas-demo.ps1 -Paquete RUTA_DEL_PAQUETE -Repositorio RUTA_DE_MEDILINK
   El importador registra solo estas recetas y copia los PDFs a Descargas/MediFind-Recetas-Demo.
   También acepta el archivo ZIP como -Paquete. No usa IDs de medicamentos fijos.
4. En Android, abrir Amoxicilina 250 mg/5 ml o Levotiroxina 100 mcg, Subir archivo y elegir su PDF.
5. Una receta se puede analizar otra vez con el mismo paciente. Cada una autoriza una unidad.
   Para otra prueba completa después de un retiro, elegir el siguiente PDF numerado.
   Cancelar una reserva libera la cantidad comprometida. No se permite usar la receta de otra cuenta.
6. En cada PC las recetas tienen que estar registradas en su base: compartir solo la clave OpenAI no alcanza.

Cantidad: $($fixtures.Count) PDFs. Fecha de vencimiento: $($fixtures[0].fechaVencimiento).
Los archivos persisten en Descargas aunque se cierre la app o se desconecte el celular.
Si se borra o cambia la base, volver a importar este paquete. No se borran datos anteriores.
Para preparar más archivos: scripts/preparar-recetas-demo.ps1 -Destino OTRA_CARPETA
"@ | Set-Content -LiteralPath (Join-Path $Destino 'LEEME.txt') -Encoding UTF8
$zip = Join-Path (Split-Path $Destino -Parent) ('MediFind-Recetas-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
Compress-Archive -Path (Join-Path $Destino '*') -DestinationPath $zip
Write-Output "Preparados $($fixtures.Count) PDFs ficticios. Paquete portable: $zip"
Write-Output "Para copiarlos al celular: scripts/instalar-recetas-demo.ps1 -Paquete `"$Destino`""
