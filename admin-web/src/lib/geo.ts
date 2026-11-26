/**
 * Utilidades de mapa: ubicacion del navegador, busqueda de direcciones (Nominatim)
 * y rutas (OSRM). Todo usa servicios publicos de OpenStreetMap, sin API key.
 *
 * Privacidad: las coordenadas se redondean a 4 decimales (~11 m) antes de salir
 * del navegador y solo se recuerdan en sessionStorage de esta pestaña.
 */

export type LatLng = { lat: number; lng: number };
export type Origen = LatLng & { etiqueta: string; fuente: 'gps' | 'direccion' };
export type ModoViaje = 'pie' | 'auto';
export type PasoRuta = { texto: string; distanciaM: number };
export type Ruta = { puntos: [number, number][]; distanciaM: number; duracionS: number; pasos: PasoRuta[] };
export type ResultadoDireccion = LatLng & { etiqueta: string };
/** Encuadre pedido al mapa: cambia `clave` para que el mapa se mueva una sola vez. */
export type Encuadre = { clave: string; puntos: [number, number][] };

export function esCoordenadaValida(p: LatLng): boolean {
  return Number.isFinite(p.lat) && Number.isFinite(p.lng) && p.lat >= -90 && p.lat <= 90 && p.lng >= -180 && p.lng <= 180;
}
export function coords(f: { latitud?: number | null; longitud?: number | null }): LatLng | null {
  if (f.latitud == null || f.longitud == null) return null;
  const p = { lat: Number(f.latitud), lng: Number(f.longitud) };
  return esCoordenadaValida(p) ? p : null;
}

/** Centro aproximado de CABA y caja para acotar busquedas. */
export const CABA_CENTRO: LatLng = { lat: -34.6118, lng: -58.4173 };
const CABA_VIEWBOX = '-58.5315,-34.5265,-58.3351,-34.7051';

const NOMINATIM = 'https://nominatim.openstreetmap.org/search';
const OSRM: Record<ModoViaje, string> = {
  auto: 'https://router.project-osrm.org/route/v1/driving',
  pie: 'https://routing.openstreetmap.de/routed-foot/route/v1/driving',
};
const STORAGE_KEY = 'medlink.ubicacion';
// Limite por pestaña. El servicio público impone además un límite GLOBAL a cada aplicación:
// esta demo local no debe exponerse a tráfico concurrente sin geocodificador propio/proveedor.
const CACHE_MS = 5 * 60 * 1000;
let proximaConsulta = 0;
const cacheDirecciones = new Map<string, { hasta: number; resultados: ResultadoDireccion[] }>();

async function turnoNominatim(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException('Solicitud cancelada', 'AbortError');
  const espera = Math.max(0, proximaConsulta - Date.now());
  proximaConsulta = Date.now() + espera + 1100;
  if (!espera) return;
  await new Promise<void>((resolve, reject) => {
    const t = setTimeout(() => { signal?.removeEventListener('abort', cancel); resolve(); }, espera);
    function cancel() { clearTimeout(t); reject(new DOMException('Solicitud cancelada', 'AbortError')); }
    signal?.addEventListener('abort', cancel, { once: true });
  });
}

/** Intenta usar la división administrativa estructurada, no solo el texto libre. */
export function estaEnCaba(d: { address?: Record<string, string>; display_name: string }): boolean {
  const estado = d.address?.state || d.address?.province || '';
  if (/ciudad aut[oó]noma de buenos aires|autonomous city of buenos aires/i.test(estado)) return true;
  if (estado) return false;
  return /ciudad aut[oó]noma de buenos aires/i.test(d.display_name);
}

export function redondear(p: LatLng): LatLng {
  return { lat: Math.round(p.lat * 1e4) / 1e4, lng: Math.round(p.lng * 1e4) / 1e4 };
}

export function leerOrigenGuardado(): Origen | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const o = JSON.parse(raw) as Origen;
    return esCoordenadaValida(o) && typeof o.etiqueta === 'string' && (o.fuente === 'gps' || o.fuente === 'direccion') ? o : null;
  } catch { return null; }
}
export function guardarOrigen(o: Origen | null) {
  try {
    if (o) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(o));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch { /* storage bloqueado: solo se pierde el recordatorio */ }
}

export function ubicacionActual(): Promise<LatLng> {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) { reject(new Error('Tu navegador no permite compartir la ubicación.')); return; }
    navigator.geolocation.getCurrentPosition(
      pos => resolve(redondear({ lat: pos.coords.latitude, lng: pos.coords.longitude })),
      err => reject(new Error(err.code === err.PERMISSION_DENIED
        ? 'No diste permiso para usar tu ubicación. Podés buscar una dirección.'
        : 'No pudimos obtener tu ubicación. Probá de nuevo o buscá una dirección.')),
      { enableHighAccuracy: false, timeout: 12000, maximumAge: 60000 },
    );
  });
}

/** Nominatim: solo al enviar el formulario; nunca autocompletado. */
export async function buscarDireccion(texto: string, signal?: AbortSignal): Promise<ResultadoDireccion[]> {
  const clave = texto.trim().toLocaleLowerCase('es-AR');
  if (clave.length < 3) return [];
  if (signal?.aborted) throw new DOMException('Solicitud cancelada', 'AbortError');
  const previo = cacheDirecciones.get(clave);
  if (previo && previo.hasta > Date.now()) return previo.resultados;
  const params = new URLSearchParams({
    format: 'jsonv2', addressdetails: '1', q: texto.trim(), countrycodes: 'ar',
    viewbox: CABA_VIEWBOX, bounded: '1', limit: '5', 'accept-language': 'es',
  });
  await turnoNominatim(signal);
  let res: Response;
  try { res = await fetch(`${NOMINATIM}?${params}`, { signal, headers: { Accept: 'application/json' } }); }
  catch (e) { if ((e as Error).name === 'AbortError') throw e; throw new Error('No se pudo conectar con el buscador de direcciones.'); }
  if (!res.ok) throw new Error('El buscador de direcciones no respondió. Probá en unos segundos.');
  const data = await res.json() as { lat: string; lon: string; display_name: string; address?: Record<string, string> }[];
  if (signal?.aborted) throw new DOMException('Solicitud cancelada', 'AbortError');
  const vistos = new Set<string>();
  const resultados = data.filter(estaEnCaba)
    .map(d => ({ ...redondear({ lat: Number(d.lat), lng: Number(d.lon) }), etiqueta: acortarDireccion(d.display_name) }))
    .filter(r => esCoordenadaValida(r) && !vistos.has(`${r.lat},${r.lng}`) && Boolean(vistos.add(`${r.lat},${r.lng}`)));
  if (cacheDirecciones.size >= 50) cacheDirecciones.delete(cacheDirecciones.keys().next().value!);
  cacheDirecciones.set(clave, { hasta: Date.now() + CACHE_MS, resultados });
  return resultados;
}

/**
 * Nominatim devuelve "Tribeca, 2000, Avenida Cabildo, Belgrano, Buenos Aires, Comuna 13, ...":
 * se arma "Avenida Cabildo 2000, Belgrano" (calle + altura + barrio).
 */
export function acortarDireccion(nombre: string): string {
  const partes = nombre.split(',').map(p => p.trim()).filter(p => p && !/^(Argentina|Comuna \d+|[A-Z]\d{4}[A-Z]{0,3}|Ciudad Autónoma de Buenos Aires|Buenos Aires)$/i.test(p));
  const altura = partes.findIndex(p => /^\d+[a-z]?$/i.test(p));
  if (altura >= 0 && altura + 1 < partes.length) {
    const barrio = partes[altura + 2];
    return `${partes[altura + 1]} ${partes[altura]}${barrio ? `, ${barrio}` : ''}`;
  }
  return partes.slice(0, 2).join(', ') || nombre;
}

export async function calcularRuta(desde: LatLng, hasta: LatLng, modo: ModoViaje, signal?: AbortSignal): Promise<Ruta> {
  const a = redondear(desde);
  const url = `${OSRM[modo]}/${a.lng},${a.lat};${hasta.lng},${hasta.lat}?overview=full&geometries=polyline&steps=true`;
  let res: Response;
  try { res = await fetch(url, { signal }); }
  catch (e) { if ((e as Error).name === 'AbortError') throw e; throw new Error('No se pudo calcular el recorrido.'); }
  if (!res.ok) throw new Error('El servicio de recorridos no respondió.');
  const data = await res.json() as OsrmRespuesta;
  const ruta = data.routes?.[0];
  if (data.code !== 'Ok' || !ruta) throw new Error('No encontramos un recorrido hasta esa farmacia.');
  const pasos = ruta.legs.flatMap(l => l.steps).map(s => ({ texto: textoPaso(s), distanciaM: s.distance }));
  return { puntos: decodificarPolyline(ruta.geometry), distanciaM: ruta.distance, duracionS: ruta.duration, pasos };
}

type OsrmPaso = { distance: number; name: string; maneuver: { type: string; modifier?: string; exit?: number } };
type OsrmRespuesta = { code: string; routes?: { geometry: string; distance: number; duration: number; legs: { steps: OsrmPaso[] }[] }[] };

/** Decodifica una polyline de Google/OSRM (precision 5) a pares [lat, lng]. */
export function decodificarPolyline(texto: string, precision = 5): [number, number][] {
  const factor = 10 ** precision;
  const puntos: [number, number][] = [];
  let i = 0, lat = 0, lng = 0;
  while (i < texto.length) {
    for (const eje of [0, 1]) {
      let resultado = 0, shift = 0, b: number;
      do { b = texto.charCodeAt(i++) - 63; resultado |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
      const delta = resultado & 1 ? ~(resultado >> 1) : resultado >> 1;
      if (eje === 0) lat += delta; else lng += delta;
    }
    puntos.push([lat / factor, lng / factor]);
  }
  return puntos;
}

const MODIFICADOR: Record<string, string> = {
  left: 'a la izquierda', right: 'a la derecha', 'slight left': 'levemente a la izquierda', 'slight right': 'levemente a la derecha',
  'sharp left': 'bien cerrado a la izquierda', 'sharp right': 'bien cerrado a la derecha', straight: 'derecho', uturn: 'en U',
};

export function textoPaso(s: OsrmPaso): string {
  const { type, modifier, exit } = s.maneuver;
  const mod = modifier ? MODIFICADOR[modifier] ?? '' : '';
  const calle = s.name ? ` por ${s.name}` : '';
  switch (type) {
    case 'depart': return s.name ? `Salí por ${s.name}` : 'Comenzá el recorrido';
    case 'arrive': return 'Llegaste a la farmacia';
    case 'turn': case 'on ramp': case 'off ramp':
      return modifier === 'straight' ? `Seguí derecho${calle}` : `Girá ${mod}${s.name ? ` en ${s.name}` : ''}`.trim();
    case 'end of road': return `Al final de la calle, girá ${mod}${calle}`.trim();
    case 'fork': return `En la bifurcación, mantenete ${mod}${calle}`.trim();
    case 'merge': return `Incorporate ${mod}${calle}`.trim();
    case 'roundabout': case 'rotary': case 'roundabout turn':
      return exit ? `En la rotonda, tomá la ${exit}ª salida${calle}` : `Pasá la rotonda${calle}`;
    case 'continue': case 'new name':
      return mod && modifier !== 'straight' ? `Seguí ${mod}${calle}` : `Seguí derecho${calle}`;
    default: return `Seguí${calle || ' el recorrido'}`;
  }
}

export function formatoDistancia(metros: number): string {
  if (metros <= 0) return '0 m';
  if (metros < 10) return `${Math.round(metros)} m`;
  if (metros < 1000) return `${Math.round(metros / 10) * 10} m`;
  return `${(metros / 1000).toLocaleString('es-AR', { maximumFractionDigits: metros < 10000 ? 1 : 0 })} km`;
}
export function formatoDuracion(segundos: number): string {
  const min = Math.max(1, Math.round(segundos / 60));
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60), resto = min % 60;
  return resto ? `${h} h ${resto} min` : `${h} h`;
}
export function enlaceComoLlegar(destino: LatLng, modo: ModoViaje): string {
  const params = new URLSearchParams({ api: '1', destination: `${destino.lat},${destino.lng}`, travelmode: modo === 'pie' ? 'walking' : 'driving' });
  return `https://www.google.com/maps/dir/?${params}`;
}
