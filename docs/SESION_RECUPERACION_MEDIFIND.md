# Sesión, recuperación y recetas de MediFind

Android y el dashboard muestran la marca MediFind; se conservan los identificadores técnicos y el applicationId para actualizar la instalación existente.

La app guarda el JWT y su vencimiento en preferencias privadas cifradas con AES-GCM. La clave está en Android Keystore y el backup de la app sigue deshabilitado. Minimizar, rotar o recrear el proceso no borra una sesión vigente. Logout, vencimiento (30 minutos), revocación o deshabilitación sí la eliminan. No se almacena la contraseña. Un error de red o una contraseña incorrecta no revoca el JWT actual.

## Recuperación de contraseña

Android y el login web incluyen **¿Olvidaste tu contraseña?**. El flujo pide el correo, envía un código de 8 dígitos y permite establecer una nueva contraseña. El código vence a los 15 minutos, admite hasta 5 intentos fallidos y se usa una sola vez. Se guarda su hash; el servidor aplica una espera de 60 segundos entre envíos por cuenta y 10 solicitudes por minuto y dirección de cliente. El cambio incrementa `token_version`, invalida las sesiones anteriores y registra auditoría sin códigos ni contraseñas. La migración V13 agrega la tabla de recuperación (V12 se conserva para el ajuste de vencimientos).

Endpoints públicos: `POST /api/auth/password-recovery` (`correo`) y `POST /api/auth/password-reset` (`correo`, `codigo`, `nuevaContrasena`). La solicitud responde con el mismo mensaje para correos existentes y desconocidos.

Con `docker compose -f docker-compose.yml -f android/compose.dev.yml --profile api up -d --build`, los correos se capturan en un buzón local: [Mailpit](http://127.0.0.1:8025). No se envían a casillas reales. El SMTP queda accesible solo dentro de la red de Docker y la interfaz del buzón se publica solo en loopback. [Documentación de Mailpit](https://mailpit.axllent.org/docs/install/docker/).

Para entrega real configurar `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` y `MAIL_STARTTLS=true` en `.env`, sin versionar credenciales. El compose base no configura un proveedor por defecto.

## Recetas

`GET /api/me/recetas-demo/configuracion` informa si el proveedor de escaneo está disponible. **Mis recetas** consulta esa capacidad antes de ofrecer el escaneo y explica la alternativa por código. El escaneo de recetas de demostración requiere `MEDLINK_RECETAS_ESCANEO_ENABLED=true`, `OPENAI_API_KEY` y `OPENAI_MODEL` válidos; no se inventan claves ni se simula una validación exitosa.

El paso de receta de una presentación **BAJO_RECETA** usa el mismo análisis que **Mis recetas**. Cámara y archivo se envían a `/api/me/recetas-demo/escaneo`, junto a la presentación y la cantidad elegidas. El backend verifica el documento y valida que autorice esa presentación y cantidad en una sola transacción. Un error conserva el adjunto en la pantalla y no vincula una receta incorrecta. El original queda guardado para su revisión por la farmacia. La vinculación por código valida también la presentación, vigencia y saldo antes de mostrar farmacias, y funciona sin IA.

La API de adjuntos para revisión manual sigue disponible para compatibilidad, pero el paso de reserva de Android ya no la utiliza como sustituto de la verificación. La farmacia sigue confirmando la reserva antes de entregar el medicamento.

Para probar, generar PDFs vigentes mediante `/api/admin/recetas-demo/lotes` y descargar el PDF individual de cada receta. Usar recetas ficticias emitidas por esta base; la verificación compara contra sus registros. Los PDFs de otra base o vencidos no son fixtures válidos. En la sesión local se prepararon recetas de amoxicilina y levotiroxina en `android/build/demo-recetas/`, con copias en Descargas del Pixel 10 Pro.
