import { afterEach, describe, expect, it, vi } from 'vitest';
import { acortarDireccion, buscarDireccion, coords, decodificarPolyline, enlaceComoLlegar, esCoordenadaValida, estaEnCaba, formatoDistancia, formatoDuracion, redondear, textoPaso } from '../src/lib/geo';

describe('geo', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('descarta coordenadas invalidas y ubicaciones incompletas', () => {
    expect(coords({ latitud: -34.60, longitud: -58.40 })).toEqual({ lat: -34.60, lng: -58.40 });
    expect(coords({ latitud: 91, longitud: -58.40 })).toBeNull();
    expect(coords({ latitud: Number.NaN, longitud: -58.40 })).toBeNull();
    expect(coords({ latitud: -34.60, longitud: null })).toBeNull();
    expect(esCoordenadaValida({ lat: -34.60, lng: -58.40 })).toBe(true);
  });
  it('filtra jurisdiccion usando address.state', () => {
    expect(estaEnCaba({ address: { state: 'Ciudad Autónoma de Buenos Aires' }, display_name: '' })).toBe(true);
    expect(estaEnCaba({ address: { state: 'Buenos Aires' }, display_name: 'CABA' })).toBe(false);
    expect(estaEnCaba({ display_name: 'Corrientes, Ciudad Autónoma de Buenos Aires, Argentina' })).toBe(true);
  });
  it('usa cache y excluye direcciones fuera de CABA', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [
      { lat: '-34.6', lon: '-58.4', display_name: 'Av. Corrientes 1500, Ciudad Autónoma de Buenos Aires', address: { state: 'Ciudad Autónoma de Buenos Aires' } },
      { lat: '-34.8', lon: '-58.3', display_name: 'Avellaneda', address: { state: 'Buenos Aires' } },
    ] });
    vi.stubGlobal('fetch', fetchMock);
    const first = await buscarDireccion('Corrientes 1500');
    expect(first).toHaveLength(1);
    expect(first[0].lat).toBe(-34.6);
    expect(await buscarDireccion(' corrientes 1500 ')).toEqual(first);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('respeta la cancelacion previa a la busqueda', async () => {
    const ctrl = new AbortController(); ctrl.abort();
    await expect(buscarDireccion('Rivadavia 1000', ctrl.signal)).rejects.toMatchObject({ name: 'AbortError' });
  });
  it('decodifica la polyline de ejemplo de Google/OSRM', () => {
    expect(decodificarPolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@')).toEqual([[38.5, -120.2], [40.7, -120.95], [43.252, -126.453]]);
  });
  it('redondea la ubicacion a 4 decimales antes de enviarla', () => {
    expect(redondear({ lat: -34.603722, lng: -58.381592 })).toEqual({ lat: -34.6037, lng: -58.3816 });
  });
  it('formatea distancias y duraciones en castellano', () => {
    expect(formatoDistancia(0)).toBe('0 m');
    expect(formatoDistancia(4)).toBe('4 m');
    expect(formatoDistancia(846)).toBe('850 m');
    expect(formatoDistancia(1234)).toBe('1,2 km');
    expect(formatoDuracion(20)).toBe('1 min');
    expect(formatoDuracion(14 * 60)).toBe('14 min');
    expect(formatoDuracion(75 * 60)).toBe('1 h 15 min');
  });
  it('traduce las maniobras de OSRM', () => {
    expect(textoPaso({ distance: 100, name: 'Av. Corrientes', maneuver: { type: 'turn', modifier: 'left' } })).toBe('Girá a la izquierda en Av. Corrientes');
    expect(textoPaso({ distance: 0, name: '', maneuver: { type: 'arrive' } })).toBe('Llegaste a la farmacia');
    expect(textoPaso({ distance: 50, name: 'Callao', maneuver: { type: 'roundabout', exit: 2 } })).toBe('En la rotonda, tomá la 2ª salida por Callao');
  });
  it('acorta las direcciones de Nominatim', () => {
    expect(acortarDireccion('1500, Avenida Corrientes, San Nicolás, Buenos Aires, Comuna 1, Ciudad Autónoma de Buenos Aires, C1042AAP, Argentina'))
      .toBe('Avenida Corrientes 1500, San Nicolás');
    expect(acortarDireccion('Tribeca, 2000, Avenida Cabildo, Belgrano, Buenos Aires, Comuna 13, Ciudad Autónoma de Buenos Aires, C1428AAO, Argentina'))
      .toBe('Avenida Cabildo 2000, Belgrano');
    expect(acortarDireccion('Obelisco, Avenida 9 de Julio, San Nicolás, Buenos Aires, Argentina')).toBe('Obelisco, Avenida 9 de Julio');
  });
  it('arma el enlace de Google Maps sin API key', () => {
    expect(enlaceComoLlegar({ lat: -34.6, lng: -58.38 }, 'pie')).toBe('https://www.google.com/maps/dir/?api=1&destination=-34.6%2C-58.38&travelmode=walking');
  });
});
