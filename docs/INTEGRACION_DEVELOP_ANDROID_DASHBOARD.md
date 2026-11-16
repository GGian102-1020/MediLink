# Integración Android + dashboard de operadores

Se preservan las aplicaciones MediLink existentes (backend, Android, portal web de visitantes/pacientes/administradores) y se incorpora el dashboard de operadores con vistas de reservas, stock, movimientos, actualización en vivo y confirmación de retiro mediante PIN.

## Uso del PIN

La reserva activa devuelve `pinRetiro` **solo al paciente titular** a través de la API autenticada. Se muestra en el detalle de la reserva web y Android. El operador debe solicitar al paciente el PIN para completar la operación; el backend limita los intentos incorrectos. El PIN no debe aparecer en logs, vistas de operador ni documentación con valores reales.

## Base de datos

La rama Android ya utiliza `V5__recetas_demo.sql` y `V6__receta_demo_verificacion.sql`. La nueva migración para el PIN se aplica como **`V7__pin_retiro_reserva.sql`**. No se debe ejecutar manualmente ni renumerar una migración que ya haya sido aplicada en una base de datos compartida. **Si una base ya ejecutó `V5__pin_retiro_reserva.sql` de la rama aislada del dashboard, no aplicar este parche sobre esa base sin diseñar una migración de reconciliación y verificar `flyway_schema_history`.**

## Pruebas

- Frontend: `npm ci`, `npm run build`, `npm test` desde `frontend/`.
- Backend: `./mvnw.cmd verify` desde `backend/` en Windows. Las pruebas PostgreSQL necesitan los recursos configurados (Docker/Testcontainers, según el caso).
- Android: `gradlew.bat testDebugUnitTest assembleDebug` desde `android/`, con SDK Android instalado y JDK compatible.

La integración se publica con PR contra **`develop`**, nunca contra `main`.
