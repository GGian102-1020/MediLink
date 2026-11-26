import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { QRCodeSVG } from 'qrcode.react';
import { AndroidDownloadDialog } from '../src/components/AndroidDownloadDialog';
import { findLocalAndroidApk, resolveAndroidDownload } from '../src/download';

const sha256 = 'a'.repeat(64);
const manifest = { href: `http://192.168.1.7:5174/downloads/MediFind-${sha256}.apk`, version: '0.2.0-lan', buildId: 'test-build', sha256, sourceCommit: 'da344f1643af3111404e42703dac6053c333aaa1', originalMainSources: true };
const fetchJson = (value: unknown, status = 200) => (async (_url: unknown, options: RequestInit) => {
  expect(_url).toBe('/local-build.json');
  expect(options.cache).toBe('no-store');
  return new Response(JSON.stringify(value), { status });
}) as typeof fetch;
describe('Distribución local', () => {
  it('el QR y el botón codifican exactamente la URL del APK identificado por hash', async () => {
    const download = await findLocalAndroidApk(fetchJson(manifest));
    const html = renderToStaticMarkup(<AndroidDownloadDialog download={download} onClose={() => {}} />);
    const expectedQr = renderToStaticMarkup(<QRCodeSVG value={manifest.href} size={220} level="M" marginSize={4} bgColor="#FFFFFF" fgColor="#133a58" />);
    expect(html).toContain(`href="${manifest.href}"`);
    expect(html).toContain(expectedQr.match(/d="([^"]+)"/g)!.at(-1)!);
    expect(html).toContain('Versión de pruebas locales');
    expect(html).toContain(manifest.version);
    expect(html).not.toContain('github.com');
  });
  it('no acepta localhost, URL públicas o un hash distinto ni hace fallback a Releases', async () => {
    for (const href of ['http://localhost:5174/a.apk', 'http://127.0.0.1/a.apk', 'https://example.com/a.apk', manifest.href.replace(sha256, 'b'.repeat(64))]) {
      await expect(findLocalAndroidApk(fetchJson({ ...manifest, href }))).rejects.toThrow();
    }
    await expect(findLocalAndroidApk(fetchJson({}, 404))).rejects.toThrow();
  });
  it('la configuración pública rechaza destinos privados', () => {
    for (const href of ['http://192.168.1.7/app.apk', 'https://192.168.1.7/app.apk', 'https://localhost/a.apk', 'https://[::1]/a.apk']) expect(resolveAndroidDownload(href).published).toBe(false);
  });
  it('rechaza una compilación de la rama anterior aunque su APK exista', async () => {
    await expect(findLocalAndroidApk(fetchJson({ ...manifest, sourceCommit: '3624d2fb3b94efe5ffa2258c9308d723f6cf198c' }))).rejects.toThrow('Android v1.0');
    await expect(findLocalAndroidApk(fetchJson({ ...manifest, originalMainSources: false }))).rejects.toThrow('Android v1.0');
  });
});
