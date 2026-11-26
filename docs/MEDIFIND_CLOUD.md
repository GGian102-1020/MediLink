# MediFind cloud

Estado al 26/11/2026: preparación y pruebas locales realizadas; **publicación pendiente de autorización explícita del usuario**. Este documento se actualiza con las URLs y resultados reales después de cada etapa. No constituye una declaración de que el sistema ya esté desplegado.

## Referencia y recuperación

Rama de despliegue: `deploy/medifind-cloud`, creada desde `codex/medifind-v1-local`, HEAD original `da344f1643af3111404e42703dac6053c333aaa1`. El estado local probado, incluidos landing, administración, scripts y cambios pendientes, se respaldó antes de editar en `.local-demo/backups/cloud-preparation-20261126-131141/`. Incluye diff binario, inventario Git, HEAD y tar de archivos no versionados; SHA-256 del tar `5211a4db5d07d6ba693cd37fc9c44fff17d755b3cf674b515062d78b356fb1eb`.

Los 94 archivos Android y los 184 archivos backend originales siguen sin cambios. La configuración cloud se aplica mediante `deploy/cloud/backend/` al compilar su imagen y en copias de trabajo bajo `.local-cloud/`. El entorno local sigue usando el APK LAN, PostgreSQL y backend anteriores. No hay sincronización automática entre bases.

## Cuentas y arquitectura

| Servicio | Verificación actual | Función |
| --- | --- | --- |
| Neon | Organización MediFind, plan `free`; proyecto existente `flat-shadow-76171117`, región `aws-sa-east-1`, PostgreSQL 18; base `neondb` sin tablas de usuario antes de migrar | Copia PostgreSQL |
| Render | Espacio `medi's workspace`, cuenta de MediFind confirmado por el usuario; sin servicios existentes | Backend Java 21 / Spring Boot |
| Cloudflare | OAuth oficial Wrangler completado con la cuenta MediFind; sin proyectos Pages existentes | Landing, operador y administración estáticos |
| GitHub | Repositorio `GGian102-1020/MediLink`, acceso de escritura comprobado; release estable existente `android-v0.2.0` | Fuente y APK preliminar |

Arquitectura: Android cloud y sitios Pages se conectan por HTTPS a Render; Render conecta por TLS verificado a Neon. El contenido adjunto y el estado de negocio se guardan en PostgreSQL, no en el disco efímero de Render. Las claves de OpenAI y JWT permanecen exclusivamente en el backend. No se usarán Pages Functions, Workers de pago, R2, dominios comprados ni servidores para mantener despierto Render.

URLs públicas: **todavía no asignadas**. Los nombres propuestos son `medifind-api-ggian102` para Render y `medifind-ggian102`, `medifind-operador-ggian102`, `medifind-admin-ggian102` para Pages, sujetos a disponibilidad. Se usarán las URLs que devuelvan los proveedores; no se compila un APK con una URL adivinada.

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

Migración efectiva: **pendiente**. Tamaño en Neon y comparación de filas remotas: **pendientes**, no inferidos a partir del dump local.

## Backend Render

Dockerfile: `deploy/cloud/Dockerfile`, Java 21, build Maven reproducible desde los fuentes funcionales más adiciones cloud; runtime sin root. El perfil cloud usa `PORT` de Render, Hikari 3/0, Hibernate validate y Flyway sin baseline automático. La imagen acota heap a 256 MiB, metaspace a 128 MiB y code cache a 32 MiB; usa SerialGC y un procesador visible. No elimina módulos del sistema.

La clase `CloudRequestGuard` añade límites a autenticación, serializa escaneos y aplica un presupuesto de IA persistente. No altera la comparación documental ni la lógica de recetas/reservas. La generación de PDFs alterados está deshabilitada. Se excluye la configuración automática de una cuenta Spring que no utiliza el JWT de la aplicación y que generaba una contraseña innecesaria en logs. CORS admite únicamente los orígenes Pages reales que se publiquen, nunca `*`.

Variables privadas necesarias: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET_BASE64` (32 bytes aleatorios codificados en Base64), `OPENAI_API_KEY`; sin valores en Git. Variables no secretas: `OPENAI_MODEL=gpt-5.4-mini`, `MEDLINK_RECETAS_ESCANEO_ENABLED=true`, `MEDLINK_RECETAS_ESCANEO_TIMEOUT_SECONDS=60`, `MEDIFIND_SCAN_DAILY_LIMIT=20`, `MEDLINK_CORS_ALLOWED_ORIGINS`, `PORT`. El puerto lo asigna Render. Las variables de correo solo se añaden con un proveedor y remitente autorizado/verificado.

Usar Free y despliegue manual (`autoDeploy=no`); no crear Render Postgres, discos, cron ni servicios pagos. Health `/api/health`. El borrador de especificación está en `deploy/cloud/render.yaml`; aún no creó recursos.

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

Pendiente después de publicar: verificar filas y tamaño reales en Neon; arranque y reactivación reales de Render; catálogo, stock, reservas y recetas por Internet; prueba documental cloud real limitada a un PDF académico; APK compilado/firma/instalación/flujos; sitios HTTPS, descarga y QR; permisos en URLs públicas; entrega de correo si existe proveedor compatible. No se afirma una prueba en teléfono físico: no hay uno conectado actualmente. Tampoco se presenta el health check como sustituto de estas pruebas.

## Actualización, respaldo y vuelta a local

Las herramientas están en `deploy/cloud/`. `prepare-copy.mjs` crea una copia nueva y rechaza sobreescribir un contenedor existente; `finalize-privacy.mjs` y `verify-frozen-copy.mjs` aprueban el dump anonimizado antes de transferir. No volver a importar el origen sobre cloud con actividad: elegir una nueva copia/destino y revisar la migración expresamente.

Para cambios de código: revisar diff, ejecutar pruebas, preparar commit en la rama de despliegue, desplegar manualmente API y comprobar salud más operaciones; compilar APK solo con la URL real y publicar una nueva versión preliminar explícita; reconstruir sitios y comparar el QR. La workflow `medifind-cloud-checks.yml` es manual y solo valida; no migra, no despliega ni publica releases por cada push.

Antes de cada migración, respaldar y restaurar para verificar. Los dumps y `.local-cloud` nunca se suben a GitHub, Pages ni artefactos públicos. Para respaldar Neon, usar el cliente PostgreSQL con credenciales privadas y TLS verificado; comprobar restauración en destino temporal aislado. Mantener keystore cloud y secretos de infraestructura en almacenamiento privado.

Para seguir en local: mantener la app LAN y abrir `http://192.168.1.40:5174`; ejecutar `scripts/demo-local/Start.ps1` si hace falta. Ese arranque conserva la selección histórica. La app `.cloud` convivirá con `.lan`; no requiere ADB reverse ni IP local. Los scripts cloud rechazan `medlink-db` como destino de migración y no sincronizan datos.

Para detener cloud sin borrar datos: suspender el servicio Render y conservar Neon; no eliminar proyecto/branch, no ejecutar down -v y no borrar releases o Pages para apagar el backend. La despublicación o eliminación definitiva requiere un pedido específico. Los contenedores locales de revisión llevan la etiqueta `medifind.cloud.review=true`, usan puertos 18182/18183 solo en loopback y no son parte de producción; detenerlos no afecta a `medifind-local-*` ni `medlink-db`.
