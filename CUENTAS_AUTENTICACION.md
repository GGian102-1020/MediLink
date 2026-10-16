# Cuentas y autenticación - MediLink v1.4.3

Implementación del backend a partir de la **Especificación Maestra del MVP v1.4.3**. No se creó un segundo modelo de Cuenta: el código usa las tablas y los enum PostgreSQL existentes en `database/01_medlink_esquema.sql` mediante `JdbcTemplate`, con transacciones Spring. El ZIP original **solo tenía un endpoint de salud**: no hay frontend React en este repositorio. La interfaz web de login/registro puede conectarse a estos endpoints al integrarse el frontend del equipo.

## Requisitos

- Java **21**, Maven (o Maven Wrapper con acceso para descargar su distribución), Docker con Compose.
- PostgreSQL 15+ con `database/01_medlink_esquema.sql` y, en entorno de prueba, `database/02_datos_prueba.sql`.
- Variables en archivo `.env` **no versionado**. JWT firmado con HS256 y un secreto propio de 32 bytes aleatorios o más, expresado en base64.

## Ejecución local (con HTTPS)

```bash
cp .env.example .env
python3 -c 'import secrets,base64; print(base64.b64encode(secrets.token_bytes(32)).decode())'
# Copiar el resultado a JWT_SECRET_BASE64=... dentro de .env
# Base de datos y Adminer (como antes):
docker compose up -d
# Backend y reverse proxy con TLS, además de la base:
docker compose --profile api up --build -d
# HTTPS con CA local de Caddy (es normal que no esté instalada como confiable):
curl -k https://localhost:8443/api/health
python3 scripts/smoke_cuentas.py --insecure
```

El proxy no publica directamente el puerto HTTP del backend: únicamente publica HTTPS `localhost:8443`. Para un entorno de evaluación externo reemplazá `https://localhost` en `gateway/Caddyfile` por un **dominio con certificado de confianza**; no uses `-k` fuera de localhost. Por defecto, CORS no permite orígenes externos. Si desarrollás React/Vite en otro origen, agregá `MEDLINK_CORS_ALLOWED_ORIGINS=http://localhost:5173` al `.env` local; jamás habilites `*` arbitrariamente. El perfil `api` evita que los otros miembros del equipo deban levantar el backend para usar solo la base.

Para ejecutar el backend desde el host en lugar de Docker:

```bash
cd backend
# Exportar DB_URL, DB_USER, DB_PASSWORD y JWT_SECRET_BASE64.
./mvnw test
./mvnw spring-boot:run
# Por defecto escucha en localhost:8081 (HTTP: solo desarrollo local).
```

La configuración de `application.properties` intencionalmente no incluye secretos reales. Si falta el secreto JWT, la API falla al iniciar, para impedir arranques inseguros.

### Cuentas de prueba (solo datos seed)

`admin@medlink.test` y `juan.perez@mail.test`, contraseña `Password123!`. Operador de Farmacia 1: `carla.mendez@farmacia-central.test`. Correo comparado sin distinguir mayúsculas y eliminando espacios extremos, con `UNIQUE` normalizado en BD. No trasladar contraseñas de demostración a producción.

## Endpoints implementados

| Método | Ruta | Rol | Resultado |
|---|---|---|---|
| POST | `/api/auth/register` | Público | 201 `UsuarioPerfilDTO` |
| POST | `/api/auth/login` | Público | 200 `{accessToken, tokenType, expiresInSeconds, perfil}` |
| POST | `/api/auth/logout` | Autenticado | 204, revoca jti |
| POST | `/api/auth/change-password` | Autenticado | 204, incrementa tokenVersion |
| GET | `/api/me` | Autenticado | 200 `MeDTO`; suspensión solo USUARIO |
| GET | `/api/admin/operadores` | ADMINISTRADOR | 200 `PageDTO<OperadorDTO>` |
| GET | `/api/admin/operadores/{id}` | ADMINISTRADOR | 200 `OperadorDTO` |
| POST | `/api/admin/operadores` | ADMINISTRADOR | 201 `OperadorDTO` |
| PUT | `/api/admin/operadores/{id}` | ADMINISTRADOR | 200 `OperadorDTO` |
| GET | `/api/admin/usuarios` | ADMINISTRADOR | 200 `PageDTO<UsuarioPerfilDTO>` |
| POST | `/api/admin/cuentas/{id}/deshabilitacion` | ADMINISTRADOR | 200 `CuentaPerfilDTO` |
| POST | `/api/admin/cuentas/{id}/reactivacion` | ADMINISTRADOR | 200 `CuentaPerfilDTO` |

**Paginación y filtros**: `page` desde 0, `size` por defecto 20, máximo 100. Ordenamiento `sort=campo,asc|desc` repetible, con whitelist de campos para evitar SQL injection. Filtros de Operadores: `farmaciaId`, `estadoCuenta`, `correo`. Filtros de Usuarios: `correo`, `estadoCuenta`.

## Ejemplos JSON

Registro:

```http
POST /api/auth/register
Content-Type: application/json

{"nombre":"Ana","apellido":"Paz","correo":"ana@ejemplo.test","contrasena":"Password123!"}
```

Login:

```http
POST /api/auth/login
Content-Type: application/json

{"correo":"ana@ejemplo.test","contrasena":"Password123!"}
```

Autenticados: incluir cabecera `Authorization: Bearer <accessToken>`; **nunca** enviar contraseñas ni tokens en URL, logs o auditoría.

Cambio de contraseña:

```json
{"contrasenaActual":"Password123!","nuevaContrasena":"NuevaPassword456!"}
```

Alta administrativa de operador:

```json
{"nombre":"Julia","apellido":"Gómez","correo":"julia@farmacia.test","contrasenaInicial":"Password123!","farmaciaId":1}
```

Deshabilitar o reactivar:

```json
{"motivo":"Solicitud administrativa documentada"}
```

Errores (400/401/403/404/409, según contrato) usan:

```json
{"codigo":"INVALID_TOKEN","mensaje":"Token inválido, expirado o revocado.","detalle":null,"timestamp":"2026-10-14T13:49:00.000-03:00","correlationId":"c7099562-6a2c-4204-8b3b-9c041706c35a"}
```

## Invariantes y seguridad

- Contraseña validada antes de BCrypt (8 a 72 caracteres). Para evitar la truncación silenciosa de BCrypt también se rechazan entradas que superen **72 bytes UTF-8** (protección técnica más estricta para Unicode multibyte); el cambio propio verifica la actual y exige una distinta.
- JWT HS256 de 30 minutos, sin refresh, con `sub`, `rol`, `jti`, `tokenVersion`, `iat`, `exp`. Firma, vencimiento, `jti`, `tokenVersion`, estado ACTIVA y rol se validan en **cada petición autenticada**.
- Logout persiste `sesion_revocada` por jti; cambiar contraseña o deshabilitar cuenta invalida globalmente por `tokenVersion`. Reactivar no restaura sesiones anteriores.
- Registro público crea **Cuenta + Usuario** en una transacción; no permite elegir otro rol. Administrador solamente por seed/configuración. Un Operador está permanentemente asociado a una única Farmacia y se crea Cuenta + Operador de forma atómica.
- ADMINISTRADOR no puede cambiar el estado de otra Cuenta ADMINISTRADOR por API. No se permite deshabilitar al último Operador ACTIVO de una Farmacia ACTIVA. Operador de Farmacia INACTIVA puede iniciar sesión (operaciones funcionales bloqueadas por el módulo de farmacias/reservas que aún debe implementarse).
- RN-40: deshabilitación de Usuario bloquea cuenta, perfil y recursos; materializa primero reservas funcionalmente vencidas y sus efectos, cancela las restantes con causa `CUENTA_DESHABILITADA`, restituye Stock/libera Rx con MovimientoStock y evalúa Suspensiones históricas, todo en la misma transacción; registra la auditoría. El módulo de Reservas futuro **debe respetar el orden de locks de 10.2** para preservar este comportamiento bajo concurrencia.
- La auditoría de cambios de contraseña y administración registra solamente metadatos permitidos: **sin hashes, contraseñas, JWT ni secretos**.
- No hay endpoint de recuperación de contraseña, MFA, creación de admin, eliminación física, cambio de rol ni cambio de Farmacia de operador: todos fuera de alcance normativo.

## Trazabilidad para Trello

| Requisito | Estado en este entregable | Verificación prevista |
|---|---|---|
| CU-01, RN-37/38, AC-33 | Registro Cuenta+Usuario | Prueba `AuthServiceTest` y smoke HTTP |
| CU-02, SEC-01/02/05 | Login, BCrypt, JWT, perfil | `AuthServiceTest`, `JwtTokensTest`, smoke |
| SEC-03, AC-24 | Revocación logout | Smoke HTTP |
| SEC-04, AC-23 | Cambio de contraseña, invalidación global | Pruebas de servicio, smoke HTTP |
| CU-14, RN-38/39 | Alta, edición, consulta Operadores | Smoke HTTP; prueba de contrato PostgreSQL pendiente |
| RN-40, AC-10/12/27/31/32 | Estado de Cuentas y reglas de Farmacia/Reservas | Unit test de último operador; **integración concurrente pendiente** |
| RN-31..36 | Evaluación determinista de suspensiones durante deshabilitación | `SuspensionPlannerTest`; integración con BD pendiente |
| SEC-06 | Restricción de rol en backend | Smoke HTTP con Usuario y Operador |
| SEC-07/08 | Secretos externos, HTTPS proxy | Prueba manual y `curl -k` local |
| RN-44 | Auditoría administrativa | Verificación BD tras smoke pendiente |

### Pendientes reales de integración

**Desviación técnica pendiente:** la sección 13 de la especificación prescribe Spring Data JPA/Hibernate para persistencia. Este módulo utiliza `JdbcTemplate` para interoperar directamente con los tipos `ENUM`, triggers y locks del esquema PostgreSQL existente. Es una decisión de implementación de este entregable que **debe revisarse e integrarse con Spring Data JPA/Hibernate** para cerrar la conformidad arquitectónica completa del MVP. No se declara esa conformidad como terminada.

Este repositorio no contiene todavía el módulo de Reservas, Farmacias/Stock, Prescripciones, Auditoría de lectura ni frontend; no se declara el MVP completo. Falta ejecutar pruebas **reales** con PostgreSQL/Docker y concurrencia de operaciones cruzadas (especialmente AC-12/31/32) una vez que las otras tarjetas se integren. La lógica `ReservationClosureService` implementa sus movimientos según el esquema actual, pero deberá coordinarse con los servicios de Reservas y el scheduler cuando existan. La política completa de expiración/suspensiones para `POST /api/reservas` todavía pertenece a esas tarjetas.

## Pruebas

En un equipo con Maven/Java 21:

```bash
cd backend && ./mvnw test
```

Luego levantar Docker con `--profile api` y ejecutar:

```bash
python3 scripts/smoke_cuentas.py --insecure
```

**El smoke deja una Cuenta USUARIO y una OPERADOR nuevas** para respetar RN-43 (sin borrado físico). Ejecutarlo sobre la base de pruebas, no sobre datos reales. El test supone los IDs de seed `1` y `4` para los casos de administrador y último operador.
