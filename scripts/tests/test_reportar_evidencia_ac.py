"""Contract tests for the AC evidence index: never infer normative acceptance."""
import json
import tempfile
import unittest
from pathlib import Path
from xml.etree.ElementTree import Element, SubElement, ElementTree

from scripts.reportar_evidencia_ac import (API_STEP_LABELS, SOURCES, find_api_steps,
                                           junit_results, make_report, parse_matrix, R)

ROOT = Path(__file__).resolve().parents[2]


class AcceptanceEvidenceTests(unittest.TestCase):
    def test_ac_31_32_40_41_48_55_only_have_evidence_when_executed(self):
        from scripts.reportar_evidencia_ac import ui_evidence_valid
        missing = {31, 32, 40, 41, 48, 55}
        self.assertTrue(all(x in SOURCES for x in missing))
        with tempfile.TemporaryDirectory() as folder:
            d = Path(folder)
            matrix = ROOT / 'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md'
            initial = make_report(matrix, d, None, 'SHA', ui_evidence=d/'no_ui.json')
            self.assertTrue(all(not initial['criterios'][n-1]['evidencia_automatizada'] for n in missing))
            record = d / 'resultado_ui.json'
            valid = {'schema': 1, 'ac': 'AC-48', 'sha': 'SHA', 'status': 'PASS',
                     'base': 'https://localhost:8443', 'widths': [360,390,768,1440],
                     'failures': [], 'browsers': {b: {'version': '123', 'checks': ['ok']*5}
                                               for b in ('chromium','firefox')}}
            record.write_text(json.dumps(valid), encoding='utf-8')
            self.assertTrue(ui_evidence_valid(record, 'SHA'))
            self.assertFalse(ui_evidence_valid(record, 'DIFFERENT_SHA'))
            yes = make_report(matrix, d, None, 'SHA', ui_evidence=record)
            self.assertTrue(yes['criterios'][47]['evidencia_automatizada'])
            self.assertEqual(0, yes['ac_verificados_completamente'])
            valid['status'] = 'FAIL'
            record.write_text(json.dumps(valid), encoding='utf-8')
            self.assertFalse(ui_evidence_valid(record, 'SHA'))

    def test_ci_uses_caddy_tls_hostname_for_ui_and_benchmark(self):
        # gateway/Caddyfile uses a TLS site for localhost, not the raw IPv4 literal.
        caddy = (ROOT / 'gateway/Caddyfile').read_text(encoding='utf-8')
        self.assertIn('https://localhost {', caddy)
        for rel in ('frontend/scripts/validar_ui.mjs', 'scripts/medir_p95.py',
                    'scripts/reportar_evidencia_ac.py'):
            source = (ROOT / rel).read_text(encoding='utf-8')
            self.assertIn('https://localhost:8443', source, rel)
            self.assertNotIn('https://127.0.0.1:8443', source, rel)

    def test_matrix_contains_exactly_sixty_normative_ids(self):
        rows = parse_matrix(ROOT / 'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md')
        self.assertEqual([n for n, _, _ in rows], list(range(1, 61)))
        self.assertTrue(all(item[1] and item[2] for item in rows))

    def test_every_mapped_api_step_has_stable_description(self):
        smoke_source = (ROOT / 'scripts/smoke_roles.py').read_text(encoding='utf-8')
        lines = [line.strip() for line in smoke_source.splitlines()
                 if line.strip().startswith('pass_step("')]
        self.assertEqual(len(lines), 20, 'Actualizar el mapeo AC si se agrega un escenario')
        for num, expected in API_STEP_LABELS.items():
            self.assertIn(expected, lines[num - 1], f'Paso API {num} apunta a otro escenario')
        for refs in SOURCES.values():
            for kind, key in refs:
                if kind == 'api':
                    self.assertIn(key, API_STEP_LABELS)

    def test_pg_method_names_really_exist_in_source(self):
        root = ROOT / 'backend/src/test/java'
        all_methods = ''.join(path.read_text(encoding='utf-8')
                              for path in root.rglob('*.java'))
        for refs in SOURCES.values():
            for kind, selector in refs:
                if kind == 'pg':
                    self.assertIn(f'void {selector.rsplit(".", 1)[-1]}(', all_methods)

    def test_new_acceptance_references_are_not_accepted_until_their_real_pg_run(self):
        from scripts.reportar_evidencia_ac import ADDITIONAL_PG
        matrix=ROOT/'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md'
        with tempfile.TemporaryDirectory() as folder:
            d=Path(folder)
            before=make_report(matrix,d,None,'SHA_NEW')
            for ac in ADDITIONAL_PG:
                self.assertFalse(any(ev['referencia'].rsplit('.',1)[-1] in ADDITIONAL_PG[ac]
                                     for ev in before['criterios'][ac-1]['evidencia_automatizada']))
                self.assertEqual('PENDIENTE',before['criterios'][ac-1]['aceptacion_normativa'])
            method=ADDITIONAL_PG[16][0]
            xml=Element('testsuite', {'tests':'1','failures':'0','errors':'0','skipped':'0'})
            SubElement(xml,'testcase',{'classname':R[:-1],'name':method})
            ElementTree(xml).write(d/'TEST-new.xml',encoding='utf-8')
            after=make_report(matrix,d,None,'SHA_NEW')
            self.assertTrue(any(ev['referencia'].endswith(method)
                                for ev in after['criterios'][15]['evidencia_automatizada']))
            self.assertEqual('PENDIENTE',after['criterios'][15]['aceptacion_normativa'])
            xml.find('testcase').append(Element('skipped'))
            ElementTree(xml).write(d/'TEST-new.xml',encoding='utf-8')
            skipped=make_report(matrix,d,None,'SHA_NEW')
            self.assertFalse(any(ev['referencia'].endswith(method)
                                 for ev in skipped['criterios'][15]['evidencia_automatizada']))

    def test_ac02_points_to_global_five_reservation_limit_not_otc(self):
        self.assertIn(("pg", R + "globalLimitIsCountedAcrossDifferentPresentations"), SOURCES[2])
        self.assertNotIn(("pg", R + "otcLimitAndGlobalLimitDoNotMutateStockOnRejection"), SOURCES[2])
        self.assertIn(("pg", R + "otcLimitAndGlobalLimitDoNotMutateStockOnRejection"), SOURCES[3])

    def test_partial_or_missing_logs_never_count_as_pass(self):
        self.assertEqual(find_api_steps('PASS 01: Visitante: catalogo y disponibilidad\n'), {})
        self.assertEqual(find_api_steps('PASS: 20 escenarios de recorrido API; NO certifica AC-01..AC-60\n'), {})
        with tempfile.TemporaryDirectory() as folder:
            info = make_report(ROOT / 'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md',
                               Path(folder) / 'absent', None, 'FAKE')
            self.assertEqual(info['con_evidencia_parcial'], 0)
            self.assertEqual(info['ac_verificados_completamente'], 0)
            self.assertTrue(all(x['aceptacion_normativa'] == 'PENDIENTE' for x in info['criterios']))

    def test_successful_xml_counts_only_exact_methods_and_excludes_skips(self):
        with tempfile.TemporaryDirectory() as folder:
            d = Path(folder)
            xml = Element('testsuite', {'tests': '2', 'failures': '0', 'errors': '0', 'skipped': '1'})
            SubElement(xml, 'testcase', {'classname': R[:-1], 'name': 'concurrentLastStockUnitIsNeverOversold'})
            skipped = SubElement(xml, 'testcase', {'classname': R[:-1], 'name': 'failedReservationDoesNotConsumeIdempotencyKey'})
            SubElement(skipped, 'skipped')
            ElementTree(xml).write(d / 'TEST-any.xml', encoding='utf-8')
            seen = junit_results(d)
            self.assertTrue(seen[R + 'concurrentLastStockUnitIsNeverOversold'])
            self.assertFalse(seen[R + 'failedReservationDoesNotConsumeIdempotencyKey'])
            info = make_report(ROOT / 'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md', d, None, 'FAKE')
            self.assertTrue(info['criterios'][0]['evidencia_automatizada'])
            self.assertFalse(info['criterios'][42]['evidencia_automatizada'])
            self.assertEqual(info['ac_verificados_completamente'], 0)

    def test_backup_requires_two_explicit_successful_restorations(self):
        with tempfile.TemporaryDirectory() as folder:
            d = Path(folder)
            a, b = d / 'backup_ci.md', d / 'backup_ci_flyway.md'
            a.write_text('Resultado: PASS (esquema, conteos y checksums coinciden).', encoding='utf-8')
            b.write_text('Resultado: FAIL', encoding='utf-8')
            matrix = ROOT / 'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md'
            no = make_report(matrix, None, None, 'FAKE', [a, b])
            self.assertFalse(no['criterios'][58]['evidencia_automatizada'])
            b.write_text('Resultado: PASS (esquema, conteos y checksums coinciden).', encoding='utf-8')
            yes = make_report(matrix, None, None, 'FAKE', [a, b])
            self.assertTrue(yes['criterios'][58]['evidencia_automatizada'])
            self.assertEqual(yes['criterios'][58]['aceptacion_normativa'], 'PENDIENTE')

    def test_api_requires_real_final_marker_and_matching_step_label(self):
        lines = [f'PASS {i:02d}: {API_STEP_LABELS.get(i, "Otro escenario")}' for i in range(1, 21)]
        log_text = '\n'.join(lines + ['PASS: 20 escenarios de recorrido API; NO certifica AC-01..AC-60'])
        self.assertEqual(len(find_api_steps(log_text)), 20)
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / 'api.log'
            p.write_text(log_text, encoding='utf-8')
            info = make_report(ROOT / 'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md', None, p, 'FAKE')
            self.assertTrue(info['criterios'][42]['evidencia_automatizada'])
            self.assertEqual(info['ac_verificados_completamente'], 0)
            p.write_text(log_text.replace('PASS 16: Error previo', 'PASS 16: Error equivocado'), encoding='utf-8')
            info = make_report(ROOT / 'docs/MATRIZ_ACEPTACION_MVP_v1.4.3.md', None, p, 'FAKE')
            self.assertFalse(info['criterios'][42]['evidencia_automatizada'])


if __name__ == '__main__':
    unittest.main()
