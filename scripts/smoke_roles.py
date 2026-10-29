#!/usr/bin/env python3
"""API end-to-end smoke on an ephemeral, explicitly seeded MedLink demo.

Only localhost HTTPS is supported. The test creates, cancels and completes reservations.
It is *not* a full AC-01..AC-60 certification or a production load test.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, BrokenBarrierError
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass


class SmokeFailure(AssertionError):
    pass


@dataclass
class Reply:
    status: int
    body: object


def check_base_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname not in {"localhost", "127.0.0.1"}:
        raise ValueError("Solo HTTPS localhost/127.0.0.1; nunca ejecutar sobre un servidor publico")
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ValueError("La URL base debe contener solamente esquema, host y puerto")
    if not parsed.port:
        raise ValueError("Indicá un puerto explicito para evitar ejecutar en un servicio no previsto")
    return url.rstrip("/")


class Client:
    def __init__(self, base: str, timeout: float = 15):
        self.base = check_base_url(base)
        self.timeout = timeout
        # Trust bypass is ONLY for Caddy's self-signed localhost demo certificate.
        self.context = ssl._create_unverified_context()

    def request(self, method: str, route: str, token: str | None = None, data: object = None,
                key: str | None = None) -> Reply:
        if not route.startswith("/api/"):
            raise ValueError("Solo rutas API")
        headers = {"Accept": "application/json", "X-Correlation-Id": str(uuid.uuid4())}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if key:
            headers["Idempotency-Key"] = key
        payload = None
        if data is not None:
            payload = json.dumps(data, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + route, data=payload, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self.context) as response:
                code, raw = response.status, response.read()
        except urllib.error.HTTPError as exc:
            code, raw = exc.code, exc.read()
        except urllib.error.URLError as exc:
            raise SmokeFailure(f"No hay respuesta de {route}: {exc.reason}") from exc
        try:
            body = json.loads(raw.decode("utf-8")) if raw else None
        except (ValueError, UnicodeDecodeError) as exc:
            raise SmokeFailure(f"Respuesta no JSON en {route}, HTTP {code}") from exc
        return Reply(code, body)

    def expect(self, method: str, route: str, status: int, token: str | None = None,
               data: object = None, key: str | None = None, error: str | None = None) -> object:
        reply = self.request(method, route, token, data, key)
        if reply.status != status:
            # Do not print tokens, passwords, request bodies or PII to logs.
            code = reply.body.get("codigo") if isinstance(reply.body, dict) else None
            raise SmokeFailure(f"{method} {route}: HTTP {reply.status} ({code}), esperado {status}")
        if error and (not isinstance(reply.body, dict) or reply.body.get("codigo") != error):
            raise SmokeFailure(f"{method} {route}: codigo esperado {error}, recibido "
                               f"{reply.body.get('codigo') if isinstance(reply.body, dict) else 'sin JSON'}")
        return reply.body


def find_stock(page: object, presentation: int) -> int:
    if not isinstance(page, dict) or not isinstance(page.get("content"), list):
        raise SmokeFailure("Stock no respondio PageDTO")
    matches = [row for row in page["content"] if row.get("presentacionId") == presentation]
    if len(matches) != 1 or not isinstance(matches[0].get("cantidadDisponible"), int):
        raise SmokeFailure(f"Saldo inexistente o duplicado para presentacion {presentation}")
    return matches[0]["cantidadDisponible"]


def verify_foreign_operator_withdrawal_scope(api: Client, reservation_code: str,
                                           reservation_pharmacy_id: int,
                                           operator_pharmacy_id: int,
                                           foreign_operator_token: str) -> None:
    """Distinguish forbidden pharmacy access (403) from missing resource in scope (404).

    AC-09: a pharmacy 2 operator cannot invoke pharmacy 1 endpoints (403).
    A reservation from pharmacy 1 is not a resource of pharmacy 2 (404),
    even if its non-secret code is known. Both calls must leave it unchanged.
    """
    if reservation_pharmacy_id == operator_pharmacy_id:
        raise ValueError("El operador de ensayo debe pertenecer a otra farmacia")
    api.expect("POST", f"/api/farmacias/{reservation_pharmacy_id}/reservas/{reservation_code}/retiro",
               403, foreign_operator_token, error="FORBIDDEN")
    api.expect("POST", f"/api/farmacias/{operator_pharmacy_id}/reservas/{reservation_code}/retiro",
               404, foreign_operator_token, error="RESOURCE_NOT_FOUND")


def run(base: str, password: str) -> None:
    api = Client(base)
    count = 0

    def pass_step(name: str) -> None:
        nonlocal count
        count += 1
        print(f"PASS {count:02d}: {name}", flush=True)

    public = api.expect("GET", "/api/medicamentos?nombre=Ibuprofeno&size=100", 200)
    if not isinstance(public, dict) or not any(m.get("id") == 1 for m in public.get("content", [])):
        raise SmokeFailure("Ibuprofeno no aparece en el catalogo publico")
    api.expect("GET", "/api/presentaciones/1", 200)
    available = api.expect("GET", "/api/presentaciones/1/disponibilidad", 200)
    if not isinstance(available, list) or not any(r.get("farmaciaId") == 1 for r in available):
        raise SmokeFailure("No aparece Farmacia 1 con Stock reservable")
    if any(r.get("farmaciaId") == 5 for r in available):
        raise SmokeFailure("Farmacia INACTIVA visible en disponibilidad")
    pass_step("Visitante: catalogo y disponibilidad; farmacia inactiva excluida (parcial AC-21/48)")

    api.expect("GET", "/api/me", 401)
    pass_step("Visitante: ruta autenticada protegida")

    def sign_in(email: str, role: str) -> str:
        data = api.expect("POST", "/api/auth/login", 200, data={"correo": email, "contrasena": password})
        if not isinstance(data, dict) or data.get("perfil", {}).get("rol") != role or not data.get("accessToken"):
            raise SmokeFailure(f"No se pudo iniciar sesion como {role}")
        return data["accessToken"]

    admin = sign_in("admin@medlink.test", "ADMINISTRADOR")
    op1 = sign_in("carla.mendez@farmacia-central.test", "OPERADOR")
    op2 = sign_in("laura.benitez@fsanmartin.test", "OPERADOR")
    user = sign_in("juan.perez@mail.test", "USUARIO")
    maria = sign_in("maria.gonzalez@mail.test", "USUARIO")
    if api.expect("GET", "/api/me", 200, user)["perfil"]["rol"] != "USUARIO":
        raise SmokeFailure("/me devuelve rol incorrecto")
    pass_step("Autenticacion de los tres roles y /me")

    admin_list = api.expect("GET", "/api/admin/farmacias?size=100", 200, admin)
    if not isinstance(admin_list, dict) or not admin_list.get("content"):
        raise SmokeFailure("Administracion no devuelve Farmacias")
    api.expect("GET", "/api/admin/farmacias", 403, op1, error="FORBIDDEN")
    api.expect("GET", "/api/admin/farmacias", 403, user, error="FORBIDDEN")
    pass_step("Permisos administrativos backend")

    history = api.expect("GET", "/api/farmacias/1/reservas?size=100", 200, op1)
    if not isinstance(history, dict) or not history.get("content"):
        raise SmokeFailure("Operador no puede consultar su Farmacia")
    api.expect("GET", "/api/farmacias/2/reservas", 403, op1, error="FORBIDDEN")
    api.expect("GET", "/api/farmacias/1/reservas", 403, user, error="FORBIDDEN")
    pass_step("Operador: scope de farmacia y segregacion de roles (parcial AC-09)")

    other = api.expect("GET", "/api/farmacias/1/reservas/7K2Q9MA4BC01", 200, op1)
    if any(k in other for k in ("correo", "telefono", "prescripcionCodigo")):
        raise SmokeFailure("Datos privados expuestos al operador")
    api.expect("GET", "/api/reservas/mias/5QN1ZRK7MN04", 404, user, error="RESOURCE_NOT_FOUND")
    pass_step("Privacidad operador y reserva ajena (parcial AC-47)")

    # Rechazos administrativos normativos: pruebas NO destructivas.
    api.expect("POST", "/api/admin/cuentas/4/deshabilitacion", 409, admin,
               {"motivo": "Ensayo ultimo operador en CI"},
               error="LAST_OPERATIONAL_OPERATOR")
    api.expect("POST", "/api/admin/farmacias/1/desactivacion", 409, admin,
               {"motivo": "Ensayo reserva activa en CI"},
               error="PHARMACY_HAS_ACTIVE_RESERVATIONS")
    pass_step("Ultimo operador y farmacia con reserva activa (AC-10/11)")

    # Ausencia de saldo y catalogo inactivo no deben habilitar nuevas reservas.
    api.expect("POST", "/api/reservas", 409, user,
               {"farmaciaId": 1, "presentacionId": 3, "cantidad": 1},
               str(uuid.uuid4()), error="INSUFFICIENT_STOCK")
    api.expect("GET", "/api/presentaciones/17", 404, error="RESOURCE_NOT_FOUND")
    api.expect("POST", "/api/reservas", 409, user,
               {"farmaciaId": 1, "presentacionId": 17, "cantidad": 1},
               str(uuid.uuid4()), error="MEDICATION_INACTIVE")
    pass_step("Stock ausente y catalogo inactivo (parcial AC-20/21)")

    # Juan tiene dos unidades de la presentacion 4 reservadas en el seed.
    api.expect("POST", "/api/reservas", 409, user,
               {"farmaciaId": 2, "presentacionId": 4, "cantidad": 2},
               str(uuid.uuid4()), error="MAX_OTC_UNITS_EXCEEDED")
    pass_step("Limite OTC por usuario y presentacion (AC-03)")

    def stock(presentation: int) -> int:
        return find_stock(api.expect("GET", "/api/farmacias/1/stock?size=100", 200, op1), presentation)

    pre = stock(1)
    payload = {"farmaciaId": 1, "presentacionId": 1, "cantidad": 1}
    key = str(uuid.uuid4())
    reservation = api.expect("POST", "/api/reservas", 201, user, payload, key)
    if reservation["estado"] != "ACTIVA" or reservation["cantidad"] != 1:
        raise SmokeFailure("Reserva nueva sin estado ACTIVA/cantidad 1")
    if stock(1) != pre - 1:
        raise SmokeFailure("Creacion de reserva no descuento stock exactamente una vez")
    replay = api.expect("POST", "/api/reservas", 201, user, payload, key)
    if replay != reservation or stock(1) != pre - 1:
        raise SmokeFailure("Replay idempotente cambio la respuesta o el saldo")
    api.expect("POST", "/api/reservas", 409, user, {**payload, "cantidad": 2}, key,
               error="IDEMPOTENCY_KEY_REUSED")
    pass_step("Reserva real y replay idempotente (parcial AC-17/18/33)")

    code = reservation["codigoVisible"]
    dto = api.expect("GET", f"/api/reservas/mias/{code}", 200, user)
    if dto["codigoVisible"] != code or dto["segundosRestantes"] <= 0:
        raise SmokeFailure("Reserva propia sin codigo/tiempo restante")
    api.expect("POST", f"/api/farmacias/1/reservas/{code}/retiro", 403, admin, error="FORBIDDEN")
    verify_foreign_operator_withdrawal_scope(api, code, 1, 2, op2)
    completed = api.expect("POST", f"/api/farmacias/1/reservas/{code}/retiro", 200, op1)
    if completed["estado"] != "COMPLETADA" or stock(1) != pre - 1:
        raise SmokeFailure("Retiro no completo o altero stock")
    api.expect("POST", f"/api/farmacias/1/reservas/{code}/retiro", 409, op1,
               error="INVALID_STATE_TRANSITION")
    pass_step("Retiro, ambito y stock sin segundo descuento (parcial AC-08/28/52)")

    pre2 = stock(2)
    second = api.expect("POST", "/api/reservas", 201, user,
                        {"farmaciaId": 1, "presentacionId": 2, "cantidad": 1}, str(uuid.uuid4()))
    if stock(2) != pre2 - 1:
        raise SmokeFailure("Segunda reserva no descuento Stock")
    cancelled = api.expect("POST", f"/api/reservas/{second['codigoVisible']}/cancelacion", 200, user)
    if cancelled["estado"] != "CANCELADA" or stock(2) != pre2:
        raise SmokeFailure("Cancelacion propia no restituyo stock")
    if cancelled.get("origenCancelacion") != "USUARIO" or cancelled.get("causaCancelacion") != "CANCELACION_USUARIO" or not cancelled.get("fechaHoraCancelacion"):
        raise SmokeFailure("Cancelacion sin metadatos normativos")
    movements = api.expect("GET", "/api/farmacias/1/stock/movimientos?presentacionId=2&size=100", 200, op1)
    if not isinstance(movements, dict) or not any(m.get("tipo") == "CANCELACION_RESTITUCION" and
                                                 m.get("reservaCodigo") == second["codigoVisible"]
                                                 for m in movements.get("content", [])):
        raise SmokeFailure("MovimientoStock de restitucion no trazable")
    pass_step("Cancelacion y restitucion OTC (parcial AC-30/49/58)")

    pre_rx = stock(11)
    rx = api.expect("POST", "/api/reservas", 201, user,
                    {"farmaciaId": 1, "presentacionId": 11, "cantidad": 1,
                     "codigoPrescripcion": "RX-2026-000008"}, str(uuid.uuid4()))
    if rx["condicionDispensa"] != "BAJO_RECETA" or stock(11) != pre_rx - 1:
        raise SmokeFailure("Reserva Rx invalida")
    rx_cancelled = api.expect("POST", f"/api/reservas/{rx['codigoVisible']}/cancelacion", 200, user)
    if rx_cancelled["estado"] != "CANCELADA" or stock(11) != pre_rx:
        raise SmokeFailure("Cancelacion Rx no restituyo stock")
    pass_step("Reserva bajo receta y liberacion de stock (parcial AC-06/29)")

    # AC-01/34: dos Usuarios intentan simultaneamente consumir la ultima unidad.
    # Farmacia 1 + Presentacion 3 no tiene saldo inicial en el seed.
    adjusted = api.expect("PUT", "/api/farmacias/1/stock/3", 200, admin,
                          {"cantidadDisponible": 1, "motivo": "Stock exclusivo smoke CI"})
    if adjusted["cantidadDisponible"] != 1 or stock(3) != 1:
        raise SmokeFailure("Ajuste absoluto no creo Stock ausente")
    concurrent = {"farmaciaId": 1, "presentacionId": 3, "cantidad": 1}
    gate = Barrier(3)

    def compete(token: str) -> Reply:
        gate.wait(timeout=10)
        return api.request("POST", "/api/reservas", token, concurrent, str(uuid.uuid4()))

    with ThreadPoolExecutor(max_workers=2) as executor:
        tasks = [executor.submit(compete, token) for token in (user, maria)]
        gate.wait(timeout=10)
        results = [job.result(timeout=20) for job in tasks]
    status = sorted(reply.status for reply in results)
    if status != [201, 409] or any(reply.body.get("codigo") != "INSUFFICIENT_STOCK"
                                  for reply in results if reply.status == 409):
        raise SmokeFailure(f"Ultima unidad concurrente: HTTP esperados [201,409], recibidos {status}")
    if stock(3) != 0:
        raise SmokeFailure("Stock inconsistente tras carrera por ultima unidad")
    winner_index = next(i for i, reply in enumerate(results) if reply.status == 201)
    winner = (user, maria)[winner_index]
    win_code = results[winner_index].body["codigoVisible"]
    api.expect("POST", f"/api/reservas/{win_code}/cancelacion", 200, winner)
    if stock(3) != 1:
        raise SmokeFailure("La ultima unidad no se restituyo tras cancelar ganador")
    pass_step("Dos usuarios, ultima unidad real PostgreSQL y saldo final (AC-01/34)")

    # AC-42: POST simultaneos sobre la misma Cuenta+Idempotency-Key.
    same_key = str(uuid.uuid4())
    before_same = stock(1)
    gate = Barrier(3)

    def same_request() -> Reply:
        gate.wait(timeout=10)
        return api.request("POST", "/api/reservas", user, payload, same_key)

    with ThreadPoolExecutor(max_workers=2) as executor:
        tasks = [executor.submit(same_request) for _ in range(2)]
        gate.wait(timeout=10)
        two_replies = [job.result(timeout=20) for job in tasks]
    if [reply.status for reply in two_replies] != [201, 201]:
        raise SmokeFailure("Dos POST con misma clave no devolvieron ambos HTTP 201")
    if two_replies[0].body != two_replies[1].body or stock(1) != before_same - 1:
        raise SmokeFailure("Dos POST con misma clave duplicaron reserva o descuento")
    api.expect("POST", f"/api/reservas/{two_replies[0].body['codigoVisible']}/cancelacion", 200, user)
    if stock(1) != before_same:
        raise SmokeFailure("Stock no recuperado tras idempotencia concurrente")
    pass_step("Dos POST simultaneos misma clave, una sola reserva (AC-42)")

    api.expect("POST", "/api/auth/logout", 204, user)
    api.expect("GET", "/api/me", 401, user, error="INVALID_TOKEN")
    pass_step("Logout invalida token (parcial AC-24)")
    print(f"PASS: {count} escenarios de recorrido API; NO certifica AC-01..AC-60", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="https://localhost:8443")
    parser.add_argument("--demo-ack", required=True, help="Escribir SEED_DEMO_EFIMERO para autorizar mutaciones")
    args = parser.parse_args()
    if args.demo_ack != "SEED_DEMO_EFIMERO":
        parser.error("No se autorizaron mutaciones sobre el entorno efimero")
    try:
        check_base_url(args.base_url)
        # Never allow this script to run against an environment without the explicit
        # ephemeral-CI marker, even if a seed password happens to work.
        if os.getenv("MEDLINK_SMOKE_EPHEMERAL") != "1":
            raise ValueError("Falta MEDLINK_SMOKE_EPHEMERAL=1: solo ejecutar en CI efimero")
        run(args.base_url, os.environ.get("MEDLINK_DEMO_PASSWORD", "Password123!"))
        return 0
    except (ValueError, SmokeFailure, AssertionError, KeyError, TypeError, BrokenBarrierError, TimeoutError, OSError) as exc:
        print(f"FAIL: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
