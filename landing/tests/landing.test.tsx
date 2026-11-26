import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import App from '../src/App';
import { ANDROID_APK_ASSET, ANDROID_LATEST_APK_URL, ANDROID_RELEASE_API_URL, ANDROID_RELEASES_URL, findPublishedAndroidApk, resolveAndroidDownload } from '../src/download';

describe('Landing pública', () => {
  it('presenta inicio, guía y descarga Android sin rutas ni acciones de la web anterior', () => {
    const html = renderToStaticMarkup(<App />);

    expect(html).toContain('id="inicio"');
    expect(html).toContain('id="como-funciona"');
    expect(html).toContain('id="descargar"');
    expect(html).toContain('Encontrá tus medicamentos'); // La landing explica la propuesta.
    expect(html).toContain('Buscá tu medicamento');
    expect(html).toContain('Reservá y retiralo');
    expect(html).toContain('Android');
    expect(html).toContain('Ver QR y versiones');
    expect(html).toContain('aria-haspopup="dialog"');
    expect(html).not.toMatch(/href="\/(?:ingresar|registro|panel|catalogo|admin|mis-reservas)/);
    expect(html).not.toContain('type="password"');
    expect(html).not.toContain('/api/');
  });
});

describe('Destino de distribución Android', () => {
  it('no inventa un enlace de APK si todavía no hay una versión pública', () => {
    expect(resolveAndroidDownload(undefined)).toEqual({ href: ANDROID_RELEASES_URL, published: false });
    expect(resolveAndroidDownload('')).toEqual({ href: ANDROID_RELEASES_URL, published: false });
  });

  it('acepta un enlace HTTPS explícito de distribución', () => {
    expect(resolveAndroidDownload('  https://example.org/releases/app.apk  ')).toEqual({
      href: 'https://example.org/releases/app.apk',
      published: true,
    });
  });

  it('rechaza protocolos inseguros, URL relativas y credenciales en URL', () => {
    for (const url of ['javascript:alert(1)', 'http://example.org/app.apk', '//example.org/app.apk', '/app.apk', 'https://user:password@example.org/app.apk', 'no-es-una-url']) {
      expect(resolveAndroidDownload(url).published).toBe(false);
    }
  });
});


describe('Detección automática del APK firmado en GitHub Releases', () => {
  const release = (assets: unknown[], extra = {}) => ({
    draft: false, prerelease: false, assets, ...extra,
  });
  const asset = { name: ANDROID_APK_ASSET, size: 12400, state: 'uploaded' };
  const fetchJson = (body: unknown, status = 200) => ((async (url: string) => {
    expect(url).toBe(ANDROID_RELEASE_API_URL);
    return new Response(JSON.stringify(body), { status });
  }) as unknown as typeof fetch);

  it('resuelve el enlace PERMANENTE de descarga solo con APK en latest release', async () => {
    expect(await findPublishedAndroidApk(fetchJson(release([asset])))).toEqual({
      href: ANDROID_LATEST_APK_URL, published: true,
    });
    expect(ANDROID_LATEST_APK_URL).toMatch(/releases\/latest\/download\/MediLink-android\.apk$/);
  });

  it('no habilita descarga en releases vacías, borradores o prereleases', async () => {
    for (const data of [
      release([]), release([asset], { draft: true }), release([asset], { prerelease: true }),
      release([{ ...asset, size: 0 }]), release([{ ...asset, name: 'app-debug.apk' }]),
      release([{ ...asset, state: 'starter' }]),
    ]) {
      expect(await findPublishedAndroidApk(fetchJson(data))).toBeNull();
    }
  });

  it('ante 404, limitación API, JSON incorrecto o fallo de red conserva Releases', async () => {
    expect(await findPublishedAndroidApk(fetchJson({}, 404))).toBeNull();
    expect(await findPublishedAndroidApk(fetchJson({}, 403))).toBeNull();
    expect(await findPublishedAndroidApk(fetchJson({ assets: [asset] }))).toBeNull();
    expect(await findPublishedAndroidApk((async () => { throw new Error('sin red'); }) as typeof fetch)).toBeNull();
  });
});
