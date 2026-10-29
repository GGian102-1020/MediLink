# Auditoria de evidencia automatizada - MedLink MVP v1.4.3

**Fecha:** 2026-10-29. **Rama:** `fix/cierre-mvp-v1.4.3`.
**SHA al que pertenecen las evidencias:** `4dd252e2ce9f1a04095aab3999634855c6192a6e`.
**Alcance:** revision documental de los artifacts de GitHub Actions, NO aprobacion integral de AC-01..AC-60.

## Ejecuciones certificadas por sus artifacts

| Workflow | Run | Resultado | Evidencia |
|---|---|---|---|
| Integracion PostgreSQL #30 | [40938548571](https://github.com/GGian102-1020/MediLink/actions/runs/40938548571) | SUCCESS | 34 suites JUnit; 183 tests; 0 skipped; 0 failures; 0 errors |
| Despliegue HTTPS #5 | [40938548538](https://github.com/GGian102-1020/MediLink/actions/runs/40938548538) | SUCCESS | 20/20 recorridos API reales; SPA, API, Flyway y seed explicito |
| Frontend React #7 | [40938548519](https://github.com/GGian102-1020/MediLink/actions/runs/40938548519) | SUCCESS | TypeScript, tests y build del workflow, sin reporte unitario de navegador incluido |

**Archivos y SHA-256 publicados por GitHub (coinciden con los ZIP revisados):**

- `medlink-postgres-surefire-reports.zip`: `45e31fe1b7f306cc506a52f6d890aab4cf80726f0936ab4637710841aea499ab`.
- `medlink-ac-e2e-evidencia.zip`: `810ec46e46955f792624e15c5ba621c365fdc0b773b87c9cad43a2708d1afc4c`.

Los XML registran 183 casos en 34 suites. Siete suites PostgreSQL suman 62 tests que **se ejecutaron** sin skips; no son las 62 omisiones del entorno Windows sin Docker, que correspondian a otro SHA y otra ejecucion. El log API incluye los 20 pasos PASS y una linea final de resumen satisfactorio; no hubo FAIL.

## Restauracion real (AC-59, evidencia fuerte)

- `evidencias/backup_ci.md`: PostgreSQL 16.15, `pg_dump -Fc`, `pg_restore --single-transaction --exit-on-error`, base destino distinta y descartable, esquema/conteos/checksums coincidentes: `PASS`.
- `evidencias/backup_ci_flyway.md`: la misma verificacion sobre la BD `medlink_ci`, incluida la tabla `flyway_schema_history`: `PASS`.
- `_temp/backup-regression/regression.md` y `restore.md`: la regresion de readiness del servidor temporal y la restauracion real tambien informan `PASS`.

La evidencia satisface materialmente el objetivo de restaurar en una base separada. Antes de modificar AC-59 a PASS normativo, verificar que el procedimiento y los logs conservados cumplen la precondicion de entrega y que la documentacion refleja el entorno exacto.

## Trazabilidad de aceptacion (no confundir cobertura con cumplimiento)

- Indice PostgreSQL: **49/60** AC con algun caso correlacionado que paso.
- Indice HTTPS: **25/60** AC con algun paso correlacionado que paso.
- Union sin duplicados: **54/60** AC con al menos una fuente candidata; 20 tienen ambas fuentes, 29 solo PostgreSQL, 5 solo API.
- **0/60 aceptados normativamente por los indices**: todas las filas permanecen `PENDIENTE` hasta comprobar precondiciones, saldos, codigo HTTP, roles, estado final y evidencia individual necesaria.

### Seis AC sin referencia automatizada individual

| AC | Escenario faltante | Evidencia a incorporar |
|---|---|---|
| AC-31 | Crear Reserva vs deshabilitar Usuario | Concurrencia real; API y BD; nunca dejar una Cuenta DESHABILITADA con Reserva activa |
| AC-32 | Crear Reserva vs deshabilitar ultimo Operador | Concurrencia real contra Farmacia con un solo Operador |
| AC-40 | Crear Reserva Rx vs anular Prescripcion | Concurrencia real; compromiso Rx y estado final en ambos ordenes |
| AC-41 | Crear Reserva vs desactivacion administrativa | Concurrencia real; serializacion de Farmacia/Medicamento/Presentacion |
| AC-48 | Navegacion publica catalogo a disponibilidad | Navegador React real; busqueda, detalle, disponibilidad, refresh y viewport |
| AC-55 | Reactivar Cuenta conserva Suspension | API/BD/reloj controlado; persistencia de Suspension vigente |

### Correccion de referencia del AC-02

El indice original relacionaba AC-02 (limite de **cinco Reservas activas**) con `ReservasPostgresTest.otcLimitAndGlobalLimitDoNotMutateStockOnRejection`, pero ese metodo verifica el limite de **tres unidades OTC** de AC-03. La comprobacion adecuada **si existe y paso**: `ReservasPostgresTest.globalLimitIsCountedAcrossDifferentPresentations`, que confirma cinco Reservas de presentaciones distintas y el rechazo de la sexta sin modificar Stock.

El mapeo de AC-02 se corrige en `scripts/reportar_evidencia_ac.py`. No cambia codigo productivo ni convierte ese AC automaticamente a PASS; el siguiente workflow generara un indice con la referencia correcta para su propio SHA.

## Pendientes no funcionales

- RNF-06/07: navegadores Chrome/Firefox/Edge y pruebas reales de interfaz a 360, 390, 768 y 1440 px (roles Visitante, Usuario, Operador, Administrador). No hay capturas o trazas de navegador en estos ZIP.
- RNF-11: medicion p95 de lecturas <= 1 s y transacciones <= 2 s, con hardware, dataset, concurrencia y volumen documentados. Los tiempos de ejecucion JUnit **no son** esa medicion.
- RNF-12 / AC-59: los artifacts muestran restauraciones exitosas; guardar los logs y su revision final junto al SHA.

## Decision y alcance

**La evidencia tecnica automatizada de `4dd252e` es satisfactoria.** No aparecen fallos, errores ni skips en los XML, ni FAIL en el recorrido HTTPS. **El MVP aun NO puede declararse aceptado por completo**, porque quedan AC sin trazabilidad especifica y otros con evidencia parcial; faltan pruebas de navegador y rendimiento.

No ejecutar smoke destructivo sobre entornos persistentes. No modificar `main` ni mergear por el solo hecho de tener CI verde. Conservar estos artifacts y el SHA al documentar AC individuales.
