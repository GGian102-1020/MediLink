# Base de datos local (PostgreSQL + Docker)

En una instalación nueva, **Flyway es la única fuente de instalación del esquema**. El backend ejecuta `V1__esquema_compartido.sql`, `V2__extension_busqueda_catalogo.sql` y `V3__indice_consulta_auditoria.sql` sobre un PostgreSQL vacío. El Compose **ya no** monta `01_medlink_esquema.sql` en `/docker-entrypoint-initdb.d` y **no** carga contraseñas ficticias automáticamente.

## Requisitos

- Docker Desktop abierto (o Docker Engine + Compose v2).
- `.env` creado a partir de `.env.example` con **JWT_SECRET_BASE64 aleatorio** y contraseñas locales únicas si corresponde.
- No mezclar bases nuevas con un volumen que ya contiene un esquema histórico no registrado en Flyway.

## Instalación nueva (volumen sin datos)

Desde la raíz:

```bash
docker compose --profile api up -d --build
```

- Web React: **https://localhost:8443** (`tls internal`, CA local de Caddy).
- API por mismo origen: **https://localhost:8443/api/medicamentos**.
- PostgreSQL: `127.0.0.1:5432` · base `medlink` · usuario `medlink` · contraseña definida en `.env` (el valor de ejemplo **solo** sirve para demo local).
- Adminer: http://localhost:8080, restringido a loopback (servidor `db`).
- Si hay conflictos de puertos, editar `POSTGRES_PORT`, `ADMINER_PORT` o `HTTPS_PORT` en `.env`.

Para comprobar, en PowerShell:

```powershell
docker compose --profile api ps
curl.exe -k https://localhost:8443/api/medicamentos
curl.exe -k https://localhost:8443/ingresar
```

`curl -k` omite validación de la CA **solo para la demostración local**; en un despliegue real usá un certificado confiable. Los puertos de base de datos, Adminer y HTTPS están ligados a `127.0.0.1`. Por diseño, este Compose no es un despliegue público.

### Datos de prueba OPCIONALES y explícitos

Si querés usar las cuentas de `database/02_datos_prueba.sql`, esperá a que el backend haya completado Flyway, comprobá la instalación anterior y ejecutá **una sola vez en una base descartable**:

```bash
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/medlink/02_datos_prueba.sql'
```

En Windows **PowerShell**, la misma línea funciona desde la raíz del repositorio. Si se vuelve a correr sobre una base ya poblada, se producirán conflictos de unicidad; **no es un script idempotente**. Las credenciales conocidas del seed (`Password123!`) **solo** son aceptables en un entorno local restringido; nunca publiques esa instancia.

La carga de cuentas administrativas para un entorno que no sea demo debe prepararse por un procedimiento de aprovisionamiento seguro propio; el MVP no permite crear Administradores desde la API.

### Volúmenes existentes: adoptar sin borrar datos

Si anteriormente iniciabas `docker compose up -d` y PostgreSQL ya ejecutó `01_medlink_esquema.sql` / `02_datos_prueba.sql` automáticamente, **no se debe ejecutar Flyway V1 encima de esas tablas** sin revisar el caso. Primero:

1. Realizá backup y probá restauración separada (procedimiento más abajo); no uses `down -v`.
2. Verificá que el esquema existente es **exactamente** el esquema compartido v1.4.3 y que todavía no existe `flyway_schema_history`.
3. Solo si se confirmó la identidad del esquema, en `.env` configurá `DB_FLYWAY_BASELINE_ON_MIGRATE=true`, iniciá `docker compose --profile api up -d --build` y comprobá que Flyway registra V1 como baseline y ejecuta V2/V3.
4. Volvé a `DB_FLYWAY_BASELINE_ON_MIGRATE=false` para reinicios normales y conservá el backup.

Si `flyway_schema_history` ya existe, **no cambies baseline**: conservá la configuración false. No uses esta adopción para bases externas o esquemas parcialmente creados.

### Búsqueda de catálogo en bases ya existentes

El catálogo utiliza la extensión oficial de PostgreSQL `unaccent` para buscar
medicamentos sin distinguir acentos (CV-07). Los volúmenes nuevos la instalan
al ejecutar `01_medlink_esquema.sql`; en volúmenes **ya existentes** la instala
Flyway mediante `V2__extension_busqueda_catalogo.sql` al iniciar la API.
No borres el volumen ni edites la migración V1 que ya fue aplicada.
Si la extensión aún no está instalada y se usa una base sin iniciar Flyway, la
búsqueda de medicamentos requerirá ejecutar V2 primero. Se puede verificar con:

```sql
SELECT extname FROM pg_extension WHERE extname='unaccent';
```

### Consulta administrativa de Auditoría (V3)

Flyway aplica `V3__indice_consulta_auditoria.sql` después de V1/V2 para crear
el índice de historial global por `fecha_hora DESC,id_auditoria DESC`.
Los índices por actor, usuario relacionado y entidad ya existen en V1.
No modificar migraciones aplicadas ni borrar el volumen para incorporar V3.
La instalación nueva y la adopción explícita del esquema con seed fueron
verificadas en PostgreSQL 16 real. El endpoint y sus permisos están documentados
en [AUDITORIA_INFRAESTRUCTURA.md](../AUDITORIA_INFRAESTRUCTURA.md).

### Backup y restauración (RNF-12 / AC-59)

Procedimiento automatizado para **PowerShell 7+ y Docker**, desde la raíz:

```powershell
# Prueba completamente aislada: crea su propio contenedor y restaura en otra base.
pwsh -File ./scripts/backup_restore.ps1 -Fixture `
  -BackupPath ../medlink-fixture-20261026.dump `
  -EvidencePath ./evidencias/backup-local-20261026.md

# Backup del servicio indicado y verificación en una base temporal con UUID propio.
pwsh -File ./scripts/backup_restore.ps1 `
  -SourceContainer medlink-db -Database medlink -User medlink `
  -BackupPath ../medlink-20261026.dump `
  -EvidencePath ./evidencias/backup-desarrollo-20261026.md
```

Elegir nombres nuevos para cada ejecución: el script rechaza sobrescribir
backup/evidencia. Compara esquema, conteos y checksums de todas las tablas,
conserva el dump y elimina únicamente los recursos temporales que creó.
En una base con actividad, ejecutar la comparación durante una ventana sin
escrituras. Se requiere permiso SQL para crear la base temporal.
El script espera al servidor PostgreSQL definitivo por TCP dentro del contenedor
y comprueba la conexión SQL local antes de copiar SQL o generar el backup.
Así evita aceptar el servidor temporal de inicialización, cuyo socket se elimina
antes del arranque definitivo. Timeout predeterminado: 60 s; se puede ajustar
con `-ReadyTimeoutSeconds`. No se publican puertos nuevos ni se cambian claves.
Regresión y diagnóstico de GitHub Actions:
[BACKUP_WORKFLOW_FIX.md](../docs/BACKUP_WORKFLOW_FIX.md).
Detalle y evidencia real de las restauraciones del 26/10/2026 (16 tablas de
fixture y 17 de una base con historial Flyway):
[AUDITORIA_INFRAESTRUCTURA.md](../AUDITORIA_INFRAESTRUCTURA.md).

### Comandos manuales de backup/restauración

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

Luego de completar las migraciones con **Flyway** y cargar explícitamente el script 02 en una base **de prueba**, ejecutá:

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

## Reinicio descartable (DESTRUYE TODOS LOS DATOS)

Solo en una **base de demostración sin datos valiosos** y después de asegurarte de que tenés un respaldo si lo necesitás:

```bash
docker compose --profile api down -v
docker compose --profile api up -d --build
```

Esto recrea el esquema mediante Flyway, pero **NO** carga las cuentas demo: si las necesitás, ejecutá una sola vez el comando de carga opcional indicado arriba. **Jamás uses `down -v` como solución a fallos de migración.**

## Consola SQL
```bash
docker exec -it medlink-db psql -U medlink -d medlink
```

## Archivos
```
docker-compose.yml                   -> PostgreSQL 16 + API + gateway + Adminer
backend/src/main/resources/db/migration/ -> fuente de migraciones Flyway V1..V3
database/01_medlink_esquema.sql      -> esquema SQL LEGACY, NO se ejecuta automáticamente
database/02_datos_prueba.sql         -> seed DEMO manual, montado en /opt/medlink (no auto)
gateway/Dockerfile                   -> build React + Caddy
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

Las fechas son relativas al momento **de cargar el seed**, por lo que las Reservas marcadas ACTIVAS nacen vigentes para la demo.
