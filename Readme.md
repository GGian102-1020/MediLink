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


