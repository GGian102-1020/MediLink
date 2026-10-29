# MediLink

> **Evidencia histórica (26/10/2026; no valida el código posterior):** se integró Auditoría sobre los componentes existentes. `mvnw.cmd clean verify`: **124 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**, incluidas **35 pruebas PostgreSQL reales** de Auditoría, Catálogo y Farmacia/Stock. Se probaron instalación/adopción Flyway V1..V3 y restauraciones completas en bases separadas. Evidencia, alcance e integraciones pendientes: [AUDITORIA_INFRAESTRUCTURA.md](AUDITORIA_INFRAESTRUCTURA.md). Esto no certifica funcionalidades aún pendientes del MVP ni una ejecución del workflow remoto.

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


Frontend implementado en `frontend/`
- React + TypeScript + Vite
- Compilación de producción incorporada en `gateway/Dockerfile` (Caddy HTTPS)

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

La configuración incluida levanta PostgreSQL, el backend Java y **React bajo un único origen HTTPS**. Docker no es requisito para GitHub Desktop, pero sí para esta instalación y las pruebas Testcontainers. En una instalación nueva (base vacía):

1. Copiá `.env.example` a `.env` y completá **una clave JWT aleatoria de 32 bytes o más, codificada en Base64** (`JWT_SECRET_BASE64`). No la subas a Git.
2. Iniciá desde la raíz:

```bash
docker compose --profile api up -d --build
```

3. Abrí `https://localhost:8443` y comprobá la API en `https://localhost:8443/api/medicamentos`. Caddy usa una CA local: el navegador puede advertir que no confía todavía en ella. El puerto HTTPS se publica **solo en 127.0.0.1**.
4. **Solo para una demo local descartable**, cargá las cuentas ficticias una vez, después de las migraciones de Flyway:

```bash
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/medlink/02_datos_prueba.sql'
```

**IMPORTANTE:** el arranque nuevo no carga automáticamente cuentas con contraseñas de demostración. Si el volumen ya tiene el esquema anterior a Flyway, **no ejecutes este procedimiento como instalación nueva**: seguí la adopción controlada en [database/README.md](database/README.md). Nunca uses `down -v` en una base con datos que necesites conservar.

## Cuentas y autenticación (backend)

Se agregó el módulo backend de registro, login, JWT, perfil, cambio de contraseña,
logout y administración de Operadores/Cuentas. Documentación técnica, comandos,
trazabilidad normativa y pruebas: [CUENTAS_AUTENTICACION.md](CUENTAS_AUTENTICACION.md).

Flyway **crea las tablas desde cero** en un volumen nuevo; las cuentas demo se cargan solamente si el equipo ejecuta el comando explícito. En bases históricas no gestionadas por Flyway, la adopción con `DB_FLYWAY_BASELINE_ON_MIGRATE=true` requiere verificar primero estructura, respaldar y coordinar con el equipo. Pasos: [database/README.md](database/README.md).


## Farmacia y Stock

Las rutas y servicios implementados están en `backend/src/main/java/ar/edu/uade/medlink/farmacia/`
y `backend/src/main/java/ar/edu/uade/medlink/stock/`. Contratos, locking, pruebas y
limitaciones de integración: [FARMACIA_STOCK.md](FARMACIA_STOCK.md).

Las correcciones de alineación con la especificación v1.4.3 están descritas en
[CORRECCIONES_TRELLO_SQL.md](CORRECCIONES_TRELLO_SQL.md), y las decisiones de limpieza
en [REFACTORIZACION_TRELLO_SQL.md](REFACTORIZACION_TRELLO_SQL.md).
El PDF maestro v1.4.3 sigue siendo la única autoridad funcional, no estos README.


## Parches, rama y entrega en GitHub Desktop

Para integrar los cambios de cierre, trabajá en **`fix/cierre-mvp-v1.4.3` creada desde `feature/frontend-mvp-v1.4.3`** y aplicá los parches en el orden documentado en [docs/GUIA_GITHUB_DESKTOP_PARCHES.md](docs/GUIA_GITHUB_DESKTOP_PARCHES.md). **No sustituyas un repositorio existente por un ZIP completo** y no elimines `.git`.

Las pruebas deben aprobar en el commit candidato (Java con PostgreSQL real, frontend y smoke Docker Compose). La matriz de [AC-01..AC-60](docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md) es un registro de verificaciones: un test escrito no equivale a un PASS si no se ha ejecutado. **No se ha certificado el cierre funcional del MVP en este parche.**

### Registro y seguimiento de la validacion v1.4.3

- [Registro de los workflows exitosos del 29/10/2026 y alcances](docs/REGISTRO_VERIFICACION_CI_2026-10-29.md)
- [Campana de validacion AC-01..AC-60 y evidencia faltante](docs/CI_INDICE_AC_Y_PLAN_CIERRE.md)
- [Protocolo individual de los 60 AC](docs/PROTOCOLO_AC_01_60.md)
- [Matriz normativa de aceptacion (no marcar PASS sin evidencia)](docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md)

Las dos workflows PostgreSQL/HTTPS generan artifacts de indice por AC (`indice_ac.md` / `indice_ac.json`) para el SHA del run. El indice solo indica **evidencia automatizada parcial**, no certifica los 60 AC. La rama `fix/cierre-mvp-v1.4.3` puede validarse sin Docker local y no se fusiona automaticamente a `main`.

### Validación dirigida de los seis AC sin correlación previa y p95

Las pruebas nuevas en GitHub Actions para AC-31, AC-32, AC-40, AC-41, AC-48 y AC-55, la navegación real Chromium/Firefox, la referencia p95 y la interpretación rigurosa de sus evidencias están documentadas en [CI_AC_31_32_40_41_48_55_RNF11_2026-10-29.md](docs/CI_AC_31_32_40_41_48_55_RNF11_2026-10-29.md). **No equivalen a cierre normativo automático ni requieren Docker Desktop en el equipo local.**
