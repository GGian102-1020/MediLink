import type { ApiErrorBody } from './types';

const TOKEN_KEY = 'medlink.demo.token';

export function tokenActual(): string | null {
  try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
}
export function guardarToken(token: string | null): void {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch { /* Sesion sin persistencia si el navegador bloquea almacenamiento. */ }
}

export function pageQuery(page: number, otros: Record<string, string | number | undefined> = {}) {
  return { page, size: 20, ...otros };
}

async function request<T>(path: string, options: RequestInit = {}, params?: Record<string, string | number | undefined>, idempotencyKey?: string): Promise<T> {
  const url = new URL(path, window.location.origin);
  Object.entries(params || {}).forEach(([key, val]) => {
    if (val !== undefined && val !== '') url.searchParams.set(key, String(val));
  });
  const token = tokenActual();
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey);
  let response: Response;
  try { response = await fetch(url, { ...options, headers }); }
  catch { throw new Error('No se pudo conectar con MediLink. Comprobá que la API esté encendida.'); }
  if (!response.ok) {
    const error = await response.json().catch(() => null) as ApiErrorBody | null;
    throw new Error(error?.mensaje || `La solicitud falló (HTTP ${response.status}).`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function get<T>(path: string, params?: Record<string, string | number | undefined>): Promise<T> {
  return request<T>(path, {}, params);
}
export function post<T>(path: string, body?: unknown, key?: string): Promise<T> {
  return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }, undefined, key);
}
