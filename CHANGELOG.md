# Changelog — MediLink

Registro de los cambios efectivamente realizados en el alcance de las tarjetas **Catálogo de medicamentos**, **Cuentas y autenticación** y **Farmacias y stock**, más la **configuración PostgreSQL**. Se toma la especificación maestra del MVP **v1.4.3** como criterio de comparación, sin declarar una nueva versión del producto ni incorporar funcionalidades pendientes de otros módulos.

Convenciones: **Implementado** = cambio incorporado en el código; **Verificado** = comprobado por las pruebas indicadas; **Pendiente** = sin evidencia suficiente para darlo por cerrado. El resultado de las pruebas de abajo corresponde al registro de Maven compartido el **19/10/2026**, no a una ejecución independiente contra una base PostgreSQL.

## [Sin publicar] — 2026-10-19: limpieza final de archivos y distribución completa

- Se retiraron los tres informes de revisión fechados el 14/10/2026, porque describían versiones obsoletas y confundían el estado actual. Las referencias del README se reemplazaron por documentos vigentes.
- Se eliminó `BackendApplicationTests.java`: solo comprobaba que existía una clase Java, sin arrancar Spring ni verificar comportamiento; **no se eliminó ninguna prueba funcional**. La ejecución posterior conservada en los informes Maven incluye 99 pruebas, con 79 aprobadas y 20 de integración omitidas.
- Se eliminaron `.github/modernize/java-upgrade/` (hooks de modernización no referenciados por el proyecto) y `.vscode/settings.json` (preferencia local del editor) para no exigir herramientas ajenas al desarrollo del backend. Se añadieron exclusiones del editor y archivos de sistema a `.gitignore`.
- Se retiró `Logo Medlink.png`: recurso gráfico de aproximadamente 793 KiB sin referencia en este backend ni en su documentación vigente. Se debe conservar una copia por separado si el equipo lo utiliza en entregables externos.
- Se revisaron dependencias y referencias cruzadas. Se mantuvieron todos los archivos necesarios de Maven Wrapper, pruebas útiles, Java de producción, SQL de inicialización, seeds, Flyway, Docker Compose, gateway, contratos y seguridad.
- Se convirtió la entrega en un ZIP **completo** sin `.git`, `.env`, bases de datos locales, backups ni `target`. Se actualizó la guía de sustitución para GitHub Desktop.
- No se modificaron las reglas de negocio, firmas de API, bloqueo transaccional, esquema PostgreSQL ni migraciones Flyway de esta etapa. Las 20 pruebas PostgreSQL requieren validación efectiva; no se afirma que hayan pasado.

## [Sin publicar] — Refactorización de los módulos del Trello y SQL (posterior a la corrección)

- Se desacopló `PageDTO` de `AdminDtos` y se preservó su estructura JSON en un contrato compartido (`common/PageDTO.java`).
- Se unificó la comprobación de rangos de paginación para listados JPA/JDBC (`PageRequests.validate`) y la validación de motivos administrativos (`RequestValidation.requiredReason`).
- `AuthService.me()` delega la lectura de Suspensiones a `AccountRepository.activeSuspension()` sin exponer `JdbcTemplate` al servicio.
- La creación de Farmacia evita consultar operadores que todavía no pueden existir y conserva `operativa=false`.
- Se declaró la carga de la relación `ManyToOne` Medicamento en los listados de Presentaciones (`@EntityGraph`) para prevenir consultas adicionales por elemento; el rendimiento real queda pendiente de medición en PostgreSQL.
- Se centralizaron las rutas GET públicas utilizadas por Spring Security y por el filtro Bearer, manteniendo los controles de rol.
- Se agregaron pruebas de regresión para contrato paginado, validación común, rutas públicas, asociación cargada y perfil de Usuario.
- Se actualizaron README y documentos desactualizados para corregir referencias al Catálogo; los informes históricos se retiraron del árbol activo en la limpieza posterior.
- **No se modificaron** las migraciones históricas, índices PostgreSQL, triggers, bloqueos de Stock ni funcionalidades ajenas al Trello. Las 20 pruebas PostgreSQL siguen sin evidencia de ejecución. Los informes Surefire incluidos en el ZIP recibido del equipo el 19/10/2026 registran **99 pruebas, 79 aprobadas, 0 fallos, 0 errores y 20 omitidas**; esto no certifica PostgreSQL.

## [Sin publicar] — 2026-10-19

**Rama de trabajo:** `fix/trello-postgresql`

**Tipo:** correcciones e integración de módulos existentes (sin ampliación de alcance).

### Corregido — Catálogo de medicamentos

- Se sustituyeron archivos Java originales con extensiones `.JAVA`/`.Java`, nombres y paquetes inconsistentes por clases `.java` con nombres válidos para compilación e integración con Spring.
- Se corrigieron errores de sintaxis, referencias a métodos inexistentes, imports y nombres de clase/constructor del catálogo.
- Se alineó la entidad `Medicamento` con las columnas y el tipo enumerado de PostgreSQL (`estado`), eliminando del modelo persistente las diferencias que impedían su correspondencia con la tabla.
- Se alineó `PresentacionMedicamento` con los identificadores, relaciones, cantidad de envase, condición de dispensa y estados definidos en el modelo físico.
- Se corrigió el enum `CondicionDispensa` y se incorporaron enums de estado de Medicamento y Presentación.
- Se reorganizaron repositorios, DTO, servicios y controladores para separar consultas públicas de operaciones administrativas, con rutas REST y controles del rol `ADMINISTRADOR` acordes a la especificación.
- Se implementó la normalización y validación de nombres y textos estructurales del catálogo, aplicando los límites de longitud después de normalizar.
- Se contemplaron búsquedas parciales de Medicamentos sin distinguir mayúsculas ni acentos; las expresiones de búsqueda tratan `%`, `_` y `!` como texto literal cuando se ingresan en el filtro.
- Se contempló la unicidad estructural de **Presentaciones activas** durante la creación y reactivación, con mapeo del conflicto a `PRESENTATION_DUPLICATE`. La garantía real de la restricción PostgreSQL aún requiere ejecución de integración.
- Se preservó la inmutabilidad de los campos estructurales de Presentación, el tratamiento de estados activo/inactivo y el registro de auditoría de operaciones administrativas.
- Se corrigió una restricción excesiva: el alta administrativa de una Presentación no se rechaza por el solo hecho de que el Medicamento asociado esté inactivo. Esa condición sigue afectando su visibilidad y capacidad de reserva según la especificación.

**Archivos representativos:** `backend/src/main/java/ar/edu/uade/medlink/medicamento/` y `backend/src/main/java/ar/edu/uade/medlink/presentacion/`.

### Corregido — Cuentas y autenticación

- Se escaparon los metacaracteres SQL (`%`, `_`, `!`) del filtro administrativo por correo electrónico para que la búsqueda literal no amplíe accidentalmente el conjunto de resultados.
- Se evitó agregar un segundo ordenamiento `id ASC` cuando el cliente ya especificó ordenamiento por `id`, preservando la dirección solicitada.
- Se mantuvo la implementación existente de registro, autenticación, JWT, revocación, cambio de contraseña y administración de cuentas; **este conjunto de cambios no reimplementó esos mecanismos**.

**Archivos representativos:** `backend/src/main/java/ar/edu/uade/medlink/admin/AdministrationService.java` y prueba asociada.

### Mejorado — Farmacias y stock

- Se optimizó el cálculo de la condición `operativa` en el listado administrativo de Farmacias: se determina para todos los identificadores de una página mediante una consulta agrupada, evitando una consulta adicional por cada Farmacia.
- Se preservó la regla de que una Farmacia sea operativa solamente si está activa y dispone de al menos un Operador con Cuenta activa.
- Se conservó la lógica existente de ajustes de Stock, movimientos, autorización por Farmacia y tratamiento transaccional. **No se añadieron operaciones de Stock ni flujos nuevos de Reserva.**
- Se amplió la prueba de servicio de Farmacias para cubrir el cálculo por página.

**Archivos representativos:** `FarmaciaIntegration.java`, `FarmaciaService.java` y `FarmaciaServiceTest.java`.

### Agregado y documentado — PostgreSQL

- Se añadió `CREATE EXTENSION IF NOT EXISTS unaccent` a `database/01_medlink_esquema.sql` para inicializaciones nuevas.
- Se añadió la migración Flyway `backend/src/main/resources/db/migration/V2__extension_busqueda_catalogo.sql` para instalaciones donde corresponda aplicar migraciones posteriores a V1, sin modificar retroactivamente la migración inicial.
- Se documentó en `database/README.md` cómo verificar la extensión en una base existente y por qué no debe borrarse el volumen de datos para corregir la migración.
- Se preservó el esquema existente de tablas, claves, índices y triggers fuera de la adición de `unaccent`; no se reconstruyó ni reinicializó ninguna base de datos del usuario.

**Nota:** la existencia de los SQL y de la migración no acredita que ya se hayan ejecutado con éxito en una instancia PostgreSQL real.

### Pruebas incorporadas o ajustadas

- Se agregaron `MedicamentoServiceTest.java` y `PresentacionServiceTest.java` para validaciones y reglas de los servicios de catálogo.
- Se agregó `CatalogoPostgresTest.java` para comprobar el catálogo contra PostgreSQL real (ejecución pendiente en el entorno compartido).
- Se agregó `CatalogoSecurityTest.java` para comprobar las consultas públicas y la restricción de operaciones administrativas por rol.
- **Corrección posterior al ZIP y al parche inicial:** se ajustó `CatalogoSecurityTest.userAndOperatorCannotModifyCatalog` para enviar una Presentación con cuerpo válido y verificar `403 FORBIDDEN` sin confundir ese caso con `400 VALIDATION_ERROR` por campos faltantes.
- Se ampliaron `AdministrationServiceTest.java` y `FarmaciaServiceTest.java` en los casos afectados.
- Se registró además el permiso de ejecución de `backend/mvnw` para plataformas donde dicho permiso es aplicable.

### Validación realizada (evidencia informada por Maven)

Ejecución comprobada en Windows: `.\mvnw.cmd -B clean test`, **19/10/2026**.

| Control | Resultado registrado |
| --- | --- |
| Compilación del backend | **Correcta**: 61 archivos fuente, compilados con `--release 21` |
| Compilación de tests | **Correcta**: 18 archivos fuente de pruebas |
| Resultado Maven | **BUILD SUCCESS** |
| Total de pruebas contabilizadas | **94** |
| Aprobadas y ejecutadas | **74** |
| Fallidas | **0** |
| Errores | **0** |
| Omitidas | **20** (18 de stock/farmacias PostgreSQL y 2 de catálogo PostgreSQL) |
| Entorno de ejecución informado | Java 25.0.3; compilación configurada para Java 21 |

La reconstrucción limpia resolvió los errores pasajeros de clases no encontradas (`NoClassDefFoundError`) observados en una ejecución incremental anterior. En la última ejecución, las tres pruebas de `CatalogoSecurityTest` finalizaron sin fallos.

Las 20 pruebas de integración PostgreSQL **NO pasaron ni fallaron: no se ejecutaron** porque Testcontainers no encontró un entorno Docker disponible. `BUILD SUCCESS` no equivale a certificación de PostgreSQL.

### Pendiente de validación / decisiones no cerradas

- **PostgreSQL real:** ejecutar las 20 pruebas actualmente omitidas y verificar las restricciones físicas, búsquedas con `unaccent`, auditoría, movimientos y escenarios de concurrencia que correspondan a los módulos existentes.
- **Migraciones e inicio:** verificar Flyway V1/V2 según el estado real de la base, el arranque de Hibernate con validación del esquema y la disponibilidad de las consultas REST contra PostgreSQL. No ejecutar borrados de volúmenes ni cambios destructivos para hacer pasar pruebas.
- **Datos y recuperación:** ejecutar y acreditar una restauración completa en una base separada (RNF-12 / AC-59).
- **Arquitectura de Cuentas:** `AccountRepository` y parte de `AdministrationService` continúan utilizando `JdbcTemplate`; permanece la diferencia con Spring Data JPA/Hibernate indicado en la sección 13 de la especificación. No fue modificado ni dado por resuelto.
- **Verificaciones adicionales:** rendimiento p95 y otras observaciones no bloqueantes siguen sin medición real. No se declaran cerradas por la compilación o por pruebas unitarias.

### Fuera del alcance de este registro

No se añadieron pantallas del frontend, compra/pagos, notificaciones, geolocalización ni los flujos pendientes de **Reservas** o **Prescripciones** del MVP. Este changelog no los marca como implementados o verificados. Tampoco acredita que el MVP completo esté terminado.

### Referencias internas

- [Detalle técnico de las correcciones y trazabilidad de hallazgos](CORRECCIONES_TRELLO_SQL.md).
- [Configuración de PostgreSQL y procedimiento de backup/restauración](database/README.md).
- Especificación normativa utilizada: **Medlink v1.4.3 (27/09/2026)**, especialmente CV-07/CV-09, RN-01..11, RN-37..44, secciones 10 a 13 y AC relacionados.

---

**Criterio de cierre:** este registro documenta cambios de código y resultados de pruebas observados. No debe usarse para declarar la integración PostgreSQL ni las tres tarjetas del Trello cerradas hasta obtener las evidencias pendientes.
