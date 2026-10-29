# Protocolo de ejecución y evidencia — AC-01 a AC-60

**Referencia normativa:** Especificación Maestra MedLink v1.4.3, sección 15 (27/09/2026). Este protocolo es auxiliar: no sustituye la especificación ni cambia la matriz original.

**Estado de aceptacion normativa al preparar esta version:** 60/60 PENDIENTE. Hay pruebas automatizadas de apoyo, pero ninguna fila ha sido aprobada individualmente con el conjunto completo de evidencias; no confundir con los checks verdes del commit `642bce7`.

## Preparación segura

Usar PostgreSQL 16 y entorno DEMO descartable, con usuarios/recetas/stock fixture controlados. Antes de cada caso registrar URL del run, SHA, datos iniciales y resultado HTTP o capturas. No usar credenciales reales ni conservar JWT en los logs.

Para concurrencia enviar HTTP realmente en paralelo (barrera común, no dos clics consecutivos), con usuarios/keys correctos, y comprobar estado final en BD. Para tiempo capturar un T determinista con precisión milisegundos, estado persistido y DTO API antes y después del scheduler.

Para UI recorrer Visitante, Usuario, Operador y Administrador en Chrome, Firefox y Edge (dos últimas versiones estables disponibles), comprobando permisos, sesión, navegación, refresh y anchos 360, 390, 768 y 1440 px. Anotar fecha, navegador, versión y capturas.

## Verificación individual

| AC | Escenario normativo resumido | Resultado que hay que demostrar | Evidencia mínima | Estado |
|---|---|---|---|---|
| AC-01 | Ultima unidad de Stock: un ganador | Una reserva gana; la otra INSUFFICIENT_STOCK; saldo 0. | HTTP simultáneo + BD final | PENDIENTE |
| AC-02 | Limite de 5 Reservas activas | 409 MAX_ACTIVE_RESERVATIONS_EXCEEDED; stock intacto. | HTTP + verificación BD | PENDIENTE |
| AC-03 | Limite de 3 unidades OTC por Presentacion | 409 MAX_OTC_UNITS_EXCEEDED. | HTTP + verificación BD | PENDIENTE |
| AC-04 | Dos Reservas compiten por 1 unidad Rx | Solo una reserva Rx; comprometida + utilizada <= autorizada. | HTTP simultáneo + BD final; ItemRx comprometida/utilizada | PENDIENTE |
| AC-05 | Cancelar exactamente al vencimiento | VENCIDA y RESERVATION_EXPIRED al milisegundo exacto; libera una vez. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-06 | Cancelar Reserva bajo receta y liberar recursos | CANCELADA; restituye stock y libera Rx sin incumplimiento. | ItemRx comprometida/utilizada | PENDIENTE |
| AC-07 | Scheduler vence una sola vez | VENCIDA; exactamente un movimiento VENCIMIENTO. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-08 | Retiro no descuenta Stock dos veces | COMPLETADA sin nuevo descuento; Rx pasa a utilizada. | HTTP + verificación BD | PENDIENTE |
| AC-09 | Operador no accede a otra Farmacia | 403 FORBIDDEN; sin datos ajenos. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-10 | No deshabilitar ultimo Operador | 409 LAST_OPERATIONAL_OPERATOR. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-11 | Desactivacion con Reserva activa rechazada | 409 PHARMACY_HAS_ACTIVE_RESERVATIONS. | HTTP + verificación BD | PENDIENTE |
| AC-12 | Deshabilitar Usuario cancela y libera atomicamente | Cancelaciones y liberaciones atómicas; cuenta deshabilitada. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-13 | Tres incumplimientos crean Suspensión | Una suspensión de 7 días al completar tres incumplimientos elegibles. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-14 | Suspendido no reserva, puede retirar anterior | USER_SUSPENDED en nueva; retiro previo permitido. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-15 | Incumplimientos previos a fin de Suspensión no reutilizables | Incumplimientos anteriores al fin no se reutilizan. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-16 | Evaluaciones concurrentes no duplican Suspensión | Sin suspensión duplicada ni intervalos superpuestos. | HTTP simultáneo + BD final | PENDIENTE |
| AC-17 | Replay de misma Idempotency-Key y payload | Replay 201 original; sin segunda reserva. | HTTP + verificación BD | PENDIENTE |
| AC-18 | Misma clave con payload distinto | 409 IDEMPOTENCY_KEY_REUSED. | HTTP + verificación BD | PENDIENTE |
| AC-19 | Reserva Rx sobrevive a anulacion posterior | Retiro Rx autorizado pese a anulación posterior. | ItemRx comprometida/utilizada | PENDIENTE |
| AC-20 | Stock ausente equivale a cero | Stock ausente = 0; rechazo INSUFFICIENT_STOCK. | HTTP + verificación BD | PENDIENTE |
| AC-21 | Catalogo inactivo no es reservable | No reservable; reservas anteriores conservan ciclo. | captura UI + DTO/refresh | PENDIENTE |
| AC-22 | STOCK_INCONSISTENTE atomico | Cancelación + Rx + stock real + auditoría atómicos. | HTTP + verificación BD | PENDIENTE |
| AC-23 | Cambio de clave invalida JWT previos | JWT antiguo inválido; nueva contraseña permite login. | JWT/rol + HTTP y estado intacto; captura UI + DTO/refresh | PENDIENTE |
| AC-24 | Logout revoca jti | Mismo jti rechazado tras logout. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-25 | Snapshots historicos no cambian al renombrar | Snapshots históricos inmutables al renombrar. | HTTP + verificación BD | PENDIENTE |
| AC-26 | Suspension determinista con vencimientos desordenados | Intervalos deterministas independientemente del orden técnico. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-27 | Deshabilitar Operador no ultimo | Operador deshabilitado; sesiones inválidas; farmacia operativa. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-28 | Admin no confirma retiro | 403 FORBIDDEN; ningún cambio en reserva o Rx. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-29 | Vinculo Item Rx y venta libre sin Item | Rx vincula al item correcto; OTC tiene item null. | ItemRx comprometida/utilizada | PENDIENTE |
| AC-30 | Movimientos de Stock trazables y ordenados | Movimientos completos ordenados DESC por fechaHora,id. | HTTP + verificación BD | PENDIENTE |
| AC-31 | Crear Reserva contra deshabilitar Usuario | No queda cuenta deshabilitada con reserva activa. | HTTP simultáneo + BD final | PENDIENTE |
| AC-32 | Crear Reserva contra deshabilitar ultimo Operador | La farmacia nunca queda sin operador operativo. | HTTP simultáneo + BD final | PENDIENTE |
| AC-33 | Estados iniciales correctos | Cuentas y entidades con estados iniciales especificados. | HTTP + verificación BD | PENDIENTE |
| AC-34 | Ajuste Stock crea saldo ausente | Se crea Stock 5 y AJUSTE_MANUAL desde 0. | HTTP + verificación BD | PENDIENTE |
| AC-35 | Duplicado de Presentacion textual y sin equivalencia | PRESENTATION_DUPLICATE textual; sin equivalencias implícitas. | HTTP + verificación BD | PENDIENTE |
| AC-36 | Suspension sin scheduler rechaza nueva Reserva | USER_SUSPENDED antes de scheduler sin cambiar recursos. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-37 | Backlog reconstruye suspensiones historicas | Reconstruye suspensiones históricas hasta T. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-38 | Ajuste absoluto materializa vencimiento pendiente | Materializa vencimiento y fija saldo EXACTO 5, no 6. | HTTP simultáneo + BD final | PENDIENTE |
| AC-39 | STOCK_INCONSISTENTE con vencimientos pendientes | Materializa vencimientos, cancela Rx y fija stock real exacto. | HTTP simultáneo + BD final | PENDIENTE |
| AC-40 | Crear Reserva Rx contra anular Prescripcion | Reserva valida primero o anulación rechaza nueva Rx. | HTTP simultáneo + BD final; ItemRx comprometida/utilizada | PENDIENTE |
| AC-41 | Crear Reserva contra desactivacion administrativa | Confirmación/desactivación dejan combinación compatible. | HTTP simultáneo + BD final | PENDIENTE |
| AC-42 | Dos POST simultaneos con misma clave idempotente | Un solo 201 almacenado y una reserva como máximo. | HTTP simultáneo + BD final | PENDIENTE |
| AC-43 | Error no consume Idempotency-Key | La key fallida se libera y reintento puede confirmar. | HTTP + verificación BD | PENDIENTE |
| AC-44 | Estado API efectivo VENCIDA antes del scheduler | DTO VENCIDA efectivo con segundosRestantes=0. | reloj T/ms + estado BD/API; captura UI + DTO/refresh | PENDIENTE |
| AC-45 | Nombre Medicamento no es unico | Medicamentos homónimos con IDs distintos. | HTTP + verificación BD | PENDIENTE |
| AC-46 | Operador de Farmacia INACTIVA solo consulta historial | Historial y login sí; acciones funcionales bloqueadas. | JWT/rol + HTTP y estado intacto; captura UI + DTO/refresh | PENDIENTE |
| AC-47 | Privacidad Reserva en vista de Operador | Solo nombres y apellidos; sin correo ni teléfono. | JWT/rol + HTTP y estado intacto; captura UI + DTO/refresh | PENDIENTE |
| AC-48 | Navegacion publica catalogo a disponibilidad | Visitante navega medicamento -> presentación -> disponibilidad. | captura UI + DTO/refresh | PENDIENTE |
| AC-49 | Multiples Reservas simultaneas validas | Se permiten varias reservas mientras respeten límites. | HTTP + verificación BD | PENDIENTE |
| AC-50 | Stock persiste tras desactivar/reactivar | Stock conserva saldo al desactivar/reactivar. | HTTP + verificación BD | PENDIENTE |
| AC-51 | Ventana de incumplimientos inclusiva al milisegundo | Ventana inclusiva al ms y descarte de candidato fuera. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-52 | Segundos restantes y vencimiento efectivo en DTO | segundosRestantes correcto; 0 en terminales. | captura UI + DTO/refresh | PENDIENTE |
| AC-53 | Consulta administrativa sin secretos | Admin solo historial seguro, sin secretos ni ediciones. | JWT/rol + HTTP y estado intacto; captura UI + DTO/refresh | PENDIENTE |
| AC-54 | Backlog mas antiguo que ventana actual | Backlog antiguo crea suspensiones históricas correctas. | reloj T/ms + estado BD/API | PENDIENTE |
| AC-55 | Reactivar Cuenta no elimina Suspensión vigente | Reactivar no levanta suspensión vigente. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-56 | Auditoria usuarioId filtra sujeto, no actor | Filtro usuarioId usa sujeto usuarioRelacionadoId, no actor. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-57 | Operador no usa causas exclusivas Admin/Sistema | Operador no puede usar causas exclusivas de Admin/Sistema. | JWT/rol + HTTP y estado intacto | PENDIENTE |
| AC-58 | Metadatos obligatorios de cancelación | Metadatos de cancelación estructurados y motivo cuando aplica. | HTTP + verificación BD | PENDIENTE |
| AC-59 | Backup y restauracion real en base separada | Restauración real en BD separada con evidencia. | pg_dump + pg_restore en BD separada | PENDIENTE |
| AC-60 | Misma historia y T producen misma cadena de suspensiones | Misma historia y T producen mismos intervalos. | reloj T/ms + estado BD/API | PENDIENTE |

## Datos del resultado de cada caso

Anotar **SHA del commit exacto**, URL del run o archivo, comando reproducible, entorno, versión de PostgreSQL, instante T, IDs y código HTTP. Para pruebas concurrentes adjuntar ambos request IDs y el resultado final de stock/compromisos/suspensiones.

No completar PASS por ver el nombre de un test o por resultados históricos. Si una prueba se intentó pero no pudo ejecutarse, registrar BLOCKED con causa; si se ejecutó y discrepó, registrar FAIL y abrir corrección.

## Gates adicionales

- RNF-06/07: prueba real de navegadores y responsive, más los cuatro roles.
- RNF-11: p95 lecturas <=1 s, transaccionales <=2 s; adjuntar hardware, versiones, dataset, volumen, concurrencia y método.
- RNF-12/AC-59: restauración real en base separada; verificar datos y esquema Flyway.
- CI: tres workflows verdes sobre SHA de la rama/PR y reporte Maven XML con cero skipped, failures y errors.
- Merge: no proceder hasta cerrar todas las filas y los criterios del documento normativo.
