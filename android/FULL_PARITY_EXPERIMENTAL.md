# MediLink Android — experiment/android-full-parity

Este documento describe el alcance **experimental** de la ampliación del MVP con
paridad de capacidades frente al frontend React del mismo repositorio.

> No se debe fusionar a `main` ni distribuir como aplicación médica/producción
> únicamente por el hecho de que Gradle compile. Primero hay que probar las operaciones
> reales con los tres roles, en una base **exclusivamente de demostración**.

## De dónde sale esta rama

La rama `experiment/android-full-parity` se crea desde `feat/android-compose-mvp`.
No es necesario que la primera rama se haya fusionado a `main`.
El diff de esta rama debe contener solo archivos dentro de `android/`.

## Qué añade respecto del MVP

| Web (React) | Android experimental | Endpoint de Spring Boot |
| --- | --- | --- |
| Landing pública | Inicio público móvil | — |
| Catálogo y presentaciones | Se conserva el MVP | `/api/medicamentos`, `/api/presentaciones` |
| Mapa con farmacias | Leaflet integrado dentro de WebView | Disponibilidad y proveedores cartográficos externos |
| Ubicación y búsqueda manual | Permiso de ubicación aproximada y Geocoder del sistema | `/api/presentaciones/{id}/disponibilidad?lat=&lng=` |
| Recorridos a pie / auto | OSRM + pasos y duración; enlace externo alternativo | Proveedores de rutas HTTPS de demostración |
| Dashboard de paciente | Resumen de catálogo y reservas | `/api/me`, `/api/reservas/mias` |
| Perfil y suspensión vigente | Datos vivos del servidor | `/api/me` |
| Cambio de contraseña | Formulario y confirmación | `POST /api/auth/change-password` |
| Registro con contraseña repetida | Formulario y validación | `POST /api/auth/register` |
| Filtro de reservas por estado | Filtros en listado | `GET /api/reservas/mias?estado=` |
| Detalle, tiempo restante y cancelación | Pantalla individual de reserva | `GET /api/reservas/mias/{codigo}`, `POST /api/reservas/{codigo}/cancelacion` |
| Operador: dashboard y reservas | Pantallas por farmacia, lista y consulta por código | `/api/farmacias/{id}/reservas` |
| Operador: confirmar retiro | Acción confirmada; solo operador | `POST /api/farmacias/{id}/reservas/{codigo}/retiro` |
| Operador: cancelación operativa | Causa, motivo, stock real y política de restitución | `POST /api/farmacias/{id}/reservas/{codigo}/cancelacion-operativa` |
| Operador: stock y movimientos | Listado, alta/ajuste de saldos, historial | `/api/farmacias/{id}/stock`, `/movimientos` |
| Admin: farmacias | Crear, editar, estados, filtros | `/api/admin/farmacias` |
| Admin: medicamentos | Crear, editar, estados, filtros | `/api/admin/medicamentos` |
| Admin: presentaciones | Crear, estados, filtros y detalle | `/api/admin/presentaciones` |
| Admin: operadores | Crear, editar datos, habilitar/deshabilitar | `/api/admin/operadores`, `/api/admin/cuentas` |
| Admin: usuarios | Filtros, detalle, habilitación y deshabilitación | `/api/admin/usuarios`, `/api/admin/cuentas` |
| Admin: reservas y suspensiones de usuario | Historial por usuario y filtros | `/api/admin/usuarios/{id}/reservas`, `/suspensiones` |
| Admin: prescripciones simuladas | Crear múltiples ítems, consultar, anular con motivo | `/api/admin/prescripciones` |
| Admin: auditoría | Filtros y resúmenes de eventos | `/api/admin/auditoria` |
| Dashboard de operador/admin | Resúmenes y accesos | Endpoints de cada dominio |

## Diferencias deliberadas frente al navegador

- **No se copia el diseño CSS ni las tablas grandes de escritorio**: se presentan
  tarjetas, listas, filtros y formularios móviles.
- El mapa se integra con **Leaflet en un WebView** mientras el resto de la app sigue
  siendo Compose nativo. No se introduce una API key de Google ni se agrega un SDK
  MapLibre con API inestable en esta etapa.
- El buscador de direcciones utiliza el **Geocoder del sistema Android** en vez de
  enviar consultas a Nominatim directamente. Dependiendo del dispositivo podría no
  estar disponible; el mapa y el listado siguen funcionando sin GPS ni dirección.
- El origen aproximado se mantiene **solo en memoria** durante la ejecución de la
  aplicación; se puede borrar mediante «Olvidar mi origen» y se limpia al cerrar sesión.
- No se persiste el JWT: la sesión finaliza cuando se destruye el proceso, respetando
  que la API actual no ofrece refresh tokens.
- Los pasos de OSRM se muestran en castellano simplificado y requieren validación
  funcional antes de depender de ellos para guiar a una persona.
- No se agregaron push, QR, pagos, recetas reales, modo offline ni distribución en
  Play Store; no son funciones que el frontend web proporcione.

## Límites externos para la demostración

El mapa solicita scripts de Leaflet a una CDN y teselas públicas de OpenStreetMap.
Las rutas se consultan en servicios de OSRM de demostración. No hay garantía de
servicio, cuota o rendimiento; las condiciones de uso deben verificarse antes de
cualquier despliegue público. Para distribución se recomienda un proveedor de teselas
con condiciones adecuadas y un servicio de rutas gestionado por el equipo.

Pedir ubicación al usuario es opcional. Cuando el usuario pulsa «Calcular ruta», la
ruta externa recibe un origen aproximado y el destino. No usar datos de salud reales
ni publicar direcciones/personas identificables en pruebas.

## Verificación local en Windows

La rama experimental se debe crear desde `feat/android-compose-mvp` una vez que esta
última esté limpia y con su commit guardado. **No borrar ni reescribir el trabajo base**.

```powershell
Set-Location 'C:\Users\giuli\Projects\MediLink'
git switch feat/android-compose-mvp
git switch -c experiment/android-full-parity
# Aplicar el parche experimental proporcionado por separado, con git apply --check.

$env:JAVA_HOME = 'C:\Users\giuli\.jdks\jbr-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location .\android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

El APK sigue utilizando la URL local del MVP (`http://localhost:18081/`) en **debug**,
con Docker y `adb reverse tcp:18081 tcp:18081`. La variante **release** sigue apuntando
al dominio reservado `api.example.invalid` y NO está lista para distribuir.

## Matriz manual obligatoria antes de mergear

Sobre una base nueva/disponible de demo, probar:

1. **Sin iniciar sesión**: Inicio, catálogo, paginación, presentaciones, disponibilidad,
   apertura externa del mapa, mapa integrado, manejo sin permiso GPS, búsqueda de dirección,
   cálculo pie/auto, fallos de proveedores externos.
2. **Paciente**: registro con contraseñas distintas y coincidentes; login; panel;
   `/api/me` actualizado; cambiar contraseña; filtros de reserva; reservar venta libre;
   reservar con receta simulada; detalle; cancelación; reintentos de idempotencia;
   cierre de sesión.
3. **Operador**: login de rol; farmacia asignada; reservas activas y por código;
   confirmar retiro; cancelación con diferentes causas; restitución; stock nuevo y
   ajustes; historial de movimientos; intento de acceder a otra farmacia (debe
   rechazarlo el servidor).
4. **Administrador**: login; crear/editar/reactivar/desactivar farmacias y medicamentos;
   crear y gestionar presentación; crear/editar/deshabilitar operador; consultar usuarios,
   sus reservas/suspensiones; suspender/rehabilitar cuenta según permisos;
   crear prescripción con varios ítems; consultar saldos; anular con causa;
   filtrar auditoría, inspeccionar correlación y datos anterior/posterior;
   consultar operaciones de farmacia en modo administrador.
5. **Seguridad**: usuarios no pueden abrir rutas admin/operador; operadores no pueden
   confirmar retiro de otra farmacia; errores 401/403/409 no generan crashes;
   JWT en memoria únicamente; no hay secretos, tokens o APKs en el commit.
6. **Ciclo de vida**: rotación, volver de segundo plano, pérdida de conexión, permisos
   denegados y sesión vencida. Confirmar que el stock siempre lo decide el servidor.

`testDebugUnitTest` y `assembleDebug` son condiciones **necesarias, no suficientes**.
La equivalencia real exige verificar estas operaciones manualmente en emulador y
preferentemente en al menos un dispositivo físico.
