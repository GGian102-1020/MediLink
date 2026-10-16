#!/usr/bin/env python3
"""Real HTTP acceptance smoke checks. Requires running PostgreSQL/API.

Uses only Python standard library. `--insecure` is ONLY for Caddy's localhost CA.
Creates a disposable user and operator in the dev seed database.
"""
import argparse
import json
import ssl
import sys
import urllib.error
import urllib.request
import uuid


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--url', default='https://localhost:8443')
    p.add_argument('--insecure', action='store_true', help='Only for self-signed localhost TLS')
    args = p.parse_args()
    if args.insecure and not args.url.startswith('https://localhost:'):
        p.error('--insecure only allowed with https://localhost')
    context = ssl._create_unverified_context() if args.insecure else ssl.create_default_context()
    base = args.url.rstrip('/')
    n = 0

    def call(method, path, payload=None, token=None):
        data = json.dumps(payload).encode('utf-8') if payload is not None else None
        headers = {'Content-Type': 'application/json', 'X-Correlation-Id': str(uuid.uuid4())}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, context=context, timeout=15) as res:
                raw = res.read()
                if not raw:
                    return res.status, None
                try:
                    return res.status, json.loads(raw)
                except json.JSONDecodeError:
                    return res.status, raw.decode('utf-8')
        except urllib.error.HTTPError as ex:
            raw = ex.read()
            return ex.code, json.loads(raw) if raw else None

    def expect(method, path, status, payload=None, token=None, code=None):
        nonlocal n
        actual, result = call(method, path, payload, token)
        if actual != status or (code is not None and (not result or result.get('codigo') != code)):
            raise AssertionError(f'{method} {path}: wanted {status}/{code}, got {actual}: {result}')
        if status >= 400:
            assert result['timestamp'] and result['correlationId']
        n += 1
        print(f'  OK {n:02d}: {method} {path} -> {status}' + (f' {code}' if code else ''))
        return result

    suffix = uuid.uuid4().hex[:12]
    email = f'pruebas+{suffix}@medlink.test'
    operator_email = f'operador+{suffix}@medlink.test'
    passwd = 'TestPassword123!'
    admin_email = 'admin@medlink.test'
    admin_pass = 'Password123!'
    expect('GET','/api/health',200)
    expect('GET','/api/me',401,code='INVALID_TOKEN')
    user = expect('POST','/api/auth/register',201,{
        'nombre':'Ana', 'apellido':'Prueba','correo':' '+email+' ', 'contrasena':passwd})
    assert user['rol'] == 'USUARIO' and user['estadoCuenta'] == 'ACTIVA'
    expect('POST','/api/auth/register',409,{
        'nombre':'Ana','apellido':'Prueba','correo':email.upper(),'contrasena':passwd},code='EMAIL_ALREADY_EXISTS')
    expect('POST','/api/auth/login',401,{'correo':email,'contrasena':'mal'},code='INVALID_CREDENTIALS')
    login = expect('POST','/api/auth/login',200,{'correo':email.upper(),'contrasena':passwd})
    assert login['tokenType'] == 'Bearer' and login['expiresInSeconds'] == 1800
    token = login['accessToken']
    profile = expect('GET','/api/me',200,token=token)
    assert profile['perfil']['rol'] == 'USUARIO'
    expect('GET','/api/admin/usuarios',403,token=token,code='FORBIDDEN')
    expect('POST','/api/auth/change-password',401,{
        'contrasenaActual':'incorrecta', 'nuevaContrasena':'Password456!'},token,code='CURRENT_PASSWORD_INVALID')
    expect('POST','/api/auth/change-password',204,{
        'contrasenaActual':passwd,'nuevaContrasena':'Password456!'},token)
    expect('GET','/api/me',401,token=token,code='INVALID_TOKEN')
    token = expect('POST','/api/auth/login',200,{
        'correo':email,'contrasena':'Password456!'})['accessToken']
    expect('POST','/api/auth/logout',204,token=token)
    expect('GET','/api/me',401,token=token,code='INVALID_TOKEN')
    admin = expect('POST','/api/auth/login',200,{'correo':admin_email,'contrasena':admin_pass})['accessToken']
    page = expect('GET','/api/admin/usuarios?size=3',200,token=admin)
    assert len(page['content']) <= 3 and page['size'] == 3
    expect('GET','/api/admin/usuarios?sort=correo,asc%3BDROP%20TABLE',400,
           token=admin,code='VALIDATION_ERROR')
    # Seed: Cuenta 1 = ADMINISTRADOR; Cuenta 4 = only active operator in Farmacia 2.
    expect('POST','/api/admin/cuentas/1/deshabilitacion',403,{'motivo':'prueba'},admin,code='FORBIDDEN')
    expect('POST','/api/admin/cuentas/4/deshabilitacion',409,{'motivo':'prueba'},admin,
           code='LAST_OPERATIONAL_OPERATOR')
    operator = expect('POST','/api/admin/operadores',201,{
        'nombre':'Operador','apellido':'Prueba','correo':operator_email,
        'contrasenaInicial':passwd,'farmaciaId':1},admin)
    assert operator['farmaciaId'] == 1 and operator['estadoCuenta'] == 'ACTIVA'
    operator_login = expect('POST','/api/auth/login',200,{
        'correo':operator_email,'contrasena':passwd})['accessToken']
    expect('GET','/api/admin/operadores',403,token=operator_login,code='FORBIDDEN')
    operator_id = operator['cuentaId']
    expect('POST',f'/api/admin/cuentas/{operator_id}/deshabilitacion',200,
           {'motivo':'prueba de autenticación'},admin)
    expect('GET','/api/me',403,token=operator_login,code='ACCOUNT_DISABLED')
    expect('POST','/api/auth/login',403,{'correo':operator_email,'contrasena':passwd},code='ACCOUNT_DISABLED')
    expect('POST',f'/api/admin/cuentas/{operator_id}/reactivacion',200,
           {'motivo':'fin de prueba'},admin)
    expect('GET','/api/me',401,token=operator_login,code='INVALID_TOKEN')
    expect('POST','/api/auth/login',200,{'correo':operator_email,'contrasena':passwd})
    print(f'\nPASS: {n} HTTP checks (user, roles, JWT version/revocation, operator lifecycle).')
    print('Nota: se conservaron registros de prueba, sin borrado físico (RN-43).')


if __name__ == '__main__':
    try:
        main()
    except Exception as ex:
        print(f'FAIL: {ex}',file=sys.stderr)
        sys.exit(1)
