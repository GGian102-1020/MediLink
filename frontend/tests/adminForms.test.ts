import { describe, expect, it } from 'vitest';
import { farmRequest, stringifyAuditSummary } from '../src/lib/adminForms';

describe('formulario de farmacias', () => {
  const original = { nombre: 'Farmacia Centro', direccion: 'Av. Corrientes 1500', telefono: '', emailContacto: '', latitud: '-34.6041', longitud: '-58.3851' };
  it('conserva ambas coordenadas al editar nombre y contacto', () => {
    expect(farmRequest({ ...original, nombre: 'Farmacia Centro 2' })).toEqual({
      nombre: 'Farmacia Centro 2', direccion: original.direccion,
      telefono: null, emailContacto: null, latitud: -34.6041, longitud: -58.3851,
    });
  });
  it('permite crear una farmacia sin ubicación', () => {
    expect(farmRequest({ ...original, latitud: '', longitud: '' })).toMatchObject({ latitud: null, longitud: null });
  });
  it('rechaza pares geográficos incompletos', () => {
    expect(() => farmRequest({ ...original, longitud: '' })).toThrow(/latitud y longitud/);
  });
  it('rechaza coordenadas fuera de rango', () => {
    expect(() => farmRequest({ ...original, latitud: '-91' })).toThrow(/fuera de rango/);
    expect(() => farmRequest({ ...original, longitud: '181' })).toThrow(/fuera de rango/);
  });
});

describe('visualización de auditoría', () => {
  it('serializa objetos sin provocar errores de React', () => {
    expect(stringifyAuditSummary({ estado: 'ACTIVA', stock: 4 })).toContain('"estado": "ACTIVA"');
  });
  it('conserva cadenas y tolera nulos', () => {
    expect(stringifyAuditSummary('detalle')).toBe('detalle');
    expect(stringifyAuditSummary(null)).toBe('null');
  });
});
