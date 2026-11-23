# Iniciar MediFind y probar recetas desde varias computadoras

Cada computadora ejecuta su propia API y base de MediFind. La clave de OpenAI puede ser la misma, pero se configura únicamente en el `.env` local de cada PC. No se incluye en el paquete de recetas, en el APK ni en los archivos del celular.

## Arranque

Con Docker Desktop y el SDK de Android instalados, desde la raíz del repositorio:

```powershell
.\scripts\iniciar-demo.ps1
```

El comando inicia PostgreSQL, backend, dashboard, gateway y buzón local; conecta el celular por ADB y abre la app debug instalada. Si no hay un dispositivo conectado, intenta iniciar `Pixel_10_Pro`. El dashboard queda en [localhost:5173](http://127.0.0.1:5173/panel), dentro de Docker, con `restart: unless-stopped`: cerrar el chat o una consola no lo apaga. `-SinCompilar` reutiliza las imágenes actuales. `-SinEmulador` evita iniciar un AVD nuevo.

Para una PC nueva, configurar primero `.env` y Android SDK. El backend crea el esquema con Flyway. Si la base está completamente vacía, cargar el catálogo y las cuentas ficticias siguiendo [ARRANQUE.md](ARRANQUE.md); no cargar seeds sobre una base con datos existentes. Instalar la app debug desde Android Studio. La app conecta por `adb reverse tcp:18081 tcp:18081`, sin exponer la API en la LAN.

## Archivos de recetas

En el celular quedan guardados:

- `Descargas/MediFind-Receta-Ficticia.pdf`: copia de la primera receta de amoxicilina.
- `Descargas/MediFind-Recetas-Demo/`: PDFs numerados de amoxicilina 250 mg/5 ml y levotiroxina 100 mcg, más `LEEME.txt`.

Los archivos persisten al cerrar la app y al desconectar el celular. Son recetas de demostración sin validez médica.

Para llevar el mismo paquete a otra computadora, copiar el ZIP `recetas-demo-pdf/MediFind-Recetas-*.zip` y ejecutar desde la raíz de MediLink:

```powershell
.\scripts\instalar-recetas-demo.ps1 -Paquete 'C:\ruta\MediFind-Recetas-AAAAmmdd-HHmmss.zip'
```

El paquete incluye también una copia del importador por si se recibe fuera del repositorio. El importador registra solamente las plantillas ficticias en la base local y copia los PDFs al dispositivo conectado. Resuelve los medicamentos por sus datos de catálogo; los IDs pueden ser distintos entre computadoras. Se requiere la misma versión del esquema y el catálogo de demostración. Se puede repetir la importación: no borra titulares, no restablece saldos ni modifica reservas.

Compartir la clave de OpenAI **no registra** las recetas en PostgreSQL. Sin esta importación, la otra base no reconoce los códigos impresos aunque el modelo pueda leer el PDF.

## Elegir recetas ya cargadas

Desde un medicamento bajo receta podés tocar **Elegir una receta de Mis recetas**.
Se muestran todas las páginas de recetas de tu cuenta; solo se pueden elegir las vigentes,
de esa presentación exacta y con saldo suficiente para la cantidad solicitada.
La API vuelve a comprobarlo al continuar y al reservar. No se vuelve a enviar el documento a OpenAI.

## Teléfono físico y ubicación

Para generar una APK que conecte por WiFi, en la misma red que esta PC:

```powershell
.\android\scripts\build-apk-lan.ps1
```

El script conserva el buzón de recuperación y el dashboard, y publica la API en el puerto 18081 de la PC.
La variante LAN usa la IP de esa computadora: si cambia de red/IP, hay que volver a generarla.
El emulador sigue usando la variante debug con ADB reverse.

Al pedir ubicación se ofrecen los permisos preciso y aproximado juntos. Para ordenar con mayor
exactitud elegir **Precisa**. Se buscan posiciones nuevas en los proveedores disponibles (GPS/red),
se descartan lecturas de más de 30 segundos y se espera hasta 25 segundos por una mejor posición.
La interfaz muestra la precisión estimada; con permiso aproximado puede abarcar varias cuadras.
El botón **Mi ubicación** consulta de nuevo y centra el mapa; una dirección buscada manualmente
se conserva hasta que se solicite otra ubicación. No se solicita ubicación en segundo plano.

## Integración de migraciones del 23/11/2026

Se conservó sin modificaciones `V12__ajuste_vencimiento_reserva.sql` del cambio `e7cd65f`.
La recuperación de contraseña local pasó a `V13__recuperacion_contrasena.sql`.
Una base nueva o que ya tenga el V12 de vencimientos se actualiza normalmente.
Solo esta base de desarrollo ya tenía aplicado el antiguo V12 de recuperación: se respaldó con
`pg_dump`, se comprobó su nombre/checksum y se renumeró esa entrada del historial a V13,
sin ejecutar de nuevo su SQL ni cambiar la tabla. Se aplicó el V12 faltante con `out-of-order`
una sola vez y se retiró esa excepción. No ejecutar `repair`, borrar tablas ni cargar seeds para resolverlo.

## Repetir las pruebas

Una receta se puede volver a analizar con su titular. Cada PDF autoriza una unidad: una reserva activa compromete ese saldo y un retiro lo consume. Cancelar la reserva libera lo comprometido. Para otra prueba completa después de un retiro, usar el siguiente PDF numerado. Las otras cuentas deben usar otro PDF; así se conserva la prueba de titularidad.

El paquete preparado contiene 20 recetas (10 de cada presentación), vigentes por 90 días desde su emisión. Para emitir otro paquete sin sobrescribir el anterior:

```powershell
.\scripts\preparar-recetas-demo.ps1 -Destino .\recetas-demo-pdf\portables-otro
.\scripts\instalar-recetas-demo.ps1 -Paquete .\recetas-demo-pdf\portables-otro
```

El preparador usa exclusivamente la API local y la cuenta administradora ficticia documentada del seed. Para otra cuenta, pasar `-CorreoAdmin` y `-ClaveAdmin` como `SecureString`. Exporta solo las plantillas recién generadas y los datos necesarios para reconocer sus presentaciones; nunca cuentas, claves, pacientes ni reservas.
