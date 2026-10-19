# Farmacias y Stock — módulo implementado (MediLink v1.4.3)

Este archivo describe el código del módulo en el ZIP y **no sustituye** la
Especificación Maestra v1.4.3 (PDF). El catálogo de Medicamentos/Presentaciones
se incorporó posteriormente; su estado vigente figura en [CHANGELOG.md](CHANGELOG.md).
Las operaciones pendientes de Reservas no se dan por terminadas por la mera existencia de tablas.

## Implementación

- `backend/src/main/java/ar/edu/uade/medlink/farmacia/controller/FarmaciaController.java`:
  rutas de administración de Farmacia (`GET /api/admin/farmacias`, `POST`, `PUT`,
  desactivación y reactivación); requiere `ADMINISTRADOR`.
- `farmacia/service/FarmaciaService.java`: validaciones, estado funcional,
  operador activo, bloqueo pesimista en ediciones y auditoría.
- `farmacia/repository/FarmaciaIntegration.java`: consulta de operatividad y
  reservas activas funcionalmente (CV-04).
- `stock/controller/StockController.java`: lectura de saldos, movimientos y
  `PUT /api/farmacias/{id}/stock/{presentacionId}` con motivo obligatorio.
- `stock/service/StockService.java`: ajuste absoluto, bloqueo y composición
  transaccional; aplica expiraciones pendientes del mismo par antes de fijar
  el saldo (RN-04 / AC-38).
- `stock/repository/StockIntegration.java`: frontera con datos de Reservas,
  catálogo, usuarios y prescripciones que ya existen en el esquema;
  serialización con los locks definidos en sección 10.2.
- `stock/controller/DisponibilidadController.java`: disponibilidad pública por
  presentación, únicamente para stock positivo y entidades activas/operativas.
- `stock/entity/MovimientoStock.java`: trazabilidad automática o manual sin
  editar ni borrar el historial (RN-05 / RN-43).

## Requisitos normativos relacionados

RN-01..RN-08, RN-44, CV-04, CV-10, sección 6.3, secciones 10.1–10.4,
secciones 11.7–11.8, SEC-06 y AC-20/22/30/34/38/39/46/50.
La especificación distingue permisos de operador (su propia Farmacia) y de
administrador, y prohíbe operar funcionalmente una Farmacia inactiva.

## Pruebas y cierre

- `stock/StockServiceTest.java` y `stock/StockScopeTest.java`: unitarias.
- `farmacia/FarmaciaServiceTest.java`: reglas de administración.
- `stock/FarmaciaStockPostgresTest.java`: integración real, constraints,
  rollback, JWT, expiraciones y concurrencia. **Puede omitirse** si no hay Docker
  ni `MEDLINK_TEST_DB_URL`; verificar que se ejecutó realmente, no solo que
  Maven terminó en verde.
- `security/FarmaciaStockSecurityTest.java`: control de acceso a endpoints.

Desde `backend/`: `./mvnw test`. Para la fixture compartida,
`docker compose up -d` y la verificación opcional
`docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < scripts/verificar_seed.sql`
(desde la raíz). Los pasos de Flyway, HTTPS y backup están en `database/README.md`.

**Integración futura:** los módulos de creación/cancelación/retiro de Reserva
necesitan respetar locks, estados y atomicidad del contrato normativo. Este
archivo no los considera implementados.
