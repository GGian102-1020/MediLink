# MediFind Cloud: mapa y captura local de documentos

Actualización del 26/11/2026 sobre `deploy/medifind-cloud`. No se migró ni restauró Neon nuevamente. OpenAI sigue deshabilitado, sin clave en Render; no se cambiaron planes ni facturación.

Publicación posterior autorizada: esta candidata se publicó como **`cloud-v1.0.0-rc.2`**, conservando rc.1 y la estable. La landing y el QR ya apuntan a rc.2. Ver [verificación final e instalación](MEDIFIND_CLOUD_RC2.md). Los párrafos de preparación siguientes registran el estado previo a esa autorización.

## Diagnóstico del mapa y actualización de datos

Android llama a `GET /api/presentaciones/{id}/disponibilidad`. `DisponibilidadDto` interpreta `latitud` y `longitud` como `Double?`; el JSON del backend ya contiene ambos campos y `distanciaKm`. No hay intercambio latitud/longitud. `MapasScreen` convierte los valores válidos a `PuntoGeo` y omite los registros sin coordenadas. El geocodificador Android es para una dirección elegida como origen por el usuario, no completa automáticamente las coordenadas de las farmacias.

El mapa conserva los filtros del negocio: farmacia activa, con operador activo, catálogo/presentación activos y stock positivo del medicamento consultado. La farmacia 5 continúa inactiva y las cuentas deshabilitadas conservan su estado. Por eso georreferenciar cinco registros no implica mostrar cinco pines simultáneos. La API pública probada para la presentación 6 devuelve actualmente San Martín como elegible.

Se consultó [Georef, servicio oficial de Argentina](https://www.argentina.gob.ar/georef) con calle/altura y provincia `02`. Todas devolvieron una coincidencia única. Los valores son geocodificaciones de la dirección oficial: no son un relevamiento GPS del acceso al local ni verifican la existencia comercial de los nombres académicos. Los siete decimales corresponden a la escala existente de PostgreSQL y no expresan precisión centimétrica.

| ID / farmacia | Dirección conservada | Latitud | Longitud | Consulta reproducible |
| --- | --- | ---: | ---: | --- |
| 1 / Central Palermo | Av. Santa Fe 3200, Palermo, CABA | -34.5890572 | -58.4100812 | [Georef](https://apis.datos.gob.ar/georef/api/direcciones?direccion=Santa%20Fe%203200&provincia=02&max=10) |
| 2 / San Martín | Av. Rivadavia 5100, Caballito, CABA | -34.6188733 | -58.4377466 | [Georef](https://apis.datos.gob.ar/georef/api/direcciones?direccion=Rivadavia%205100&provincia=02&max=10) |
| 3 / del Centro | Av. Corrientes 1200, San Nicolás, CABA | -34.6038283 | -58.3837116 | [Georef](https://apis.datos.gob.ar/georef/api/direcciones?direccion=Corrientes%201200&provincia=02&max=10) |
| 4 / Belgrano Salud | Av. Cabildo 2100, Belgrano, CABA | -34.5620358 | -58.4566933 | [Georef](https://apis.datos.gob.ar/georef/api/direcciones?direccion=Cabildo%202100&provincia=02&max=10) |
| 5 / Flores | Av. Rivadavia 7000, Flores, CABA | -34.6292517 | -58.4641120 | [Georef](https://apis.datos.gob.ar/georef/api/direcciones?direccion=Rivadavia%207000&provincia=02&max=10) |

Se informó antes de actualizar la discrepancia de Santa Fe 3200: Georef lo asigna a Comuna 2, que [corresponde a Recoleta](https://buenosaires.gob.ar/gobierno-y-vinculo-ciudadano/gestion-comunal/comunas/comuna-2), mientras el texto guardado dice Palermo. El usuario eligió expresamente usar calle y altura y conservar el texto actual. No se corrigieron nombres, direcciones ni estados.

### Respaldo y alcance de la escritura

Antes del cambio se hizo un `pg_dump` custom completo por TLS `verify-full`, con las credenciales existentes del rol de aplicación leídas privadamente. Archivo privado: `.local-cloud/backups/neon-before-geography-2026-11-26T22-13-26-763Z.dump`; 3.739.223 bytes; SHA-256 `24cb5328e52269114b3989654f38e59aa3800eadc9bcacdb4d698ad4a291298f`. Su catálogo contiene 22 tablas. Se restauró el archivo en PostgreSQL 18 temporal, sin red y con almacenamiento en memoria; restricciones validadas y triggers activos. Ese contenedor de comprobación se eliminó; el dump privado se conserva. Nunca se restauró sobre Neon ni la base local.

`update-pharmacy-geography.sql` se aplicó al proyecto `flat-shadow-76171117`, branch `br-small-fire-b6ptpcic`, base `neondb`, en una sola transacción Repeatable Read. Exige los cinco IDs/direcciones esperados, coordenadas inicialmente nulas y exactamente cinco actualizaciones. Compara conteos y huellas completas de las 22 tablas antes/después, excluyendo únicamente latitud/longitud de farmacia; aborta si cambia cualquier otro campo o relación. Resultado: 22 verificaciones aprobadas; solo diez valores geográficos cambiaron. No hubo inserts, deletes, DDL permanente, cambios de permisos ni modificación del historial Flyway.

Verificación posterior por Neon: cinco farmacias con coordenadas, cero constraints sin validar, cero triggers deshabilitados y cero migraciones fallidas. La API Render devuelve números correctos y distancias no negativas con un origen sintético de CABA.

### Android actual y navegación externa

La prueba instrumentada `CloudMapTest` se ejecutó sobre el APK público rc.1, cuya huella instalada se volvió a comparar con la publicada: `438f15a7e450a110a45fe0fc7944ad5b3465d1cefc82f2bfc7d98108f86bd29d`. Navegó como invitado por Amoxicilina, presentación, disponibilidad y Ver en mapa; inspeccionó el `MapView` real y comprobó el marcador en las coordenadas de San Martín. La captura muestra el pin y su tarjeta. Pasaron también los dos tests existentes de contratos de archivos/cámara. **Los pines se resuelven con los datos de Neon, sin APK nueva**; volver a abrir disponibilidad/mapa recarga los datos.

Se conservaron las acciones externas originales. El detalle de reserva abre `geo:0,0?q=<dirección>` y tiene fallback HTTPS de Google Maps; la app elegida por Android puede ser Waze. La pantalla de mapa usa una URL de rutas de Google Maps con coordenadas y modo caminar/conducir. No se introdujo un enlace exclusivo de Waze ni se cambió la selección de apps del usuario. El usuario ya informó que Waze funciona en su teléfono; no se declara una nueva prueba automatizada de Waze físico.

## Cámara y PDF con OpenAI desactivado

La configuración real autenticada de Render devuelve `{"escaneoHabilitado":false}`. En rc.1, tanto `MisRecetasScreen` como `ReservaConRecetaScreen` utilizan ese valor para deshabilitar el acceso a cámara/archivos; no es una ausencia del selector ni un permiso de cámara denegado. El backend también rechaza el escaneo con HTTP 503 / `ESCANEO_NO_CONFIGURADO` antes de extraer contenido o llamar al proveedor.

La cámara usa `ActivityResultContracts.TakePicture`, una URI privada de `FileProvider` con la autoridad `${applicationId}.recetas` y el archivo de tamaño completo. Se delega en la cámara instalada del sistema: [no requiere que MediFind solicite CAMERA](https://developer.android.com/privacy-and-security/minimize-permission-requests). El manifiesto no lo declara, evitando una solicitud innecesaria. El PDF usa `OpenDocument` y MIME `application/pdf`, además de JPG/PNG/WebP; [Storage Access Framework no requiere permisos generales de almacenamiento](https://developer.android.com/training/data-storage/shared/documents-files). Android 13 / One UI 5.1 soporta ambos contratos; esto no sustituye una prueba con la cámara concreta del Samsung.

Se preparó una candidata rc.2 exclusivamente en `.local-cloud/android`, mediante `android-capture-preview.mjs`. Los fuentes originales Android, la variante LAN y su APK permanecen iguales. Cambios limitados:

- Cámara y selector disponibles para preparar y revisar un documento local, aunque la configuración de análisis sea falsa o no haya respondido.
- Mensaje explícito de que preparar el adjunto no lo envía ni verifica.
- El botón de análisis y la continuación de una reserva mediante adjunto siguen deshabilitados si el servidor no habilita el escaneo; los ViewModels conservan su bloqueo adicional.
- La vinculación por código y las recetas ya vinculadas siguen funcionando con sus validaciones originales.
- Ubicación: se reutiliza un permiso aproximado ya concedido, además del preciso, en disponibilidad/mapa; no se exige una nueva solicitud de precisión para leer una posición aproximada.

La candidata usa el mismo applicationId y certificado cloud, versionCode 4 / `1.0.0-cloud.2`. Archivo privado de trabajo: `.local-cloud/artifacts/MediFind-cloud-1.0.0-rc.2-40719fe8ec0d.apk`; SHA-256 `40719fe8ec0deedac93cd34a187c13a68f317cd53bc6ae02ea9132f0229b02d7`. Certificado SHA-256 `b2ce4dc41b34fd010505939e023c53d88b99deb269eed10ea37d80d0f2e161f2`, idéntico al de rc.1. No se publicó una release, no se cambió el QR y `cloud-v1.0.0-rc.1` permanece disponible. Compilación y 40 tests unitarios aprobados; firma v2 verificada y auditoría de 211 entradas del APK sin secretos detectados.

Pruebas de la candidata aprobadas en emulador sobre ese mismo APK, con comprobación posterior de los bytes instalados:

- `CloudCaptureTest`: login real contra Render y configuración falsa; adjuntar habilitado; recepción de JPEG completo y PDF válido mediante resultados sintéticos de los contratos Android; vista previa de imagen y nombre del PDF; quitar adjunto; cancelar el selector y volver a habilitar la captura; análisis deshabilitado. También navega por Amoxicilina al paso de adjuntar receta de una reserva: cámara y selección de PDF funcionan mientras **Verificar y continuar permanece deshabilitado**, sin crear una reserva ni subir el documento. Termina con cierre de sesión. El test intercepta los resultados de cámara/documentos para evitar capturar información personal y no realiza subidas. Se comprobó además que el sistema instalado resuelve los intents a su cámara y DocumentsUI. Esto no constituye una prueba del sensor o la cámara Samsung.
- `CloudLocationTest`: permiso aproximado concedido con permiso preciso inicialmente denegado; proveedor de red simulado con una posición sintética de CABA; etiqueta de ubicación aproximada, punto azul y pin de farmacia. Al conceder precisión, se obtiene una lectura reciente de 10 m y el marcador del origen coincide con el punto sintético redondeado. No hay un nuevo diálogo al reutilizar un permiso ya concedido. El proveedor temporal y el permiso de ubicaciones simuladas se retiraron al terminar. No se obtuvo la ubicación real del usuario.
- `CloudSmokeTest`: catálogo/disponibilidad reales, autenticación, perfil, conservación de sesión al recrear actividad, navegación de reservas y logout. No creó ni canceló reservas; esas operaciones en teléfono físico fueron confirmadas por el usuario antes de esta revisión.

El Samsung indicado por el usuario es Galaxy S20 FE, Android 13, One UI 5.1. No apareció conectado en ADB ni en el inventario USB; el usuario pidió expresamente continuar sin conexión porque no podía habilitar las opciones de desarrollador. **Cámara, selector y GPS específicos del teléfono quedan pospuestos**, sin instalar ni modificar nada allí. No hay otra comprobación crítica fallida pendiente en las pruebas disponibles del emulador.

La base local se volvió a comparar en modo lectura: 21/21 tablas preservadas. Los fuentes Android/backend originales siguen idénticos a `da344f1`; la APK LAN mantiene SHA-256 `91da1ba0eb568ba204716e87e677390e1db374135e8dd149b03f78ff887a3ab6`. La configuración privada de Render se reconfirmó: un servicio Free, escaneo false y clave OpenAI ausente.

Para reproducir la candidata: configurar `MEDIFIND_CLOUD_API_URL` con el origen Render real y ejecutar `node deploy/cloud/build-android.mjs --candidate-rc2`. Este flag aplica la adaptación solo en la copia y añade los tests a `androidTest`, nunca al APK principal. `test-android-features.mjs --capture-rc2` ejecuta las tres clases en fixtures independientes del emulador; `--only=capture`, `--only=location` y `--only=smoke` permiten repetir solo una comprobación cuando cambia ese test. El script rechaza usar un teléfono físico para esos resets/permisos simulados. La prueba de mapa rc.1 se hizo antes de instalar la candidata, conservando su huella original.

## Qué implica habilitar el escaneo en el futuro

Con la autorización correspondiente, configurar una clave válida en Render y habilitar `MEDLINK_RECETAS_ESCANEO_ENABLED` haría que `/configuracion` devuelva true y los controles de análisis se habiliten al refrescar la pantalla. Solo al solicitar análisis/continuación se enviaría el documento al backend; la extracción del proveedor se contrastaría con las recetas demo y las reglas de vinculación existentes. Esto consume API externa y requiere pruebas controladas de formato, límites, errores y autorización; no equivale a admitir recetas médicas reales arbitrarias. No se realizó ese cambio ni se hizo una llamada nueva a OpenAI.

Sin consumir API se pueden probar apertura/cancelación de cámara y selector, URI privada, MIME, foto completa, importación de PDF, tamaños/formato, vista previa local, eliminación del temporal y bloqueo de envío. Los tests usan documentos sintéticos; no deben usar documentación médica personal. El emulador puede inyectar ubicaciones sintéticas para comprobar punto azul y permisos sin transmitir la ubicación real de una persona.

Evidencias privadas locales: `pharmacy-geocoding.json`, `neon-geography-backup.json`, `neon-geography-update.json`, `cloud-geography-api-tests.json`, `android-map-rc1-tests.json`, `android-rc2-unit-tests.json`, `android-rc2-feature-summary.json`, informes por clase, capturas y logs correspondientes en `.local-cloud/`. El resumen final confirma las tres clases aprobadas sobre la misma huella. El respaldo contiene los datos cloud actuales y permanece fuera de Git y de los sitios públicos.
