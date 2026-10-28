import type { ApiErrorBody, Login, Me, Page } from './types';

/** API siempre con mismo origen. Vite reenvía /api en desarrollo; deploy requiere reverse proxy /api. */
const SESSION_KEY = 'medlink.tab.session';
let accessToken: string | null = null;
try { accessToken = sessionStorage.getItem(SESSION_KEY); } catch { /* storage bloqueado */ }

export function getAccessToken() { return accessToken; }
export function setAccessToken(token: string | null) {
  accessToken = token;
  try {
    if (token) sessionStorage.setItem(SESSION_KEY, token);
    else sessionStorage.removeItem(SESSION_KEY);
  } catch { /* no impide autenticación de esta pestaña */ }
}
export class ApiError extends Error {
  constructor(public status: number, public codigo: string, mensaje: string, public correlationId?: string) { super(mensaje); this.name = 'ApiError'; }
}

export type Query = Record<string, string | number | boolean | null | undefined>;
export function withQuery(path: string, query: Query = {}): string {
  const parts = Object.entries(query).filter(([, v]) => v !== null && v !== undefined && v !== '');
  if (!parts.length) return path;
  const params = new URLSearchParams();
  parts.forEach(([key, value]) => params.set(key, String(value)));
  return `${path}?${params.toString()}`;
}

export async function api<T>(path: string, options: RequestInit & { idempotencyKey?: string } = {}): Promise<T> {
  if (!path.startsWith('/api/')) throw new Error('Ruta API inválida');
  const { idempotencyKey, ...init } = options;
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  headers.set('X-Correlation-Id', crypto.randomUUID());
  if (init.body !== undefined && init.body !== null) headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey);
  let response: Response;
  try { response = await fetch(path, { ...init, headers, cache: 'no-store' }); }
  catch { throw new ApiError(0, 'NETWORK_ERROR', 'No se pudo conectar con MediLink. Revisá que la API esté en funcionamiento.'); }
  if (!response.ok) {
    let err: ApiErrorBody = {};
    try { err = await response.json() as ApiErrorBody; } catch { /* respuesta inesperada */ }
    if (response.status === 401 && accessToken) {
      setAccessToken(null);
      window.dispatchEvent(new Event('medlink:session-expired'));
    }
    throw new ApiError(response.status, err.codigo || `HTTP_${response.status}`, err.mensaje || `Ocurrió un error (${response.status}).`, err.correlationId);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
export function get<T>(path: string, params?: Query): Promise<T> { return api<T>(withQuery(path, params)); }
export function post<T>(path: string, payload?: unknown, idempotencyKey?: string): Promise<T> {
  return api<T>(path, { method: 'POST', ...(payload === undefined ? {} : { body: JSON.stringify(payload) }), idempotencyKey });
}
export function put<T>(path: string, payload: unknown): Promise<T> { return api<T>(path, { method: 'PUT', body: JSON.stringify(payload) }); }
export const authApi = {
  login: (correo: string, contrasena: string) => post<Login>('/api/auth/login', { correo, contrasena }),
  me: () => get<Me>('/api/me'),
  logout: () => post<void>('/api/auth/logout'),
};
export function pageQuery(page = 0, extra?: Query): Query { return { page, size: 10, ...extra }; }
export function emptyPage<T>(): Page<T> { return { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true }; }
