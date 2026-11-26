# MediFind — Landing pública y distribución local

Esta copia proviene de `develop-web` y vive separada del panel de farmacias en `frontend/`.
Para levantar **toda la demo local**, usar `../scripts/demo-local/Start.ps1` y consultar
`../docs/MEDIFIND_DEMO_LOCAL.md`. La configuración `localdemo` consulta un manifiesto
local, muestra la versión de pruebas y no accede a GitHub Releases. No publicar `dist-local/`.

Este frontend **solo** presenta MediFind y dirige al usuario a la aplicación **Android**.
No permite ingresar, registrarse, consultar stock ni gestionar reservas desde el navegador.
Es una SPA de una sola pantalla, con enlaces internos a `#inicio`, `#como-funciona` y `#descargar`.
La landing pública no consume la API REST ni necesita el backend para funcionar.

La marca comercial es **MediFind**. Los identificadores técnicos, el repositorio,
las URLs de Releases y el archivo `MediLink-android.apk` conservan sus nombres
para mantener la compatibilidad de la descarga.

## Desarrollo

Requiere Node.js >= 22.12.

```powershell
Set-Location .\landing
npm ci
npm run dev
```

Abrí `http://127.0.0.1:5174`. Para verificar y generar un sitio estático público:

```powershell
npm run typecheck
npm test
npm run build
# Salida: landing/dist/
```

`dist/` puede desplegarse en un hosting estático. El servidor web debe responder con
`index.html` para rutas desconocidas si se necesita redirigir enlaces antiguos;
no deben recrearse las pantallas anteriores. En producción este sitio es estático.
En la demo local, un servidor Node independiente sirve la landing y reenvía `/api`
al backend de loopback para Android, con acceso limitado a la red privada.

## Cómo funciona el botón y el código QR

El botón **«Ver QR y versiones»** (o **«Descargar Android con QR»** cuando hay APK)
abre una ventana accesible con QR local mediante `qrcode.react`, enlace directo y
opción para copiarlo. Funciona con Escape, clic fuera y teclado. No envía la URL
a servicios de QR de terceros.

**La landing ahora descubre automáticamente si hay APK disponible**, sin redeploy:

1. Una sola consulta HTTPS a `https://api.github.com/repos/GGian102-1020/MediLink/releases/latest`.
2. Si la última release pública, estable y no borrador incluye el asset `MediLink-android.apk`
   en estado `uploaded`, con tamaño mayor que cero, se habilita el enlace permanente:
   `https://github.com/GGian102-1020/MediLink/releases/latest/download/MediLink-android.apk`.
3. Si todavía no hay release o no se puede verificar (404, red o límite de GitHub API),
   se ofrece únicamente la página de GitHub Releases sin simular una descarga.

Se puede definir `VITE_ANDROID_DOWNLOAD_URL` con una URL HTTPS explícita si luego se
prefiere Google Play u otro canal: **esa configuración reemplaza la detección automática**.
Para GitHub Releases no configures la variable, dejala vacía. La descarga directa requiere
**publicar primero un APK release firmado**. No se necesita backend para **descargar ni instalar**; login, catálogo y reservas sí necesitan un servidor. Revisá `../android/RELEASE_ANDROID.md`.

Guía completa de publicación, firma segura, GitHub Actions, tag y pruebas desde Android:
[`../android/RELEASE_ANDROID.md`](../android/RELEASE_ANDROID.md).

## Alcance de este cambio

Se eliminaron las rutas, formularios, clientes REST, autenticación, mapas y pantallas
web de usuario/operador/administrador. Se retiraron sus dependencias y pruebas.
Se conservan `android/`, `backend/`, `database/`, la infraestructura de API y su
historial Git. Los documentos técnicos antiguos pueden mencionar al frontend web
anterior como referencia histórica.
