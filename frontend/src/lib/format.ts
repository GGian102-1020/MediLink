const TZ = 'America/Argentina/Buenos_Aires';
export const dateTime = (value?: string | null) => {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short', timeZone: TZ }).format(d);
};
export const shortDateTime = (value?: string | null) => {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: TZ }).format(d);
};
export const number = (n: number) => new Intl.NumberFormat('es-AR').format(n);
export const readable = (text?: string | null) => text ? text.replace(/_/g, ' ').toLocaleLowerCase('es-AR').replace(/^./, x => x.toUpperCase()) : '—';
const MOVIMIENTOS: Record<string, string> = {
  RESERVA_CONFIRMADA: 'Reserva', CANCELACION_RESTITUCION: 'Devolución por cancelación', VENCIMIENTO: 'Devolución por vencimiento',
  AJUSTE_MANUAL: 'Ajuste manual', CORRECCION_STOCK_INCONSISTENTE: 'Corrección de stock',
};
export const movimientoLabel = (tipo: string) => MOVIMIENTOS[tipo] || readable(tipo);
export const plural =(n: number, one: string, many: string) => `${number(n)} ${n === 1 ? one : many}`;

/** Tiempo restante compacto: "45 min", "2 h 10 min", "1 d 3 h". */
export const remaining = (seconds: number) => {
  const s = Math.max(0, Math.floor(seconds));
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  if (d) return `${d} d ${h} h`;
  if (h) return `${h} h ${m} min`;
  return `${Math.max(m, s > 0 ? 1 : 0)} min`;
};
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
