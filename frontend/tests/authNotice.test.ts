import { describe, expect, it } from 'vitest';
import { loginNotice } from '../src/lib/authNotice';

describe('mensajes de autenticación', () => {
  it('confirma cambio de contraseña en la página de login', () => {
    expect(loginNotice({ passwordChanged: true })).toContain('Contraseña actualizada');
  });
  it('confirma registro completo en la página de login', () => {
    expect(loginNotice({ registered: true })).toContain('Cuenta creada');
  });
  it('no muestra mensajes para estados arbitrarios', () => {
    expect(loginNotice({ passwordChanged: 'true' })).toBeNull();
    expect(loginNotice(null)).toBeNull();
  });
});
