# Matriz de aceptación - MedLink MVP v1.4.3

**Origen normativo:** sección 15, AC-01..AC-60, de la especificación maestra v1.4.3 (27/09/2026).

**Estado inicial de esta matriz: NO VERIFICADO.** Los test existentes o las evidencias de otros commits no constituyen automáticamente PASS del commit actual. Una persona debe completar cada fila después de ejecutar la comprobación y conservar evidencia reproducible.

## Cómo registrar un resultado

- Reemplazar `PENDIENTE` por `PASS`, `FAIL` o `BLOCKED` **únicamente** después de ejecutarlo; anexar URL del run de Actions o archivo de evidencia con comando, entorno, fecha y commit SHA.
- Para escenarios concurrentes: usar solicitudes realmente simultáneas y PostgreSQL, no mocks. Acreditar estado final BD y salida HTTP.
- Para flujos UI: abrir navegador (360/390/768/1440 px cuando aplique), validar rol/permiso/refresh y dejar captura o registro reproducible.
- Conservar el origen de la especificación; esta matriz es derivada y no redefine reglas funcionales.

| AC | Escenario (síntesis de la especificación) | Evidencia recomendada | Estado | Evidencia / commit |
|---|---|---|---|---|
| AC-01 | Ultima unidad de Stock: un ganador | PostgreSQL concurrente | PENDIENTE | — |
| AC-02 | Limite de 5 Reservas activas | PostgreSQL/API | PENDIENTE | — |
| AC-03 | Limite de 3 unidades OTC por Presentacion | PostgreSQL/API | PENDIENTE | — |
| AC-04 | Dos Reservas compiten por 1 unidad Rx | PostgreSQL concurrente | PENDIENTE | — |
| AC-05 | Cancelar exactamente al vencimiento | PostgreSQL/reloj | PENDIENTE | — |
| AC-06 | Cancelar Reserva bajo receta y liberar recursos | PostgreSQL/API | PENDIENTE | — |
| AC-07 | Scheduler vence una sola vez | PostgreSQL/scheduler | PENDIENTE | — |
| AC-08 | Retiro no descuenta Stock dos veces | PostgreSQL/API | PENDIENTE | — |
| AC-09 | Operador no accede a otra Farmacia | API autorizacion | PENDIENTE | — |
| AC-10 | No deshabilitar ultimo Operador | PostgreSQL/API | PENDIENTE | — |
| AC-11 | Desactivacion con Reserva activa rechazada | PostgreSQL/API | PENDIENTE | — |
| AC-12 | Deshabilitar Usuario cancela y libera atomicamente | PostgreSQL/API | PENDIENTE | — |
| AC-13 | Tres incumplimientos crean Suspensión | PostgreSQL/reloj | PENDIENTE | — |
| AC-14 | Suspendido no reserva, puede retirar anterior | PostgreSQL/API | PENDIENTE | — |
| AC-15 | Incumplimientos previos a fin de Suspensión no reutilizables | PostgreSQL/reloj | PENDIENTE | — |
| AC-16 | Evaluaciones concurrentes no duplican Suspensión | PostgreSQL concurrente | PENDIENTE | — |
| AC-17 | Replay de misma Idempotency-Key y payload | PostgreSQL/API | PENDIENTE | — |
| AC-18 | Misma clave con payload distinto | PostgreSQL/API | PENDIENTE | — |
| AC-19 | Reserva Rx sobrevive a anulacion posterior | PostgreSQL/API | PENDIENTE | — |
| AC-20 | Stock ausente equivale a cero | API/BD | PENDIENTE | — |
| AC-21 | Catalogo inactivo no es reservable | API/UI | PENDIENTE | — |
| AC-22 | STOCK_INCONSISTENTE atomico | PostgreSQL/API | PENDIENTE | — |
| AC-23 | Cambio de clave invalida JWT previos | API/UI | PENDIENTE | — |
| AC-24 | Logout revoca jti | API | PENDIENTE | — |
| AC-25 | Snapshots historicos no cambian al renombrar | PostgreSQL/API | PENDIENTE | — |
| AC-26 | Suspension determinista con vencimientos desordenados | PostgreSQL/reloj | PENDIENTE | — |
| AC-27 | Deshabilitar Operador no ultimo | PostgreSQL/API | PENDIENTE | — |
| AC-28 | Admin no confirma retiro | API autorizacion | PENDIENTE | — |
| AC-29 | Vinculo Item Rx y venta libre sin Item | PostgreSQL/BD | PENDIENTE | — |
| AC-30 | Movimientos de Stock trazables y ordenados | API/BD | PENDIENTE | — |
| AC-31 | Crear Reserva contra deshabilitar Usuario | PostgreSQL concurrente | PENDIENTE | — |
| AC-32 | Crear Reserva contra deshabilitar ultimo Operador | PostgreSQL concurrente | PENDIENTE | — |
| AC-33 | Estados iniciales correctos | API/BD | PENDIENTE | — |
| AC-34 | Ajuste Stock crea saldo ausente | PostgreSQL/API | PENDIENTE | — |
| AC-35 | Duplicado de Presentacion textual y sin equivalencia | PostgreSQL/API | PENDIENTE | — |
| AC-36 | Suspension sin scheduler rechaza nueva Reserva | PostgreSQL/reloj | PENDIENTE | — |
| AC-37 | Backlog reconstruye suspensiones historicas | PostgreSQL/reloj | PENDIENTE | — |
| AC-38 | Ajuste absoluto materializa vencimiento pendiente | PostgreSQL concurrente | PENDIENTE | — |
| AC-39 | STOCK_INCONSISTENTE con vencimientos pendientes | PostgreSQL concurrente | PENDIENTE | — |
| AC-40 | Crear Reserva Rx contra anular Prescripcion | PostgreSQL concurrente | PENDIENTE | — |
| AC-41 | Crear Reserva contra desactivacion administrativa | PostgreSQL concurrente | PENDIENTE | — |
| AC-42 | Dos POST simultaneos con misma clave idempotente | PostgreSQL concurrente | PENDIENTE | — |
| AC-43 | Error no consume Idempotency-Key | PostgreSQL/API | PENDIENTE | — |
| AC-44 | Estado API efectivo VENCIDA antes del scheduler | API/reloj | PENDIENTE | — |
| AC-45 | Nombre Medicamento no es unico | API/BD | PENDIENTE | — |
| AC-46 | Operador de Farmacia INACTIVA solo consulta historial | API autorizacion | PENDIENTE | — |
| AC-47 | Privacidad Reserva en vista de Operador | API autorizacion | PENDIENTE | — |
| AC-48 | Navegacion publica catalogo a disponibilidad | UI/API | PENDIENTE | — |
| AC-49 | Multiples Reservas simultaneas validas | PostgreSQL/API | PENDIENTE | — |
| AC-50 | Stock persiste tras desactivar/reactivar | PostgreSQL/API | PENDIENTE | — |
| AC-51 | Ventana de incumplimientos inclusiva al milisegundo | PostgreSQL/reloj | PENDIENTE | — |
| AC-52 | Segundos restantes y vencimiento efectivo en DTO | API/UI | PENDIENTE | — |
| AC-53 | Consulta administrativa sin secretos | API autorizacion | PENDIENTE | — |
| AC-54 | Backlog mas antiguo que ventana actual | PostgreSQL/reloj | PENDIENTE | — |
| AC-55 | Reactivar Cuenta no elimina Suspensión vigente | API/BD | PENDIENTE | — |
| AC-56 | Auditoria usuarioId filtra sujeto, no actor | API/BD | PENDIENTE | — |
| AC-57 | Operador no usa causas exclusivas Admin/Sistema | API autorizacion | PENDIENTE | — |
| AC-58 | Metadatos obligatorios de cancelación | API/BD | PENDIENTE | — |
| AC-59 | Backup y restauracion real en base separada | Operacion/backup | PENDIENTE | — |
| AC-60 | Misma historia y T producen misma cadena de suspensiones | PostgreSQL/reloj | PENDIENTE | — |

## Criterios para aprobar el merge

1. `MediLink - Integracion PostgreSQL`: `mvn verify`, verificador XML con cero omitted/failures/errors, y backup/restauración; registrar run.
2. `MediLink - Frontend React`: TypeScript, tests y build; registrar run.
3. `MediLink - Despliegue HTTPS y seed demo`: Flyway en BD vacía, páginas SPA, API y seed explícito con login en entorno efímero; registrar run.
4. Validación humana por los cuatro roles y todas las filas AC-01..AC-60; documentar las que todavía requieren datos/reloj/concurrencia especial.
5. Para RNF-11: p95 con hardware, dataset y carga anotados; RNF-12/AC-59 restauración comprobada; no declararlos PASS por existir un script.

**No marcar el MVP como cerrado** mientras haya filas `PENDIENTE`, `FAIL` o `BLOCKED`.
