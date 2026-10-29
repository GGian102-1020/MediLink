# Registro de verificacion tecnica - MedLink MVP v1.4.3

**Fecha de registro:** 2026-10-29. **Rama:** `fix/cierre-mvp-v1.4.3`.
**Ultimo commit con ejecuciones observadas en verde:** [`642bce7`](https://github.com/GGian102-1020/MediLink/commit/642bce7).

## Historial constatado

| Commit | Workflow | Ejecucion observada | Alcance |
|---|---|---|---|
| `642bce7` | `MediLink - Integracion PostgreSQL` #29 | **SUCCESS** (captura de Actions aportada por el equipo, 29/10/2026) | Java 21/Maven, tests contra PostgreSQL 16, verificador sin omisiones, auditoria y backup/restauracion |
| `642bce7` | `MediLink - Frontend React` #6 | **SUCCESS** (misma captura) | TypeScript, tests y build |
| `642bce7` | `MediLink - Despliegue HTTPS y seed demo` #4 | **SUCCESS** (misma captura) | Docker Compose efimero, Flyway, Caddy HTTPS, React SPA, API, seed explicito y 16 recorridos API |

**Consulta en GitHub:** [checks del commit 642bce7](https://github.com/GGian102-1020/MediLink/commit/642bce7/checks) y [historial de Actions](https://github.com/GGian102-1020/MediLink/actions).
Los numeros #29/#6/#4 son secuencias de *workflow*, no IDs de run. No inventar URLs ni dar por vigente su resultado para un SHA posterior.

### Incidente resuelto durante la validacion

- Commit `5d6bf95`: el nuevo E2E fallo en el recorrido de retiro por una expectativa errada: se esperaba `403` al consultar una reserva de otra farmacia por la ruta de la farmacia propia, pero el contrato devuelve `404 RESOURCE_NOT_FOUND` (recurso ajeno a ese ambito).
- Commit `642bce7`: el runner distingue **403** por acceso a farmacia ajena y **404** por codigo de reserva que no pertenece a la farmacia de la ruta. Los tres workflows volvieron a verde.
- No fue necesario modificar la logica de Spring Security para resolver este incidente.

### Observacion sobre la validacion local de Windows

- La corrida local del 29/10/2026 en el commit `f7671654a163dc7e1be565a4b2958fc322f2290c` registro **17 PASS, 1 FAIL, 8 BLOCKED**.
- El analizador posterior determino **34 reportes JUnit, 183 pruebas, 62 skipped, 0 failures y 0 errors**: las siete suites PostgreSQL se omitieron por ausencia de motor local/Docker.
- No equivale a haber ejecutado PostgreSQL en Windows. El workflow **remoto** dispone de PostgreSQL 16 y verifica que los tests no se omitan.
- Los resultados de `f767165` pertenecen a otro SHA; no trasladar automaticamente una etiqueta AC a `642bce7` ni a un commit futuro.

## Que significa exactamente "verde"

Las tres acciones exitosas prueban que sus comandos definidos terminaron con codigo cero en los entornos registrados. **No equivalen a PASS de los 60 AC**, a ensayos completos de navegadores ni a rendimiento p95. En particular, el runner E2E previo tenia 16 recorridos, varios rotulados como *parciales*.

Se agrega en esta etapa:

1. `scripts/reportar_evidencia_ac.py`: un **indice de evidencia parcial por AC** basado en nombres exactos de casos JUnit y pasos reales de API. La columna normativa siempre permanece `PENDIENTE` hasta comprobar todo el AC.
2. `scripts/tests/test_reportar_evidencia_ac.py`: evita que un test omitido, XML inexistente, log inconcluso o cambio de orden de escenarios cuenten como evidencia satisfactoria.
3. Artifacts por SHA con reportes Surefire y evidencias de backup, y otro artifact con el log del smoke HTTPS y el indice de AC. Retencion solicitada: 90 dias (sujeta a la politica del repositorio/plan).
4. Cuatro recorridos adicionales sobre la base CI descartable: AC-43, AC-19, AC-45 y AC-53 (aun **no ejecutados** en GitHub a la fecha de preparacion de este documento).

**No etiquetar como validados esos cambios nuevos hasta observar las nuevas ejecuciones verdes y revisar sus artifacts.**

## Como conservar evidencia del siguiente SHA

En la rama `fix/cierre-mvp-v1.4.3`, sin PR ni merge:

1. Subir el commit de pruebas/documentacion y abrir los tres workflows de Actions para **ese mismo SHA**.
2. Descargar el artifact `medlink-postgres-surefire-reports` (Surefire XML, backup/restauracion, indice PostgreSQL).
3. Descargar el artifact `medlink-ac-e2e-evidencia` (log de recorridos e indice API).
4. Adjuntar URL de los runs, SHA, fecha, cantidad de tests, backups verificados, entorno y cualquier limitacion a la matriz normativa `docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md`.
5. Ejecutar y registrar la comprobacion manual de cada AC pendiente, incluidos los cuatro roles y RNF-06/07/11. **Nunca reemplazar PENDIENTE por PASS en bloque.**

## Criterio de cierre

- Requerir los tres workflows en verde para el SHA final de la rama.
- No tener AC-01..AC-60 sin evidencia completa; todo `FAIL` o `BLOCKED` debe resolverse o quedar documentado antes de declarar MVP cerrado.
- RNF-11: p95 de lecturas <=1 s y transacciones <=2 s, con hardware, dataset, volumen y concurrencia documentados.
- RNF-12 / AC-59: verificar al menos una restauracion en base separada, con contenido y esquema contrastados. El workflow proporciona evidencias, pero hay que conservarlas.
- La rama no debe fusionarse con `main` durante esta campaña de validacion sin decision posterior del equipo.
