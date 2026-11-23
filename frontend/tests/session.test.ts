// @vitest-environment jsdom
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { api, getAccessToken, setAccessToken } from '../src/lib/api';

describe('sesión del operador', () => {
  beforeEach(() => { setAccessToken('current'); });
  afterEach(() => { vi.unstubAllGlobals(); setAccessToken(null); });
  it('conserva la sesión ante fallos de red', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(api('/api/me')).rejects.toMatchObject({ codigo: 'NETWORK_ERROR' });
    expect(getAccessToken()).toBe('current');
  });
  it('una contraseña actual incorrecta no equivale a un JWT inválido', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ codigo: 'CURRENT_PASSWORD_INVALID' }), { status: 401 })));
    await expect(api('/api/auth/change-password')).rejects.toMatchObject({ codigo: 'CURRENT_PASSWORD_INVALID' });
    expect(getAccessToken()).toBe('current');
  });
  it('revoca la sesión ante un JWT inválido', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ codigo: 'INVALID_TOKEN' }), { status: 401 })));
    await expect(api('/api/me')).rejects.toMatchObject({ codigo: 'INVALID_TOKEN' });
    expect(getAccessToken()).toBeNull();
  });
  it('una respuesta tardía no borra una sesión más nueva', async () => {
    let resolve!: (response: Response) => void;
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(r => { resolve = r; })));
    const pending = api('/api/me'); setAccessToken('new-session');
    resolve(new Response(JSON.stringify({ codigo: 'INVALID_TOKEN' }), { status: 401 }));
    await expect(pending).rejects.toMatchObject({ codigo: 'INVALID_TOKEN' });
    expect(getAccessToken()).toBe('new-session');
  });
});
