# Publicar un APK universitario para descargar desde el QR (sin servidor)

**Objetivo:** que otra persona abra la landing, escanee el QR y descargue/instale
`MediLink-android.apk` desde GitHub Releases. **No requiere ngrok, Docker,
dominio, hosting ni API pública.** La aplicación puede instalarse y abrirse;
**login, catálogo y reservas seguirán necesitando un backend accesible**.
Este APK no es una versión clínica ni debe utilizar datos personales reales.

## Qué está preparado

- `develop-web` contiene la landing con detección automática de la última GitHub
  Release **estable**, con el asset exacto `MediLink-android.apk`.
- `.github/workflows/android-release.yml` ejecuta pruebas de Android, compila
  **release**, lo firma con una clave **privada y estable**, verifica la firma,
  publica un SHA-256 y crea una release al subir `android-vX.Y.Z`.
- `android/app/build.gradle.kts` usa `https://api.example.invalid/` cuando no
  configurás API. `.invalid` es una URL reservada, no un servicio real; el APK
  en ese modo **no hará operaciones de red de MediLink con éxito**.
- El QR tiene URL permanente:
  `https://github.com/GGian102-1020/MediLink/releases/latest/download/MediLink-android.apk`.
  No hay que modificar la web después de publicar.

## Una sola vez: preparar la firma en GitHub

Desde PowerShell 5.1, en la raíz del proyecto, después de hacer commit y push
al parche de publicación:

```powershell
Set-Location 'C:\Users\giuli\Projects\MediLink'
git switch develop-web
gh auth status
.\android\scripts\configurar-publicacion-github.ps1
```

**No pases `-ApiBaseUrl`.** El script comprobará que el repo es público, creará
(si hace falta) un PKCS12 **fuera del repositorio**, en
`$HOME\Documents\MediLink-private\medilink-release.p12`, y registrará cuatro
secretos de firma en GitHub Actions usando GitHub CLI. Si existía la variable
`MEDILINK_API_BASE_URL` de intentos anteriores, la elimina para evitar que el
APK use un backend viejo. Usa `keytool` del JDK instalado/Android Studio; no hay
que instalar ngrok. Si no encuentra `keytool`, indicará cómo localizar Java.

**Importante:** hacé backup seguro del `.p12` y su contraseña, y **no los subas a
GitHub ni los compartas**. Perderlos impide actualizar instalaciones anteriores
manteniendo la misma identidad de firma. No incluyas el archivo `.p12` dentro
ningún parche Git.

## Primera publicación

Antes del tag, el código debe estar confirmado y subido a `develop-web` y las
pruebas pertinentes deben haber pasado. Después:

```powershell
Set-Location 'C:\Users\giuli\Projects\MediLink'
git status -sb
.\android\scripts\publicar-version.ps1
```

El script verifica branch, sincronización y secretos; muestra advertencia de
modo sin API y pide escribir **PUBLICAR** antes de subir la etiqueta.

En el proyecto actual la primera versión es **`android-v0.2.0`**. El `versionName`
del archivo Gradle debe coincidir con el tag, y `versionCode` debe aumentar en
nuevas versiones. Si el tag ya existe, no se sobrescribe: hay que preparar una
nueva versión, no reemplazar silenciosamente la anterior.

Seguí la compilación en:
https://github.com/GGian102-1020/MediLink/actions

Comprobá los assets al finalizar:

```powershell
gh release view android-v0.2.0 -R GGian102-1020/MediLink --json assets --jq '.assets[].name'
```

Se deben publicar `MediLink-android.apk` y `MediLink-android.apk.sha256`.
Si Actions falla, **no hay APK público** todavía: revisar los logs del job, no
considerarlo completado por el solo hecho de haber subido el tag.

## Prueba final: QR -> APK -> Android

1. Visitar https://github.com/GGian102-1020/MediLink/releases y confirmar
   que `android-v0.2.0` existe y tiene el APK.
2. Abrir la **landing desplegada** desde otra computadora o teléfono. Si solo
   está ejecutándose en `localhost`, los demás no podrán abrir la web (el APK
   sí se puede descargar desde GitHub directamente).
3. Pulsar «Descargar Android con QR»; el navegador consulta la última release
   y muestra el QR directo cuando confirma el APK.
4. Escanear con la cámara de Android o usar el enlace de descarga de la ventana.
5. Android descargará el APK; el usuario debe **autorizar voluntariamente** la
   instalación de esa fuente. No existe instalación silenciosa por QR.
6. Comprobar que se instala y abre en un Android de prueba (minSdk 26: Android
   8.0 o posterior). Las acciones con servidor mostrarán errores de red sin API.

Si un teléfono no puede acceder al enlace de GitHub Releases, comprobar conexión
Internet. Si el modal sigue diciendo «Ver QR y versiones», revisar que la
landing desplegada tenga el código actualizado y que GitHub API no esté
limitando las solicitudes.

## Más adelante: habilitar la API (opcional, no para descargar)

Cuando exista un backend público real, se puede configurar
`-ApiBaseUrl https://HOST-REAL/` y publicar una **nueva versión** firmada con
el mismo `.p12`. La URL debe ser HTTPS; no es obligatorio comprar un dominio.
Para una demo solo local se puede usar la guía
`docs/DEMO_ANDROID_SIN_DOMINIO.md`, pero **no es necesaria para este objetivo**.

No distribuir el APK como aplicación de salud de producción sin verificar
seguridad, disponibilidad del backend y tratamiento de datos.
