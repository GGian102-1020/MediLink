# Registro de ejecución CI — MedLink MVP v1.4.3

- Fecha de ejecución: 29/10/2026.
- Rama: `fix/cierre-mvp-v1.4.3`.
- Commit evaluado: `c7c2db1b1f1f28dd4390967b68a7c7691a50c4c7`.
- Fuente de evidencia: dos artifacts descargados de GitHub Actions, enviados
  por el equipo, más captura del listado de workflows.
- **Estado global de despliegue:** FAIL (AC-48 navegador). No se hace merge.

## Datos observados

| Control | Resultado | Observación |
|---|---|---|
| PostgreSQL/JUnit | PASS 190/190, 0 omitted/failed/errors | Incluye AC-31, AC-32, AC-40, tres AC-41 y AC-55 con métodos exitosos |
| Backup/restauración PostgreSQL 16.15 | PASS 2/2 | Dos bases distintas, checksums, esquema y conteos coinciden |
| API E2E | PASS 20/20 | Smoke de roles, reservas, stock, Rx e idempotencia |
| Catálogo/disponibilidad Chromium | PASS en 360, 390, 768, 1440 px | Las ocho capturas de catálogo y disponibilidad están en artifact |
| Restaurar sesión en UI | FAIL en test | `USUARIO: se perdio sesion tras refresh`; test comprobó DOM demasiado pronto |
| Firefox/roles completos | NO EJECUTADO | El runner terminó después del primer rol en Chromium |
| Rendimiento p95 | NO EJECUTADO | Step omitido tras fallo UI; no existe JSON de resultados |
| AC-01..60 normativos | PENDIENTES de aceptación integral | Un test aislado no certifica automáticamente la totalidad de un AC |

## Causa del fallo y acción

En `frontend/scripts/validar_ui.mjs`, `waitForURL` detectaba de inmediato la URL
original tras `page.reload()`; el `.count()` siguiente corría mientras `AuthProvider`
seguía ejecutando `/api/me`. Esto puede ser un falso negativo. Se corrige la
sincronización en el parche 11. Si en el siguiente run se redirige realmente al login,
el diagnóstico permitirá investigar autenticación sin ocultar el fallo.

## Evidencias que deben conservarse

Los artifacts son registros efímeros en GitHub; conservar los ZIP del commit antes
de su vencimiento. Este documento resume resultados, no los sustituye. La matriz
`docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md` mantiene `PENDIENTE` para los AC sin
aceptación funcional completa. No introducir resultados de futuros commits aquí.
