import { api, ApiError, get, pageQuery, post } from './api';
import type { Page, Presentacion, Prescripcion } from './types';

export type VinculacionReceta = {
  nueva: boolean;
  prescripcion: Prescripcion;
  codigo?: 'RECETA_VERIFICADA' | 'RECETA_VERIFICADA_LEGADA';
};

const tiposArchivo: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
};
const tiposAdmitidos = Object.values(tiposArchivo);
const limiteArchivo = 6 * 1024 * 1024;

export const formatosReceta = '.pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp';

export function validarArchivoReceta(archivo: File) {
  const extension = archivo.name.split('.').pop()?.toLowerCase() ?? '';
  const tipo = archivo.type && archivo.type !== 'application/octet-stream' ? archivo.type : tiposArchivo[extension];
  if (!tiposAdmitidos.includes(tipo)) return { error: 'Elegí un archivo PDF, JPG, PNG o WebP.' };
  if (archivo.size === 0) return { error: 'El archivo está vacío.' };
  if (archivo.size > limiteArchivo) return { error: 'El archivo supera el máximo de 6 MiB.' };
  return { tipo };
}

const mensajesReceta: Record<string, string> = {
  RESOURCE_NOT_FOUND: 'El código no está disponible. Puede no existir o haber sido vinculado a otra cuenta.',
  RECETA_DEMO_NO_VIGENTE: 'La receta está vencida o no está vigente.',
  RECETA_DEMO_DATOS_NO_COINCIDEN: 'Los datos leídos no coinciden con la receta registrada.',
  RECETA_DEMO_CODIGO_VERIFICACION_INCORRECTO: 'El código secundario impreso no coincide con el registrado.',
  LECTURA_INCONCLUSA: 'No se pudo leer la receta completa. Probá con un documento más nítido o ingresá el código.',
  ARCHIVO_DEMASIADO_GRANDE: 'El archivo supera el tamaño admitido por el servidor.',
  ESCANEO_LIMITE: 'Se alcanzó el límite de escaneos. Esperá unos minutos o ingresá el código.',
  ESCANEO_NO_CONFIGURADO: 'El servicio de IA no está habilitado. Podés vincular la receta por código.',
  ESCANEO_PROVEEDOR_ERROR: 'El servicio de IA devolvió un error. Podés reintentar o ingresar el código.',
  ESCANEO_TIMEOUT: 'El servicio de IA tardó demasiado. Podés reintentar o ingresar el código.',
};

export function mensajeVinculacion(error: unknown) {
  if (!(error instanceof ApiError)) return 'Ocurrió un error al vincular la receta. Volvé a intentarlo.';
  return mensajesReceta[error.codigo] ?? error.message;
}

export const recetasPropiasApi = {
  listar: (page: number) => get<Page<Prescripcion>>('/api/me/prescripciones', pageQuery(page)),
  detalle: (id: number) => get<Prescripcion>(`/api/me/prescripciones/${id}`),
  vincular: (codigoReceta: string) => post<VinculacionReceta>('/api/me/recetas-demo/vinculacion', { codigoReceta }),
  escanear: (archivo: File) => {
    const body = new FormData();
    body.append('archivo', archivo);
    return api<VinculacionReceta>('/api/me/recetas-demo/escaneo', { method: 'POST', body });
  },
};

/**
 * Recetas propias que hoy pueden usarse para reservar esta presentación:
 * activas, no vencidas y con unidades disponibles. La más reciente primero.
 */
export async function recetasUsablesPara(presentacionId: number, hoy = new Date()): Promise<Prescripcion[]> {
  const fechaHoy = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
  const recetas: Prescripcion[] = [];
  for (let page = 0; page < 5; page++) {
    const pagina = await get<Page<Prescripcion>>('/api/me/prescripciones', { page, size: 100 });
    recetas.push(...pagina.content);
    if (pagina.last) break;
  }
  return recetas
    .filter(r => r.estado === 'ACTIVA' && r.fechaVencimiento >= fechaHoy
      && r.items.some(i => i.presentacionId === presentacionId && i.cantidadDisponible > 0))
    .sort((a, b) => b.fechaEmision.localeCompare(a.fechaEmision) || b.id - a.id);
}

export async function nombresMedicamentos(recetas: Prescripcion[]) {
  const ids = new Set(recetas.flatMap(receta => receta.items.map(item => item.presentacionId)));
  const resultados = await Promise.allSettled(Array.from(ids, id => get<Presentacion>(`/api/presentaciones/${id}`)));
  const nombres = new Map<number, string>();
  for (const resultado of resultados) {
    if (resultado.status === 'fulfilled') nombres.set(resultado.value.id, resultado.value.medicamentoNombre);
  }
  return nombres;
}
