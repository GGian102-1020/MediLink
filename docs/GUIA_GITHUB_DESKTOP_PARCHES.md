# Guía de integración de parches - MedLink v1.4.3

**Fecha de preparación:** 29/10/2026. **Base exacta:** `706189ba527880b94d059e8204660a20f30fbb55`, rama `feature/frontend-mvp-v1.4.3`. **Rama recomendada:** `fix/cierre-mvp-v1.4.3`.

Los parches se distribuyen como archivos `.patch` de diferencias Git. **No contienen `.git`, backups ni credenciales reales** y **no sustituyen el repositorio entero**. Los commits internos que se usaron para construirlos tienen un autor técnico de preparación; al aplicarlos mediante `git apply` y hacer commit desde GitHub Desktop, la autoría será la de tu propia configuración GitHub Desktop.

## Antes de tocar nada

1. Abrí el repositorio verdadero en GitHub Desktop y elegí **Fetch origin**. Si tenés cambios sin commit, guardalos en una rama/commit aparte; no los mezcles con los parches. El panel **Changes** debe estar vacío.
2. Elegí la rama **`feature/frontend-mvp-v1.4.3`**. Si el remoto avanzó desde `706189b`, **no apliques estos parches a ciegas**: revisá el diff; no uses `reset --hard` para forzar coincidencia.
3. En Desktop: **Branch > New Branch...** y creá **`fix/cierre-mvp-v1.4.3`** desde esa rama, no desde el `main` anterior al frontend.
4. Extraé los cuatro `.patch` en una carpeta fuera del repositorio (por ejemplo, Descargas). En Desktop elegí **Repository > Open in Git Bash** (o abrí Git Bash y entrá a la raíz del repositorio).
5. Verificá `git status --short --branch` y `git rev-parse HEAD`. En una rama nueva basada en este paquete, el HEAD inicial debe empezar por `706189b`. Si no coincide, detenerse y comparar antes de aplicar.

## Aplicación y commits: exactamente en este orden

En estos ejemplos, reemplazá `/c/Users/TU_USUARIO/Downloads/medlink-parches` por **la ruta real** en Git Bash. Los archivos `01...patch` a `04...patch` vienen en el ZIP de parches.

### Commit 1 - Despliegue

```bash
git apply --check '/c/Users/TU_USUARIO/Downloads/medlink-parches/01-deploy.patch'
git apply '/c/Users/TU_USUARIO/Downloads/medlink-parches/01-deploy.patch'
git status --short
```

Revisá en Desktop los cambios de `.env.example`, `.dockerignore`, `docker-compose.yml`, `gateway/Caddyfile` y `gateway/Dockerfile`. **Seleccioná solo estos archivos**.

**Summary:** `fix(deploy): servir React y API bajo HTTPS con esquema Flyway limpio`

**Description:** `Compila React en Caddy y comparte HTTPS con /api y fallback SPA. Flyway inicializa nuevas bases sin baseline automático; el seed ficticio pasa a carga explícita y HTTPS queda ligado a loopback.`

Hacé **Commit to fix/cierre-mvp-v1.4.3**. No subas el archivo `.env` de tu equipo.

### Commit 2 - Autenticación/UX

```bash
git apply --check '/c/Users/TU_USUARIO/Downloads/medlink-parches/02-auth.patch'
git apply '/c/Users/TU_USUARIO/Downloads/medlink-parches/02-auth.patch'
```

**Summary:** `fix(frontend): cerrar sesion y confirmar cambio de contrasena`

**Description:** `Tras cambiar la contraseña se descarta el JWT invalidado por SEC-04, se redirige a ingresar y se muestra un aviso claro. También se confirma registro y se agregan pruebas unitarias de los mensajes.`

Archivos: `frontend/src/pages/Auth.tsx`, `frontend/src/lib/authNotice.ts`, `frontend/tests/authNotice.test.ts`. Hacé commit en Desktop.

### Commit 3 - CI y smoke

```bash
git apply --check '/c/Users/TU_USUARIO/Downloads/medlink-parches/03-ci.patch'
git apply '/c/Users/TU_USUARIO/Downloads/medlink-parches/03-ci.patch'
```

**Summary:** `ci: verificar backend, frontend y despliegue integrado en PR y main`

**Description:** `El gate PostgreSQL corre también en main; los PR siempre reciben los checks de backend y frontend. Se agrega prueba efímera Docker Compose con Flyway, SPA, API, seed explícito y login; la imagen backend compila sin duplicar los tests.`

Archivos: `.github/workflows/*`, `backend/Dockerfile`, `scripts/smoke_gateway.sh`, `scripts/smoke_gateway.ps1`. Hacé commit en Desktop.

### Commit 4 - Documentación y aceptación

```bash
git apply --check '/c/Users/TU_USUARIO/Downloads/medlink-parches/04-docs.patch'
git apply '/c/Users/TU_USUARIO/Downloads/medlink-parches/04-docs.patch'
```

**Summary:** `docs: documentar arranque seguro, parches y matriz AC-01 a AC-60`

**Description:** `Actualiza los README para el despliegue integrado y diferencia base nueva de adopción Flyway. Añade guía de aplicación en GitHub Desktop y matriz de los 60 criterios, inicialmente pendientes hasta verificación reproducible.`

Archivos: `Readme.md`, `database/README.md`, `frontend/README.md`, `docs/GUIA_GITHUB_DESKTOP_PARCHES.md`, `docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md`. Hacé commit en Desktop.

## Validación ANTES de publicar y crear PR

Con Docker Desktop iniciado (y `.env` configurado con un `JWT_SECRET_BASE64` aleatorio), desde la raíz:

```powershell
docker compose --profile api config --quiet
docker compose --profile api up -d --build
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/smoke_gateway.ps1
```

Para cargar cuentas ficticias **en base de demo vacía y local**, después del primer arranque:

```powershell
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/medlink/02_datos_prueba.sql'
```

Para validar el frontend local:

```powershell
cd frontend
npm ci
npm run typecheck
npm test
npm run build
cd ..
```

Para ejecutar backend y verificar reportes **contra PostgreSQL real** (Docker disponible):

```powershell
cd backend
.\mvnw.cmd -B clean verify
cd ..
python scripts/verificar_tests_postgres.py
```

Este último verificador debe terminar en `PASS` y mostrar **cero `skipped`, `failures`, `errors`**; no aceptes resultados con pruebas PostgreSQL omitidas. Si hay fallos, no fuerces el merge ni borres el volumen para “repararlos”.

Si tenías un volumen `pgdata` de instalaciones anteriores, **leé primero `database/README.md`**: la nueva forma de inicio NO re-adopta automáticamente el esquema anterior. Ejecutá backup/restauración y seguí el procedimiento controlado si corresponde.

## Publicación y Pull Request

1. En GitHub Desktop, corroborá que la pestaña **Changes** está vacía y el historial muestra los cuatro commits en orden.
2. Pulsá **Publish branch** o **Push origin** sobre `fix/cierre-mvp-v1.4.3`.
3. Si la funcionalidad frontend de `706189b` **todavía NO llegó a `main`**, abrí PR con **base `feature/frontend-mvp-v1.4.3`** y compare `fix/cierre-mvp-v1.4.3`; de ese modo revisan exclusivamente los cuatro commits de cierre. Después, integrá la rama frontend (con estas mejoras) a `main` en el PR correspondiente.
4. Si `feature/frontend-mvp-v1.4.3` **ya está incorporada en `main`**, usá base `main` y compare `fix/cierre-mvp-v1.4.3`.
5. Esperá resultados de **TypeScript + Tests + Build**, **Maven + PostgreSQL 16 + Auditoria + Backup** y **Docker Compose + Flyway + React + API (HTTPS)**. Revisá que el run corresponda al SHA de la punta de la rama.
6. Solicitá revisión del equipo; cuando todas las verificaciones pasen, completá la matriz AC del commit exacto. **No merges si un check falla, no se ejecutó o hay un AC crítico bloqueado.**

**Título de PR:** `Cierre técnico MVP v1.4.3: HTTPS integrado, Flyway, sesiones y CI`

**Descripción sugerida:**

> Integra frontend React y backend bajo un mismo origen HTTPS con Docker Compose, elimina la inicialización automática insegura del seed y utiliza Flyway para instalaciones nuevas. Corrige el feedback de cambio de contraseña y amplía los gates con pruebas PostgreSQL, frontend y smoke de despliegue. Se actualiza documentación de puesta en marcha y matriz AC-01..60. No cambia las reglas RN ni el contrato REST. **Pendiente antes del merge:** verificar todos los checks y registrar evidencias funcionales/RNF.

## Qué NO hacer

- No copiar un ZIP de código sobre `.git`, ni usar `git reset --hard` o `git push --force` para resolver conflictos.
- No versionar `.env`, claves JWT, dumps de base ni `node_modules`.
- No confundir el seed ficticio con una configuración pública segura; HTTPS solo en loopback no habilita acceso remoto.
- No marcar AC como `PASS` sin ejecutar y conservar la evidencia.
