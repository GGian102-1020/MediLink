# MediFind Cloud rc.2 publicada

Publicación autorizada y verificada el **26/11/2026, hora de Argentina**. Es **preliminar**, no draft ni Latest. Se conservaron rc.1 y la estable `android-v0.2.0`; esta última continúa siendo Latest.

| Acceso | URL |
| --- | --- |
| Release | https://github.com/GGian102-1020/MediLink/releases/tag/cloud-v1.0.0-rc.2 |
| Landing y QR | https://medifind-ggian102.pages.dev |
| APK | https://github.com/GGian102-1020/MediLink/releases/download/cloud-v1.0.0-rc.2/MediFind-cloud-1.0.0-rc.2-40719fe8ec0d.apk |
| Checksum | https://github.com/GGian102-1020/MediLink/releases/download/cloud-v1.0.0-rc.2/MediFind-cloud-1.0.0-rc.2-40719fe8ec0d.apk.sha256 |

La APK congelada tiene 13.674.787 bytes y SHA-256 `40719fe8ec0deedac93cd34a187c13a68f317cd53bc6ae02ea9132f0229b02d7`. ApplicationId `ar.edu.uade.medilink.android.cloud`; versionCode **4**, superior a **3** de rc.1; versión `1.0.0-cloud.2`. Firma RSA 3072 / APK v2, certificado SHA-256 `b2ce4dc41b34fd010505939e023c53d88b99deb269eed10ea37d80d0f2e161f2`, idéntico al de rc.1. Backend real: `https://medifind-api-ggian102-mey9.onrender.com/`. Tag sobre `864a7c5b7a364a66f8ca862b6973c3130528a8ab`; se publicó el mismo archivo probado, sin recompilarlo ni cambiar sus bytes.

## Comprobaciones finales

- Ambas firmas verificadas; identidad y versiones compatibles. En un fixture del emulador se instaló rc.1 y se inició una sesión real. Luego se actualizó con `adb install -r` a rc.2, sin borrar datos ni desinstalar durante la actualización. Coincidió la huella del token antes/después, sobrevivió a recrear la actividad y permitió consultar `/api/me` en Render. Los bytes del APK instalado coinciden con el publicado. No se actuó sobre el Samsung ni LAN.
- `CloudCaptureTest` aprobó foto/PDF sintéticos, vista previa local, cancelación y bloqueo de análisis en Mis recetas y en adjuntar receta para una reserva. `CloudRequestProbe`, solo en instrumentación, observó las fábricas de los dos clientes HTTP y delegó todas las peticiones sin bloquearlas ni alterarlas: controles positivos de login/configuración, **cero intentos de transferir documentos o llamar a OpenAI**, y ninguna mutación aparte de login/logout. No leyó cuerpos/cabeceras ni forma parte del APK público. Render continúa Free, con el flag false y sin clave OpenAI.
- Se descargaron anónimamente APK y checksum; SHA-256 y firma válidos. Chrome descargó además el archivo desde el botón de la landing: idéntico tamaño y hash. Los píxeles del QR se decodificaron en escritorio y móvil y contienen exactamente ese mismo enlace. Cero solicitudes a `releases/latest`, errores de página o desbordamiento móvil. Se conservan los 40 tests unitarios y las pruebas anteriores de mapa, ubicación, sesión y navegación sobre esta misma candidata.
- Solo se recompiló la landing: 14 pruebas, TypeScript y auditoría aprobados. Pages de producción `04b0c67b-105c-4c1b-a98e-8ee332cb23d0`, rama `main`. No se añadieron Functions/Workers ni recursos pagos. La primera comprobación inmediata al despliegue se repitió cuando el dominio de producción ya servía el nuevo bundle; la comprobación completa final fue satisfactoria.
- Paneles: login de operador/admin, stock/reservas, administración, rechazo por rol/farmacia —incluida escritura ajena— y CORS negativo aprobados. Después de actualizar la landing, ambos paneles conservan exactamente el HTML de sus artefactos anteriores. API health original y catálogo de 12 medicamentos activos comprobados después de publicar.
- Neon, solo lectura: 22 tablas, 30 claves foráneas, 35 triggers, cinco farmacias con coordenadas, cero constraints sin validar, triggers deshabilitados, migraciones fallidas o stock negativo. Lectura: 32 reservas y 13.860.864 bytes. No se restauró ni modificó Neon en esta etapa.
- Local: 21/21 tablas preservadas, fuentes Android/backend idénticos a `da344f1`, APK LAN conserva SHA-256 `91da1ba0eb568ba204716e87e677390e1db374135e8dd149b03f78ff887a3ab6`. No se cambiaron secretos, JWT, servicios, planes o facturación; el backend no se redesplegó.

## Actualizar el Samsung

1. Abrir o **recargar** la landing en el teléfono y descargar la APK rc.2 con botón o QR.
2. Abrir el archivo. Si Android lo solicita, permitir instalar desde ese navegador/gestor de archivos.
3. Elegir **Actualizar** sobre MediFind Cloud. **No desinstalar ni borrar sus datos o almacenamiento**.
4. Abrir la aplicación. Una sesión aún vigente debería conservarse, según la prueba realizada. Si venció o fue revocada, se deberá iniciar sesión nuevamente: actualizar no amplía el vencimiento del JWT.

No se necesita USB ni modo desarrollador. La app LAN sigue siendo independiente. El usuario realizará las pruebas físicas de cámara, PDF, GPS, marcadores y Waze en su Samsung; no se declaran completadas aquí. El análisis IA sigue intencionalmente bloqueado, y el correo cloud permanece sin configurar.

Evidencias en `.local-cloud/`: `rc2-publication-preflight.json`, `cloud-capture-network.json`, `cloud-upgrade-tests.json`, `rc2-neon-integrity.json`, `pages-ui-tests.json`, `rc2-final-public-check.json`, `release-download-check.json`, `landing-qr-tests.json`, `rc2-release-approval-checks.json`. Keystores, contraseñas, JWT y dumps no se publicaron.
