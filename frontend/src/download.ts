/**
 * La landing no distribuye APKs de debug: usa una URL HTTPS de distribución
 * configurada en el build. Hasta que exista, lleva a Releases sin simular
 * que ya hay una descarga pública disponible.
 */
export const ANDROID_RELEASES_URL = 'https://github.com/GGian102-1020/MediLink/releases';

export type AndroidDownload = { href: string; published: boolean };

export function resolveAndroidDownload(configuredUrl: string | undefined): AndroidDownload {
  const rawUrl = configuredUrl?.trim();
  if (rawUrl) {
    try {
      const url = new URL(rawUrl);
      if (url.protocol === 'https:' && !url.username && !url.password) {
        return { href: url.toString(), published: true };
      }
    } catch {
      // Una URL inválida no debe romper la landing ni generar enlaces inseguros.
    }
  }
  return { href: ANDROID_RELEASES_URL, published: false };
}
