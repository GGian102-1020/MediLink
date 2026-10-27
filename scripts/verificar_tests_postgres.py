"""CI gate: existing and new PostgreSQL suites must execute, with no omissions."""
from pathlib import Path
from xml.etree import ElementTree
import sys

reports = Path(sys.argv[1] if len(sys.argv) > 1 else 'backend/target/surefire-reports')
expected = {
    'ar.edu.uade.medlink.presentacion.CatalogoPostgresTest': 2,
    'ar.edu.uade.medlink.stock.FarmaciaStockPostgresTest': 18,
    'ar.edu.uade.medlink.auditoria.AuditoriaPostgresTest': 15,
    'ar.edu.uade.medlink.prescripcion.PrescripcionPostgresTest': 2,
    'ar.edu.uade.medlink.prescripcion.PrescripcionConcurrencyTest': 2,
    'ar.edu.uade.medlink.suspension.SuspensionPostgresTest': 2,
    'ar.edu.uade.medlink.reserva.ReservasPostgresTest': 21,
}
for classname, minimum in expected.items():
    report = reports / f'TEST-{classname}.xml'
    if not report.is_file():
        raise SystemExit(f'MISSING REPORT: {report}')
    suite = ElementTree.parse(report).getroot()
    values = {key: int(suite.get(key, '0')) for key in ('tests', 'skipped', 'failures', 'errors')}
    print(f'{classname}: {values}')
    if values['tests'] < minimum or any(values[key] for key in ('skipped', 'failures', 'errors')):
        raise SystemExit(f'POSTGRESQL INTEGRATION FAILED OR SKIPPED: {classname}')

all_reports = list(reports.glob('TEST-*.xml'))
totals = {key: 0 for key in ('tests', 'skipped', 'failures', 'errors')}
for report in all_reports:
    suite = ElementTree.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, '0'))
if any(totals[key] for key in ('skipped', 'failures', 'errors')):
    raise SystemExit(f'FULL SUITE FAILED OR SKIPPED: {totals}')
print(f'PASS: {len(all_reports)} suites; {totals}')
