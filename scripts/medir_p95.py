#!/usr/bin/env python3
"""RNF-11 measurement on disposable seed only; never a production benchmark.

Public/API reads: 4 concurrent workers. Transactional samples: sequential
registrations, logins, reservations and cancellations; measured separately.
The resulting SLO is a REFERENCE benchmark, not normative AC acceptance.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
from statistics import mean
import sys
from time import perf_counter
import uuid

from scripts.smoke_roles import Client, SmokeFailure, check_base_url


def p95(samples: list[float]) -> float:
    if not samples:
        raise ValueError('No hay muestras suficientes')
    ordered = sorted(samples)
    return round(ordered[(95 * len(ordered) + 99) // 100 - 1], 4)


def hardware() -> dict:
    memory_kib = None
    if Path('/proc/meminfo').exists():
        for line in Path('/proc/meminfo').read_text().splitlines():
            if line.startswith('MemTotal:'):
                memory_kib = int(line.split()[1])
                break
    return {'machine': platform.machine(), 'platform': platform.platform(),
            'python': platform.python_version(), 'cpu_logical': os.cpu_count(),
            'memory_total_kib': memory_kib}


def run(n: int, workers: int) -> dict:
    if n < 20 or workers < 1 or workers > 16:
        raise ValueError('Usar >=20 iteraciones y entre 1 y 16 workers')
    client = Client('https://127.0.0.1:8443')
    pwd = os.environ['MEDLINK_DEMO_PASSWORD']
    admin_user = {'correo': 'juan.perez@mail.test', 'contrasena': pwd}
    user_login = client.expect('POST', '/api/auth/login', 200, data=admin_user)
    token = user_login['accessToken']
    catalogue = client.expect('GET', '/api/medicamentos?size=1', 200)
    available = client.expect('GET', '/api/presentaciones/1/disponibilidad', 200)
    groups: dict[str, list[float]] = {key: [] for key in
        ('catalogo', 'presentacion', 'disponibilidad', 'reservas_mias', 'registro', 'login', 'reserva', 'cancelacion')}
    routes = ['/api/medicamentos?nombre=Ibu&size=10', '/api/presentaciones/1',
              '/api/presentaciones/1/disponibilidad', '/api/reservas/mias?size=10']
    names = ('catalogo', 'presentacion', 'disponibilidad', 'reservas_mias')

    def read_sample(index: int) -> tuple[str, float]:
        name, route = names[index % 4], routes[index % 4]
        started = perf_counter()
        client.expect('GET', route, 200, token if name == 'reservas_mias' else None)
        return name, perf_counter() - started

    with ThreadPoolExecutor(max_workers=workers) as executor:
        for key, duration in executor.map(read_sample, range(4 * n)):
            groups[key].append(duration)

    for i in range(n):
        unique = uuid.uuid4().hex
        started = perf_counter()
        client.expect('POST', '/api/auth/register', 201, data={
            'nombre': 'Prueba', 'apellido': 'Rendimiento',
            'correo': f'perf-{unique}@example.test', 'contrasena': pwd})
        groups['registro'].append(perf_counter() - started)

        started = perf_counter()
        client.expect('POST', '/api/auth/login', 200, data=admin_user)
        groups['login'].append(perf_counter() - started)

        started = perf_counter()
        reserved = client.expect('POST', '/api/reservas', 201, token,
                                 {'farmaciaId': 1, 'presentacionId': 1, 'cantidad': 1}, str(uuid.uuid4()))
        groups['reserva'].append(perf_counter() - started)
        started = perf_counter()
        client.expect('POST', f"/api/reservas/{reserved['codigoVisible']}/cancelacion", 200, token)
        groups['cancelacion'].append(perf_counter() - started)

    results = {name: {'samples': len(values), 'p95_seconds': p95(values),
                      'mean_seconds': round(mean(values), 4), 'max_seconds': round(max(values), 4)}
               for name, values in groups.items()}
    reads = [value for name in names for value in groups[name]]
    transactions = [value for name in ('registro','login','reserva','cancelacion') for value in groups[name]]
    result = {'sha': os.environ.get('GITHUB_SHA', 'LOCAL'), 'dateUtc': datetime.now(timezone.utc).isoformat(),
              'environment': hardware(), 'dataset': {'medicamentos_total': catalogue['totalElements'],
                         'farmacias_disponibles_presentacion_1': len(available), 'seed_demo_explicit': True},
              'load': {'read_workers': workers, 'transaction_workers': 1,
                       'iterations_per_operation': n, 'https_loopback': True,
                       'warmup': 'login and public catalog/availability before samples'},
              'operations': results, 'p95_reads_seconds': p95(reads),
              'p95_transactions_seconds': p95(transactions),
              'reference_slo': {'reads_seconds': 1.0, 'transactions_seconds': 2.0},
              'result': 'PASS' if p95(reads) <= 1 and p95(transactions) <= 2 else 'FAIL',
              'scope': 'Referencia CI descartable; RNF-11 necesita aceptacion con dataset/hardware/carga convenidos.'}
    return result


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--demo-ack', required=True)
    parser.add_argument('--iterations', type=int, default=25)
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--out', type=Path, default=Path('evidencias/perf_ci/resultado_p95.json'))
    args = parser.parse_args(argv)
    try:
        check_base_url('https://127.0.0.1:8443')
        if args.demo_ack != 'SEED_DEMO_EFIMERO' or os.getenv('MEDLINK_PERF_EPHEMERAL') != '1' or \
                os.getenv('MEDLINK_DEMO_PASSWORD') != 'Password123!':
            raise ValueError('Solo se ejecuta en la BD efimera con seed demo de GitHub Actions')
        data = run(args.iterations, args.workers)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        print(f"p95 lecturas {data['p95_reads_seconds']:.4f}s (limite 1s); "
              f"transaccionales {data['p95_transactions_seconds']:.4f}s (limite 2s); "
              f"resultado referencia {data['result']}")
        return 0 if data['result'] == 'PASS' else 1
    except (ValueError, KeyError, OSError, SmokeFailure, AssertionError, TypeError) as exc:
        # Preserve a non-secret failure record for the always-uploaded CI artifact.
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps({
            'sha': os.environ.get('GITHUB_SHA', 'LOCAL'), 'result': 'FAIL',
            'dateUtc': datetime.now(timezone.utc).isoformat(),
            'failure_type': type(exc).__name__, 'message': str(exc)[:300],
            'scope': 'No hay resultado de rendimiento valido; no declarar RNF-11 PASS.'
        }, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        print(f'FAIL benchmark: {type(exc).__name__}: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
