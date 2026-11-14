# MediLink para la facultad: QR + APK + backend sin dominio ni hosting

Esta guia usa **ngrok Free (HTTPS con dominio de desarrollo estable)** y una base
PostgreSQL **aislada** en Docker Desktop. La landing y el APK siguen estando en
GitHub. **No hace falta comprar un dominio ni pagar hosting.**

> Es una DEMOSTRACION, no un servicio 24/7. El backend solo funciona mientras
> la computadora, Docker y ngrok sigan encendidos. Si se apagan, la descarga del
> APK sigue disponible en GitHub Releases, pero login, catalogo y reservas no
> podran consultar la API.

## 1. Preparar Docker Desktop y arrancar una base descartable

Desde `C:\Users\giuli\Projects\MediLink` en la rama `develop-web`:

```powershell
# Comprobar que Docker Desktop esta abierto
 docker info
# Arrancar el backend y una base con volumen SEPARADO del proyecto principal
.\scripts\demo\iniciar-demo.ps1
# Opcional: catalogo/stock inventados, SIN usuarios con claves conocidas
.\scripts\demo\cargar-catalogo.ps1
# Comprobar API en la propia PC
Invoke-WebRequest http://127.0.0.1:18081/api/health -UseBasicParsing
```

`iniciar-demo.ps1` crea `.env.demo` en el primer inicio con claves aleatorias
independientes de `.env`; se excluye de Git y **no se reescribe** luego. Solo
exponemos `127.0.0.1:18081` en el host. La base no publica puertos y vive en
un volumen exclusivo `medilink-demo_datos_demo`. No se modifica `docker-compose.yml`,
la base de siempre ni el volumen del equipo. El primer build necesita Internet
y puede demorar por Maven y Docker.

Si el puerto `18081` esta ocupado por otro backend, detenelo con cuidado (sin
borrar sus datos) antes de usar el entorno aislado. No cambies las credenciales
de `.env.demo` con el volumen iniciado: PostgreSQL conserva su clave anterior.

**Catalogo demo:** las farmacias, nombres y existencias son ficticios, y el SQL
se puede ejecutar varias veces. Para el login podés registrar un usuario de
prueba desde Android; **no uses datos de personas reales**. Evitamos cargar
`database/02_datos_prueba.sql` porque incluye cuentas con contrasenas
publicas, peligroso si la API queda accesible desde Internet.

## 2. Instalar ngrok gratis (una sola vez)

1. Registrate en https://dashboard.ngrok.com/signup (plan **Free**). ngrok
   asigna un dominio de desarrollo propio, por ejemplo
   `https://algo-aleatorio.ngrok-free.dev` o `.ngrok-free.app`, **estable** para
   tu cuenta; **no lo podes elegir en el plan gratuito**.
2. Instala ngrok en Windows: `winget install ngrok -s msstore` (o desde
   https://ngrok.com/download/windows).
3. Copia TU authtoken del dashboard e ingresalo **solo en tu PowerShell**:

   ```powershell
   ngrok config add-authtoken "TU_AUTHTOKEN_PRIVADO"
   ```

   No pegues el token en un chat, script del repositorio, issue ni captura.

## 3. Crear la URL HTTPS para que Android llegue a tu PC

En una **segunda ventana** de PowerShell, deja ejecutandose:

```powershell
ngrok http 18081
```

ngrok deberia indicar algo como `https://abc123.ngrok-free.dev -> http://localhost:18081`.
Esa URL gratuita es de ngrok: **no requiere que compres dominio**.

Volviendo a la primera ventana:

```powershell
Set-Location 'C:\Users\giuli\Projects\MediLink'
$api = & .\scripts\demo\verificar-tunel.ps1
$api
# Debe devolver https://TU_HOST_NGROK/ (con / al final)
```

Este script compara la respuesta del backend local y la respuesta HTTPS de
ngrok; no acepta una pagina HTML de error como si fuera la API. Si el tunel
no aparece en `http://127.0.0.1:4040/api/tunnels`, confirma que ngrok sigue
abierto en otra ventana y apunta al puerto 18081.

El plan Free de ngrok tiene limites de transferencia y solicitudes; alcanza
para una demo pequena, no para uso masivo. Puede mostrar una pagina de aviso
al abrir la URL desde un **navegador**: el APK y la landing no envian al
usuario a esa URL al descargar (el APK esta en GitHub). La app Android
normalmente consume JSON via OkHttp, no navega a esa pagina HTML. Proba la app
con **datos moviles** antes de la presentacion.

## 4. Preparar firma, API y GitHub Releases

Con ngrok y Docker encendidos, estando el parche de publicacion instalado y
confirmado en `develop-web`:

```powershell
Set-Location 'C:\Users\giuli\Projects\MediLink'
$api = & .\scripts\demo\verificar-tunel.ps1
# Pedira crear una clave privada y guardar una contrasena segura
.\android\scripts\configurar-publicacion-github.ps1 -ApiBaseUrl $api
```

No inventes `api.medilink.com.ar`: el valor correcto sale de tu ngrok.
Hace un backup del `.p12` generado **fuera de Git** y guarda la contrasena.
No cambies esa firma para las siguientes versiones.

Antes de publicar, verifica el codigo:

```powershell
Set-Location 'C:\Users\giuli\Projects\MediLink\frontend'
npm ci
npm run typecheck
npm test
npm run build
Set-Location 'C:\Users\giuli\Projects\MediLink\android'
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Si los cambios todavia no estan subidos a `develop-web`, hace commit y push
primero. Para lanzar la version (`android-v0.2.0` segun la configuracion):

```powershell
Set-Location 'C:\Users\giuli\Projects\MediLink'
.\android\scripts\publicar-version.ps1
```

El script pide confirmar `PUBLICAR`, sube un tag y GitHub Actions compila,
firma y publica el APK en Releases. Mientras Actions corre, deja abiertos
Docker y ngrok: comprueba la salud de la API publica. **No crea un servidor
permanente**. Al finalizar, consulta:

- https://github.com/GGian102-1020/MediLink/actions
- https://github.com/GGian102-1020/MediLink/releases
- `https://github.com/GGian102-1020/MediLink/releases/latest/download/MediLink-android.apk`

**El codigo QR apunta a ese APK de GitHub**. Quien lo escanee descarga la
aplicacion, autoriza la instalacion en Android, y la app usa la URL de ngrok
incorporada en el APK. El QR se mantiene identico al publicar otra version.

## 5. Prueba REAL (dos redes distintas)

1. PC: Docker + backend demo + ngrok deben estar encendidos.
2. Celular Android: desconecta Wi-Fi y usa datos moviles; escanea el QR.
3. Confirma que se descarga `MediLink-android.apk` desde GitHub Releases.
4. Permite la instalacion desde esa fuente si Android lo solicita.
5. Abre la app y prueba el catalogo de ejemplo y el registro/login con un
   correo ficticio. Luego proba una reserva con datos de test si el flujo
   concreto de la app la admite. Evita ingresar recetas o datos clinicos reales.
6. Apaga ngrok momentaneamente: la app perdera la conexion a la API. Volvelo
   a prender con `ngrok http 18081`: el hostname de tu cuenta es el mismo.

## 6. Al terminar la presentacion

En la ventana de ngrok: `Ctrl+C`. Luego:

```powershell
.\scripts\demo\detener-demo.ps1
```

Los datos del volumen demo quedan conservados para la siguiente clase. Si
**queres borrar solo el volumen de demostracion** (irreversible), usa
`-BorrarDatos` y escribi `BORRAR-DEMO` cuando te lo solicite; el volumen
principal de MediLink no se toca.

## Advertencias importantes

- **Internet:** necesitas Internet tanto en la PC como en el telefono para
  usar el tunel. No sirve sin conectividad externa.
- **Disponibilidad:** el hostname de ngrok es estable pero el proceso no
  tiene uptime garantizado. Si se cae la conexion o se apaga la PC, Android
  no puede consultar datos aunque el APK siga publicado.
- **Acceso publico:** cualquier persona con la URL del tunel puede llamar a
  la API. Evita cuentas con contrasenas conocidas, datos personales, recetas,
  y credenciales reales. No lo dejes prendido innecesariamente.
- **Clave APK:** nunca publiques una llave de firma, ni archivos `.env.demo`,
  ni el authtoken de ngrok. Podes usar un repositorio privado, pero GitHub
  Releases tendra que ser publico si queres que cualquiera descargue el APK.
- **ngrok Free:** verificar limites vigentes: https://ngrok.com/docs/pricing-limits .
- **Siguiente etapa:** si necesitaran Android operativo 24/7 sin la PC,
  recien entonces corresponderia evaluar un hosting para backend y BD.
