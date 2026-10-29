# Registro del CI y auditoria normativa - 29/10/2026

Commit observado: `80bfbe06ae21b7e814700f9e88edd8bae2131f74`.
Rama: `fix/cierre-mvp-v1.4.3`. Fuente: artifacts de la ejecucion remota,
no una ejecucion local de PostgreSQL, Docker o navegadores.

- Maven/PostgreSQL 16: 190 tests en 34 XML; 0 skipped, failures, errors.
- API HTTPS real: 20/20 recorridos correctos.
- Backup/restore: tres restauraciones en bases separadas; esquema/conteos/checksums coinciden.
- RNF-11 referencia CI: p95 44.1 ms lectura y 303.6 ms transacciones, con dataset demo
  y hardware 4 CPU logicas. No demuestra cualquier volumen productivo.
- UI AC-48: ocho PNG publicos Chromium 360/390/768/1440; sin resultado final
  ni Firefox. Workflow HTTPS #9 fallo, por lo que AC-48 no esta cerrado.

La auditoria de `docs/AUDITORIA_60_AC_80bfbe0.md` examina los 60 casos.
No confundir el resultado verde de tests aislados con certificacion normativa.

Referencia remota: https://github.com/GGian102-1020/MediLink/actions/runs/40947241159

## Mejora diagnostica en esta entrega

Se conserva `playwright.log`, `progreso_ui.json` y `resultado_ui.json` (este ultimo
con estado de fallo por defecto si Node termina antes de escribirlo) como
artifacts incluso cuando AC-48 falle. No se altera el criterio de exito:
una falla de browser sigue dando exit code distinto de cero y bloquea el job.
El script informa rol, fase, ruta y codigos HTTP de /api/me; no vuelca JWT.

El archivo de auditoria JSON contiene las referencias exactas de los casos.
**No aprobar merge/entrega mientras existan criterios parcialmente verificados
ni el workflow visual en rojo.**
