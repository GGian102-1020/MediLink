# Reservas — Implementación backend del MVP v1.4.3

## Alcance y base

Este cambio se aplica sobre la **versión del repositorio que ya incluye el parche de compatibilidad sistémica**. No reemplaza Cuentas, Catálogo, Farmacias, Stock, Prescripciones ni el planificador determinista de Suspensiones existente. No modifica el esquema PostgreSQL: aprovecha las tablas, constraints y triggers de `database/01_medlink_esquema.sql` gestionados por Flyway.

**IMPORTANTE:** el ZIP de origen no contiene una aplicación frontend React + TypeScript. Este cambio implementa **el backend de Reservas y pruebas**; la interfaz web del CU-04 a CU-12 debe construirse/integrarse cuando exista el frontend. Esta limitación no debe confundirse con cumplimiento integral del MVP.

## Endpoints nuevos

| Método | Ruta | Actor | Resultado / contrato |
|---|---|---|---|
| POST | `/api/reservas` | USUARIO | `Idempotency-Key: UUID` y `CrearReservaRequest`; 201 `ReservaDTO` |
| GET | `/api/reservas/mias` | USUARIO | `PageDTO<ReservaDTO>`; filtro `estado`, `sort`, `page`, `size` |
| GET | `/api/reservas/mias/{codigo}` | USUARIO titular | `ReservaDTO`; ajenas 404 |
| POST | `/api/reservas/{codigo}/cancelacion` | USUARIO titular | 200 `ReservaDTO`; vencida 409 |
| GET | `/api/farmacias/{id}/reservas` | OPERADOR de Farmacia / ADMINISTRADOR | `PageDTO<ReservaDTO>`, filtros `estado`, `desde`, `hasta` |
| GET | `/api/farmacias/{id}/reservas/{codigo}` | OPERADOR de Farmacia / ADMINISTRADOR | `ReservaDTO` sin correo ni teléfono de Usuario |
| POST | `/api/farmacias/{id}/reservas/{codigo}/retiro` | OPERADOR de Farmacia | 200 `ReservaDTO`; vencida 409 |
| POST | `/api/farmacias/{id}/reservas/{codigo}/cancelacion-operativa` | OPERADOR de Farmacia / ADMINISTRADOR | Causa normativa + reglas de restitución |
| GET | `/api/admin/usuarios/{id}/reservas` | ADMINISTRADOR | Historial paginado y filtrado conforme RN-47 |

La cancelación operativa admite `restitucionStock: "RESTITUIR" | "NO_RESTITUIR"` **solo** para `ERROR_ADMINISTRATIVO` u `OTRA_CAUSA`; `cantidadStockReal` (entero ≥ 0) se admite **solo** para `STOCK_INCONSISTENTE`.

## Reglas de negocio implementadas

- Se captura un único instante funcional en milisegundos (zona Argentina para respuestas) por operación; la vigencia de Reservas usa `[creacion, vencimiento)` con cuatro horas exactas.
- Solo USUARIO con Cuenta activa, sin Suspensión vigente, dentro de los límites (máximo 5 Reservas funcionalmente activas y máximo 3 unidades VENTA_LIBRE por Presentación en todas las Farmacias) puede crear una Reserva.
- Lock canónico `Cuenta Usuario → Usuario → Farmacia → Medicamento → Presentación → Stock → Prescripción → ItemPrescripción → Reserva`; relectura y protección de condiciones concurrentes. La Farmacia activa debe contar con Operador operativo.
- Reserva y descuento de Stock se confirman en una única transacción con MovimientoStock `RESERVA_CONFIRMADA`; BAJO_RECETA valida mediante `PrescriptionValidationService` y compromete Item de Prescripción en la misma transacción. Se guardan snapshots descriptivos y código visible Base32 Crockford de 12 caracteres con CSPRNG.
- Cancelación propia libera Stock y compromiso Rx sin generar incumplimiento. Cancelación operativa aplica la tabla normativa de causas (incluyendo `STOCK_INCONSISTENTE` como saldo absoluto), registra auditoría y conserva metadatos estructurados.
- Retiro exclusivo del Operador de la Farmacia: no vuelve a descontar Stock y transforma compromiso Rx en cantidad utilizada. La Prescripción válida al reservar no se revalida durante el retiro.
- Vencimiento materializado tras lock: libera Stock e Item Rx exactamente una vez y reevalúa Suspensiones con `ReservationClosureService`. GET expone `VENCIDA` incluso antes del scheduler (CV-10). El scheduler se ejecuta cada 30 s y maneja registros por transacción para posibilitar reintentos seguros.
- Idempotencia: clave UUID por Cuenta y endpoint, hash del request normalizado, replay del 201 almacenado; falla no consume clave. Se conservan los registros por al menos 24 h (no se purgan automáticamente).

## Pruebas agregadas

`backend/src/test/java/ar/edu/uade/medlink/reserva/ReservasPostgresTest.java`: **21 casos de integración**, incluidos última unidad bajo concurrencia, idempotencia concurrente, límites, Stock inconsistente con vencimiento pendiente, Rx comprometida/liberada/utilizada, prescripción anulada tras confirmar, protección de ámbitos, suspensión vigente y vencimiento exacto.

`scripts/verificar_tests_postgres.py` exige que la suite de Reservas se ejecute efectivamente sin errores ni `skipped`, además de las suites PostgreSQL existentes.

## Verificaciones pendientes antes del merge

1. Ejecutar desde `backend`: `mvnw.cmd clean verify` (Windows) o `./mvnw clean verify` (Linux/macOS) usando Java 21 y PostgreSQL de pruebas.
2. Desde raíz: `python scripts/verificar_tests_postgres.py` y comprobar que ninguna suite quede omitida.
3. Revisar en CI el workflow `.github/workflows/medlink-postgres-integration.yml`, que utiliza PostgreSQL 16.
4. Probar solicitudes HTTP reales, autorización por rol y peticiones simultáneas. Ejecutar además las regresiones preexistentes de Stock, Prescripciones, Cuentas y Suspensiones.
5. Desarrollar/integrar las pantallas frontend de Reservas cuando se incorpore React + TypeScript al repositorio.

**Estado de verificación en el entorno de preparación:** las clases de producción del módulo compilan con `javac` Java 21 y el classpath del backend empaquetado. Maven y las pruebas reales de PostgreSQL **NO se ejecutaron**: el Maven Wrapper no pudo descargar Maven 3.9.16. Esto no certifica todos los AC ni RNF de rendimiento.

## No cambios

No se modifican migraciones Flyway ni reglas RN-31..36 preexistentes. No se agregan pagos, emisión de recetas, precios, geolocalización ni otras funcionalidades excluidas en 2.3 del documento normativo.
