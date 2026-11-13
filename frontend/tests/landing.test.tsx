import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import App from '../src/App';
import { ANDROID_RELEASES_URL, resolveAndroidDownload } from '../src/download';

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
