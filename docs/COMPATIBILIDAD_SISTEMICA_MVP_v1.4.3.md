# Compatibilidad sistemica del backend - Medlink v1.4.3

Rama: `fix/compatibilidad-sistemica-mvp-v1.4.3`
Origen: `main` (`4b6c368`, merge del PR #5 de Suspensiones).

## Alcance

Esta rama alinea **Suspensiones, consulta administrativa, Auditoria y la verificacion CI de Prescripciones** con la especificacion maestra v1.4.3. Los modulos de Catalogo, Cuentas, Farmacias/Stock y PostgreSQL se mantienen cuando su implementacion actual ya cumple las responsabilidades propias identificadas en la revision. No se reescriben modulos estables sin defectos verificables.

**No implementa ni modifica el modulo de Reservas**: no incluye endpoints de creacion/cancelacion/retiro, scheduler, idempotencia de reservas, sus movimientos de Stock, ni `GET /api/admin/usuarios/{id}/reservas`. Esas operaciones quedan para la futura rama de Reservas. Tampoco se modifica `ReservationClosureService`: su contrato preexistente para RN-40 y RN-31..36 se conserva.

## Cambios realizados

1. Retirada de los ocho archivos del commit `f84ddbb` (`hecho suspension`): utilizaban la entidad ausente `Paciente`, repositorio `UsuarioRepository` no existente, columnas distintas a PostgreSQL, estados y operaciones manuales prohibidas por RN-31..36. Evita endpoints de crear/levantar sanciones sin autorizacion por rol. No se borra ningun dato de la base.
2. Conservacion del planificador automatico `SuspensionPlanner` y la insercion historica de `ReservationClosureService`, que ya existian antes del commit anterior.
3. Nuevo endpoint **solo de consulta**, `GET /api/admin/usuarios/{id}/suspensiones`, protegido por `ADMINISTRADOR`; filtros `vigente`, `desde`, `hasta` (rango [desde,hasta) sobre `fechaHoraInicio`), `page`, `size`, `sort`. Usa `PageDTO<SuspensionDTO>`, fecha Argentina con offset, un solo instante T, paginacion, orden por inicio DESC/id DESC y validacion de filtros.
4. Tests unitarios y web del servicio y endpoint: permisos, historial, rangos, ordenamiento, filtros y errores de validacion. Ausencia de endpoints de creacion/levantamiento manual.
5. Auditoria de Prescripciones: los resumenes existentes ya producian codigo externo, usuario, fechas y cantidad de items, pero `AuditService` los descartaba por su lista permitida. Ahora retiene esos campos operativos de forma acotada, sin aceptar credenciales/JWT ni objetos anidados. Incluye test de regression.
6. La puerta de CI `scripts/verificar_tests_postgres.py` ahora exige tambien **PrescripcionPostgresTest** y **PrescripcionConcurrencyTest** (2 pruebas cada una), ademas de las suites previas de catalogo, stock y auditoria.

## Lo que no se declara verificado

- No se ejecuto Maven/Surefire completo: el Maven Wrapper intenta descargar Maven 3.9.16 de Maven Central y la descarga fallo en el entorno de preparacion.
- No se ejecuto PostgreSQL ni Docker: no estan disponibles aqui. Las migraciones y restricciones preexistentes se conservaron; no se altero el esquema.
- Se compilaron las clases de produccion cambiadas con `javac --release 21` y las bibliotecas incluidas en el JAR empaquetado. Tambien se compilaron los otros fuentes Java que no requieren generar getters de Lombok, usando las clases compiladas anteriores para las entidades. Esta verificacion parcial **no sustituye `./mvnw clean verify`**.
- Se ejecuto una prueba offline de consultas, filtros, autorizacion del servicio, Auditoria y planificador. Los nuevos tests JUnit estan incluidos, pero no se ejecutaron contra Maven.
- No se realizaron pruebas de extremo a extremo del ciclo de Reservas ni de su interaccion con suspensiones/stock/prescripciones.

## Verificaciones exigidas en el repositorio del equipo

1. `cd backend` y `./mvnw clean verify` (Windows: `mvnw.cmd clean verify`) con Java 21 y acceso a dependencias.
2. Con PostgreSQL local de pruebas o Testcontainers habilitado, ejecutar las suites de integracion, **sin skips** y sin reutilizar reportes viejos. Alternativamente, definir `MEDLINK_TEST_DB_URL`, `MEDLINK_TEST_DB_USER` y `MEDLINK_TEST_DB_PASSWORD` sobre una base exclusiva para tests.
3. Desde la raiz, `python scripts/verificar_tests_postgres.py` sobre los reportes nuevos.
4. Probar HTTP: Administrador puede consultar historial de Suspensiones; Usuario/Operador obtienen 403; no autenticado recibe 401; rutas manuales antiguas no existen; filtros invalidos devuelven `400 VALIDATION_ERROR`.
5. Verificar que Flyway + validacion Hibernate arrancan con esquema limpio; no borrar volúmenes productivos para forzar migraciones.

## Integracion futura (para quien implemente Reservas)

La implementacion de Reservas debe reutilizar el planificador historico y serializar la evaluacion por Usuario (RN-31..36, RN-12, 10.2). Este documento **no implementa** esa interaccion, ni da por aprobados los AC cuya accion requiere crear, cancelar, vencer o retirar Reservas. El endpoint de historial de suspensiones esta disponible independientemente de esos flujos.
