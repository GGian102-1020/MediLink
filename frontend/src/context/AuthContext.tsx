import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { authApi, getAccessToken, setAccessToken } from '../lib/api';
import type { Cuenta, Me } from '../lib/types';

type AuthState = { me: Me | null; loading: boolean; signIn: (email: string, password: string) => Promise<Cuenta>; signOut: () => Promise<void>; refresh: () => Promise<void> };
const AuthContext = createContext<AuthState | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(Boolean(getAccessToken()));
  async function refresh() {
    if (!getAccessToken()) { setMe(null); return; }
    try { setMe(await authApi.me()); }
    catch { setAccessToken(null); setMe(null); }
  }
  useEffect(() => {
    void refresh().finally(() => setLoading(false));
    const onExpired = () => setMe(null);
    window.addEventListener('medlink:session-expired', onExpired);
    return () => window.removeEventListener('medlink:session-expired', onExpired);
  }, []);
  async function signIn(email: string, password: string) {
    const response = await authApi.login(email.trim(), password);
    setAccessToken(response.accessToken);
    try { setMe(await authApi.me()); }
    catch { setAccessToken(null); setMe(null); throw new Error('No se pudo validar la sesión. Volvé a iniciar sesión.'); }
    return response.perfil;
  }
  async function signOut() {
    try { if (getAccessToken()) await authApi.logout(); } catch { /* borrar token aunque la red falle */ }
    finally { setAccessToken(null); setMe(null); }
  }
  return <AuthContext.Provider value={{ me, loading, signIn, signOut, refresh }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('AuthProvider faltante');
  return context;
}
