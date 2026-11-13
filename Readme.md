# MediLink

**Documentación:** [database/README.md](database/README.md) (base, seeds, backup) · [docs/ARRANQUE.md](docs/ARRANQUE.md) (arranque con Docker) · [CUENTAS_AUTENTICACION.md](CUENTAS_AUTENTICACION.md) (API de cuentas) · [FARMACIA_STOCK.md](FARMACIA_STOCK.md) (API de farmacias y stock) · [docs/SEGURIDAD.md](docs/SEGURIDAD.md) (revisión de seguridad).

`GET /api/admin/auditoria` permite al Administrador consultar registros de todos
los módulos con filtros, paginación y snapshots seguros (`AuditService`, triggers append-only).

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


Frontend: panel de operadores
- React + TypeScript + Vite
- Alcance de este repo: login y dashboard exclusivo para cuentas con rol OPERADOR (resumen, reservas, retiros/cancelaciones, stock, movimientos y mi cuenta).
- Al entrar a la URL se muestra el login (`/ingresar`); cuentas que no sean OPERADOR son rechazadas.
- El backend, la base (Flyway + seeds) y Docker Compose se mantienen completos para que otros clientes consuman la misma API.

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

La base de datos vacía se migra automáticamente mediante Flyway al iniciar la API.
La carga de cuentas y stock de demostración es **manual y solo para una base local descartable**.
No se deben cargar seeds en bases de datos compartidas ni utilizar las credenciales ficticias en un entorno expuesto.

Guía de arranque: [docs/ARRANQUE.md](docs/ARRANQUE.md).
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

El PDF maestro v1.4.3 sigue siendo la única autoridad funcional, no estos README.
