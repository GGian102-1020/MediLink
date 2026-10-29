# Auditoría normativa de los 60 AC — última ejecución verificada

**SHA de la evidencia ejecutada:** `33f946f32f71347f9385dbd8f554fb366bd5b3ce` · **Rama:** `fix/cierre-mvp-v1.4.3` · **Norma:** Especificación Maestra v1.4.3, sección 15.

**Resultado técnico revisado en el SHA:** DIRECTO **24**, PARCIAL **35**, BLOQUEADO **1** (AC-16).
**Certificación final 60/60: NO ALCANZADA.** Ninguna prueba incorporada a continuación debe marcarse PASS antes de ejecutarla en CI.

## Evidencia real comprobada
- PostgreSQL/JUnit: **190 tests**, 0 failures, 0 errors, 0 skipped, en 34 XML.
- API HTTPS: **20/20 recorridos PASS**.
- UI: `resultado_ui.json` **PASS**, Chromium y Firefox, 360/390/768/1440 px; no equivale a validar Microsoft Edge.
- Backups/restauraciones: **3 informes con PASS** en bases separadas.
- RNF-11 (carga de referencia): p95 lecturas **17.1 ms** y transacciones **208.4 ms**, con hardware/dataset/volumen declarados; no significa una garantía en otro entorno.
- SHA-256 PostgreSQL: `ab1df42a91e8d7f5d0765a72d842693690a87b9e6d457124fd95e683c122e611`
- SHA-256 E2E: `861a201676bc25b767bb54ea136ea868689d146fb9d7c2f0c154f3b48d9547f6`

## Criterios: cumplimiento observado frente a condiciones aún no demostradas
La categoría **DIRECTO** significa evidencia técnica suficiente para la condición revisada; no es firma de aceptación ni prueba universal. **PARCIAL** conserva condiciones abiertas. **BLOQUEADO** carece de la prueba decisiva.

| Criterio | Estado comprobado al SHA 33f946f | Evidencia real | Condición observada / faltante | Nueva prueba propuesta (AÚN SIN EJECUTAR) |
|---|---|---|---|---|
| AC-01 | **DIRECTO** | `A14;J:ReservasPostgresTest.concurrentLastStockUnitIsNeverOversold` | HTTP 201/409, INSUFFICIENT_STOCK y saldo final 0 con peticiones simultaneas. | — |
| AC-02 | **PARCIAL** | `J:ReservasPostgresTest.globalLimitIsCountedAcrossDifferentPresentations` | Falta verificar HTTP 409 y saldo inmutable contra API real en el mismo caso. | — |
| AC-03 | **DIRECTO** | `A09;J:ReservasPostgresTest.otcLimitAndGlobalLimitDoNotMutateStockOnRejection` | HTTP 409 MAX_OTC_UNITS_EXCEEDED y stock sin cambios. | — |
| AC-04 | **DIRECTO** | `J:ReservasPostgresTest.concurrentSamePrescriptionItemNeverExceedsAuthorization` | Dos Reservas Rx reales compiten por un Item y no sobrecomprometen; consultar respuesta HTTP para mayor cobertura. | — |
| AC-05 | **PARCIAL** | `J:FarmaciaStockPostgresTest.expiredReservationsDoNotBlockPharmacyDeactivationAtTheExactBoundary` | No hay cancelacion propia exactamente T ni comprobacion HTTP RESERVATION_EXPIRED. | `ReservasPostgresTest.ac05CancellationAtExactExpiryMaterializesWithoutDoubleRelease` |
| AC-06 | **PARCIAL** | `A13;J:ReservasPostgresTest.prescriptionCommitCancellationAndCompletionConserveAuthorizedQuantity` | Cancelacion Rx libera stock/compromiso; falta comprobar ausencia de incumplimiento del mismo caso. | — |
| AC-07 | **PARCIAL** | `J:ReservasPostgresTest.expiredReservationIsEffectiveBeforeMaterializationAndReleaseIsOnce` | Se verifica materializacion idempotente, pero no ejecucion efectiva del scheduler. | `ReservasPostgresTest.ac07ActualExpirySchedulerIsIdempotent` |
| AC-08 | **DIRECTO** | `A11;J:ReservasPostgresTest.operatorCompletesWithoutRedeductingStock` | Retiro COMPLETADA en API y stock sin segundo descuento en BD. | — |
| AC-09 | **DIRECTO** | `A05;J:ReservasPostgresTest.adminAndOperatorPermissionsAreSeparated` | Operador fuera de farmacia recibe 403, sin datos de reserva. | — |
| AC-10 | **DIRECTO** | `A07;J:AdministrationServiceTest.cannotDisableLastOperationalPharmacyOperator` | 409 LAST_OPERATIONAL_OPERATOR por API. | — |
| AC-11 | **DIRECTO** | `A07;J:FarmaciaServiceTest.cannotDeactivateWithFunctionallyActiveReservations` | 409 PHARMACY_HAS_ACTIVE_RESERVATIONS por API. | — |
| AC-12 | **PARCIAL** | `J:ReservasPostgresTest.ac31ConcurrentReservationAndUserDeactivationAreAtomic` | Falta fixture con mezcla de vencidas y Rx activas para verificar todas las liberaciones en una misma transaccion. | — |
| AC-13 | **PARCIAL** | `J:FarmaciaStockPostgresTest.expirationReleasesPrescriptionCommitmentsAndEvaluatesSuspensions;J:SuspensionPlannerTest.boundary30DaysInclusiveAndSchedulingOrderIrrelevant` | Falta comprobar en PG duracion de Suspension e inicio en el tercer vencimiento con historia completa. | — |
| AC-14 | **PARCIAL** | `J:ReservasPostgresTest.suspendedUserCannotReserveAndSuspensionStillVisibleAfterReplay` | Bloqueo de nuevas Reservas probado; retiro de Reserva previa durante la suspension sin prueba conjunta. | — |
| AC-15 | **PARCIAL** | `J:SuspensionPlannerTest.exactlyEndOfPreviousSuspensionIsEligible` | Seleccion historica cubierta en unidad; falta validacion de no reutilizacion de incumplimientos en persistencia real. | — |
| AC-16 | **BLOQUEADO** | `J:SuspensionPostgresTest.schemaForbidsChangingOrOverlappingHistoricalIntervals` | No hay dos evaluaciones concurrentes que compitan por crear la Suspension y demuestren unicidad. | `ReservasPostgresTest.ac16TwoConcurrentExpiriesCreateExactlyOneSuspension` |
| AC-17 | **DIRECTO** | `A10;J:ReservasPostgresTest.sameIdempotencyKeyReplaysOriginal201EvenAfterCancellation` | Respuesta 201 original reemitida sin otra Reserva aun tras cancelacion. | — |
| AC-18 | **DIRECTO** | `A10;J:ReservasPostgresTest.idempotencyKeyWithDifferentPayloadIsRejected` | 409 IDEMPOTENCY_KEY_REUSED al cambiar payload. | — |
| AC-19 | **DIRECTO** | `A17;J:ReservasPostgresTest.prescriptionCommitCancellationAndCompletionConserveAuthorizedQuantity` | Anulacion posterior no invalida retiro Rx ya confirmado. | — |
| AC-20 | **DIRECTO** | `A08;J:FarmaciaStockPostgresTest.publicAvailabilityExcludesAbsentZeroInactiveAndNonOperationalStock` | Stock ausente => no disponibilidad y 409 INSUFFICIENT_STOCK. | — |
| AC-21 | **PARCIAL** | `A01;A08;J:CatalogoPostgresTest.activeDuplicateAndStateTransitionsDoNotErasePresentationOrAudit` | Falta comprobar expresamente ciclo de Reserva anterior al desactivar junto con exclusion UI. | — |
| AC-22 | **PARCIAL** | `J:ReservasPostgresTest.manualRealStockCorrectionIsAbsoluteAndAudited;J:FarmaciaStockPostgresTest.auditFailureRollsBackBalanceMovementAndExpirationTogether` | No se prueba junto en mismo escenario: cancelacion Rx, libera Item, stock real, auditoria y rollback ante fallo. | — |
| AC-23 | **PARCIAL** | `J:BearerAuthenticationFilterTest.tokenVersionChangeInvalidatesPreviouslySignedJwt;J:AuthServiceTest.rejectsSamePasswordOrIncorrectCurrentPassword` | JWT invalida tokenVersion; falta login real con nueva clave y rechazo HTTP del JWT previo en mismo flujo. | — |
| AC-24 | **DIRECTO** | `A20;J:BearerAuthenticationFilterTest.revokedJtiIsNeverAccepted` | Logout seguido de /api/me 401 INVALID_TOKEN. | — |
| AC-25 | **PARCIAL** | `J:ReservasPostgresTest.createIsAtomicAndProducesMovementAndSnapshots` | Snapshots al crear si; falta editar/renombrar farmacia y medicamento y consultar historia. | `ReservasPostgresTest.ac25SnapshotsStayConstantAfterAdminRenames` |
| AC-26 | **PARCIAL** | `J:SuspensionPlannerTest.boundary30DaysInclusiveAndSchedulingOrderIrrelevant` | Orden de entrada irrelevante en memoria; falta secuencia PG de materializaciones desordenadas y no superposicion. | — |
| AC-27 | **PARCIAL** | `J:AdministrationServiceTest.cannotDisableLastOperationalPharmacyOperator;J:BearerAuthenticationFilterTest.tokenVersionChangeInvalidatesPreviouslySignedJwt` | Falta deshabilitar un operador NO ultimo y comprobar JWT previo, reservas intactas y operatividad. | — |
| AC-28 | **DIRECTO** | `A11;J:ReservasPostgresTest.adminCannotCompleteButCanCancelOperativelyWithoutRestitution` | 403 FORBIDDEN a administrador y posterior retiro del operador demuestra Reserva intacta. | — |
| AC-29 | **PARCIAL** | `A13;J:ReservasPostgresTest.prescriptionCommitCancellationAndCompletionConserveAuthorizedQuantity` | Falta SELECT explicito sobre itemPrescripcionId de Rx y NULL de OTC y coherencia Usuario/Presentacion. | `ReservasPostgresTest.ac29PrescriptionItemLinkAndOtcNullArePersisted` |
| AC-30 | **PARCIAL** | `A12;J:FarmaciaStockPostgresTest.stockRestHistoryIsPaginatedFilteredAndOrderedAndRejectsNegative` | Movimientos trazables y ordenados; falta demostrar secuencia DESCUENTO + RESTITUCION con saldo final e identidad exacta. | `ReservasPostgresTest.ac30ReservationAndRestitutionMovementsAreTraceableAndOrdered` |
| AC-31 | **PARCIAL** | `J:ReservasPostgresTest.ac31ConcurrentReservationAndUserDeactivationAreAtomic` | Se verifica estado final PG con transacciones simultaneas; falta observar salida HTTP 409/201 en carrera. | — |
| AC-32 | **PARCIAL** | `J:ReservasPostgresTest.ac32ConcurrentReservationCannotDisableLastOperationalOperator` | Se prueba carrera y DB; falta observar salida HTTP LAST_OPERATIONAL_OPERATOR en ambas solicitudes. | — |
| AC-33 | **PARCIAL** | `A10;J:ReservasPostgresTest.createIsAtomicAndProducesMovementAndSnapshots;J:FarmaciaStockPostgresTest.pharmacyRestCreationEditionFiltersAndStateTransitions` | Faltan todos los estados iniciales de CUENTA, OPERADOR, MEDICAMENTO, PRESENTACION y PRESCRIPCION reunidos en un test. | — |
| AC-34 | **PARCIAL** | `A14;J:StockServiceTest.absentStockCreatesZeroBaselineBeforeAbsoluteAdjustment;J:FarmaciaStockPostgresTest.absoluteAdjustmentPersistsMovementAuditAndRealTimestamp` | Se crea Stock ausente y movimiento; falta caso exacto PUT 5 con anterior 0, delta 5 y motivo. | — |
| AC-35 | **PARCIAL** | `J:CatalogoPostgresTest.activeDuplicateAndStateTransitionsDoNotErasePresentationOrAudit;J:PresentacionServiceTest.exactDuplicateReturns409` | Falta diferencia textual farmacologicamente equivalente sin deduplicacion implicita. | `ReservasPostgresTest.ac35NormalizedDuplicateRejectedButPharmacologicalEquivalenceNotInferred` |
| AC-36 | **PARCIAL** | `J:SuspensionPlannerTest.pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension` | Falta POST /api/reservas con tercer vencimiento aun ACTIVA en BD y scheduler detenido. | — |
| AC-37 | **PARCIAL** | `J:SuspensionPlannerTest.pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension` | Backlog calculado en unidad; falta persistir historicos con PG y verificar resultado tras reinicio de scheduler. | `ReservasPostgresTest.ac37Ac54Ac60HistoricalBacklogPersistsDeterministically` |
| AC-38 | **DIRECTO** | `J:FarmaciaStockPostgresTest.expiredReservationIsReleasedExactlyOnceBeforeAbsoluteCount;J:FarmaciaStockPostgresTest.concurrentAdjustmentsMaterializeTheSameExpirationOnlyOnce` | Stock absoluto tras vencer en PG sin llevar balance a 6. | — |
| AC-39 | **PARCIAL** | `J:ReservasPostgresTest.inconsistentStockCorrectionRespectsPendingExpiry` | Materializa vencimiento y fija stock, pero no prueba simultaneamente liberacion de Item Rx en ese flujo. | — |
| AC-40 | **DIRECTO** | `J:ReservasPostgresTest.ac40ConcurrentPrescriptionAnnulmentAndReservationSerialize` | Carrera real PG; estado Rx ANULADA, max 1 Reserva, compromiso/stock invariantes. | — |
| AC-41 | **DIRECTO** | `J:ReservasPostgresTest.ac41ConcurrentPharmacyDeactivationAndReservationAreCompatible;J:ReservasPostgresTest.ac41ConcurrentMedicationDeactivationAndReservationAreCompatible;J:ReservasPostgresTest.ac41ConcurrentPresentationDeactivationAndReservationAreCompatible` | Tres carreras reales y estados finales PG compatibles. | — |
| AC-42 | **DIRECTO** | `A15;J:ReservasPostgresTest.concurrentSameIdempotencyKeyCreatesExactlyOneReservation` | Dos POST HTTP simultaneos retornan 201 igual; stock disminuye solo una vez. | — |
| AC-43 | **DIRECTO** | `A16;J:ReservasPostgresTest.failedReservationDoesNotConsumeIdempotencyKey` | Tras 409 por stock y ajuste, misma key permite 201 sin duplicacion. | — |
| AC-44 | **PARCIAL** | `J:ReservasPostgresTest.expiredReservationIsEffectiveBeforeMaterializationAndReleaseIsOnce` | Estado VENCIDA y filtro probado; falta segundosRestantes=0 en DTO de API sin scheduler. | — |
| AC-45 | **DIRECTO** | `A18;J:MedicamentoServiceTest.createsAnActiveMedicineAuditingNormalizedName` | API permite dos medicamentos con mismo nombre e ids distintos. | — |
| AC-46 | **PARCIAL** | `J:FarmaciaStockPostgresTest.realJwtRolesScopesAndInactivePharmacyRules` | Historial y ajuste denegado comprobados en MockMvc; falta login y cancelacion/retiro en farmacia inactiva. | — |
| AC-47 | **PARCIAL** | `A06;J:ReservasPostgresTest.visibilityIsRestrictedByUserAndOperatorScope` | No expone correo/telefono; falta aseverar que nombre y apellido del titular SI aparecen. | — |
| AC-48 | **DIRECTO** | UI:resultado_ui.json PASS; Chromium/Firefox 360/390/768/1440; A01 | Recorrido público catálogo -> presentación -> disponibilidad y refresh; roles y sesiones en ambos navegadores; API consulta operativas. | — |
| AC-49 | **DIRECTO** | `J:ReservasPostgresTest.globalLimitIsCountedAcrossDifferentPresentations;A12` | Multiples reservas ACTIVA en distintas presentaciones; no unicidad indebida. | — |
| AC-50 | **PARCIAL** | `J:FarmaciaStockPostgresTest.realJwtRolesScopesAndInactivePharmacyRules` | Persistencia del saldo tras inactivar/reactivar; falta disponibilidad publica antes/durante/despues. | `ReservasPostgresTest.ac50StockSurvivesPharmacyAndPresentationInactivation` |
| AC-51 | **DIRECTO** | `J:SuspensionPlannerTest.boundary30DaysInclusiveAndSchedulingOrderIrrelevant;J:SuspensionPlannerTest.olderThan30DaysExcludedByOneMillisecond` | Ventana de 30 dias exactos inclusiva y candidato 1 ms fuera excluido. | — |
| AC-52 | **PARCIAL** | `A11;J:ReservasPostgresTest.expiredReservationIsEffectiveBeforeMaterializationAndReleaseIsOnce` | Segundos restantes positivos mientras ACTIVA; falta demostrar cero y VENCIDA en DTO en la frontera. | `ReservasPostgresTest.ac52EffectiveExpiryAndSecondsInUserDTO` |
| AC-53 | **PARCIAL** | `A19;J:AuditoriaPostgresTest.secretsAreNeitherPersistedNorExposedFromLegacySummaries` | Sin secretos en listados; falta flujo Admin Usuario que consulte conjuntamente reservas/suspensiones/auditoria. | — |
| AC-54 | **PARCIAL** | `J:SuspensionPlannerTest.pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension` | Falta prueba PG con eventos ya mas antiguos que [T-30d,T] y reconstruccion historica tras pausa. | `ReservasPostgresTest.ac37Ac54Ac60HistoricalBacklogPersistsDeterministically` |
| AC-55 | **PARCIAL** | `J:ReservasPostgresTest.ac55ReactivatingAccountPreservesCurrentSuspension` | Persistencia, bloqueo y stock comprobados en PostgreSQL; falta HTTP USER_SUSPENDED de POST real. | — |
| AC-56 | **DIRECTO** | `J:AuditoriaPostgresTest.relatedUserNeverMeansActorAccount` | Filtrado por sujeto usuarioRelacionadoId, no por actorCuentaId, probado en PostgreSQL. | — |
| AC-57 | **PARCIAL** | `J:ReservasPostgresTest.adminAndOperatorPermissionsAreSeparated;J:ReservasPostgresTest.operationalCancellationRequiresStructuredRestitutionChoice` | Falta comprobar ambos 403 del operador con ERROR_ADMINISTRATIVO y CUENTA_DESHABILITADA. | `ReservasPostgresTest.ac57AndAc58OperationalCancellationAuthorityAndMetadata` |
| AC-58 | **PARCIAL** | `A12;J:ReservasPostgresTest.ownCancellationReleasesExactlyOnceAndHasNoSuspension` | Metadatos de usuario correctos; falta probar causa operativa con motivo obligatorio y momento T. | `ReservasPostgresTest.ac57AndAc58OperationalCancellationAuthorityAndMetadata` |
| AC-59 | **DIRECTO** | `B:backup_ci.md;B:backup_ci_flyway.md;B:backup-regression/restore.md` | Tres restauraciones en DB separada con esquema/conteos/checksums verificados. | — |
| AC-60 | **PARCIAL** | `J:SuspensionPlannerTest.boundary30DaysInclusiveAndSchedulingOrderIrrelevant;J:SuspensionPlannerTest.pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension` | Falta comparar cadena PG persistida frente a dos ejecuciones de mismo historial y T con orden tecnico distinto. | `ReservasPostgresTest.ac37Ac54Ac60HistoricalBacklogPersistsDeterministically` |

## Reglas para actualizar los resultados
1. Una prueba nueva no certifica un AC hasta verificar el XML del SHA nuevo contra PostgreSQL 16 y sus aserciones; confirmar ausencia de skipped/failures/errors.
2. Para carreras se exige concurrencia real y estado final de PostgreSQL; las pruebas de servicio no sustituyen automáticamente la observación HTTP si la norma la requiere.
3. Para tiempo/scheduler se exige comprobar límites temporales y persistencia con reloj conocido; una prueba del planificador en memoria no equivale a una ejecución del scheduler.
4. AC-48 requiere resultado visual final, capturas y API consistente; RNF-06 Edge y validación humana de los cuatro roles se registran aparte.
5. No modificar `docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md` a PASS sin evidencia individual, reproducible, correspondiente al mismo SHA.

## Próxima ejecución
La ampliación incluida en esta entrega aporta **11 pruebas JUnit PostgreSQL nuevas** (varias cubren más de un AC). **No están ejecutadas aún** y su resultado no está sumado a los 190 tests reales. El bloque de mayor riesgo es la concurrencia AC-16 y la reconstrucción persistida del backlog AC-37/54/60.
Las demás condiciones abiertas de los 35 parciales siguen requiriendo pruebas específicas antes de declarar 60/60.
