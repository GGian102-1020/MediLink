# MediFind cloud

Estado al 26/11/2026: **copia anonimizada migrada y backend Render Free desplegado**. El usuario autorizó también Android Cloud, su release preliminar verificada y los tres sitios Pages gratuitos. Los dos paneles están publicados y probados; Android superó compilación, firma, auditoría e instalación/navegación en emulador. Se registrarán a continuación la publicación efectiva del APK, la landing y el QR. OpenAI permanece deshabilitado y sin clave desplegada.

## Referencia y recuperación

Rama de despliegue: `deploy/medifind-cloud`, creada desde `codex/medifind-v1-local`, HEAD original `da344f1643af3111404e42703dac6053c333aaa1`. El estado local probado, incluidos landing, administración, scripts y cambios pendientes, se respaldó antes de editar en `.local-demo/backups/cloud-preparation-20261126-131141/`. Incluye diff binario, inventario Git, HEAD y tar de archivos no versionados; SHA-256 del tar `5211a4db5d07d6ba693cd37fc9c44fff17d755b3cf674b515062d78b356fb1eb`.

Los 94 archivos Android y los 184 archivos backend originales siguen sin cambios. La configuración cloud se aplica mediante `deploy/cloud/backend/` al compilar su imagen y en copias de trabajo bajo `.local-cloud/`. El entorno local sigue usando el APK LAN, PostgreSQL y backend anteriores. No hay sincronización automática entre bases.

## Cuentas y arquitectura

| Servicio | Verificación actual | Función |
| --- | --- | --- |
| Neon | Organización MediFind, plan `free` reconfirmado; proyecto existente `flat-shadow-76171117`, región `aws-sa-east-1`, PostgreSQL 18; `neondb` contiene la copia anonimizada verificada | Copia PostgreSQL |
| Render | Espacio `medi's workspace`, plan Hobby; un único servicio web Free, sin tarjeta y con límite de builds USD 0 comprobados antes de crearlo | Backend Java 21 / Spring Boot |
| Cloudflare | Cuenta MediFind autorizada; tres proyectos Pages Direct Upload gratuitos creados, sin Functions ni Workers | Landing, operador y administración estáticos |
| GitHub | Repositorio `GGian102-1020/MediLink`, acceso de escritura comprobado; release estable existente `android-v0.2.0` | Fuente y APK preliminar |

Arquitectura: Android cloud y sitios Pages se conectan por HTTPS a Render; Render conecta por TLS verificado a Neon. El contenido adjunto y el estado de negocio se guardan en PostgreSQL, no en el disco efímero de Render. Las claves de OpenAI y JWT permanecen exclusivamente en el backend. No se usarán Pages Functions, Workers de pago, R2, dominios comprados ni servidores para mantener despierto Render.

Backend HTTPS real: **https://medifind-api-ggian102-mey9.onrender.com**. Health: `/api/health`. Panel operador: **https://medifind-operador-ggian102.pages.dev**. Administración: **https://medifind-admin-ggian102.pages.dev**. Proyecto de landing: **https://medifind-ggian102.pages.dev**, pendiente en este punto del enlace final al APK verificado.

## Límites y costos

Límites consultados en documentación oficial el 26/11/2026:

- [Render Free](https://render.com/docs/free): 750 horas de instancia por espacio/mes, suspensión tras 15 minutos sin solicitudes entrantes, disco efímero y suspensión al agotar horas. Con medio de pago, ciertos excesos de transferencia/build pueden facturarse; sin medio de pago se suspende el recurso/build. No añadir tarjeta ni ampliar a planes pagos. La [instancia Free](https://render.com/docs/compute-plans) tiene 0,1 CPU y 512 MB. Las cuotas adicionales del espacio deben comprobarse en Billing antes de publicar.
- [Neon Free](https://neon.com/docs/introduction/plans): 100 CU-h/proyecto/mes, 1 GB de PostgreSQL por proyecto, 5 GB de salida al mes, suspensión tras cinco minutos de inactividad. Plan gratuito confirmado en esta cuenta. Preferir compute de 0,25 CU; no activar facturación por consumo ni autoscaling pagado. El dashboard del operador abierto y sus consultas legítimas pueden consumir cuota; no se agregan pings de mantenimiento.
- [Cloudflare Pages](https://developers.cloudflare.com/pages/platform/limits/): 500 builds/mes, un build concurrente, 20.000 archivos, 25 MiB por archivo. Los activos estáticos tienen solicitudes gratuitas; no se necesita compute backend en Pages. [Los cargos de Functions/Workers](https://developers.cloudflare.com/workers/platform/pricing/) pertenecen a otros productos y se evitan en esta arquitectura.
- OpenAI tiene consumo independiente. El backend cloud limita la concurrencia a un análisis y usa un contador persistente de intentos diarios, inicialmente 20, además de los límites originales por cuenta/minuto y tamaño. El contador conserva consumos ante reinicios. No representa un tope monetario garantizado del proveedor; mantener también los controles de gasto de la cuenta OpenAI.

La persistencia de PDFs consume la cuota de Neon: revisar almacenamiento y transferencia. No borrar historiales ni documentos automáticamente para recuperar cuota. El servicio puede quedar suspendido al alcanzar límites gratuitos; no autorizar un upgrade automático.

## Copia PostgreSQL preparada

Origen: base histórica `medlink`, contenedor `medlink-db`, volumen `medilink_pgdata`. Respaldo actualizado: `.local-demo/backups/medlink-original-2026-11-26T16-12-05-814Z.dump`, SHA-256 `87e87f6f5c9b0852fcc18341d26d9f4217d0d1cfd2ebb78120308a45e2e715c4`. Su restauración y las huellas de 21 tablas se comprobaron en un contenedor temporal. La base original solo se consultó/respaldó.

La copia aislada se restauró en PostgreSQL 18 para comprobar compatibilidad con el proyecto Neon existente. Conserva identificadores, relaciones, farmacias, catálogo, stock, prescripciones, recetas, adjuntos y estados. Tiene 5 farmacias, 13 medicamentos, 17 presentaciones, 36 filas de stock, 14 cuentas, 7 pacientes, 6 operadores, 28 recetas demo, 15 prescripciones, 29 reservas y 5 adjuntos.

Se detectó un perfil con campos personales. Para no transferir datos reales o dudosos se preparó una copia anonimizada: identidades de cuentas y perfiles, campos de documento/contacto/cobertura, mensajes, notas libres, resúmenes JSON de auditoría, respuestas de idempotencia y nombres de archivos. Se conservan los metadatos de auditoría, actores y relaciones, cantidades, fechas y códigos de recetas. Se revisaron visualmente los cinco adjuntos: cuatro documentos académicos ficticios y una imagen genérica de prueba sin texto/datos personales. Los bytes de los adjuntos se conservaron.

Se rotaron todas las contraseñas de la copia con BCrypt de costo 12; solo tres cuentas quedan habilitadas: paciente de pruebas, operador de San Martín y administrador. Las demás se deshabilitaron conservando sus relaciones. Las contraseñas conocidas del seed se probaron y no coinciden. Las credenciales privadas están exclusivamente en `.local-cloud/credentials.private.json`, ignorado por Git; nunca se publican ni se incluyen en el informe. No se alteraron credenciales locales.

Para anonimizar notas históricas se suspendieron temporalmente, **solo en la copia aislada y dentro de una transacción**, los triggers de inmutabilidad correspondientes. Todos quedaron restaurados antes de aprobar la copia; la base original y sus protecciones no se tocaron. No se modificaron migraciones previas ni se ejecutó `repair`.

Cloud añade únicamente `V14__cloud_scan_budget.sql`, en una ubicación Flyway exclusiva del perfil cloud. V1 es el baseline histórico; V2–V13 conservan sus checksums. La copia congelada tiene 22 tablas, incluida la de presupuesto cloud, y V1–V14. Su dump anonimizado volvió a restaurarse y pasó integridad, triggers activos y rechazo de contraseñas conocidas. Evidencia: `.local-cloud/copy-review.json`.

Las cinco farmacias tienen dirección, pero **ninguna tiene coordenadas** en el origen recuperado. La migración preserva ese estado. No se prometen marcadores de farmacia ni se inventan ubicaciones; se necesitan coordenadas verificadas para esa prueba.

## Migración a Neon

`deploy/cloud/migrate-neon.mjs` recibe secretos por stdin sin eco y exige proyecto, huella del dump y autorización explícita. Con `--check-only` solo consulta por TLS. La restauración usa la conexión directa, `sslmode=verify-full`, y certificados raíz del sistema. Rechaza hosts ajenos a Neon, conexión pooled y destino con tablas existentes. No usa `--clean`, no sobrescribe una base y restaura en una transacción con relaciones/identificadores originales.

Después compara conteos y huellas canónicas de cada tabla del dump, con zona UTC y colación C. Solo entonces prepara `medifind_app`, sin permisos de crear bases/roles, y permisos de aplicación sobre tablas/secuencias. Flyway conserva acceso de lectura a su historial; nuevas migraciones de mantenimiento deben aplicarse por el procedimiento administrativo, no otorgando privilegios adicionales al cliente público. No se guarda una cadena de conexión en archivos versionados ni se imprime en el informe.

Para Spring JDBC usar TLS con `sslmode=verify-full` y `sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`, que utiliza el almacén confiable de Java. Un pool Hikari de tres conexiones directas y cero conexiones mínimas evita sumar otro pool y permite que las conexiones ociosas se liberen; no se necesita PgBouncer para esta carga. Se conserva la opción de reevaluarlo si aumenta la concurrencia.

Migración efectiva: **completada el 26/11/2026**. Inmediatamente antes de restaurar se reconfirmaron el plan Free, la región São Paulo, cero tablas de usuario y ausencia del rol `medifind_app`. Se utilizó exclusivamente el dump anonimizado congelado; ninguna prueba modificó el destino local original.

Las **22 tablas remotas coinciden en cantidad de filas y huella completa** con la copia aprobada. Tamaño medido en Neon después de las verificaciones: **13.803.520 bytes (13,16 MiB)** mediante `pg_database_size`; no es una estimación del dump ni una medición de RAM del servicio.

| Datos en Neon | Cantidad |
| --- | ---: |
| Farmacias / con dirección / con coordenadas | 5 / 5 / 0 |
| Medicamentos / presentaciones | 13 / 17 |
| Filas de stock | 36 |
| Cuentas / habilitadas | 14 / 3 |
| Pacientes / operadores | 7 / 6 |
| Recetas demo / prescripciones | 28 / 15 |
| Reservas / adjuntos | 29 / 5 |

Verificación posterior: 30 claves foráneas comprobadas sin registros huérfanos; cero constraints sin validar; 35 triggers de usuario activos; cero índices inválidos; historial V1–V14 exitoso, con sus checksums originales y el orden histórico conservado. Las tres cuentas habilitadas corresponden a paciente, operador y administrador; cero correos ajenos al dominio ficticio, cero campos personales del perfil y cero coincidencias con las contraseñas conocidas del seed.

Se limitó el compute existente a **0,25–0,25 CU**, manteniendo Free y la suspensión automática del plan. No se creó otro proyecto ni se habilitó facturación. El usuario `medifind_app` conecta y opera las tablas del negocio, pero no es superusuario, no crea bases/roles/objetos del esquema, no evita RLS y no puede modificar el historial Flyway.

`node deploy/cloud/check-neon-java.mjs` comprobó la conexión con Java 21, el driver PostgreSQL y las dependencias Flyway del backend: TLS `verify-full` con el almacén confiable de Java, autenticación del rol de aplicación, consultas reales y validación de checksums aprobadas. Las sesiones se forzaron a **solo lectura**; no se inició Spring ni sus tareas de vencimiento/purga durante esta etapa. `verify-local-preserved.mjs` comparó nuevamente las 21 tablas originales con el respaldo: todas preservadas. Los fuentes Android y backend originales siguen sin diferencias contra la referencia funcional.

Evidencias privadas locales, sin secretos en los informes: `.local-cloud/neon-migration.json`, `neon-verification.json`, `neon-java-preflight.json` y `local-preservation.json`. Las credenciales de aplicación quedan en `.local-cloud/neon-app.private.json`, ignorado por Git; los archivos temporales de conexión del propietario se eliminaron. No volver a ejecutar la restauración contra esta base ya poblada.

La conexión directa al destino se comprobó con el cliente PostgreSQL: **TLS 1.3, verify-full**, y cero tablas de usuario. Esa comprobación fue solo lectura. La configuración privada temporal utilizada para comunicar el cliente con las herramientas se eliminó después de leerla; no se incluyó en Git ni se mostró en el informe. El indicador interno `pg_stat_ssl` del servidor detrás del proxy no se utiliza para inferir el cifrado del cliente: la verificación se hizo con `psql \\conninfo`.

## Backend Render

Dockerfile: `deploy/cloud/Dockerfile`, Java 21, build Maven reproducible desde los fuentes funcionales más adiciones cloud; runtime sin root. El perfil cloud usa `PORT` de Render, Hikari 3/0, Hibernate validate y Flyway sin baseline automático. La imagen acota heap a 256 MiB, metaspace a 128 MiB y code cache a 32 MiB; usa SerialGC y un procesador visible. No elimina módulos del sistema.

La clase `CloudRequestGuard` añade límites a autenticación, serializa escaneos y aplica un presupuesto de IA persistente. No altera la comparación documental ni la lógica de recetas/reservas. La generación de PDFs alterados está deshabilitada. Se excluye la configuración automática de una cuenta Spring que no utiliza el JWT de la aplicación y que generaba una contraseña innecesaria en logs. CORS admite únicamente los orígenes Pages reales que se publiquen, nunca `*`.

Variables privadas necesarias: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET_BASE64` (32 bytes aleatorios codificados en Base64), sin valores en Git. Render generará el secreto JWT mediante `generateValue: true`. **Etapa inicial autorizada: `MEDLINK_RECETAS_ESCANEO_ENABLED=false` y ninguna clave OpenAI desplegada**. Una futura habilitación controlada requiere nueva autorización. Variables no secretas: `OPENAI_MODEL=gpt-5.4-mini`, `MEDLINK_RECETAS_ESCANEO_TIMEOUT_SECONDS=60`, `MEDIFIND_SCAN_DAILY_LIMIT=20`, `MEDLINK_CORS_ALLOWED_ORIGINS`, `PORT`. El puerto lo asigna Render. CORS inicialmente no admite orígenes web, ya que Pages no se publica en esta etapa. Las variables de correo solo se añaden con un proveedor y remitente autorizado/verificado.

Usar Free y despliegue manual (`autoDeployTrigger: 'off'`); previews deshabilitados; no crear Render Postgres, discos, cron ni servicios pagos. Health `/api/health`. La especificación está en `deploy/cloud/render.yaml`, con el repositorio y la rama de despliegue explícitos y nombre `medifind-api-ggian102`. Se validó antes de aplicarla con el esquema JSON oficial de Render descargado el 26/11/2026 mediante `validate-render-config.mjs`. La validación de esquema se complementó con la aprobación del plan y las pruebas reales posteriores al despliegue.

El servicio autorizado se creó después de comprobar Billing autenticado: Hobby, **sin tarjeta**, sin cargos pendientes, builds con límite mensual USD 0, 500 minutos de pipeline y 5 GB de transferencia incluidos; no se modificaron ajustes de facturación. Se aplicó la Blueprint con credenciales reales enviadas directamente por HTTPS desde un proceso local; no se imprimieron valores privados ni se creó un servicio con credenciales vacías. La CLI oficial 2.39.0 se verificó por SHA-256 y fue autorizada expresamente por el usuario. Su token queda en `.local-cloud/render-cli-config/`, ignorado por Git y excluido de la imagen.

Servicio: `srv-dc47mhajnfac73bpn770`, nombre `medifind-api-ggian102-mey9`, región Virginia, un único servicio Free de 0,1 CPU/512 MiB. [Dashboard del servicio](https://dashboard.render.com/web/srv-dc47mhajnfac73bpn770). Blueprint `exs-dc47ahe7bikc738c8vtg`, `autoSync=false`; estado `paused` significa sincronización automática deshabilitada, no servicio detenido. Los pushes no despliegan automáticamente. El primer despliegue `6b9d8a9` llegó a `live`; la corrección de seguridad `7884ad0`, despliegue `dep-dc47q6tg1s2s73dj27o0`, llegó a `live` a las 18:44:08 UTC.

La prueba pública detectó que la configuración heredada de headers reenviados permitía falsificar la IP usada por el limitador. Se corrigió **solo en el perfil cloud**, con `server.forward-headers-strategy=none`: esta API JSON no genera redirects ni URLs absolutas y Render termina TLS, por lo que no necesita confiar en headers `Forwarded` del cliente. El límite ahora usa el origen real de la conexión del proxy de Render: 10 solicitudes de autenticación/minuto por ese origen y 60/minuto globales, con ventanas fijas y almacenamiento acotado. Varios usuarios pueden compartir el límite del proxy; es una protección conservadora para esta instancia académica Free. Las pruebas públicas posteriores confirmaron HTTP 429 incluso al falsificar tanto `X-Forwarded-For` como `Forwarded`. No se alteró el entorno local, que conserva su configuración anterior.

Verificación privada de variables: conexión y contraseña coinciden con el usuario de aplicación Neon, JWT válido generado por Render, ausencia de clave OpenAI, escaneo `false`, CORS sin orígenes web habilitados y health HTTP correcto. El endpoint health conserva su respuesta original y verifica el proceso; las consultas de negocio y las comprobaciones SQL independientes verificaron Neon, sin confundir health 200 con una prueba completa.

Pruebas públicas aprobadas: inicio de sesión de los tres roles con contraseñas rotadas; catálogo de 12 medicamentos activos, cinco farmacias y disponibilidad; 401 sin token o JWT inválido; 403 de paciente/operador en administración y de operador contra otra farmacia; origen web no autorizado rechazado; generación alterada 404; escaneo externo 503 y configuración deshabilitada. Se vinculó la receta académica existente 19, vigente hasta el 21/02/2027, sin cambiar fechas ni cantidades autorizadas; se creó la reserva de prueba 30, se verificó idempotencia y visibilidad por farmacia y se canceló conservando historial.

Neon después de esa prueba tiene **30 reservas** (29 migradas más la prueba cancelada), **16 prescripciones** (15 migradas más la vinculación normal de la receta), 28 recetas demo y cinco adjuntos. El stock de la presentación 9 en la farmacia 2 volvió de la operación a su valor inicial **4**; cantidad comprometida y utilizada de esa receta: 0/0. Stock no negativo, cantidades de prescripciones válidas, sin constraints inválidos, triggers deshabilitados ni migraciones fallidas. Tamaño medido en ese punto: **13.828.096 bytes (13,19 MiB)**. No se restauró nuevamente la base.

Memoria observada en métricas reales de Render durante las pruebas: última muestra del período, a las 19:11 UTC y después de reanudar, **333,1 MiB**; pico observado por instancia **348,6 MiB**, límite **512 MiB**. Son muestras del período de prueba, no una garantía de consumo máximo futuro ni una lectura actual cuando el servicio está dormido. Evidencias: `.local-cloud/render-security-config.json`, `render-memory.json`, `public-api-tests.json`, `public-readiness-tests.json`, `auth-proxy-test.json` y `neon-public-integrity.json`.

Suspensión/reanudación: **aprobada mediante observación real**. Última petición a las 18:44:41 UTC; los logs registraron cierre ordenado de Tomcat y Hikari a las 18:59:41 UTC, tras 15 minutos sin tráfico y sin una orden de suspensión manual. Neon se suspendió a las 19:04:46 UTC, comprobado mediante su API de control sin consultas SQL. Tras **1.284 segundos de inactividad** se inició una petición normal al catálogo a las 19:06:05 UTC: el primer intento devolvió HTTP 200 y JSON válido en **156 segundos (2 min 36 s)**. No se usaron pings para mantener el servicio despierto.

Después del arranque frío se verificaron el catálogo real de Neon, la sesión emitida antes de suspender, el rechazo del rol paciente en administración y la reserva 30 aún cancelada. La sesión conservó su vigencia normal de 30 minutos; no se amplió su TTL. La prueba terminó a las 19:08:45 UTC. Evidencias: `.local-cloud/render-idle-observation.json`, `neon-idle-final.json` y `cold-start-test.json`. Una comprobación SQL final mantuvo 30 reservas, 16 prescripciones, 28 recetas, cinco adjuntos y stock 4, sin cantidades inválidas, constraints sin validar, triggers deshabilitados ni migraciones fallidas.

## Revisión previa a activar servicios públicos

La autorización vigente cubre la migración anterior, el commit/push de la preparación revisada y **un único backend Render Free**, después de comprobar que no se habiliten cargos. **No autoriza desplegar Pages, publicar una release ni habilitar OpenAI externo.** Se conservarán la release estable y el APK LAN actuales.

| Componente que se propone publicar | Configuración preparada | Condición para activarlo |
| --- | --- | --- |
| Código de despliegue | Rama `deploy/medifind-cloud`; fuentes funcionales preservados; secretos, dumps y firmas excluidos | Auditado y subido con autorización |
| Backend Render | Un servicio Docker Free en `medi's workspace`, Virginia, Java 21, 512 MiB, heap 256 MiB, pool 3/0, health `/api/health`, despliegues manuales | Desplegado; Billing, pruebas públicas y reanudación aprobados |
| Acceso a Neon | Solo rol `medifind_app`, TLS verificado; migración ya aplicada y validada | Configurado privadamente y comprobado mediante la API pública |
| Seguridad | JWT propio de cloud; roles y farmacia verificados; límites de autenticación; generador de documentos alterados cerrado; OpenAI sin clave | Comprobaciones públicas aprobadas, incluida la corrección de headers falsificados |
| Landing, operador y administración Pages | Tres proyectos estáticos Free, CORS limitado a los orígenes efectivos, sin claves del backend | API HTTPS real; aprobación final de publicación y artefactos comprobados |
| Android cloud preliminar | Identidad `.cloud`, firma independiente, versión `1.0.0-cloud.1`, versionCode 3; diseño y funcionalidades preservados | URL real de Render, compilación, firma, instalación y pruebas antes de aprobar la release |

Render ya está activado y verificado, incluida la suspensión y reanudación naturales. OpenAI queda deshabilitado y sin clave. Pages y la release Android requieren otra etapa autorizada; no se publicaron como parte del backend.

La clave OpenAI todavía no se transfirió a Render ni se hicieron llamadas nuevas al proveedor en esta etapa. El correo cloud sigue pendiente de proveedor/remitente gratuito y verificable; las cinco farmacias siguen sin coordenadas de origen. No se declararán completos esos flujos sin resolverlos o informar su limitación.

## Correo de recuperación

Render Free bloquea SMTP saliente en 25/465/587. El Mailpit local no se publica. El módulo existente admite SMTP STARTTLS en 2525, pero se necesita un proveedor gratuito compatible y un remitente que el usuario pueda verificar. La documentación de Brevo contempla 2525, con requisitos de autenticación de dominio; no se creó una cuenta ni se enviaron mensajes. Una API HTTPS de correo requeriría configurar una identidad válida y adaptar solo el transporte.

Por ahora el endpoint devuelve `RECOVERY_UNAVAILABLE` sin exponer códigos ni buzones. Las pantallas de recuperación se conservan, pero **el envío cloud queda pendiente** de una configuración gratuita verificable y autorización del remitente. No se declara probado. No usar una identidad ficticia para entregar correos a personas reales.

## Sitios y APK

La landing existente conserva diseño y marca de `develop-web`, referencia `a9f70a17ae4beeed0db1a283f0dafd8fabd7a47c`, con la distribución local ya adaptada. `prepare-web.mjs` trabaja en copias, configura el origen API HTTPS para operador/admin y usa un enlace explícito al APK preliminar en la landing; botón y QR comparten ese valor. No se usa `releases/latest`, que sigue apuntando a la estable anterior. Los bundles finales requieren la URL real del backend y un asset verificado.

La variante Android se crea con `android-cloud.init.gradle` sobre una copia de los fuentes, identidad `.cloud` y firma privada persistente separada del APK LAN. Versión propuesta `1.0.0-cloud.1`, versionCode 3. Solo cambian identidad/firma/URL HTTPS y timeouts de conectividad (30 s de conexión, 240 s de lectura por el arranque frío). No cambian pantallas, navegación, sesión, validaciones ni comparación documental. Se exige backend real accesible antes de compilar. El keystore y su clave quedan fuera de Git en `.local-cloud/signing/`; respaldarlos antes de futuras versiones.

Flujo posterior a la autorización: desplegar API, registrar URL devuelta, compilar/tests del APK, verificar con `apksigner`/`aapt`, instalar en emulador o teléfono disponible, probar flujos reales, preparar asset con SHA-256 y release preliminar `cloud-v1.0.0-rc.1` sin sobrescribir versiones. Subir con la CLI oficial de GitHub usando su sesión existente. Después compilar sitios con ese enlace explícito, publicar tres proyectos estáticos Pages por Wrangler y decodificar QR para comparar exactamente el asset. La release estable anterior se conserva.

## Pruebas realizadas y pendientes

Hecho antes de publicar:

- Respaldo del estado Git y base local; restauración original y anonimizada con integridad.
- Compatibilidad PostgreSQL 18 y Flyway V1–V14 en la copia.
- Rotación de contraseñas, tres roles habilitados, cuentas restantes deshabilitadas y rechazo del seed.
- Backend probado con **512 MiB y 0,1 CPU**: arranque de aproximadamente 214 segundos; memoria observada después de las pruebas alrededor de 285 MiB. La imagen final sin cuenta Spring automática observó 268 MiB con límite de 512 MiB.
- Login de tres roles, catálogo (12 medicamentos activos), cinco farmacias, 401/403, aislamiento de farmacia, CORS exacto, reserva y cancelación con restitución de stock y cantidad comprometida. Pruebas únicamente sobre la copia, con una reserva adicional que no se incluye en el dump congelado.
- Límite de autenticación y presupuesto persistente de escaneo; comprobado que sigue aplicándose al iniciar otro proceso. Cero llamadas nuevas a OpenAI durante esta preparación.
- Suites web adaptadas: operador 5, administración 17, landing 14, todas aprobadas.
- Imagen Docker compilada; fuente local Android/backend revalidada idéntica al commit funcional. Ver `.local-cloud/runtime-tests.json`, `final-image-check.json` y `copy-review.json`.
- Migración real a Neon: 22 tablas iguales a la copia congelada, 30 relaciones sin huérfanos, 35 triggers activos, Flyway validado por Java y rol de aplicación con TLS verificado. Base local original: 21 tablas nuevamente comparadas y preservadas.
- Configuración Render validada contra el esquema oficial, sin aplicar cambios ni publicar servicios.

Render: arranque, catálogo, stock, reservas, vinculación de receta, permisos por Internet y reactivación tras suspensión natural aprobados. Pendiente de otra etapa autorizada: prueba documental externa con un PDF académico y OpenAI habilitado de manera controlada; APK compilado/firma/instalación/flujos; sitios HTTPS, descarga y QR; permisos de esos sitios públicos; entrega de correo si existe proveedor compatible. No se afirma una prueba en teléfono físico: no hay uno conectado actualmente. Tampoco se presenta el health check como sustituto de estas pruebas.

## Actualización, respaldo y vuelta a local

Las herramientas están en `deploy/cloud/`. `prepare-copy.mjs` crea una copia nueva y rechaza sobreescribir un contenedor existente; `finalize-privacy.mjs` y `verify-frozen-copy.mjs` aprueban el dump anonimizado antes de transferir. No volver a importar el origen sobre cloud con actividad: elegir una nueva copia/destino y revisar la migración expresamente.

Para cambios de código: revisar diff, ejecutar pruebas, preparar commit en la rama de despliegue, desplegar manualmente API y comprobar salud más operaciones; compilar APK solo con la URL real y publicar una nueva versión preliminar explícita; reconstruir sitios y comparar el QR. La workflow `medifind-cloud-checks.yml` es manual y solo valida; no migra, no despliega ni publica releases por cada push.

Antes de cada migración, respaldar y restaurar para verificar. Los dumps y `.local-cloud` nunca se suben a GitHub, Pages ni artefactos públicos. Para respaldar Neon, usar el cliente PostgreSQL con credenciales privadas y TLS verificado; comprobar restauración en destino temporal aislado. Mantener keystore cloud y secretos de infraestructura en almacenamiento privado.

Para seguir en local: mantener la app LAN y abrir `http://192.168.1.40:5174`; ejecutar `scripts/demo-local/Start.ps1` si hace falta. Ese arranque conserva la selección histórica. La app `.cloud` convivirá con `.lan`; no requiere ADB reverse ni IP local. Los scripts cloud rechazan `medlink-db` como destino de migración y no sincronizan datos.

Para detener cloud sin borrar datos: suspender el servicio Render y conservar Neon; no eliminar proyecto/branch, no ejecutar down -v y no borrar releases o Pages para apagar el backend. La despublicación o eliminación definitiva requiere un pedido específico. Los contenedores locales de revisión llevan la etiqueta `medifind.cloud.review=true`, usan puertos 18182/18183 solo en loopback y no son parte de producción; detenerlos no afecta a `medifind-local-*` ni `medlink-db`.
