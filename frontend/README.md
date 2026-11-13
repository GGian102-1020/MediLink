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

El botón **«Ver QR y versiones»** de la sección Android abre una ventana accesible con:

- Un **QR escaneable** que se genera localmente en el navegador con `qrcode.react` (SVG, sin APIs de terceros).
- Un botón **«Abrir GitHub Releases»** para quienes están mirando la landing desde el celular.
- Un botón para copiar el enlace, más cierre mediante Escape, clic fuera de la ventana o la cruz.

**Mientras no haya APK público, el QR solo lleva a GitHub Releases, no descarga ni instala nada.**
Cuando se configure `VITE_ANDROID_DOWNLOAD_URL`, el QR y el enlace directo apuntarán
**a esa misma URL HTTPS**, sin necesidad de cambiar código. No es posible escanear
un QR mostrado en el mismo celular con su propia cámara: en ese caso se usa «Abrir enlace».

## Habilitar el botón de descarga de Android

1. Preparar y publicar una **versión de producción** de Android: configurar
   un `BASE_URL` HTTPS real en la variante release, firmar el paquete y probarlo
   contra el backend publicado. Consultar `../android/README.md` y
   `../android/app/build.gradle.kts`. **No publicar `app-debug.apk`:** usa
   `http://localhost:18081/` y depende de `adb reverse`.
2. Subir el APK firmado a [GitHub Releases](https://github.com/GGian102-1020/MediLink/releases)
   (o distribuir en Google Play); copiar su URL HTTPS de descarga.
3. Copiar `.env.example` a `.env.local` y definir `VITE_ANDROID_DOWNLOAD_URL`
   con la URL de distribución, **sin comillas**. En producción, pasar la misma
   variable de entorno al proceso de build y volver a ejecutar `npm run build`.

Mientras la variable esté vacía o sea inválida, el botón abre un modal con el QR
de GitHub Releases y la página explica que todavía no existe una descarga pública. **Configurar la URL
no genera ni firma un APK**, solo habilita el vínculo a una versión ya publicada.

## Alcance de este cambio

Se eliminaron las rutas, formularios, clientes REST, autenticación, mapas y pantallas
web de usuario/operador/administrador. Se retiraron sus dependencias y pruebas.
Se conservan `android/`, `backend/`, `database/`, la infraestructura de API y su
historial Git. Los documentos técnicos antiguos pueden mencionar al frontend web
anterior como referencia histórica.
