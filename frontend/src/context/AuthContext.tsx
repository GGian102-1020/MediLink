import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { get, guardarToken, post, tokenActual } from '../lib/api';
import type { Login, Me } from '../lib/types';

type AuthContextType = {
  me: Me | null;
  loading: boolean;
  login: (correo: string, contrasena: string) => Promise<void>;
  logout: () => Promise<void>;
};
const Context = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(Boolean(tokenActual()));
  useEffect(() => {
    if (!tokenActual()) return;
    let active = true;
    get<Me>('/api/me').then(data => { if (active) setMe(data); })
      .catch(() => { if (active) guardarToken(null); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  async function login(correo: string, contrasena: string) {
    const data = await post<Login>('/api/auth/login', { correo, contrasena });
    guardarToken(data.accessToken);
    try { setMe(await get<Me>('/api/me')); }
    catch (error) { guardarToken(null); throw error; }
  }
  async function logout() {
    try { if (tokenActual()) await post<void>('/api/auth/logout'); }
    catch { /* Logout local aun cuando la API no responda. */ }
    finally { guardarToken(null); setMe(null); }
  }
  return <Context.Provider value={{ me, loading, login, logout }}>{children}</Context.Provider>;
}

export function useAuth() {
  const ctx = useContext(Context);
  if (!ctx) throw new Error('AuthProvider no esta disponible.');
  return ctx;
}
