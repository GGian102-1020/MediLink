import type { Presentacion } from './types';
export function presentationLabel(p: Presentacion): string {
  return `${p.concentracionTexto} · ${p.formaFarmaceutica} · ${p.cantidadEnvase} ${p.unidadExpendio}`;
}
export function number(n: number): string { return n.toLocaleString('es-AR'); }
