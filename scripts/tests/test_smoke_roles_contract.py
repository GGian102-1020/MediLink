"""Tests unitarios del runner E2E; no reemplazan la ejecucion HTTPS real."""
import io
import json
import unittest
from unittest.mock import call, patch

from scripts.smoke_roles import (Client, Reply, SmokeFailure, check_base_url, find_stock,
                                 verify_foreign_operator_withdrawal_scope)


class SmokeRoleContractTests(unittest.TestCase):
    def test_solo_https_localhost_puerto_explicito(self):
        for uri in (
            "http://localhost:8443", "https://example.com:8443",
            "https://127.0.0.2:8443", "https://localhost",
            "https://localhost:8443/api", "https://localhost:8443?x=1",
            "https://usuario:clave@localhost:8443",
        ):
            with self.subTest(uri=uri), self.assertRaises(ValueError):
                check_base_url(uri)
        self.assertEqual(check_base_url("https://localhost:8443/"), "https://localhost:8443")
        self.assertEqual(check_base_url("https://127.0.0.1:8443"), "https://127.0.0.1:8443")

    def test_rutas_fuera_de_api_se_rechazan(self):
        with self.assertRaises(ValueError):
            Client("https://localhost:8443").request("GET", "/admin")

    def test_stock_unico_o_error(self):
        self.assertEqual(find_stock({"content": [{"presentacionId": 7, "cantidadDisponible": 0}]}, 7), 0)
        for rows in ([], [{"presentacionId": 7, "cantidadDisponible": 2},
                            {"presentacionId": 7, "cantidadDisponible": 3}],
                     [{"presentacionId": 7, "cantidadDisponible": "5"}]):
            with self.subTest(rows=rows), self.assertRaises(SmokeFailure):
                find_stock({"content": rows}, 7)

    def test_retiro_ajeno_distingue_acceso_prohibido_de_recurso_fuera_de_farmacia(self):
        client = Client("https://localhost:8443")
        with patch.object(client, "request", side_effect=[
            Reply(403, {"codigo": "FORBIDDEN"}),
            Reply(404, {"codigo": "RESOURCE_NOT_FOUND"}),
        ]) as request:
            verify_foreign_operator_withdrawal_scope(client, "ABCD1234EFGH", 1, 2, "token-operador-2")
        self.assertEqual(request.call_args_list, [
            call("POST", "/api/farmacias/1/reservas/ABCD1234EFGH/retiro", "token-operador-2", None, None),
            call("POST", "/api/farmacias/2/reservas/ABCD1234EFGH/retiro", "token-operador-2", None, None),
        ])
        # Si el test confunde 403 y 404, debe fallar y no ocultar un problema de scope.
        with patch.object(client, "request", return_value=Reply(404, {"codigo": "RESOURCE_NOT_FOUND"})):
            with self.assertRaises(SmokeFailure):
                verify_foreign_operator_withdrawal_scope(client, "ABCD1234EFGH", 1, 2, "token-operador-2")

    def test_retiro_ajeno_no_admite_misma_farmacia_para_los_dos_ambitos(self):
        client = Client("https://localhost:8443")
        with patch.object(client, "expect") as expect:
            with self.assertRaises(ValueError):
                verify_foreign_operator_withdrawal_scope(client, "ABCD1234EFGH", 1, 1, "token-operador-1")
            expect.assert_not_called()

    def test_error_funcional_y_codigo_se_verifican(self):
        client = Client("https://localhost:8443")
        with patch.object(client, "request", return_value=Reply(403, {"codigo": "FORBIDDEN"})):
            client.expect("GET", "/api/admin/farmacias", 403, error="FORBIDDEN")
            with self.assertRaises(SmokeFailure):
                client.expect("GET", "/api/admin/farmacias", 403, error="RESOURCE_NOT_FOUND")
            with self.assertRaises(SmokeFailure):
                client.expect("GET", "/api/admin/farmacias", 200)


if __name__ == "__main__":
    unittest.main()
