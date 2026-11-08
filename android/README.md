# MediLink Android — MVP de pacientes

Este directorio es **un cliente Android nativo independiente** (Kotlin + Jetpack Compose).
No cambia los controladores Java, PostgreSQL, las migraciones, ni el frontend React.
La API REST existente es la fuente de verdad para stock, sesiones y reservas.

## Alcance implementado

- Catálogo público de medicamentos, filtro por nombre y paginación.
- Presentaciones por medicamento y farmacias con stock.
- Apertura de una app de mapas externa cuando la farmacia tiene coordenadas.
- Registro e inicio de sesión de pacientes (JWT en memoria, sin persistencia de claves).
- Creación de reservas con `Idempotency-Key` estable por intento y prescripción de demo.
- Listado paginado de reservas, actualización y cancelación voluntaria con confirmación.
- Pantalla de perfil y cierre de sesión (se revoca el JWT del servidor si hay conexión).
- Pruebas unitarias del mecanismo de idempotencia.

No incluye mapas embebidos, ubicación GPS, operadores, administración, alertas push ni un
servidor desplegado públicamente. No está preparado para publicar la variante release:
`https://api.example.invalid/` es un marcador de posición deliberado.

## Requisitos Windows

- Git, Docker Desktop con Compose v2 y Android Studio.
- SDK Android 36 y Android SDK Platform-Tools (se instalan desde SDK Manager).
- Un emulador Android (AVD) o un teléfono con depuración USB.
- JDK 17 o superior; en Android Studio se puede usar el JBR incluido.
- Conexión a Internet al realizar la primera sincronización Gradle.

## Primera instalación

Desde la raíz `MediLink`:

```powershell
# Ejecutar una vez: descarga el JAR oficial y verifica SHA256.
& .\android\scripts\bootstrap-wrapper.ps1

# Crear .env solo si aún no existe; configurar JWT_SECRET_BASE64 según la
# documentación del repo. NO regenerar secretos de una base compartida.
if (-not (Test-Path .\.env)) { Copy-Item .\.env.example .\.env }

# Exponer SOLO en loopback host el backend para el túnel ADB.
docker compose -f .\docker-compose.yml -f .\android\compose.dev.yml --profile api up -d --build
Invoke-RestMethod 'http://127.0.0.1:18081/api/health'
```

En **Android Studio → File → Open**, seleccionar la carpeta `MediLink/android`
(no la raíz `MediLink`). Esperar Gradle Sync, y en SDK Manager instalar SDK Platform
36 y Platform-Tools. Abrir Device Manager y crear/iniciar un emulador.

En PowerShell, identificar `adb.exe` (o la ruta equivalente a tu SDK):

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb reverse tcp:18081 tcp:18081
& $adb reverse --list
```

Ejecutar la configuración **app / debug** desde Android Studio, o compilar:

```powershell
Set-Location .\android
.\gradlew.bat :app:assembleDebug
# El resultado queda en app\build\outputs\apk\debug\app-debug.apk
```

La variante debug usa `http://localhost:18081/` **dentro de Android**, que llega
al backend local por `adb reverse`. HTTP hacia `localhost` está habilitado solamente mediante la configuración
de red de `src/debug`; los demás destinos HTTP están bloqueados. El puerto del host se publica solo en `127.0.0.1`, no en la LAN.
Repetir `adb reverse` luego de reiniciar el emulador o desconectar el USB.

### Datos de demo (solo si corresponde)

Un PostgreSQL nuevo tiene las tablas por Flyway, pero no necesariamente datos de
medicamentos. Para una base local *nueva y descartable*, consultar el procedimiento
de seed manual en `docs/DEMO_MAPAS_MVP.md` y ejecutar solo el seed 02. NO cargarlo
en bases con información real/compartida ni usar `docker compose down -v`.

### Solución de problemas

- **`java` no encontrado:** seleccionar Gradle JDK = JBR incluido en Android Studio,
  o establecer `JAVA_HOME` a un JDK 17+.
- **`gradle-wrapper.jar` ausente:** ejecutar `bootstrap-wrapper.ps1`, verificar el
  SHA256 y agregar el JAR verificado al commit Git.
- **Falta Android SDK 36:** instalarlo en Android Studio → SDK Manager.
- **No conecta desde Android:** comprobar `Invoke-RestMethod .../api/health`,
  `adb devices`, `adb reverse --list` y los logs de `docker compose ... logs backend`.
- **`401` o sesión vencida:** iniciar sesión otra vez; el JWT dura 30 minutos.
- **Catálogo vacío:** primero confirmar datos del seed en una base de pruebas.
- **No hay mapas:** instalar una aplicación compatible con el enlace `geo:`.

## Decisiones de arquitectura

- UI: Compose Material 3, Navigation Compose.
- Estado/lógica de presentación: ViewModels + StateFlow.
- Acceso remoto: Repository + Retrofit + OkHttp + Gson.
- Manejo de sesión: solo en memoria; no se persiste JWT ni contraseñas.
- El backend conserva autorización por rol y las transacciones de reserva.
- Clave de idempotencia: misma solicitud/mismo reintento → mismo UUID; si cambia
  el cuerpo → UUID nuevo; se reinicia después de confirmar correctamente.
- Seguridad local: el `compose.dev.yml` abre el puerto del backend únicamente en
  la interfaz loopback. La configuración release exige HTTPS real.

## Verificaciones recomendadas

```powershell
Set-Location .\android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Probar además registro/login, reservas y cancelación en un emulador con un backend
encendido. Esta entrega es código de integración; se requiere una compilación y
pruebas en el equipo con el SDK Android instalado.
