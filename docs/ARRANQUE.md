# Arranque local — backend + frontend MediLink

**Arranque recomendado para Android y operador:** ejecutar `scripts/iniciar-demo.ps1` desde la raíz. El dashboard corre dentro de Docker y permanece encendido al cerrar el chat o la consola. Para llevar los PDFs de prueba a otro celular o computadora, consultar [DEMO_ANDROID_PORTABLE.md](DEMO_ANDROID_PORTABLE.md).

Proyecto universitario (MVP). El stock y las cuentas de demo son **ficticios**: no usar datos sanitarios reales
ni exponer las credenciales de demostración fuera de la máquina local.

## Requisitos

Docker Desktop con Compose y Node.js >= 22.12. Para correr el backend sin Docker, Java 21.

## Pasos (Windows / PowerShell, desde la raíz del proyecto)

```powershell
# 1. Configuración local con una clave JWT propia.
Copy-Item .env.example .env
$bytes = New-Object 'byte[]' 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$secret = [Convert]::ToBase64String($bytes)
(Get-Content .env) -replace '^JWT_SECRET_BASE64=.*$', "JWT_SECRET_BASE64=$secret" | Set-Content .env

# 2. Base de datos, API y gateway. Flyway crea el esquema al iniciar el backend.
docker compose --profile api up -d --build

# 3. Esperar a que la API responda (la CA de Caddy es interna: -k solo para localhost).
curl.exe -k https://localhost:8443/api/medicamentos

# 4. Datos de prueba: UNA SOLA VEZ y solo en una base local nueva y descartable.
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/medlink/02_datos_prueba.sql'

# 5. El dashboard ya corre en Docker en http://127.0.0.1:5173.
# No hace falta mantener npm ni una terminal abiertos.
# Solo para desarrollo con recarga automática:
# docker compose --profile api stop frontend
# Set-Location frontend
# npm ci
# npm run dev
```

Abrir `http://127.0.0.1:5173`. Cuenta de operador de prueba: `carla.mendez@farmacia-central.test` / `Password123!`
(el resto de las cuentas está en [database/README.md](../database/README.md)). Las cuentas OPERADOR acceden al dashboard de operaciones; pacientes y administradores conservan sus rutas propias y las pantallas públicas siguen accesibles.

Si la API falla: `docker compose --profile api logs --tail=100 backend`. Si ya existe una base con datos,
**no** cargar el seed ni usar `docker compose down -v` (borra el volumen); ver [database/README.md](../database/README.md).

## Tests

```powershell
Set-Location frontend
npm run typecheck
npm test
npm run build
Set-Location ..\backend
./mvnw.cmd -B test   # requiere Java 21; las pruebas PostgreSQL usan Docker (Testcontainers)
```

GitHub Actions corre estas verificaciones en cada push (`.github/workflows/medlink-postgres-integration.yml`).
