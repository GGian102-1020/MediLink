import test from 'node:test';
import assert from 'node:assert/strict';
import { waitForAuthenticatedApp } from '../esperar_sesion_ui.mjs';

test('espera de verdad a que aparezca el panel y no confunde URL con autenticacion', async () => {
  let release;
  const ready = new Promise(resolve => { release = resolve; });
  let finished = false;
  const page = {
    locator(selector) {
      assert.equal(selector, '.app-main');
      return {waitFor: async ({state, timeout}) => {
        assert.equal(state, 'visible'); assert.equal(timeout, 20000);
        await ready;
      }};
    }
  };
  const task = waitForAuthenticatedApp(page, 'USUARIO', 'refresh').then(() => {finished = true;});
  await Promise.resolve();
  assert.equal(finished, false, 'No debe terminar mientras React sigue hidratando');
  release();
  await task;
  assert.equal(finished, true);
});

test('una redireccion real a login produce un error diagnosticable sin publicar el token', async () => {
  const page = {
    locator: () => ({waitFor: async () => { throw new Error('Timeout'); }}),
    url: () => 'https://localhost:8443/ingresar',
    evaluate: async () => false
  };
  await assert.rejects(waitForAuthenticatedApp(page, 'OPERADOR', 'refresh', [200, 401], 50), error => {
    assert.match(error.message, /OPERADOR\/refresh/);
    assert.match(error.message, /ruta=\/ingresar/);
    assert.match(error.message, /tokenGuardado=no/);
    assert.match(error.message, /statusApiMe=200,401/);
    assert.doesNotMatch(error.message, /Bearer|eyJ/);
    return true;
  });
});

test('conserva un fallo genuino incluso si el token aun esta almacenado', async () => {
  const page = {
    locator: () => ({waitFor: async () => {throw new Error('Timeout');}}),
    url: () => 'https://localhost:8443/mis-reservas',
    evaluate: async () => true
  };
  await assert.rejects(waitForAuthenticatedApp(page, 'USUARIO', 'refresh', [500], 50),
    /tokenGuardado=si; statusApiMe=500/);
});
