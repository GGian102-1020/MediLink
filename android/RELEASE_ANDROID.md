# Distribución Android con QR desde `develop-web`

## Qué queda automatizado

La landing (`frontend/`) consulta la **última GitHub Release estable** al abrirse. Solo cuando
encuentra un asset llamado exactamente **`MediLink-android.apk`** habilita «Descargar Android
con QR». El QR y el botón directo apuntan siempre a:

`https://github.com/GGian102-1020/MediLink/releases/latest/download/MediLink-android.apk`

No requiere reeditar ni reconstruir el frontend por cada nueva versión; comprobá que
`frontend/.env.local` tenga `VITE_ANDROID_DOWNLOAD_URL` vacío o ausente (un override manual
deshabilita la detección automática). Si Releases está vacío,
el API público de GitHub no responde, o la release no tiene ese archivo, se enlaza a la página
de Releases y **no se anuncia que hay una descarga disponible**. El QR no instala aplicaciones
por sí solo: Android abre la descarga y el usuario autoriza la instalación si corresponde.

La publicación se automatiza con `.github/workflows/android-release.yml`: al subir un tag
`android-vX.Y.Z` que apunta a un commit de `develop-web`, GitHub Actions prueba Android,
comprueba que la API HTTPS responda a `/api/health`, arma el APK **release firmado**, verifica
la firma con `apksigner`, calcula SHA-256 y crea una GitHub Release pública con el nombre fijo.
**Nunca usa `app-debug.apk`**. No hay que fusionar `develop-web` con `main`: se usa un evento
`push` de tag, no `workflow_dispatch` (que necesitaría el workflow en la rama por defecto).

## Antes del primer release (REQUISITOS REALES)

1. **Contar con un endpoint HTTPS accesible** desde Android y GitHub Actions. NO hace
   falta comprar dominio: para entrega universitaria podes correr la API y BD
   aisladas en tu computadora y publicarla temporalmente con **ngrok Free**, que
   asigna un hostname HTTPS estable. Guia paso a paso:
   [`docs/DEMO_ANDROID_SIN_DOMINIO.md`](../docs/DEMO_ANDROID_SIN_DOMINIO.md).
   Alternativamente, una API alojada permanentemente tambien es compatible.
   Debe funcionar `GET https://tu-host-https/api/health`. Si es una demo,
   mantenela con datos ficticios y no expongas bases clinicas reales.
2. **Probar la app Android** conectada a ese backend. Validar registro, login, catálogo,
   reservas y conexión desde una red móvil externa. Si no hay backend accesible, **no publicar**:
   la descarga se haría, pero la app no podría funcionar correctamente.
3. Tener Java 17+, `keytool`, Android SDK 36, GitHub CLI autenticado (`gh auth login`),
   y permisos para gestionar Actions, Variables, Secrets y Releases del repo
   `GGian102-1020/MediLink`.
4. Conservar una **copia de seguridad fuera del repositorio** del archivo privado
   `.p12` y su contraseña. **No cambiar la clave de firma al actualizar la app**: Android
   rechazará las actualizaciones firmadas por otra clave.

## Configurar firma y URL (una sola vez, Windows PowerShell)

Desde `C:\Users\giuli\Projects\MediLink` en la rama `develop-web`:

```powershell
# Para universidad, iniciar ngrok sobre la API local y dejarlo abierto.
# La URL resultante NO requiere dominio propio.
$api = & .\scripts\demo\verificar-tunel.ps1
# O, si tenes hosting externo: $api = 'https://tu-host-https/'

# El script comprueba /api/health, crea una clave PKCS12 privada FUERA del repo
# (solo si no existe) y carga 4 secretos + la variable HTTPS en GitHub.
.\android\scripts\configurar-publicacion-github.ps1 -ApiBaseUrl $api

# Revisar SOLO nombres (GitHub nunca muestra el valor de los secretos).
gh secret list -R GGian102-1020/MediLink
gh variable list -R GGian102-1020/MediLink
```

Se registran los siguientes valores en GitHub Actions:

- `MEDILINK_API_BASE_URL` (Variable): URL pública HTTPS del backend, con `/` final.
- `MEDILINK_RELEASE_KEYSTORE_BASE64` (Secret): keystore PKCS12 en Base64.
- `MEDILINK_RELEASE_STORE_PASSWORD` (Secret): contraseña del almacén.
- `MEDILINK_RELEASE_KEY_ALIAS` (Secret): alias de la clave (`medilink-release`).
- `MEDILINK_RELEASE_KEY_PASSWORD` (Secret): contraseña de la clave.

El script solicita las contraseñas sin mostrarlas y envía los secretos a GitHub CLI por stdin;
no se escriben contraseñas ni el keystore en Git. Usar el mismo keystore en versiones futuras.
**No compartas esos secretos en chats ni issues.**

## Publicar `android-v0.2.0` (o siguiente versión)

Primero instalar el parche, hacer commit y push en `develop-web`. Confirmar tests Android
locales con el backend real. La versión incluida en `android/app/build.gradle.kts` es
`versionCode = 2` y `versionName = "0.2.0"`; el workflow exige que el tag coincida con el
`versionName`. Para la **siguiente** release hay que subir ambos números (`versionCode`
estrictamente creciente, `versionName` nuevo), confirmar esos cambios y luego crear el tag.

```powershell
Set-Location C:\Users\giuli\Projects\MediLink
git switch develop-web
git status -sb

# Cuando estés seguro y los secretos estén configurados:
.\android\scripts\publicar-version.ps1
```

El script compara tu commit local con `origin/develop-web`, impide tags repetidos y pide
escribir `PUBLICAR` antes del push. **Subir el tag lanza GitHub Actions**, lo que sí crea
una publicación pública si pasan todas las comprobaciones. No hace PR ni merge.

Podés seguir la ejecución en:

- [GitHub Actions](https://github.com/GGian102-1020/MediLink/actions)
- [GitHub Releases](https://github.com/GGian102-1020/MediLink/releases)

Cuando termine correctamente, verificar en Android:

1. Desde la landing, abrir «Descargar Android con QR».
2. Escanear el QR desde la cámara de otro celular Android, o tocar el botón en la misma pantalla.
3. Debe abrir la URL anterior y descargar el archivo **`MediLink-android.apk`**.
4. Android puede requerir «Permitir instalar aplicaciones de esta fuente» en el navegador
   o gestor de archivos. **El usuario debe aceptar voluntariamente**; no se puede instalar
   un APK silenciosamente por escanear un QR.
5. Abrir la app y verificar conexión real con `/api/health`, login y reservas usando datos de
   prueba apropiados para el entorno. No usar bases de datos con datos personales reales en
   pruebas de distribución.

Para verificar la integridad del APK descargado, comparar su SHA-256 con el archivo
`MediLink-android.apk.sha256` adjunto en la misma release.

## Diagnóstico

- **El QR aún lleva a Releases:** no existe release estable con el APK esperado, la llamada
  a GitHub no pudo completarse (red/CORS/límite de API), o aún estás viendo una versión
  anterior de la landing desplegada. Verificar `gh release view android-v0.2.0` y el asset.
- **El workflow no se ejecutó:** confirmar que se subió un tag `android-vX.Y.Z`, que apunta a
  `develop-web`, que Actions está habilitado en el repositorio y que el workflow está presente
  en el commit del tag. Tags no ejecutan workflows de otras ramas.
- **Falla en API/health:** revisar que Docker, ngrok y la PC esten encendidos
  (para demo), o que la API alojada responda. No usar `localhost`, IPs de red
  privada ni `example.invalid` en una version distribuida.
- **Falla `validateReleaseDistribution`:** secret o variable faltante, keystore inválido o
  URL de API no pública. Esta falla es intencional: **no existe fallback a firma debug**.
- **Falla al crear Release:** revisar que GitHub Actions permita escribir en Releases (token
  `contents: write`), que la política del repositorio no bloquee tags/releases y que el tag
  no tenga ya una release. El workflow no sobreescribe assets publicados.
- **La app se instala pero no conecta:** confirmar DNS público, TLS válido, backend y base de
  datos arriba, rutas y permisos. Instalar ≠ backend operativo.
- **Actualizar app instalada:** conservar el `applicationId`, la firma anterior y aumentar
  siempre el `versionCode`. El QR apunta a la última release, no fuerza actualizaciones.

## Seguridad y límites

El QR contiene una URL HTTPS de GitHub, **no ejecuta código** ni envía datos a terceros para
crear la imagen (se genera localmente con `qrcode.react`). La web solo consulta metadatos
públicos de GitHub Releases; no consume la API REST clínica. Para distribución real, revisar
la política de datos, privacidad, disponibilidad, seguridad del backend y la calidad funcional
de Android antes de habilitar a usuarios finales.

## Demostracion sin dominio ni hosting

En la facultad, **no es obligatorio montar una infraestructura de produccion**.
Usa la guia de [Docker aislado + ngrok Free](../docs/DEMO_ANDROID_SIN_DOMINIO.md).
GitHub aloja el APK; ngrok solo da conectividad HTTPS temporal a la API. Si
la PC se apaga, el APK se sigue descargando, pero la app no accede al backend.
