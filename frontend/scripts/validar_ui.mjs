/** AC-48 / RNF-07: real-browser navigation against disposable localhost HTTPS only. */
import { chromium, firefox } from 'playwright';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { waitForAuthenticatedApp } from './esperar_sesion_ui.mjs';
import { pathnameFromUrl } from './ui_url.mjs';

// Caddy terminates TLS for the localhost hostname (gateway/Caddyfile).
const BASE_URL = 'https://localhost:8443';
const widths = [360, 390, 768, 1440];
const reportDir = process.env.MEDLINK_UI_EVIDENCE_DIR || path.resolve('../evidencias/ui_ci');
const sha = process.env.GITHUB_SHA || 'LOCAL-SIN-SHA';
if (process.env.MEDLINK_UI_EPHEMERAL !== '1' || process.argv[2] !== '--demo-ack' ||
    process.env.MEDLINK_DEMO_PASSWORD !== 'Password123!') {
  throw new Error('Solo se permite ejecutar en el entorno efimero y seed ficticio explicito de CI.');
}
await fs.mkdir(reportDir, { recursive: true });
const result = { schema: 1, ac: 'AC-48', sha, base: BASE_URL, dateUtc: new Date().toISOString(),
  os: `${os.platform()} ${os.release()}`, widths, browsers: {}, status: 'FAIL', failures: [] };
const assert = (predicate, message) => { if (!predicate) throw new Error(message); };
const noHorizontalOverflow = async (page, label) => {
  const x = await page.evaluate(() => ({scroll: document.documentElement.scrollWidth,
    viewport: document.documentElement.clientWidth}));
  assert(x.scroll <= x.viewport + 2, `${label}: scroll horizontal ${x.scroll} > ${x.viewport}`);
};
const visitPublic = async (page, name, width) => {
  await page.goto(`${BASE_URL}/catalogo`, {waitUntil: 'domcontentloaded'});
  await page.getByRole('textbox', { name: 'Nombre del medicamento' }).fill('Ibuprofeno');
  await page.getByRole('button', {name: /^Buscar/}).click();
  await page.locator('.medicine-toggle').first().waitFor();
  await page.locator('.medicine-toggle').first().click();
  await page.locator('a.presentation-row').first().waitFor();
  await noHorizontalOverflow(page, `${name}/${width}/catalogo`);
  await page.screenshot({path: path.join(reportDir, `${name}-${width}-catalogo.png`), fullPage: true});
  await page.locator('a.presentation-row').first().click();
  await page.waitForURL(/\/presentaciones\/\d+$/);
  await page.locator('.availability-list .pharmacy-option').first().waitFor();
  assert((await page.locator('h1').first().innerText()).toLowerCase().includes('ibuprofeno'), 'AC-48: medicamento incorrecto');
  await noHorizontalOverflow(page, `${name}/${width}/disponibilidad`);
  await page.reload();
  await page.locator('.availability-list .pharmacy-option').first().waitFor();
  await noHorizontalOverflow(page, `${name}/${width}/refresh`);
  await page.screenshot({path: path.join(reportDir, `${name}-${width}-disponibilidad.png`), fullPage: true});
  await page.locator('.availability-list .pharmacy-option').first().click();
  await page.getByRole('button', {name: /Ingresar para reservar/i}).click();
  await page.waitForURL(/\/ingresar$/);
};
const roles = [
  ['USUARIO', 'juan.perez@mail.test', '/mis-reservas', '/admin/farmacias'],
  ['OPERADOR', 'carla.mendez@farmacia-central.test', '/operacion/reservas', '/mis-reservas'],
  ['ADMINISTRADOR', 'admin@medlink.test', '/admin/farmacias', '/mis-reservas'],
];
const visitRoles = async (browser, name) => {
  for (const [rol, correo, own, forbidden] of roles) {
    const context = await browser.newContext({ignoreHTTPSErrors: true, viewport: {width: 390, height: 900}});
    const page = await context.newPage();
    const meStatuses = [];
    page.on('response', response => {
      if (pathnameFromUrl(response.url()) === '/api/me') meStatuses.push(response.status());
    });
    try {
      await page.goto(`${BASE_URL}/ingresar`);
      await page.getByRole('textbox', {name: 'Correo electrónico'}).fill(correo);
      await page.locator('input[type=password]').fill(process.env.MEDLINK_DEMO_PASSWORD);
      await page.getByRole('button', {name: /Ingresar a mi cuenta/}).click();
      try {
        await page.waitForURL(/\/panel$/, {timeout: 15000});
      } catch (error) {
        const pathNow = pathnameFromUrl(page.url());
        const loginErrorVisible = await page.getByRole('alert').isVisible().catch(() => false);
        const meCodes = meStatuses.slice(-4).join(',') || 'sin respuesta';
        throw new Error(`${rol}/login: no llego a /panel; ruta=${pathNow}; ` +
          `alertaVisible=${loginErrorVisible}; statusApiMe=${meCodes}; ` +
          `causa=${error?.name || 'error'}`);
      }
      await waitForAuthenticatedApp(page, rol, 'login', meStatuses);
      await page.goto(`${BASE_URL}${own}`);
      await waitForAuthenticatedApp(page, rol, 'navegacion-propia', meStatuses);
      assert(pathnameFromUrl(page.url()) === own, `${rol}: no ingreso en su ruta ${own}`);
      await noHorizontalOverflow(page, `${name}/${rol}/panel`);
      await page.reload();
      // La URL ya coincide al iniciar la recarga. Esperar a que /api/me restaure React.
      await waitForAuthenticatedApp(page, rol, 'refresh', meStatuses);
      assert(pathnameFromUrl(page.url()) === own, `${rol}: ruta inesperada tras refresh`);
      const expectedRoleName = { USUARIO: 'Usuario', OPERADOR: 'Operador de farmacia',
        ADMINISTRADOR: 'Administrador' }[rol];
      assert((await page.locator('.user-info small').innerText()) === expectedRoleName,
        `${rol}: la sesion recuperada no muestra el rol esperado`);
      await page.goto(`${BASE_URL}${forbidden}`);
      await page.waitForURL(/\/panel$/, {timeout: 15000});
      await waitForAuthenticatedApp(page, rol, 'acceso-prohibido', meStatuses);
      await page.getByRole('button', {name: 'Abrir menú'}).click();
      await page.getByRole('button', {name:/Cerrar sesión/i}).click();
      await page.waitForURL(`${BASE_URL}/`, {timeout: 15000});
    } catch (error) {
      // Las cuentas son exclusivamente las ficticias del seed efimero.
      try { await page.screenshot({path: path.join(reportDir, `${name}-${rol.toLowerCase()}-fallo.png`), fullPage: true}); }
      catch { /* no ocultar el error original si el navegador se cerro */ }
      throw error;
    } finally {await context.close();}
  }
};
try {
  for (const [name, engine] of [['chromium', chromium], ['firefox', firefox]]) {
    const browser = await engine.launch({headless: true});
    try {
      result.browsers[name] = {version: browser.version(), checks: []};
      for (const width of widths) {
        const context = await browser.newContext({ignoreHTTPSErrors: true, viewport: {width, height: 900}});
        try {
          await visitPublic(await context.newPage(), name, width);
          result.browsers[name].checks.push(`AC-48 y responsive ${width}px: PASS`);
          console.log(`PASS AC-48 ${name} ${width}px: catalogo->presentacion->disponibilidad->refresh->ingresar`);
        } finally {await context.close();}
      }
      result.browsers[name].publicFinished = true;
      await fs.writeFile(path.join(reportDir, 'progreso_ui.json'), JSON.stringify(result, null, 2) + '\n');
      console.log(`INFO ${name}: rutas publicas comprobadas; iniciar controles autenticados`);
      await visitRoles(browser, name);
      result.browsers[name].checks.push('roles USUARIO/OPERADOR/ADMINISTRADOR: PASS');
      console.log(`PASS RNF UI ${name}: tres roles, refresh, acceso y logout`);
    } finally {await browser.close();}
  }
  result.status = 'PASS';
  console.log('PASS: AC-48 browser navigation verified (Chromium+Firefox; 360/390/768/1440).');
} catch (error) {
  result.failures.push(String(error?.message || error));
  console.error('FAIL UI:', String(error?.message || error));
  process.exitCode = 1;
} finally {
  // Keep the final and progress artifacts consistent; the progress file may have been
  // written while status was still FAIL (the safe initial state).
  const finalEvidence = JSON.stringify(result, null, 2) + '\n';
  await Promise.all([
    fs.writeFile(path.join(reportDir, 'resultado_ui.json'), finalEvidence),
    fs.writeFile(path.join(reportDir, 'progreso_ui.json'), finalEvidence),
  ]);
}
