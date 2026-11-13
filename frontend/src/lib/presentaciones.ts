import { useEffect, useState } from 'react';
import { get } from './api';
import type { Presentacion } from './types';

/** Caché en memoria: cada presentación se pide una sola vez por sesión. */
const cache = new Map<number, Promise<Presentacion | null>>();
export function fetchPresentacion(id: number): Promise<Presentacion | null> {
  if (!cache.has(id)) cache.set(id, get<Presentacion>(`/api/presentaciones/${id}`).catch(() => { cache.delete(id); return null; }));
  return cache.get(id)!;
}
export function usePresentaciones(ids: number[]) {
  const [map, setMap] = useState<Record<number, Presentacion | null>>({});
  const key = [...new Set(ids)].sort((a, b) => a - b).join(',');
  useEffect(() => {
    let active = true;
    const list = key ? key.split(',').map(Number) : [];
    void Promise.all(list.map(async id => [id, await fetchPresentacion(id)] as const)).then(entries => {
      if (active) setMap(prev => ({ ...prev, ...Object.fromEntries(entries) }));
    });
    return () => { active = false; };
  }, [key]);
  return map;
}
export const presentacionTitulo = (p: Presentacion | null | undefined, id: number) => p ? p.medicamentoNombre : `Presentación #${id}`;
export const presentacionDetalle = (p: Presentacion | null | undefined) => p ? `${p.concentracionTexto} · ${p.formaFarmaceutica} · ${p.unidadExpendio} x ${p.cantidadEnvase}` : 'Cargando detalle…';
