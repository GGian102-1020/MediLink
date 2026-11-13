# MediLink

> **Verificación local (26/10/2026):** se integró Auditoría sobre los componentes existentes. `mvnw.cmd clean verify`: **124 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**, incluidas **35 pruebas PostgreSQL reales** de Auditoría, Catálogo y Farmacia/Stock. Se probaron instalación/adopción Flyway V1..V3 y restauraciones completas en bases separadas. Evidencia, alcance e integraciones pendientes: [AUDITORIA_INFRAESTRUCTURA.md](AUDITORIA_INFRAESTRUCTURA.md). Esto no certifica funcionalidades aún pendientes del MVP ni una ejecución del workflow remoto.

**Documentos vigentes:** [CHANGELOG.md](CHANGELOG.md) · [CORRECCIONES_TRELLO_SQL.md](CORRECCIONES_TRELLO_SQL.md) · [REFACTORIZACION_TRELLO_SQL.md](REFACTORIZACION_TRELLO_SQL.md) · [database/README.md](database/README.md). Los informes obsoletos del 14/10/2026 se retiraron de la copia activa; el historial de Git permite recuperarlos si estaban versionados.

## Auditoría e infraestructura

`GET /api/admin/auditoria` permite al Administrador consultar registros de todos
los módulos con filtros, paginación y snapshots seguros. Se reutilizan
`AuditService`, JWT, errores REST, correlación y los triggers append-only.
Contrato, pruebas, backup/restauración y revisión del UML:
[AUDITORIA_INFRAESTRUCTURA.md](AUDITORIA_INFRAESTRUCTURA.md).
Los cambios están preparados para revisión grupal en `auditoria-infraestructura`;
no se realizaron commits ni publicaciones automáticas.

MediLink es un proyecto desarrollado para la materia **Seminario de Integración Profesional** de la carrera de Ingeniería en Informática de UADE.

El objetivo del sistema es facilitar la búsqueda de medicamentos disponibles en farmacias, reduciendo el tiempo y esfuerzo que implica consultar distintos establecimientos hasta encontrar stock.

Problema

Cuando una persona necesita adquirir un medicamento, no siempre sabe qué farmacias poseen disponibilidad en ese momento.

Esto puede provocar:

- Recorrer múltiples farmacias.
- Realizar varias llamadas o consultas.
- Trasladarse sin tener certeza de encontrar el medicamento.
- Perder tiempo buscando medicamentos de baja disponibilidad.

Solución

MediLink propone centralizar la información de disponibilidad de medicamentos en farmacias adheridas.

El usuario podrá:

1. Buscar un medicamento.
2. Seleccionar su presentación.
3. Consultar farmacias con stock disponible.
4. Seleccionar una farmacia.
5. Realizar una reserva temporal.
6. Retirar el medicamento presencialmente.

Para medicamentos que requieran receta, durante el MVP la validación será realizada mediante un servicio simulado.

Actores principales

- **Visitante:** puede buscar medicamentos y consultar disponibilidad.
- **Paciente / Usuario:** puede realizar y administrar reservas.
- **Operador de farmacia:** administra stock y reservas de su establecimiento.
- **Administrador:** gestiona los principales elementos del sistema.

Tecnologías

Backend
- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- JWT
- Maven

Base de datos
- PostgreSQL


Clientes actuales
- **Android** (`android/`): aplicación nativa Kotlin + Jetpack Compose para búsqueda, disponibilidad y reservas.
- **Web** (`frontend/`): landing pública React + Vite con inicio, explicación y enlace de descarga Android. No ofrece login, catálogo, reservas, operaciones ni administración web, ni consume la API.
- **Descarga Android**: depende de una release pública firmada y con backend HTTPS real; consultar [`frontend/README.md`](frontend/README.md).

Otras herramientas
- Git
- GitHub
- JUnit
- Mockito
- Trello


Arquitectura

La landing web se publica como **sitio estático**, independiente de la API. La funcionalidad de MediLink se concentra en la app Android:

```text
Landing pública (React + Vite) → enlace de descarga Android

App Android (Kotlin + Compose)
      ↓
   REST API (Spring Boot)
      ↓
Service / Lógica de negocio
      ↓
Spring Data JPA / JdbcTemplate
      ↓
PostgreSQL
```




Base de datos local

La configuración incluida permite levantar PostgreSQL mediante Docker Compose. Docker no es requisito para usar GitHub Desktop ni para revisar el código; las pruebas con Testcontainers sí requieren Docker u otra base de pruebas configurada. Desde la raíz del repo:

```bash
docker compose up -d
```

La base de datos vacía se migra automáticamente mediante Flyway al iniciar la API.
La carga de cuentas y stock de demostración es **manual y solo para una base local descartable**.
No se deben cargar seeds en bases de datos compartidas ni utilizar las credenciales ficticias en un entorno expuesto.

Guías vigentes: [frontend/README.md](frontend/README.md) para la landing y [android/README.md](android/README.md) para Android. La demostración histórica de mapas se documenta en [docs/DEMO_MAPAS_MVP.md](docs/DEMO_MAPAS_MVP.md).
Esquema existente, bases anteriores y respaldos: [database/README.md](database/README.md).

## Cuentas y autenticación (backend)

Se agregó el módulo backend de registro, login, JWT, perfil, cambio de contraseña,
logout y administración de Operadores/Cuentas. Documentación técnica, comandos,
trazabilidad normativa y pruebas: [CUENTAS_AUTENTICACION.md](CUENTAS_AUTENTICACION.md).

Para una instalación **nueva** el esquema se migra con Flyway al iniciar backend, sin baseline.
Para bases existentes consultar [database/README.md](database/README.md) antes de migrar:
no habilitar baseline a ciegas ni borrar volúmenes para resolver errores.


## Farmacia y Stock

Las rutas y servicios implementados están en `backend/src/main/java/ar/edu/uade/medlink/farmacia/`
y `backend/src/main/java/ar/edu/uade/medlink/stock/`. Contratos, locking, pruebas y
limitaciones de integración: [FARMACIA_STOCK.md](FARMACIA_STOCK.md).

Las correcciones de alineación con la especificación v1.4.3 están descritas en
[CORRECCIONES_TRELLO_SQL.md](CORRECCIONES_TRELLO_SQL.md), y las decisiones de limpieza
en [REFACTORIZACION_TRELLO_SQL.md](REFACTORIZACION_TRELLO_SQL.md).
El PDF maestro v1.4.3 sigue siendo la única autoridad funcional, no estos README.


## Rama de mapas: parche y demostración

Esta rama puede instalarse mediante `git apply` desde el commit base `ef332860`
(`IntegracionMapas`). Las instrucciones completas de arranque y los límites de servicios
públicos de mapas están en [docs/DEMO_MAPAS_MVP.md](docs/DEMO_MAPAS_MVP.md).

**Importante:** el árbol heredado ya tiene `frontend/node_modules` versionado.
La nueva regla de `.gitignore` evita incorporar dependencias nuevas, pero **no**
las desversiona automáticamente. Se recomienda retirar ese directorio del índice
mediante un cambio separado para no mezclar miles de eliminaciones con el parche de mapas.
