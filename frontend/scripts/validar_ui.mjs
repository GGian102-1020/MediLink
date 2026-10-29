/** AC-48 / RNF-07: real-browser navigation against disposable localhost HTTPS only. */
import { chromium, firefox } from 'playwright';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const URL = 'https://127.0.0.1:8443';
const widths = [360, 390, 768, 1440];
const reportDir = process.env.MEDLINK_UI_EVIDENCE_DIR || path.resolve('../evidencias/ui_ci');
const sha = process.env.GITHUB_SHA || 'LOCAL-SIN-SHA';
if (process.env.MEDLINK_UI_EPHEMERAL !== '1' || process.argv[2] !== '--demo-ack' ||
    process.env.MEDLINK_DEMO_PASSWORD !== 'Password123!') {
  throw new Error('Solo se permite ejecutar en el entorno efimero y seed ficticio explicito de CI.');
}
await fs.mkdir(reportDir, { recursive: true });
const result = { schema: 1, ac: 'AC-48', sha, base: URL, dateUtc: new Date().toISOString(),
  os: `${os.platform()} ${os.release()}`, widths, browsers: {}, status: 'FAIL', failures: [] };
const assert = (predicate, message) => { if (!predicate) throw new Error(message); };
const noHorizontalOverflow = async (page, label) => {
  const x = await page.evaluate(() => ({scroll: document.documentElement.scrollWidth,
    viewport: document.documentElement.clientWidth}));
  assert(x.scroll <= x.viewport + 2, `${label}: scroll horizontal ${x.scroll} > ${x.viewport}`);
};
const visitPublic = async (page, name, width) => {
  await page.goto(`${URL}/catalogo`, {waitUntil: 'domcontentloaded'});
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
    try {
      const page = await context.newPage();
      await page.goto(`${URL}/ingresar`);
      await page.getByRole('textbox', {name: 'Correo electrónico'}).fill(correo);
      await page.locator('input[type=password]').fill(process.env.MEDLINK_DEMO_PASSWORD);
      await page.getByRole('button', {name: /Ingresar a mi cuenta/}).click();
      await page.waitForURL(/\/panel$/, {timeout: 15000});
      await page.goto(`${URL}${own}`);
      await page.waitForURL(new RegExp(own.replaceAll('/', '\\/')+'$'));
      await noHorizontalOverflow(page, `${name}/${rol}/panel`);
      await page.reload();
      await page.waitForURL(new RegExp(own.replaceAll('/', '\\/')+'$'));
      assert(await page.locator('.app-main').count() === 1, `${rol}: se perdio sesion tras refresh`);
      await page.goto(`${URL}${forbidden}`);
      await page.waitForURL(/\/panel$/);
      await page.getByRole('button', {name: 'Abrir menú'}).click();
      await page.getByRole('button', {name:/Cerrar sesión/i}).click();
      await page.waitForURL(`${URL}/`);
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
  await fs.writeFile(path.join(reportDir, 'resultado_ui.json'), JSON.stringify(result, null, 2)+'\n');
}
