/** El backend reemplaza todas las coordenadas en cada PUT; omitirlas elimina el pin del mapa. */
export function farmRequest(values: Record<string, string>) {
  const latitud = (values.latitud ?? '').trim();
  const longitud = (values.longitud ?? '').trim();
  if (Boolean(latitud) !== Boolean(longitud)) {
    throw new Error('Completá latitud y longitud juntas, o dejá ambas vacías.');
  }
  const lat = latitud ? Number(latitud) : null;
  const lng = longitud ? Number(longitud) : null;
  if ((lat !== null && (!Number.isFinite(lat) || lat < -90 || lat > 90)) ||
      (lng !== null && (!Number.isFinite(lng) || lng < -180 || lng > 180))) {
    throw new Error('Las coordenadas están fuera de rango.');
  }
  return {
    nombre: values.nombre?.trim(), direccion: values.direccion?.trim(),
    telefono: values.telefono?.trim() || null,
    emailContacto: values.emailContacto?.trim() || null,
    latitud: lat, longitud: lng,
  };
}

/** Los resúmenes de auditoría llegan como objetos JSON, no como texto plano. */
export function stringifyAuditSummary(value: unknown): string {
  if (typeof value === 'string') return value;
  try { return JSON.stringify(value, null, 2) || '—'; }
  catch { return '—'; }
}
