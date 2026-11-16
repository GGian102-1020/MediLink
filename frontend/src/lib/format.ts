export const dateTime = (value?: string | null) => {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Argentina/Buenos_Aires' }).format(d);
};
export const dateOnly = (value?: string | null) => value ? value.split('-').reverse().join('/') : '—';
export const number = (n: number) => new Intl.NumberFormat('es-AR').format(n);
export const readable = (text?: string | null) => text ? text.replace(/_/g, ' ').toLocaleLowerCase('es-AR').replace(/^./, x => x.toUpperCase()) : '—';
export const presentationLabel = (p: { concentracionTexto: string; formaFarmaceutica: string; cantidadEnvase: number; unidadExpendio: string }) => `${p.concentracionTexto} · ${p.formaFarmaceutica} · ${p.cantidadEnvase} ${p.unidadExpendio}`;
export const remaining = (seconds: number) => {
  const s = Math.max(0, seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h ? `${h} h ${m} min` : `${m} min`;
};

// Funciones adicionales utilizadas por el dashboard de operadores.
const TZ = "America/Argentina/Buenos_Aires";
export const shortDateTime = (value?: string | null) => {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: TZ }).format(d);
};
const MOVIMIENTOS: Record<string, string> = {
  RESERVA_CONFIRMADA: 'Reserva', CANCELACION_RESTITUCION: 'Devolución por cancelación', VENCIMIENTO: 'Devolución por vencimiento',
  AJUSTE_MANUAL: 'Ajuste manual', CORRECCION_STOCK_INCONSISTENTE: 'Corrección de stock',
};
export const movimientoLabel = (tipo: string) => MOVIMIENTOS[tipo] || readable(tipo);
export const plural =(n: number, one: string, many: string) => `${number(n)} ${n === 1 ? one : many}`;

/** Relativo al pasado: "hace 5 min", "hace 3 h", "hace 2 d". */
export const ago = (value?: string | null) => {
  if (!value) return '—';
  const diff = (Date.now() - new Date(value).getTime()) / 1000;
  if (Number.isNaN(diff)) return '—';
  if (diff < 60) return 'recién';
  if (diff < 3600) return `hace ${Math.floor(diff / 60)} min`;
  if (diff < 86400) return `hace ${Math.floor(diff / 3600)} h`;
  if (diff < 86400 * 7) return `hace ${Math.floor(diff / 86400)} d`;
  return shortDateTime(value);
};
export const greeting = () => {
  const h = Number(new Intl.DateTimeFormat('es-AR', { hour: 'numeric', hourCycle: 'h23', timeZone: TZ }).format(new Date()));
  return h < 12 ? 'Buen día' : h < 20 ? 'Buenas tardes' : 'Buenas noches';
};
export const today = () => {
  const s = new Intl.DateTimeFormat('es-AR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: TZ }).format(new Date());
  return s.charAt(0).toUpperCase() + s.slice(1);
};
export const initials = (nombre?: string, apellido?: string) => `${[...(nombre || '').trim()][0] || ''}${[...(apellido || '').trim()][0] || ''}`.toUpperCase() || '·';
