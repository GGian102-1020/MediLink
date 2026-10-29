# MediLink · Frontend MVP v1.4.3

Aplicación web **React + TypeScript + Vite**, integrada con el contrato REST del backend MediLink.
No modifica el backend Java, las migraciones PostgreSQL ni las reglas de negocio.

## 1. Requisitos

- **Node.js 22.12+** y npm (recomendado Node 22 LTS).
- Backend MediLink iniciado con Java 21 y PostgreSQL, disponible en `http://localhost:8081` en desarrollo.
- Cuentas válidas en la base para probar los roles `USUARIO`, `OPERADOR` y `ADMINISTRADOR`.

## 2. Inicio rápido (Windows CMD)

Desde la raíz de MediLink:

```cmd
cd frontend
copy .env.example .env
npm install
npm run dev
```

Abrir **http://127.0.0.1:5173**.

- `npm install` crea `package-lock.json` la primera vez. **Incluir ese lockfile en el commit** para fijar el árbol de dependencias; no incluir `node_modules/`.
- El servidor Vite redirige `/api/**` a `http://localhost:8081` usando su proxy local. Se puede modificar en `frontend/.env`: `VITE_DEV_BACKEND_URL=http://localhost:8081`.
- El frontend **no debe contener** secretos JWT, credenciales de BD ni tokens en `.env`.
- `VITE_DEV_BACKEND_URL` controla únicamente la configuración de desarrollo de Vite, no es una variable de producción.
- No hay datos falsos ni modo demo embebido: sin backend, las pantallas muestran un error de conexión y permiten reintentar.

## 3. Validaciones

```cmd
cd frontend
npm run typecheck
npm test
npm run build
```

El build de producción queda en `frontend/dist/` (ignorado por Git). GitHub Actions ejecuta **MediLink - Frontend React** al abrir o actualizar el PR. Las pruebas de PostgreSQL del backend siguen a cargo de su workflow independiente.

## 4. Flujos incluidos por rol

| Rol | Funcionalidad | Ruta |
| --- | --- | --- |
| Visitante | Inicio, catálogo, presentaciones, disponibilidad | `/`, `/catalogo`, `/presentaciones/:id` |
| Visitante | Registrarse / ingresar | `/registro`, `/ingresar` |
| Usuario | Panel, estado de suspensión, nueva Reserva OTC/receta | `/panel`, `/presentaciones/:id` |
| Usuario | Mis reservas, detalle, cancelación, tiempo restante informado | `/mis-reservas`, `/mis-reservas/:codigo` |
| Todos autenticados | Perfil y cambio de contraseña | `/perfil` |
| Operador | Reservas de su farmacia, retiro y cancelación operativa | `/operacion/reservas` |
| Operador | Stock, ajustes, movimientos | `/operacion/stock`, `/operacion/movimientos` |
| Administrador | Farmacias, Medicamentos, Presentaciones | `/admin/farmacias`, `/admin/medicamentos`, `/admin/presentaciones` |
| Administrador | Operadores, Usuarios, historial de Reservas/Suspensiones | `/admin/operadores`, `/admin/usuarios`, `/admin/usuarios/:id` |
| Administrador | Prescripciones, Auditoría | `/admin/prescripciones`, `/admin/auditoria` |
| Administrador | Reservas y Stock por Farmacia | `/farmacias/:farmaciaId/reservas`, `/farmacias/:farmaciaId/stock` |

**Decisiones funcionales importantes:**

- La API verifica roles, ámbitos y reglas; el frontend solo adapta la navegación.
- `POST /api/reservas` manda `Idempotency-Key` UUID. Si un intento falla y se reintenta sin modificar los datos, se conserva la clave del intento para evitar reservas duplicadas.
- La Prescripción de `BAJO_RECETA` se ingresa **por código**. No se implementa un listado de recetas personales, de acuerdo con RN-24.
- Se muestran los estados efectivos y segundos restantes recibidos del servidor. No se calcula un nuevo estado terminal en el navegador.
- El Operador no puede confirmar retiros de otra Farmacia ni el Administrador confirmar retiros; el servidor verifica también estas restricciones.
- Las presentaciones no se editan estructuralmente: el Administrador las crea o cambia de estado.
- Las operaciones que alteran estado solicitan confirmación y, cuando el contrato exige, un motivo.

## 5. Seguridad y sesiones

JWT Bearer se mantiene en **memoria y `sessionStorage` de la pestaña**, nunca en una URL ni en logs de frontend. Un 401 limpia la sesión local. Se utiliza una ruta API relativa (`/api`) para evitar credenciales CORS en el navegador.

`sessionStorage` mejora la experiencia al refrescar una pestaña, pero es accesible a JavaScript de ese origen. Evitar XSS, scripts de terceros y contenido HTML no confiable; agregar Content Security Policy en el reverse proxy para el entorno real. En entornos de alto riesgo, preferir un BFF y cookies seguras.

## 6. Despliegue integrado para demostración local

El servidor Vite es **solo para desarrollo**. El perfil `api` de Docker Compose construye el frontend con `npm ci && npm run build` mediante `gateway/Dockerfile`, lo sirve con Caddy y reenvía `/api/*` a Spring Boot por el mismo origen HTTPS.

```bash
# Desde la raíz, con JWT_SECRET_BASE64 configurada en .env:
docker compose --profile api up -d --build
```

Abrí `https://localhost:8443`. El gateway soporta refresh directo de `/ingresar`, `/mis-reservas` y `/admin/usuarios` mediante fallback SPA. Caddy usa `tls internal` y HTTPS solo en loopback para el MVP local. **No** publiques las cuentas demo ni reutilices el certificado interno como si fuese un despliegue de producción.

El esquema lo instala Flyway. El seed opcional de cuentas ficticias se ejecuta manualmente **después del primer arranque**; instrucciones en [`../database/README.md`](../database/README.md).

## 7. Restricciones y verificación pendiente

- La implementación está basada en la especificación MVP v1.4.3 y los controladores Java de la rama de Reservas con hotfix.
- Hay pruebas unitarias del cliente API y workflow dedicado de TypeScript / Vitest / build.
- La validación E2E con personas reales, datos reales de PostgreSQL y los 60 criterios de aceptación aún requiere ejecución y revisión del equipo.
- No se incluyen imágenes externas de productos o farmacias ni un listado ficticio de establecimientos.
- La navegación, formularios y responsive están implementados, pero falta una auditoría formal de accesibilidad WCAG y pruebas en navegadores/dispositivos reales.
