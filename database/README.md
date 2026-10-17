# Base de datos local (PostgreSQL + Docker)

Todo el equipo levanta **la misma base**: al iniciar por primera vez se crean las tablas de `database/01_medlink_esquema.sql` (v1.4.3) y se cargan los datos de `database/02_datos_prueba.sql`.

## Requisitos
- Docker Desktop abierto (o Docker Engine + Compose v2)

## Levantar
```bash
docker compose up -d   # desde la raíz del repo
```
Los puertos de PostgreSQL y Adminer se publican **solo en 127.0.0.1**.

- **PostgreSQL**: `localhost:5432` · base `medlink` · usuario `medlink` · clave `medlink123`
- **Adminer** (ver tablas desde el navegador): http://localhost:8080
  - Sistema: *PostgreSQL* · Servidor: `db` · Usuario: `medlink` · Clave: `medlink123` · Base: `medlink`
- Cadena JDBC (Spring): `jdbc:postgresql://localhost:5432/medlink`


### Ejecutar además la API por HTTPS con Flyway
La base compartida creada por los scripts **ya está poblada antes de iniciar Spring**.
Como Flyway no debe asumir la versión de cualquier esquema no vacío, su baseline
viene desactivado por defecto. En el primer arranque de la API sobre esta base:

1. Verificá que la base proviene de `01_medlink_esquema.sql` v1.4.3 (no de un
   esquema arbitrario). Por ejemplo, inspeccioná las tablas y sus triggers en Adminer.
2. Generá una clave JWT local y agregala a `.env` (ver `.env.example`).
3. **Solo para adoptar ese esquema existente**, ejecutá:

```bash
DB_FLYWAY_BASELINE_ON_MIGRATE=true docker compose --profile api up --build -d
curl -k https://localhost:8443/api/health   # solo HTTPS local con CA Caddy
```

En **PowerShell de Windows**, el equivalente para ese primer arranque es:

```powershell
$env:DB_FLYWAY_BASELINE_ON_MIGRATE = "true"
docker compose --profile api up --build -d
Remove-Item Env:DB_FLYWAY_BASELINE_ON_MIGRATE
curl.exe -k https://localhost:8443/api/health
```

El baseline de Flyway registra la migración 1 como ya aplicada; no vuelve a crear
las tablas ni borra los datos. En los reinicios posteriores se usa el modo normal
(`DB_FLYWAY_BASELINE_ON_MIGRATE=false`). Si la base es nueva y se crea únicamente
mediante Flyway, dejá el baseline desactivado desde el inicio. Nunca actives
baseline de forma indiscriminada para bases de procedencia desconocida.

**Si el proyecto ya estaba desplegado**, verificar primero si existe
`flyway_schema_history`: si existe, Flyway no necesita volver a adoptar esa versión.
No usar `docker compose down -v` para intentar resolver un error de Flyway: ese comando
elimina los datos. Para el detalle normativo ver secciones 10, 13 y 16 del PDF v1.4.3.

### Backup y restauración (RNF-12 / AC-59)

Con la base ejecutándose, desde la raíz del proyecto. El backup se guarda
**fuera de la carpeta del repositorio** (`../medlink.backup.dump`) para evitar
subir datos a GitHub por accidente; `.gitignore` excluye también `*.dump` y
`*.backup` como segunda protección:

```bash
# Crear backup sin modificar la base original.
docker compose exec -T db sh -c 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > ../medlink.backup.dump
# Crear una base SEPARADA de prueba (no sobrescribe medlink).
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" medlink_restore'
# Restaurar el backup en la base separada.
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d medlink_restore --no-owner --no-privileges' < ../medlink.backup.dump
# Verificar conteos de entidades restauradas.
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d medlink_restore -c "SELECT COUNT(*) AS cuentas FROM cuenta"'
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d medlink_restore -c "SELECT COUNT(*) AS reservas FROM reserva"'
```

Si `medlink_restore` ya existe, elegí un nombre nuevo para no mezclar pruebas.
Los ejemplos requieren que el contenedor `medlink-db` exista (nombre fijado en
`docker-compose.yml`). No restaurar sobre `medlink` ni borrar el volumen.
Documentá fecha, comandos, resultados y conteos del backup/restauración antes de la
entrega evaluada. No incluir el `.dump` en Git ni en el ZIP de distribución.

En **PowerShell anterior a 7.4**, no redirijas un volcado binario con `>`:
puede tratarlo como texto. Es más seguro guardarlo en el contenedor y copiarlo:

```powershell
docker compose exec -T db sh -c 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/medlink.backup.dump'
docker cp medlink-db:/tmp/medlink.backup.dump ..\medlink.backup.dump
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" medlink_restore'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d medlink_restore --no-owner --no-privileges /tmp/medlink.backup.dump'
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d medlink_restore -c "SELECT COUNT(*) FROM reserva"'
```

### Verificar la fixture normativa

Luego de inicializar una base de **pruebas** con los scripts 01 y 02, ejecutá:

```bash
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < scripts/verificar_seed.sql
```

El comando termina con error si la suspensión de Sofía no coincide con el tercer
vencimiento, si falta un MovimientoStock o si aparece una restitución indebida.
No modifica la base de datos.

En PowerShell:

```powershell
Get-Content scripts/verificar_seed.sql -Raw | docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Si el puerto 5432 u 8080 ya está ocupado (por ejemplo, un Postgres instalado en la PC), copiá `.env.example` a `.env` y cambiá `POSTGRES_PORT` / `ADMINER_PORT`.

## Resetear la base (borra todo y vuelve a cargar los scripts)
```bash
docker compose down -v
docker compose up -d   # desde la raíz del repo
```
> Los scripts se ejecutan **solo cuando el volumen está vacío**. `down -v` elimina todos los datos: usalo exclusivamente en una base descartable y después de verificar que no necesitás conservarla. Nunca es una solución para una migración fallida en datos existentes.

## Consola SQL
```bash
docker exec -it medlink-db psql -U medlink -d medlink
```

## Archivos
```
docker-compose.yml                   -> Postgres 16 + Adminer (raíz del repo)
database/01_medlink_esquema.sql      -> enums, tablas, índices y triggers (se monta como 01)
database/02_datos_prueba.sql         -> datos de prueba (se monta como 02)
.env.example                         -> puertos y credenciales por defecto
```

## Datos de prueba
Contraseña de **todas** las cuentas: `Password123!` (bcrypt `$2a$`).

| Rol | Email | Para probar |
|---|---|---|
| Admin | admin@medlink.test | |
| Operador F1 | carla.mendez@farmacia-central.test | Farmacia Central Palermo; hizo un ajuste manual de stock |
| Operador F1 | diego.suarez@farmacia-central.test | 2 operadores en la misma farmacia |
| Operador F2 | laura.benitez@fsanmartin.test | |
| Operador F3 | pablo.romero@delcentro.test | Confirmó un retiro |
| Operador F4 | valeria.torres@salud.test | Canceló una reserva por causa de farmacia |
| Operador F5 | hernan.acosta@flores.test | Cuenta DESHABILITADA / farmacia INACTIVA |
| Paciente | juan.perez@mail.test | 2 reservas activas; receta con amoxicilina ya comprometida |
| Paciente | maria.gonzalez@mail.test | Historial completo; receta anulada |
| Paciente | lucas.fernandez@mail.test | 1 reserva vencida; receta totalmente utilizada |
| Paciente | sofia.rodriguez@mail.test | **SUSPENDIDA** al inicializar la base: reservas 7, 8 y 9 vencidas en una ventana de 30 días; el correo se guarda con mayúsculas |
| Paciente | martin.lopez@mail.test | Cuenta DESHABILITADA |

**Escenarios cargados**
- 13 medicamentos / 17 presentaciones (venta libre y bajo receta; Ranitidina INACTIVA).
- Stock en 0 y casos de “última unidad” (Amoxicilina en F2, Insulina solo en F3) para probar concurrencia.
- Recetas: vigentes, vencida por fecha (`RX-2026-000002`), anulada (`RX-2026-000004`), totalmente utilizada (`RX-2026-000006`), con varios ítems y de otro usuario.
- Reservas en los 4 estados (cancelación voluntaria oportuna y operativa, tres vencimientos de Sofía), duración fija de 4 h y `codigo_visible` de 12 caracteres.
- Suspensión de Sofía: empieza en el vencimiento de la tercera reserva, dura 7 días exactos; cancelaciones no penalizan (RN-31..36).
- `movimiento_stock` con confirmaciones, restitución, vencimiento y ajuste manual, coherentes con el stock actual.
- `idempotency_record` y `sesion_revocada` arrancan vacías (las llena la API).

Las fechas son relativas al momento en que se crea la base, así las reservas ACTIVAS arrancan vigentes.
