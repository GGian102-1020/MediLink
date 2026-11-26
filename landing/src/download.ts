/**
 * Sin backend propio: se consulta una vez la última release pública de GitHub.
 * El nombre del APK es fijo en todas las releases, por lo que el QR conserva
 * una URL permanente y no hace falta recompilar la landing tras cada versión.
 */
export const ANDROID_RELEASES_URL = 'https://github.com/GGian102-1020/MediLink/releases';
export const ANDROID_RELEASE_API_URL = 'https://api.github.com/repos/GGian102-1020/MediLink/releases/latest';
export const ANDROID_APK_ASSET = 'MediLink-android.apk';
export const ANDROID_LATEST_APK_URL = `${ANDROID_RELEASES_URL}/latest/download/${ANDROID_APK_ASSET}`;

export type AndroidDownload = { href: string; published: boolean; local?: boolean; version?: string; buildId?: string; sha256?: string };

/** Una URL manual permite distribuir también en Play Store u otro proveedor. */
export function resolveAndroidDownload(configuredUrl: string | undefined): AndroidDownload {
  const raw = configuredUrl?.trim();
  if (raw) {
    try {
      const url = new URL(raw);
      if (url.protocol === 'https:' && !url.username && !url.password &&
          !/^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(url.hostname) &&
          !url.hostname.endsWith('.local') && !url.hostname.includes(':')) {
        return { href: url.toString(), published: true };
      }
    } catch {
      // Configuración ausente o incorrecta: no habilitar falsas descargas.
    }
  }
  return { href: ANDROID_RELEASES_URL, published: false };
}

/** Only called by the explicitly selected localdemo build. Never falls back to GitHub. */
export async function findLocalAndroidApk(fetcher: typeof fetch = fetch): Promise<AndroidDownload> {
  const response = await fetcher('/local-build.json', { cache: 'no-store' });
  if (!response.ok) throw new Error('El APK local todavía no está disponible. Ejecutá Start.ps1.');
  const data = await response.json();
  if (data.sourceCommit !== 'da344f1643af3111404e42703dac6053c333aaa1' || data.originalMainSources !== true) {
    throw new Error('El APK local no corresponde a Android v1.0 verificado. Volvé a compilar.');
  }
  const url = new URL(data.href);
  const octets = url.hostname.split('.').map(Number);
  const privateIp = octets.length === 4 && octets.every(n => Number.isInteger(n) && n >= 0 && n <= 255) &&
    (octets[0] === 10 || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) ||
      (octets[0] === 192 && octets[1] === 168));
  if (url.protocol !== 'http:' || !privateIp || url.username || url.password || url.hash || url.search ||
      !/^\/downloads\/MediFind-[a-f0-9]{64}\.apk$/.test(url.pathname) ||
      typeof data.version !== 'string' || typeof data.buildId !== 'string' ||
      !/^[a-f0-9]{64}$/.test(data.sha256) || !url.pathname.includes(data.sha256)) {
    throw new Error('La información del APK local no es válida. Volvé a compilar.');
  }
  return { href: url.href, published: true, local: true, version: data.version, buildId: data.buildId, sha256: data.sha256 };
}

/**
 * Solo anunciamos "Descargar" si el API confirma un archivo con nombre y
 * tamaño válidos en la última release estable (ni borradores ni pre-releases).
 * Un 404, límite de peticiones, caída o CORS conserva el enlace a Releases.
 */
export async function findPublishedAndroidApk(
  fetcher: typeof fetch = fetch,
): Promise<AndroidDownload | null> {
  try {
    const response = await fetcher(ANDROID_RELEASE_API_URL, {
      headers: { Accept: 'application/vnd.github+json' },
    });
    if (!response.ok) return null;
    const release: unknown = await response.json();
    if (!release || typeof release !== 'object') return null;
    const data = release as { draft?: unknown; prerelease?: unknown; assets?: unknown };
    if (data.draft !== false || data.prerelease !== false || !Array.isArray(data.assets)) return null;
    const asset = data.assets.find((value: unknown) => {
      if (!value || typeof value !== 'object') return false;
      const file = value as { name?: unknown; size?: unknown; state?: unknown };
      return file.name === ANDROID_APK_ASSET && typeof file.size === 'number' && file.size > 0 && file.state === 'uploaded';
    });
    if (!asset) return null;
    return { href: ANDROID_LATEST_APK_URL, published: true };
  } catch {
    return null;
  }
}
