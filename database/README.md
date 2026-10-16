# Base de datos local (PostgreSQL + Docker)

Todo el equipo levanta **la misma base**: al iniciar por primera vez se crean las tablas de `database/01_medlink_esquema.sql` (v1.4.3) y se cargan los datos de `database/02_datos_prueba.sql`.

## Requisitos
- Docker Desktop abierto (o Docker Engine + Compose v2)

## Levantar
```bash
docker compose up -d   # desde la raíz del repo
```
- **PostgreSQL**: `localhost:5432` · base `medlink` · usuario `medlink` · clave `medlink123`
- **Adminer** (ver tablas desde el navegador): http://localhost:8080
  - Sistema: *PostgreSQL* · Servidor: `db` · Usuario: `medlink` · Clave: `medlink123` · Base: `medlink`
- Cadena JDBC (Spring): `jdbc:postgresql://localhost:5432/medlink`

Si el puerto 5432 u 8080 ya está ocupado (por ejemplo, un Postgres instalado en la PC), copiá `.env.example` a `.env` y cambiá `POSTGRES_PORT` / `ADMINER_PORT`.

## Resetear la base (borra todo y vuelve a cargar los scripts)
```bash
docker compose down -v
docker compose up -d   # desde la raíz del repo
```
> Los scripts se ejecutan **solo cuando el volumen está vacío**. Si alguien modifica el esquema o los datos, el resto tiene que hacer `down -v` + `up -d` para quedar igual.

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
| Paciente | sofia.rodriguez@mail.test | **SUSPENDIDA** (7 días); el correo está guardado con mayúsculas |
| Paciente | martin.lopez@mail.test | Cuenta DESHABILITADA |

**Escenarios cargados**
- 13 medicamentos / 17 presentaciones (venta libre y bajo receta; Ranitidina INACTIVA).
- Stock en 0 y casos de “última unidad” (Amoxicilina en F2, Insulina solo en F3) para probar concurrencia.
- Recetas: vigentes, vencida por fecha (`RX-2026-000002`), anulada (`RX-2026-000004`), totalmente utilizada (`RX-2026-000006`), con varios ítems y de otro usuario.
- Reservas en los 4 estados (cancelación oportuna, tardía y operativa), duración fija de 4 h y `codigo_visible` de 12 caracteres.
- `movimiento_stock` con confirmaciones, restitución, vencimiento y ajuste manual, coherentes con el stock actual.
- `idempotency_record` y `sesion_revocada` arrancan vacías (las llena la API).

Las fechas son relativas al momento en que se crea la base, así las reservas ACTIVAS arrancan vigentes.
