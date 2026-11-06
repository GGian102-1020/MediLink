# Diagnostico del PR #8 (6 de noviembre de 2026)

Repositorio: `GGian102-1020/MediLink`
PR: <https://github.com/GGian102-1020/MediLink/pull/8>
Commit inspeccionado: `3ded636`
Ejecucion verificada: <https://github.com/GGian102-1020/MediLink/actions/runs/42234404898>

## Causa de los cuatro checks rojos

La ejecucion #45 muestra **dos jobs bloqueados antes de comenzar**:

- `React / TypeScript / Mapas` (evento `pull_request`).
- `Maven + PostgreSQL 16 + Auditoria + Backup` (evento `pull_request`).

Los mismos dos jobs tambien se disparan en `push`; por eso el PR muestra cuatro checks fallidos.
GitHub indica literalmente:

> The job was not started because your account is locked due to a billing issue.

La anotacion sobre la migracion de `ubuntu-latest` a Ubuntu 26 es informativa, no la causa.
**No hay evidencia de que una prueba, el build ni la base PostgreSQL hayan fallado dentro del runner**: los runners no iniciaron.

**Este problema no se corrige editando YAML ni codigo.** Hay que desbloquear la facturacion de la cuenta propietaria del repositorio (incluso un intento fallido de autorizacion puede bloquear Actions en un repositorio publico).

1. Entrar como propietario en GitHub > Settings > Billing & licensing > Payment information.
2. Revisar pagos rechazados, retenciones de autorizacion, metodo de pago y presupuestos.
3. Si no aparece ningun motivo de bloqueo, pedir revision a GitHub Support: <https://support.github.com/>.
4. Cuando se desbloquee la cuenta, generar un nuevo run (nuevo push o `gh run rerun 42234404898 --failed -R GGian102-1020/MediLink`).
5. Si los runners comienzan y surgen errores reales de codigo, recien ahi analizar sus logs y artefactos.

Documentacion: <https://docs.github.com/es/billing/how-tos/troubleshooting/locked-account>

## Verificacion local independiente de Actions

Desde la raiz del repositorio, en PowerShell:

```powershell
.\scripts\diagnosticar_ci_pr8.ps1
.\scripts\diagnosticar_ci_pr8.ps1 -ProbarFrontend
# Para backend: Java 21, Docker Desktop funcionando, Python 3 y Testcontainers.
.\scripts\diagnosticar_ci_pr8.ps1 -ProbarBackend
```

La opcion `-ProbarFrontend` ejecuta `npm ci`, `npm run typecheck`, `npm test` y `npm run build`, en ese orden. `npm ci` **reinstala** las dependencias de `frontend/node_modules` (no toca los archivos versionados). La opcion `-ProbarBackend` ejecuta `mvnw.cmd -B clean verify` y verifica que se ejecutaron las suites PostgreSQL, sin aceptar una URL de base de datos externa para evitar modificar datos ajenos. Requiere Docker y Testcontainers.

### Alcance de la revision

- Se verifico el error concreto de Actions leyendo las anotaciones del run #45 en GitHub.
- El `typecheck` del frontend sobre `3ded636` termino exitosamente con Node 22.
- El ZIP contiene `node_modules` instalados en Windows. Fuera de Windows, Vitest y Vite no pudieron ejecutarse con esas dependencias porque falta `@rollup/rollup-linux-x64-gnu`. Esto se resuelve con una instalacion limpia en la plataforma correspondiente (`npm ci`); no demuestra un fallo del PR.
- No se certifica el resultado de Maven/PostgreSQL sin ejecutar el suite completo con Docker. Los workflows actuales no lo ejecutaron por el bloqueo de facturacion.
- El ZIP tiene diferencias CRLF frente al arbol Git de 189 archivos: no se incluyen en este parche para evitar cambios masivos sin contenido.

Este parche aporta diagnostico reproducible y una alternativa local; **no desbloquea ni promete que los cuatro checks de GitHub pasen**.
