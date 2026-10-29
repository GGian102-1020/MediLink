#!/usr/bin/env python3
"""Create an evidence INDEX for AC-01..AC-60; never certify acceptance automatically.

Reads local Surefire XML and/or the real HTTPS smoke log. A green CI run is
supporting evidence, NOT a substitute for the complete normative acceptance
scenario, UI/browser checks, DB final state or controlled time.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
from xml.etree import ElementTree as ET

# Exact method selectors: existing JUnit tests, not generic class-name matches.
# Source type "pg" denotes a PostgreSQL-backed test. "api" denotes one actual
# step of the end-to-end API runner (all steps must finish successfully).
PG = "ar.edu.uade.medlink."
R = PG + "reserva.ReservasPostgresTest."
S = PG + "stock.FarmaciaStockPostgresTest."
P = PG + "prescripcion.PrescripcionConcurrencyTest."
U = PG + "suspension.SuspensionPostgresTest."
A = PG + "auditoria.AuditoriaPostgresTest."
C = PG + "presentacion.CatalogoPostgresTest."
D = PG + "admin.SuspensionPlannerTest."
B = PG + "security.BearerAuthenticationFilterTest."

# Source references only; NO assertion that a whole AC has been proven.
SOURCES = {
    1: [("pg", R + "concurrentLastStockUnitIsNeverOversold"), ("api", 14)],
    2: [("pg", R + "globalLimitIsCountedAcrossDifferentPresentations")],
    3: [("pg", R + "otcLimitAndGlobalLimitDoNotMutateStockOnRejection"), ("api", 9)],
    4: [("pg", P + "onlyOneOfTwoSimultaneousReservationsGetsTheLastUnit")],
    5: [("pg", R + "expiredReservationIsEffectiveBeforeMaterializationAndReleaseIsOnce")],
    6: [("pg", R + "prescriptionCommitCancellationAndCompletionConserveAuthorizedQuantity"), ("api", 13)],
    7: [("pg", R + "expiredReservationIsEffectiveBeforeMaterializationAndReleaseIsOnce")],
    8: [("pg", R + "operatorCompletesWithoutRedeductingStock"), ("api", 11)],
    9: [("pg", R + "visibilityIsRestrictedByUserAndOperatorScope"), ("api", 5)],
    10: [("api", 7)],
    11: [("pg", S + "pharmacyRestCreationEditionFiltersAndStateTransitions"), ("api", 7)],
    12: [("pg", S + "adjustmentAndExistingUserDeactivationShareLocksAndDoNotDoubleRelease")],
    13: [("pg", S + "expirationReleasesPrescriptionCommitmentsAndEvaluatesSuspensions")],
    14: [("pg", R + "suspendedUserCannotReserveAndSuspensionStillVisibleAfterReplay")],
    15: [("unit", D + "pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension")],
    16: [("pg", U + "schemaForbidsChangingOrOverlappingHistoricalIntervals")],
    17: [("pg", R + "sameIdempotencyKeyReplaysOriginal201EvenAfterCancellation"), ("api", 10)],
    18: [("pg", R + "idempotencyKeyWithDifferentPayloadIsRejected"), ("api", 10)],
    19: [("api", 17)],
    20: [("pg", S + "publicAvailabilityExcludesAbsentZeroInactiveAndNonOperationalStock"), ("api", 8)],
    21: [("pg", S + "inactivePresentationCannotBeAdjustedOrOfferedPublicly"), ("api", 8)],
    23: [("unit", B + "tokenVersionChangeInvalidatesPreviouslySignedJwt")],
    22: [("pg", R + "inconsistentStockCorrectionRespectsPendingExpiry")],
    24: [("unit", B + "revokedJtiIsNeverAccepted"), ("api", 20)],
    25: [("pg", R + "createIsAtomicAndProducesMovementAndSnapshots")],
    26: [("unit", D + "boundary30DaysInclusiveAndSchedulingOrderIrrelevant")],
    27: [("pg", S + "realJwtRolesScopesAndInactivePharmacyRules")],
    28: [("pg", R + "adminCannotCompleteButCanCancelOperativelyWithoutRestitution"), ("api", 11)],
    29: [("pg", R + "prescriptionCommitCancellationAndCompletionConserveAuthorizedQuantity"), ("api", 13)],
    30: [("pg", S + "stockRestHistoryIsPaginatedFilteredAndOrderedAndRejectsNegative"), ("api", 12)],
    31: [("pg", R + "ac31ConcurrentReservationAndUserDeactivationAreAtomic")],
    32: [("pg", R + "ac32ConcurrentReservationCannotDisableLastOperationalOperator")],
    33: [("pg", R + "createIsAtomicAndProducesMovementAndSnapshots"), ("api", 10)],
    34: [("pg", S + "absoluteAdjustmentPersistsMovementAuditAndRealTimestamp"), ("api", 14)],
    35: [("pg", C + "activeDuplicateAndStateTransitionsDoNotErasePresentationOrAudit")],
    36: [("pg", R + "suspendedUserCannotReserveAndSuspensionStillVisibleAfterReplay")],
    37: [("unit", D + "pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension")],
    38: [("pg", S + "expiredReservationIsReleasedExactlyOnceBeforeAbsoluteCount")],
    39: [("pg", R + "inconsistentStockCorrectionRespectsPendingExpiry")],
    40: [("pg", R + "ac40ConcurrentPrescriptionAnnulmentAndReservationSerialize")],
    41: [("pg", R + "ac41ConcurrentPharmacyDeactivationAndReservationAreCompatible"),
         ("pg", R + "ac41ConcurrentMedicationDeactivationAndReservationAreCompatible"),
         ("pg", R + "ac41ConcurrentPresentationDeactivationAndReservationAreCompatible")],
    42: [("pg", R + "concurrentSameIdempotencyKeyCreatesExactlyOneReservation"), ("api", 15)],
    43: [("pg", R + "failedReservationDoesNotConsumeIdempotencyKey"), ("api", 16)],
    44: [("pg", R + "expiredReservationIsEffectiveBeforeMaterializationAndReleaseIsOnce")],
    45: [("api", 18)],
    46: [("pg", S + "realJwtRolesScopesAndInactivePharmacyRules")],
    47: [("pg", R + "visibilityIsRestrictedByUserAndOperatorScope"), ("api", 6)],
    48: [("ui", "AC-48 navegador real catalogo a disponibilidad")],
    49: [("pg", R + "globalLimitIsCountedAcrossDifferentPresentations")],
    50: [("pg", S + "deactivationUsesFunctionalExpiryAndReactivationPreservesStock")],
    51: [("unit", D + "boundary30DaysInclusiveAndSchedulingOrderIrrelevant"),
         ("unit", D + "olderThan30DaysExcludedByOneMillisecond")],
    52: [("api", 11)],
    53: [("api", 19)],
    54: [("unit", D + "pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension")],
    55: [("pg", R + "ac55ReactivatingAccountPreservesCurrentSuspension")],
    56: [("pg", A + "relatedUserNeverMeansActorAccount")],
    57: [("pg", R + "operationalCancellationRequiresStructuredRestitutionChoice")],
    58: [("pg", R + "ownCancellationReleasesExactlyOnceAndHasNoSuspension"), ("api", 12)],
    59: [("backup", "backup_ci.md + backup_ci_flyway.md")],
    60: [("unit", D + "boundary30DaysInclusiveAndSchedulingOrderIrrelevant"),
         ("unit", D + "pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension")],
}


# Additional PostgreSQL acceptance scenarios introduced after commit 33f946f.
# These references are evidence candidates ONLY after the exact method passed on a real
# PostgreSQL run; they do not turn the normative status to PASS by themselves.
ADDITIONAL_PG = {
    5: ['ac05CancellationAtExactExpiryMaterializesWithoutDoubleRelease'],
    7: ['ac07ActualExpirySchedulerIsIdempotent'],
    16: ['ac16TwoConcurrentExpiriesCreateExactlyOneSuspension'],
    25: ['ac25SnapshotsStayConstantAfterAdminRenames'],
    29: ['ac29PrescriptionItemLinkAndOtcNullArePersisted'],
    30: ['ac30ReservationAndRestitutionMovementsAreTraceableAndOrdered'],
    35: ['ac35NormalizedDuplicateRejectedButPharmacologicalEquivalenceNotInferred'],
    37: ['ac37Ac54Ac60HistoricalBacklogPersistsDeterministically'],
    50: ['ac50StockSurvivesPharmacyAndPresentationInactivation'],
    52: ['ac52EffectiveExpiryAndSecondsInUserDTO'],
    54: ['ac37Ac54Ac60HistoricalBacklogPersistsDeterministically'],
    57: ['ac57AndAc58OperationalCancellationAuthorityAndMetadata'],
    58: ['ac57AndAc58OperationalCancellationAuthorityAndMetadata'],
    60: ['ac37Ac54Ac60HistoricalBacklogPersistsDeterministically'],
}
for ac, methods in ADDITIONAL_PG.items():
    SOURCES[ac].extend(('pg', R + method) for method in methods)

# An API step may have changed numbering; indexes are pinned to the current script.
# Use named markers in newer runners to avoid assigning the wrong AC on renumbering.
API_STEP_LABELS = {
    1: "Visitante: catalogo y disponibilidad",
    5: "Operador: scope de farmacia",
    6: "Privacidad operador y reserva ajena",
    7: "Ultimo operador y farmacia con reserva activa",
    8: "Stock ausente y catalogo inactivo",
    9: "Limite OTC por usuario y presentacion",
    10: "Reserva real y replay idempotente",
    11: "Retiro, ambito y stock",
    12: "Cancelacion y restitucion OTC",
    13: "Reserva bajo receta y liberacion",
    14: "Dos usuarios, ultima unidad real",
    15: "Dos POST simultaneos misma clave",
    16: "Error previo no consume la clave",
    17: "Receta anulada posteriormente",
    18: "Nombre de medicamento no unico",
    19: "Consulta de usuarios administrativos sin secretos",
    20: "Logout invalida token",
}
# IMPORTANT: read the step label from the runtime log, don't use ordinal alone.
# The exact ordinal map is set via the helper below to guard against accidental
# reassignment if the smoke runner inserts a new step.

def find_api_steps(log: str) -> dict[int, str]:
    lines = {}
    completed = re.search(r"(?m)^PASS: (\d+) escenarios de recorrido API; NO certifica AC-01\.\.AC-60\s*$", log)
    if not completed:
        return lines
    for line in log.splitlines():
        match = re.match(r"^PASS (\d+): (.*)$", line)
        if match:
            lines[int(match.group(1))] = match.group(2)
    if not lines or set(lines) != set(range(1, max(lines) + 1)) or int(completed.group(1)) != len(lines):
        return {}
    return lines


def junit_results(path: Path | None) -> dict[str, bool]:
    result = {}
    if not path or not path.exists():
        return result
    for report in path.glob("TEST-*.xml"):
        root = ET.parse(report).getroot()
        for test in root.iter("testcase"):
            cls, name = test.get("classname"), test.get("name")
            if cls and name:
                result[f"{cls}.{name}"] = not any(test.find(tag) is not None for tag in ("skipped", "failure", "error"))
    return result


def parse_matrix(path: Path) -> list[tuple[int, str, str]]:
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^\| AC-(\d{2}) \| ([^|]+) \| ([^|]+) \|", line)
        if match:
            rows.append((int(match.group(1)), match.group(2).strip(), match.group(3).strip()))
    if [number for number, _, _ in rows] != list(range(1, 61)):
        raise ValueError("La matriz normativa no contiene exactamente AC-01..AC-60")
    return rows


def ui_evidence_valid(path: Path | None, sha: str) -> bool:
    if not path or not path.is_file():
        return False
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return (data.get("schema") == 1 and data.get("ac") == "AC-48"
                and data.get("status") == "PASS" and data.get("sha") == sha
                and data.get("base") == "https://localhost:8443"
                and data.get("widths") == [360, 390, 768, 1440]
                and not data.get("failures")
                and all(isinstance(data.get("browsers", {}).get(browser, {}).get("version"), str)
                        and data["browsers"][browser]["version"]
                        and len(data["browsers"][browser].get("checks", [])) == 5
                        for browser in ("chromium", "firefox")))
    except (OSError, ValueError, TypeError, KeyError, AttributeError):
        return False


def make_report(matrix: Path, reports: Path | None, log: Path | None, sha: str,
                backups: list[Path] | None = None, ui_evidence: Path | None = None) -> dict:
    junit = junit_results(reports)
    api_steps = find_api_steps(log.read_text(encoding="utf-8") if log and log.exists() else "")
    ui_ok = ui_evidence_valid(ui_evidence, sha)
    backup_ok = bool(backups) and len(backups) == 2 and all(
        file.exists() and "Resultado: PASS" in file.read_text(encoding="utf-8") for file in backups)
    entries = []
    for number, summary, recommended in parse_matrix(matrix):
        found = []
        for kind, selector in SOURCES.get(number, []):
            if kind in {"pg", "unit"} and junit.get(selector) is True:
                found.append({"tipo": "PostgreSQL JUnit" if kind == "pg" else "JUnit unitario", "referencia": selector})
            if kind == "ui" and ui_ok:
                found.append({"tipo": "Navegador real Chromium+Firefox", "referencia": str(selector)})
            if kind == "backup" and backup_ok:
                found.append({"tipo": "Restauracion CI", "referencia": selector})
            if kind == "api":
                expected = API_STEP_LABELS.get(selector)
                if expected and expected in api_steps.get(selector, ""):
                    found.append({"tipo": "API HTTPS", "referencia": f"PASS {selector:02d}: {expected}"})
        entries.append({"ac": f"AC-{number:02d}", "escenario": summary,
                        "evidencia_recomendada": recommended,
                        "evidencia_automatizada": found,
                        "cobertura": "EVIDENCIA_PARCIAL" if found else "SIN_EVIDENCIA_AUTOMATIZADA",
                        "aceptacion_normativa": "PENDIENTE"})
    return {"sha": sha, "ac_totales": 60,
            "con_evidencia_parcial": sum(bool(row["evidencia_automatizada"]) for row in entries),
            "ac_verificados_completamente": 0, "criterios": entries,
            "advertencia": "NO certifica AC; corroborar precondiciones/estado BD/reloj/UI manualmente."}


def markdown(data: dict) -> str:
    result = ["# Indice de evidencia automatizada para AC-01..AC-60", "",
              f"Commit evaluado: `{data['sha']}`.", "",
              f"AC con ALGUNA evidencia automatizada: **{data['con_evidencia_parcial']}/60**.",
              "AC acreditados normativamente por este informe: **0/60** (requieren revision completa).", "",
              "**Los PASS de JUnit y API NO equivalen a aceptar integralmente un AC.**",
              "Usar la matriz normativa para la decision PASS/FAIL/BLOCKED individual.", "",
              "| AC | Escenario | Evidencia ejecutada y satisfactoria | Aceptacion |", "|---|---|---|---|"]
    for row in data["criterios"]:
        ev = "; ".join("`" + item["referencia"] + "`" for item in row["evidencia_automatizada"]) or "Sin evidencia automatizada correlacionada"
        result.append(f"| {row['ac']} | {row['escenario']} | {ev} | PENDIENTE |")
    result.extend(["", "## Interpretacion", "",
                   "- Evidencia de test: una asercion automatizada especifica paso; NO prueba todos los resultados normativos.",
                   "- Criterios con concurrencia requieren solicitudes realmente simultaneas y estado final PostgreSQL.",
                   "- Criterios con reloj requieren limites en ms y T determinista.",
                   "- Flujos UI necesitan navegador real y evidencias de cuatro roles y tamaños.",
                   "- AC-59 exige restauracion en base separada, con logs y datos verificados; consultar artifact de PostgreSQL.",
                   "- No copiar PASS a la matriz normativa sin verificacion humana y SHA exacto.", ""])
    return "\n".join(result)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", default="docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md", type=Path)
    parser.add_argument("--reports", type=Path)
    parser.add_argument("--api-log", type=Path)
    parser.add_argument("--ui-evidence", type=Path)
    parser.add_argument("--sha", default="NO_DECLARADO")
    parser.add_argument("--backup-evidence", type=Path, nargs=2)
    parser.add_argument("--out-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        data = make_report(args.matrix, args.reports, args.api_log, args.sha, args.backup_evidence, args.ui_evidence)
        args.out_dir.mkdir(parents=True, exist_ok=True)
        (args.out_dir / "indice_ac.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (args.out_dir / "indice_ac.md").write_text(markdown(data), encoding="utf-8")
    except (ValueError, OSError, ET.ParseError) as exc:
        print(f"FAIL: No se pudo generar indice AC: {exc}", file=sys.stderr)
        return 1
    print(f"EVIDENCIA_PARCIAL={data['con_evidencia_parcial']}/60; AC_NORMATIVOS_PASS=0 (sin revision humana)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
