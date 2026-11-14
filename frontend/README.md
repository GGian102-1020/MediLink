# MediLink — Landing pública

Este frontend **solo** presenta MediLink y dirige al usuario a la aplicación **Android**.
No permite ingresar, registrarse, consultar stock ni gestionar reservas desde el navegador.
Es una SPA de una sola pantalla, con enlaces internos a `#inicio`, `#como-funciona` y `#descargar`.
No consume la API REST ni necesita el backend levantado para funcionar.

## Desarrollo

Requiere Node.js >= 22.12.

```powershell
Set-Location C:\Users\giuli\Projects\MediLink\frontend
npm ci
npm run dev
```

Abrí `http://127.0.0.1:5173`. Para verificar y generar un sitio estático:

```powershell
npm run typecheck
npm test
npm run build
# Salida: frontend/dist/
```

`dist/` puede desplegarse en un hosting estático. El servidor web debe responder con
`index.html` para rutas desconocidas si se necesita redirigir enlaces antiguos;
no deben recrearse las pantallas anteriores. **La API no debe exponerse ni proxyearse
por este sitio**. El backend continúa funcionando para los clientes Android.

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
