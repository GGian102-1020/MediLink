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
