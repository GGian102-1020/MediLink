# Demo universitaria de mapas (rama `fix/mapas-mvp-demo`)

## Alcance y advertencias

- Se trata de un **MVP universitario**. Las farmacias pueden corresponder a ubicaciones reales, pero el stock y las cuentas de demo son **ficticios**; nunca mostrarlo como disponibilidad verificada.
- Servicios públicos: teselas OSM, Nominatim para direcciones y OSRM para rutas. Pueden fallar o limitar tráfico. El límite de Nominatim es **1 petición/segundo para toda la aplicación**. Esta rama solo espacia consultas por pestaña y utiliza caché; **no garantiza el límite global con múltiples usuarios**. No publicar el MVP para uso masivo sin un geocodificador con cuota propia o proxy con limitación global.
- Buscar una dirección y calcular rutas envía información de ubicaciones a servicios de terceros. La ubicación GPS es optativa, se redondea antes de salir del navegador y se recuerda en `sessionStorage` de esa pestaña. Los tokens de sesión de la demo también se guardan en `sessionStorage`. No utilizar información sanitaria personal real.
- La rama incluye **solo la interfaz mínima de la demo** (inicio, catálogo, disponibilidad y mapa, ingreso/registro, detalle/cancelación de reserva). No reconstruye las pantallas administrativas del sistema.

## Instalación limpia: Windows con PowerShell

Requiere Git, Docker Desktop con Compose, Node.js >=22.12, npm y, para backend sin Docker, Java 21. Abrir Docker Desktop antes de continuar.

Desde la raíz del proyecto:

```powershell
# 1. Configuración LOCAL. No usar valores del ejemplo en un despliegue público.
Copy-Item .env.example .env
# Compatible con Windows PowerShell 5.1 y PowerShell 7.
$bytes = New-Object 'byte[]' 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$secret = [Convert]::ToBase64String($bytes)
(Get-Content .env) -replace '^JWT_SECRET_BASE64=.*$', "JWT_SECRET_BASE64=$secret" | Set-Content .env

# 2. Base de datos y API. Flyway crea el esquema desde cero.
docker compose --profile api up -d --build
docker compose --profile api ps

# 3. Esperar a que la API responda y cargue las migraciones antes del seed.
#    La CA de Caddy es interna; -k SOLO se permite para localhost en esta demo.
curl.exe -k https://localhost:8443/api/medicamentos

# 4. UNA SOLA VEZ y SOLO si esta base local es nueva y descartable:
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/medlink/02_datos_prueba.sql'
# Opcional: sustituir farmacias ficticias por ubicaciones reales con STOCK SIMULADO.
# AVISO: el siguiente script elimina y reemplaza datos dependientes del seed.
# NO ejecutarlo si hay reservas u otros datos que querés preservar.
# docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/medlink/03_farmacias_reales.sql'

# 5. Frontend; el proxy de Vite llega a Caddy sin exponer API al navegador.
Set-Location frontend
npm ci
npm run dev
```

Abrir `http://127.0.0.1:5173` para la demo. Las cuentas del seed 02 son públicas
para pruebas (por ejemplo `juan.perez@mail.test` / `Password123!`); **solo** en local.
Comprobá la búsqueda y el mapa con un medicamento que tenga stock (p. ej. ibuprofeno).

Si falla la API, ejecutar `docker compose --profile api logs --tail=100 backend` en otra
terminal desde la raíz. Si hay una base existente, **no ejecutar** el seed ni `down -v`;
consultar la guía de migración existente. El gateway solo publica `127.0.0.1:8443`.

## Tests

```powershell
Set-Location frontend
npm run typecheck
npm test
npm run build
Set-Location ..
# Requiere Java 21 y las condiciones de las pruebas PostgreSQL.
Set-Location backend
./mvnw.cmd -B test
```

GitHub Actions verifica el frontend y el backend al hacer push a `fix/**`.

## Límites conocidos

- El mapa requiere Internet. Si OSRM no responde, se mantiene el enlace alternativo a Google Maps.
- No hay límite de peticiones compartido entre pestañas/dispositivos: **no cumple la cuota global de Nominatim bajo acceso concurrente**.
- No hay navegación GPS en tiempo real ni garantías sobre rutas o distancias.
- No se recomienda exponer credenciales de demostración, Administrador, ni gateway al exterior.
- El historial incluye `frontend/node_modules` versionado; se debe retirar del índice en una tarea de mantenimiento independiente.
