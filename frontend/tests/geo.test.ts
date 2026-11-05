import { describe, expect, it } from 'vitest';
import { acortarDireccion, decodificarPolyline, enlaceComoLlegar, formatoDistancia, formatoDuracion, redondear, textoPaso } from '../src/lib/geo';

describe('geo', () => {
  it('decodifica la polyline de ejemplo de Google/OSRM', () => {
    expect(decodificarPolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@')).toEqual([[38.5, -120.2], [40.7, -120.95], [43.252, -126.453]]);
  });
  it('redondea la ubicacion a 4 decimales antes de enviarla', () => {
    expect(redondear({ lat: -34.603722, lng: -58.381592 })).toEqual({ lat: -34.6037, lng: -58.3816 });
  });
  it('formatea distancias y duraciones en castellano', () => {
    expect(formatoDistancia(4)).toBe('10 m');
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
