# Administración local MediFind

Este frontend se recuperó de `origin/main` (`f351296`) porque `androidFINAL-dashboard` conserva únicamente la web de operadores. Es independiente de `frontend/` (farmacia) y `landing/` (institucional). No incluye migraciones ni otro backend.

Ejecutar desde la raíz `scripts/demo-local/Start.ps1`. Se sirve en `http://127.0.0.1:5175`, solo en la PC, con `/api` proxyeado al mismo backend local. La cuenta ficticia es `admin@demo.medifind.test`, contraseña de demo `DemoLocal123!`.

Contiene las pantallas administrativas existentes de farmacias, medicamentos, presentaciones, operadores, usuarios, prescripciones y auditoría; conserva también sus componentes y rutas auxiliares de catálogo y perfil. El backend sigue aplicando todos los permisos. El panel nuevo de farmacia permanece en `http://127.0.0.1:5173`.

Validación: `npm.cmd --prefix admin-web test`, `npm.cmd --prefix admin-web run build` y `node scripts/demo-local/admin-smoke.mjs`. La guía completa es `docs/MEDIFIND_DEMO_LOCAL.md`.
