/** Wait for React + /api/me to restore an authenticated view after navigation/reload.
 * A matching URL is not evidence of successful auth: it can match before hydration.
 * Logs only URL pathname and the presence of storage, never a JWT or personal data.
 */
export async function waitForAuthenticatedApp(page, role, phase, meStatuses = [], timeout = 20000) {
  try {
    await page.locator('.app-main').waitFor({state: 'visible', timeout});
  } catch {
    const pathname = new URL(page.url()).pathname;
    const tokenPresent = await page.evaluate(() =>
      sessionStorage.getItem('medlink.tab.session') !== null).catch(() => null);
    const stored = tokenPresent === null ? 'no-disponible' : tokenPresent ? 'si' : 'no';
    const responses = meStatuses.length ? meStatuses.slice(-5).join(',') : 'sin respuesta';
    throw new Error(`${role}/${phase}: no aparecio panel autenticado en ${timeout}ms; ` +
      `ruta=${pathname}; tokenGuardado=${stored}; statusApiMe=${responses}`);
  }
}
