import { describe, expect, it, vi } from 'vitest';
import type { Prescripcion } from '../src/lib/types';

const getMock = vi.hoisted(() => vi.fn());
vi.mock('../src/lib/api', async importOriginal => ({ ...(await importOriginal<object>()), get: getMock }));

import { recetasUsablesPara } from '../src/lib/recetas';

function receta(id: number, extra: Partial<Prescripcion> = {}, disponible = 1, presentacionId = 16): Prescripcion {
  return {
    id, codigoExterno: `MLD-${id}`, usuarioId: 1, fechaEmision: '2026-11-01', fechaVencimiento: '2026-11-13', estado: 'ACTIVA',
    items: [{ id, presentacionId, descripcionPresentacion: '', cantidadAutorizada: 1, cantidadComprometida: 0, cantidadUtilizada: 1 - disponible, cantidadDisponible: disponible }],
    ...extra,
  };
}

describe('recetasUsablesPara', () => {
  it('devuelve solo recetas usables para la presentación, la más reciente primero', async () => {
    getMock.mockResolvedValueOnce({ content: [
      receta(1),
      receta(2, { fechaEmision: '2026-11-05' }),
      receta(3, { estado: 'ANULADA' }),
      receta(4, { fechaVencimiento: '2026-11-05' }),
      receta(5, {}, 0),
      receta(6, {}, 1, 9),
    ], last: true });
    const r = await recetasUsablesPara(16, new Date(2026, 10, 6));
    expect(r.map(x => x.codigoExterno)).toEqual(['MLD-2', 'MLD-1']);
  });

  it('recorre todas las páginas', async () => {
    getMock.mockReset();
    getMock.mockResolvedValueOnce({ content: [receta(1)], last: false }).mockResolvedValueOnce({ content: [receta(2)], last: true });
    const r = await recetasUsablesPara(16, new Date(2026, 10, 6));
    expect(r).toHaveLength(2);
    expect(getMock).toHaveBeenCalledTimes(2);
  });
});
