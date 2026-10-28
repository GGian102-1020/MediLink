import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, get, post, setAccessToken, withQuery } from '../src/lib/api';
import { readable, remaining } from '../src/lib/format';

describe('API REST MediLink', () => {
  beforeEach(() => { setAccessToken(null); });
  afterEach(() => { vi.unstubAllGlobals(); setAccessToken(null); });
  it('no agrega parámetros ausentes a las consultas', () => {
    expect(withQuery('/api/medicamentos', { nombre: '', page: 0, estado: null, size: 20 }))
      .toBe('/api/medicamentos?page=0&size=20');
  });
  it('envía Bearer y un UUID de idempotencia cuando crea Reservas', async () => {
    setAccessToken('token-test');
    const fetchMock = vi.fn(async (_path: string, options: RequestInit) => {
      const headers = new Headers(options.headers);
      expect(headers.get('Authorization')).toBe('Bearer token-test');
      expect(headers.get('Idempotency-Key')).toBe('11111111-1111-4111-8111-111111111111');
      expect(headers.get('Content-Type')).toBe('application/json');
      expect(headers.has('X-Correlation-Id')).toBe(true);
      expect(options.method).toBe('POST');
      expect(JSON.parse(options.body as string)).toEqual({ farmaciaId: 4, presentacionId: 7, cantidad: 1 });
      return new Response(JSON.stringify({ codigoVisible: 'ABC123' }), { status: 201, headers: { 'Content-Type': 'application/json' } });
    });
    vi.stubGlobal('fetch', fetchMock);
    const result = await post<{ codigoVisible: string }>('/api/reservas', { farmaciaId: 4, presentacionId: 7, cantidad: 1 }, '11111111-1111-4111-8111-111111111111');
    expect(result.codigoVisible).toBe('ABC123');
  });
  it('admite respuestas HTTP 204 sin intentar parsear JSON', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })));
    await expect(api<void>('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
  });
  it('preserva el código de error funcional del backend', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ codigo: 'INSUFFICIENT_STOCK', mensaje: 'No hay stock suficiente' }), { status: 409 })));
    await expect(get('/api/presentaciones/3')).rejects.toMatchObject({ status: 409, codigo: 'INSUFFICIENT_STOCK' } satisfies Partial<ApiError>);
  });
  it('rechaza rutas fuera de /api', async () => {
    await expect(get('https://sitio-externo.test/api/reservas')).rejects.toThrow('Ruta API inválida');
  });
});
describe('formato de datos', () => {
  it('nunca muestra un tiempo negativo', () => expect(remaining(-10)).toBe('0 min'));
  it('presenta los estados normativos legiblemente', () => expect(readable('BAJO_RECETA')).toBe('Bajo receta'));
});
