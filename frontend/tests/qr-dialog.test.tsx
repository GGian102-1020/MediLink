import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AndroidDownloadDialog } from '../src/components/AndroidDownloadDialog';
import { ANDROID_RELEASES_URL, resolveAndroidDownload } from '../src/download';

const renderDialog = (url?: string) => renderToStaticMarkup(
  <AndroidDownloadDialog download={resolveAndroidDownload(url)} onClose={() => {}} />,
);

describe('Ventana de descarga Android por QR', () => {
  it('el QR siempre apunta a Releases mientras no haya APK publicado', () => {
    const html = renderDialog();
    expect(html).toContain('role="dialog"');
    expect(html).toContain('aria-modal="true"');
    expect(html).toContain('<svg');
    expect(html).toContain('Código QR de GitHub Releases de MediLink');
    expect(html).toContain(`href="${ANDROID_RELEASES_URL}"`);
    expect(html).toContain('no pudimos confirmar un APK público disponible');
    expect(html).toContain('Este QR abre GitHub Releases');
    expect(html).toContain('aria-label="Cerrar código QR"');
    expect(html).toContain('Copiar enlace');
  });

  it('muestra la misma URL HTTPS configurada como enlace y destino del QR', () => {
    const url = 'https://example.org/releases/medilink-1.0.apk';
    const html = renderDialog(url);
    expect(html).toContain('Código QR del enlace de Android');
    expect(html).toContain(`href="${url}"`);
    expect(html).toContain('Descargar APK para Android');
    expect(html).not.toContain('todavía no tiene una versión pública');
  });

  it('URLs inseguras conservan el QR de Releases como destino de respaldo', () => {
    const html = renderDialog('javascript:alert(1)');
    expect(html).toContain(`href="${ANDROID_RELEASES_URL}"`);
    expect(html).not.toContain('javascript:');
  });
});
