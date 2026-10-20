# MediLink

> **Estado de esta copia (19/10/2026):** Catálogo de medicamentos/presentaciones, Cuentas/Autenticación y Farmacias/Stock tienen código implementado. Los informes Maven Surefire incluidos en la copia del proyecto recibida el **19/10/2026** registran **99 pruebas: 79 aprobadas, 0 fallos, 0 errores y 20 omitidas** (integración PostgreSQL). Los resultados son del entorno del equipo y no de una nueva ejecución independiente. Ni PostgreSQL real, ni las migraciones, ni backup/restauración están certificados por esa evidencia. No se declaran terminadas funcionalidades fuera de estas tarjetas.

**Documentos vigentes:** [CHANGELOG.md](CHANGELOG.md) · [CORRECCIONES_TRELLO_SQL.md](CORRECCIONES_TRELLO_SQL.md) · [REFACTORIZACION_TRELLO_SQL.md](REFACTORIZACION_TRELLO_SQL.md) · [database/README.md](database/README.md). Los informes obsoletos del 14/10/2026 se retiraron de la copia activa; el historial de Git permite recuperarlos si estaban versionados.

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


Frontend previsto en el MVP (no incluido en este ZIP)
- React + TypeScript
- Herramienta de desarrollo frontend: por definir por el equipo

Otras herramientas
- Git
- GitHub
- JUnit
- Mockito
- Trello


Arquitectura

El proyecto utilizará una arquitectura basada en:

Frontend React
      ↓
   REST API
      ↓
Spring Boot
      ↓
Service / Lógica de negocio
      ↓
Spring Data JPA / JdbcTemplate (según módulo)
      ↓
PostgreSQL




Base de datos local

La configuración incluida permite levantar PostgreSQL mediante Docker Compose. Docker no es requisito para usar GitHub Desktop ni para revisar el código; las pruebas con Testcontainers sí requieren Docker u otra base de pruebas configurada. Desde la raíz del repo:

```bash
docker compose up -d
```

Conexión, cuentas de prueba y cómo resetear la base: ver [database/README.md](database/README.md).

## Cuentas y autenticación (backend)

Se agregó el módulo backend de registro, login, JWT, perfil, cambio de contraseña,
logout y administración de Operadores/Cuentas. Documentación técnica, comandos,
trazabilidad normativa y pruebas: [CUENTAS_AUTENTICACION.md](CUENTAS_AUTENTICACION.md).

El Docker Compose de desarrollo carga el esquema/datos de prueba en el primer arranque.
Para ejecutar además la API con HTTPS se usa el perfil opcional `api`, una clave JWT
local propia en `.env` y Caddy como reverse proxy. **En el primer arranque de la API
sobre esa base ya cargada, es necesario adoptar explícitamente el esquema compartido
con Flyway** (`DB_FLYWAY_BASELINE_ON_MIGRATE=true` después de verificar que es v1.4.3).
No habilitar baseline automáticamente en bases desconocidas ni eliminar el volumen
para resolver errores de migración. Pasos detallados: [database/README.md](database/README.md).


## Farmacia y Stock

Las rutas y servicios implementados están en `backend/src/main/java/ar/edu/uade/medlink/farmacia/`
y `backend/src/main/java/ar/edu/uade/medlink/stock/`. Contratos, locking, pruebas y
limitaciones de integración: [FARMACIA_STOCK.md](FARMACIA_STOCK.md).

Las correcciones de alineación con la especificación v1.4.3 están descritas en
[CORRECCIONES_TRELLO_SQL.md](CORRECCIONES_TRELLO_SQL.md), y las decisiones de limpieza
en [REFACTORIZACION_TRELLO_SQL.md](REFACTORIZACION_TRELLO_SQL.md).
El PDF maestro v1.4.3 sigue siendo la única autoridad funcional, no estos README.


## Sustitución completa de una copia local (GitHub Desktop)

Este ZIP incluye **todos los archivos versionables de esta copia**, no únicamente los modificados, y **no incluye `.git` ni secretos `.env`**. Antes de sustituir la carpeta de trabajo:

1. Creá una copia de seguridad de la carpeta del repositorio, incluidos los archivos ocultos; guardá y revisá cualquier cambio local de tus compañeros.
2. En GitHub Desktop, trabajá en una rama nueva. **No borres `.git`**: contiene el historial y permite que GitHub Desktop siga reconociendo el repositorio. **No sobrescribas ni compartas `.env`**: contiene configuración/secretos locales y no forma parte del ZIP.
3. Si querés borrar archivos obsoletos que ya no vienen en el ZIP, quitá solamente los archivos de trabajo, **conservando `.git`, `.env` y cualquier dato local que necesites**. Copiá el contenido de la carpeta `MediLink/` extraída a la raíz del repositorio. Si tu rama evolucionó desde la copia utilizada, compará las diferencias antes de sustituirla.
4. Revisá eliminaciones y modificaciones en GitHub Desktop. Desde `backend/` ejecutá `mvnw.cmd -B clean test` en Windows; verificá por separado los tests de PostgreSQL que pueden estar omitidos. Después hacé commit.

**Esta distribución no incluye frontend, base de datos física, volumen Docker, archivos compilados, backups ni secretos.** No ejecutes `docker compose down -v` para actualizar código o migraciones: borra el volumen de PostgreSQL.
