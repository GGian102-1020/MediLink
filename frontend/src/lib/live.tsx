import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { get } from './api';
import { plural } from './format';
import type { Page, Reserva } from './types';

/**
 * Actualización "en vivo" de reservas sin tocar el backend:
 * consulta cada POLL_MS la primera página de reservas de la farmacia
 * (ordenada por creación descendente) y detecta altas y cambios de estado.
 */
const POLL_MS = 10_000;
const HIGHLIGHT_MS = 8_000;

type LiveState = {
  /** Cambia cada vez que se detecta una novedad: usarlo como dependencia para recargar. */
  version: number;
  activeCount: number | null;
  isNew: (id: number) => boolean;
  /** Fuerza una consulta inmediata (por ejemplo, después de una acción del operador). */
  refresh: () => void;
  /** Mensajes de pacientes sin leer (badge de "Chat" en la barra lateral). */
  chatUnread: number;
  refreshChat: () => void;
};
const LiveContext = createContext<LiveState>({ version: 0, activeCount: null, isNew: () => false, refresh: () => {}, chatUnread: 0, refreshChat: () => {} });

export function LiveProvider({ farmId, children }: { farmId: number | null | undefined; children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [version, setVersion] = useState(0);
  const [chatUnread, setChatUnread] = useState(0);
  const lastChatUnread = useRef<number | null>(null);
  const enChat = useRef(false);
  enChat.current = location.pathname.startsWith('/chat');
  const [activeCount, setActiveCount] = useState<number | null>(null);
  const [fresh, setFresh] = useState<Record<number, number>>({});
  const lastMaxId = useRef<number | null>(null);
  const signature = useRef('');
  const running = useRef(false);

  const poll = useCallback(async () => {
    if (!farmId || running.current || document.hidden) return;
    running.current = true;
    try {
      const url = `/api/farmacias/${farmId}/reservas`;
      const [recent, active] = await Promise.all([
        get<Page<Reserva>>(url, { page: 0, size: 20 }),
        get<Page<Reserva>>(url, { page: 0, size: 1, estado: 'ACTIVA' }),
      ]);
      const sig = `${active.totalElements}|${recent.totalElements}|${recent.content.map(r => `${r.id}:${r.estado}`).join(',')}`;
      const maxId = recent.content.reduce((m, r) => Math.max(m, r.id), 0);
      setActiveCount(active.totalElements);

      if (lastMaxId.current === null) { lastMaxId.current = maxId; signature.current = sig; return; }
      const nuevas = recent.content.filter(r => r.id > (lastMaxId.current as number));
      if (nuevas.length) {
        lastMaxId.current = maxId;
        const now = Date.now();
        setFresh(prev => ({ ...prev, ...Object.fromEntries(nuevas.map(r => [r.id, now])) }));
        if (nuevas.length > 2) {
          toast.info(`${plural(nuevas.length, 'reserva nueva', 'reservas nuevas')}`, { action: { label: 'Ver', onClick: () => navigate('/operacion/reservas') } });
        } else {
          nuevas.forEach(r => toast.info(r.estado === 'PENDIENTE' ? 'Nueva receta para revisar' : 'Nueva reserva', {
            description: `${r.medicamentoNombreSnapshot} × ${r.cantidad} · ${[r.usuarioNombre, r.usuarioApellido].filter(Boolean).join(' ') || 'Usuario'}`,
            action: { label: 'Ver', onClick: () => navigate(`/operacion/reservas?codigo=${encodeURIComponent(r.codigoVisible)}`) },
          }));
        }
      }
      if (sig !== signature.current) { signature.current = sig; setVersion(v => v + 1); }
    } catch { /* la próxima consulta lo reintenta; los errores de sesión los maneja api.ts */ }
    finally { running.current = false; }
  }, [farmId, navigate]);

  useEffect(() => {
    lastMaxId.current = null; signature.current = '';
    void poll();
    const timer = window.setInterval(() => { void poll(); }, POLL_MS);
    const onVisible = () => { if (!document.hidden) void poll(); };
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('focus', onVisible);
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', onVisible); window.removeEventListener('focus', onVisible); };
  }, [poll]);

  // Limpia los resaltados viejos.
  useEffect(() => {
    if (!Object.keys(fresh).length) return;
    const t = window.setTimeout(() => {
      const now = Date.now();
      setFresh(prev => Object.fromEntries(Object.entries(prev).filter(([, ts]) => now - ts < HIGHLIGHT_MS)));
    }, HIGHLIGHT_MS);
    return () => window.clearTimeout(t);
  }, [fresh]);

  // Mensajes de chat sin leer: avisa cuando aumentan y el operador no está mirando el chat.
  const pollChat = useCallback(async () => {
    if (!farmId || document.hidden) return;
    try {
      const { total } = await get<{ total: number }>(`/api/farmacias/${farmId}/chats/no-leidos`);
      setChatUnread(total);
      if (lastChatUnread.current !== null && total > lastChatUnread.current && !enChat.current) {
        toast.info('Nuevo mensaje de un paciente', { action: { label: 'Abrir chat', onClick: () => navigate('/chat') } });
      }
      lastChatUnread.current = total;
    } catch { /* se reintenta en la próxima consulta */ }
  }, [farmId, navigate]);

  useEffect(() => {
    lastChatUnread.current = null;
    void pollChat();
    const timer = window.setInterval(() => { void pollChat(); }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [pollChat]);

  const isNew = useCallback((id: number) => id in fresh, [fresh]);
  const refresh = useCallback(() => { void poll(); }, [poll]);
  const refreshChat = useCallback(() => { void pollChat(); }, [pollChat]);
  return <LiveContext.Provider value={{ version, activeCount, isNew, refresh, chatUnread, refreshChat }}>{children}</LiveContext.Provider>;
}
export const useLive = () => useContext(LiveContext);
