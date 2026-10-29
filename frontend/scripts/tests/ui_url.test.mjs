import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathnameFromUrl } from '../ui_url.mjs';

test('recupera rutas de respuesta y de pagina, incluyendo query y fragment', () => {
  assert.equal(pathnameFromUrl('https://localhost:8443/api/me?refresh=1'), '/api/me');
  assert.equal(pathnameFromUrl('https://localhost:8443/ingresar#login'), '/ingresar');
  assert.equal(pathnameFromUrl('https://localhost:8443/admin/farmacias'), '/admin/farmacias');
});

test('rechaza URLs invalidas en lugar de inventar un resultado', () => {
  assert.throws(() => pathnameFromUrl('no es una URL absoluta'), TypeError);
});

test('el runner no pisa el constructor URL ni usa un parser falso', () => {
  const runner = readFileSync(new URL('../validar_ui.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(runner, /\b(?:const|let|var)\s+URL\s*=/);
  assert.match(runner, /pathnameFromUrl\(response\.url\(\)\)/);
  assert.match(runner, /pathnameFromUrl\(page\.url\(\)\)/);
  assert.match(runner, /https:\/\/localhost:8443/);
});
