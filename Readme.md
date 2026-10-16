MediLink

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


Frontend
- React
- Vite

Otras herramientas
- Git
- GitHub
- Swagger / OpenAPI
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
Spring Data JPA
      ↓
PostgreSQL




Base de datos local

PostgreSQL corre en Docker con el esquema y datos de prueba compartidos por todo el equipo. Desde la raíz del repo:

```bash
docker compose up -d
```

Conexión, cuentas de prueba y cómo resetear la base: ver [database/README.md](database/README.md).

## Cuentas y autenticación (backend)

Se agregó el módulo backend de registro, login, JWT, perfil, cambio de contraseña,
logout y administración de Operadores/Cuentas. Documentación técnica, comandos,
trazabilidad normativa y pruebas: [CUENTAS_AUTENTICACION.md](CUENTAS_AUTENTICACION.md).

El Docker Compose original para DB/Adminer se mantiene. Para ejecutar además la API
con HTTPS se usa el perfil opcional `api` (`docker compose --profile api up --build -d`),
una clave JWT local propia en `.env` y Caddy como reverse proxy.


## Farmacia y Stock

Implementación, contratos REST, locking, adopción de Flyway sobre bases existentes,
pruebas y diferencias respecto del UML: [FARMACIA_STOCK.md](FARMACIA_STOCK.md).
