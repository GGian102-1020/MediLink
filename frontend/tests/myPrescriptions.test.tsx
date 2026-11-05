// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';
import { AuthProvider } from '../src/context/AuthContext';
import { emptyPage, setAccessToken } from '../src/lib/api';
import type { Prescripcion, Rol } from '../src/lib/types';

const baseLista = '/api/me/prescripciones';
const lista = `${baseLista}?page=0&size=10`;
const escaneo = '/api/me/recetas-demo/escaneo';
const manual = '/api/me/recetas-demo/vinculacion';
const analizar = 'Analizar y verificar receta con IA';
const receta: Prescripcion = {
  id: 42, usuarioId: 1, codigoExterno: 'MLD-DEMO', fechaEmision: '2026-11-01',
  fechaVencimiento: '2026-12-01', estado: 'ACTIVA',
  items: [{ id: 1, presentacionId: 9, descripcionPresentacion: '50 mcg · 30 comprimidos',
    cantidadAutorizada: 3, cantidadDisponible: 2, cantidadComprometida: 1, cantidadUtilizada: 0 }],
};
const pagina = (content: Prescripcion[]) => ({ ...emptyPage<Prescripcion>(), content,
  totalElements: content.length, totalPages: content.length ? 1 : 0 });
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { 'Content-Type': 'application/json' },
});
const fetchApi = vi.fn<(path: string, options?: RequestInit) => Promise<Response>>();
const createObjectURL = vi.fn(() => 'blob:receta-demo');
const revokeObjectURL = vi.fn();
let root: Root;
let contenedor: HTMLDivElement;
let rol: Rol | null;
let contenido: Prescripcion[];

function RutaActual() {
  return <output data-ruta>{useLocation().pathname}</output>;
}

beforeEach(() => {
  rol = 'USUARIO';
  contenido = [];
  contenedor = document.createElement('div');
  document.body.append(contenedor);
  root = createRoot(contenedor);
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = createObjectURL;
    static revokeObjectURL = revokeObjectURL;
  });
  vi.stubGlobal('fetch', fetchApi);
  fetchApi.mockImplementation(async path => {
    if (path === '/api/me') return json({ perfil: { rol, nombre: 'Ana', apellido: 'Demo' } });
    if (path === lista) return json(pagina(contenido));
    if (path === `${baseLista}/42`) return json(receta);
    if (path === '/api/presentaciones/9') return json({ id: 9, medicamentoNombre: 'Levotiroxina' });
    if (path.startsWith('/api/admin/') || path.startsWith('/api/medicamentos?')) return json(emptyPage());
    return json({ codigo: 'UNEXPECTED_REQUEST' }, 500);
  });
});

afterEach(async () => {
  await act(async () => root.unmount());
  contenedor.remove();
  setAccessToken(null);
  fetchApi.mockReset();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

async function abrir() {
  setAccessToken(rol ? 'jwt-de-prueba' : null);
  await act(async () => root.render(<MemoryRouter initialEntries={['/mis-recetas']}>
    <AuthProvider><RutaActual/><App/></AuthProvider>
  </MemoryRouter>));
}

function elemento<T extends Element>(selector: string) {
  const encontrado = contenedor.querySelector<T>(selector);
  if (!encontrado) throw new Error(`No se encontró ${selector}`);
  return encontrado;
}

function boton(texto: string) {
  const encontrado = Array.from(contenedor.querySelectorAll('button')).find(button => button.textContent?.trim() === texto);
  if (!encontrado) throw new Error(`No se encontró el botón ${texto}`);
  return encontrado;
}

async function elegir(archivo: File, selector = 'input[type=file]') {
  const input = elemento<HTMLInputElement>(selector);
  await act(async () => {
    Object.defineProperty(input, 'files', { configurable: true, value: [archivo] });
    input.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

async function ingresar(codigo: string) {
  const input = elemento<HTMLInputElement>('input[placeholder="MLD-…"]');
  const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  await act(async () => {
    setValue.call(input, codigo);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

const clic = (button: HTMLButtonElement) => act(async () => button.click());
const pedidos = (path: string) => fetchApi.mock.calls.filter(([url]) => url === path);
const texto = () => contenedor.textContent;

describe('Mis recetas — Prueba de IA', () => {
  it('permite entrar como USUARIO y carga solamente sus prescripciones con JWT', async () => {
    await abrir();
    expect(elemento('h1').textContent).toBe('Mis recetas — Prueba de IA');
    expect(elemento('nav a[href="/mis-recetas"]').textContent).toBe('Mis recetas');
    expect(texto()).toContain('Aún no tenés prescripciones');
    expect(pedidos(lista)).toHaveLength(1);
    expect(new Headers(pedidos(lista)[0][1]?.headers).get('Authorization')).toBe('Bearer jwt-de-prueba');
  });

  it.each(['OPERADOR', 'ADMINISTRADOR', null] as const)('restringe el acceso con rol %s', async perfil => {
    rol = perfil;
    await abrir();
    expect(elemento('[data-ruta]').textContent).toBe(perfil ? '/panel' : '/ingresar');
    expect(contenedor.querySelector('nav a[href="/mis-recetas"]')).toBeNull();
    expect(pedidos(lista)).toHaveLength(0);
  });

  it.each([
    ['receta.pdf', 'application/pdf', false], ['receta.jpg', 'image/jpeg', true],
    ['receta.png', 'image/png', true], ['receta.webp', '', true],
  ] as const)('admite %s y muestra vista previa para imágenes', async (nombre, type, imagen) => {
    await abrir();
    await elegir(new File(['demo'], nombre, { type }));
    expect(elemento('[role=status]').textContent).toContain(nombre);
    expect(Boolean(contenedor.querySelector('img[alt="Vista previa de la receta"]'))).toBe(imagen);
    expect(boton(analizar).disabled).toBe(false);
  });

  it.each([
    [() => new File(['demo'], 'receta.gif', { type: 'image/gif' }), 'Elegí un archivo PDF, JPG, PNG o WebP.'],
    [() => new File([new Uint8Array(6 * 1024 * 1024 + 1)], 'receta.pdf', { type: 'application/pdf' }), 'El archivo supera el máximo de 6 MiB.'],
    [() => new File([], 'receta.pdf', { type: 'application/pdf' }), 'El archivo está vacío.'],
  ] as const)('rechaza archivos inválidos y limpia la selección anterior (%s)', async (archivo, mensaje) => {
    await abrir();
    await elegir(new File(['demo'], 'receta.jpg', { type: 'image/jpeg' }));
    await elegir(archivo());
    expect(elemento('[role=alert]').textContent).toContain(mensaje);
    expect(contenedor.querySelector('img[alt="Vista previa de la receta"]')).toBeNull();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:receta-demo');
    expect(boton(analizar).disabled).toBe(true);
    expect(pedidos(escaneo)).toHaveLength(0);
  });

  it('acepta una foto de cámara y envía multipart con JWT una sola vez mientras analiza', async () => {
    await abrir();
    const archivo = new File(['demo'], 'foto.jpg', { type: 'image/jpeg' });
    await elegir(archivo, 'input[capture="environment"]');
    let responder!: (response: Response) => void;
    const respuesta = new Promise<Response>(resolve => { responder = resolve; });
    const original = fetchApi.getMockImplementation()!;
    fetchApi.mockImplementation((path, init) => path === escaneo ? respuesta : original(path, init));

    const form = boton(analizar).form!;
    await act(async () => {
      for (let intento = 0; intento < 2; intento++) {
        form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      }
    });
    expect(boton('Analizando…').disabled).toBe(true);
    expect(texto()).toContain('Analizando los datos y verificando la coherencia de la receta…');
    expect(pedidos(escaneo)).toHaveLength(1);
    const [, init] = pedidos(escaneo)[0];
    expect(init?.method).toBe('POST');
    expect(init?.body).toBeInstanceOf(FormData);
    expect((init?.body as FormData).get('archivo')).toBe(archivo);
    expect(new Headers(init?.headers).has('Content-Type')).toBe(false);
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer jwt-de-prueba');
    contenido = [receta];
    await act(async () => responder(json({ nueva: true, prescripcion: receta, codigo: 'RECETA_VERIFICADA' })));
    expect(texto()).toContain('Receta verificada y vinculada.');
    expect(pedidos(lista)).toHaveLength(2);
  });

  it('vincula por código, refresca la lista, abre el detalle y avisa si ya estaba vinculada', async () => {
    const original = fetchApi.getMockImplementation()!;
    fetchApi.mockImplementation(async (path, init) => {
      if (path !== manual) return original(path, init);
      const nueva = contenido.length === 0;
      contenido = [receta];
      return json({ nueva, prescripcion: receta });
    });
    await abrir();
    await clic(boton('Vincular receta'));
    expect(texto()).toContain('Ingresá el código de tu receta.');
    expect(pedidos(manual)).toHaveLength(0);
    await ingresar('  MLD-DEMO  ');
    await clic(boton('Vincular receta'));
    const [, init] = pedidos(manual)[0];
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({ codigoReceta: 'MLD-DEMO' });
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json');
    expect(texto()).toContain('Receta vinculada correctamente.');
    expect(texto()).not.toContain('Receta verificada y vinculada.');
    expect(pedidos(escaneo)).toHaveLength(0);
    expect(pedidos(lista)).toHaveLength(2);
    const resumen = elemento('.recetas-lista').textContent;
    expect(resumen).toContain('MLD-DEMO');
    expect(resumen).toContain('Levotiroxina');
    expect(resumen).toContain('Disponible: 2');
    expect(pedidos(`${baseLista}/42`)).toHaveLength(0);
    await clic(boton('Ver detalle'));
    expect(pedidos(`${baseLista}/42`)).toHaveLength(1);
    const detalle = elemento('.receta-detalle').textContent;
    for (const dato of ['01/11/2026', '01/12/2026', 'Activa', '50 mcg · 30 comprimidos', 'Autorizada: 3']) {
      expect(detalle).toContain(dato);
    }
    await clic(boton('Recargar recetas'));
    expect(pedidos(lista)).toHaveLength(3);
    await clic(boton('Vincular receta'));
    expect(texto()).toContain('Esta receta ya está vinculada a tu cuenta.');
    expect(contenedor.querySelectorAll('.recetas-lista > li')).toHaveLength(1);
  });

  it.each([
    [404, 'RESOURCE_NOT_FOUND', 'Puede no existir o haber sido vinculado a otra cuenta'],
    [409, 'RECETA_DEMO_NO_VIGENTE', 'vencida'],
    [409, 'RECETA_DEMO_DATOS_NO_COINCIDEN', 'no coinciden'],
    [409, 'RECETA_DEMO_CODIGO_VERIFICACION_INCORRECTO', 'El código secundario impreso no coincide'],
    [422, 'LECTURA_INCONCLUSA', 'No se pudo leer'],
    [429, 'ESCANEO_LIMITE', 'límite de escaneos'],
    [503, 'ESCANEO_NO_CONFIGURADO', 'no está habilitado'],
    [504, 'ESCANEO_TIMEOUT', 'tardó demasiado'],
    [502, 'ESCANEO_PROVEEDOR_ERROR', 'devolvió un error'],
    [413, 'ARCHIVO_DEMASIADO_GRANDE', 'tamaño admitido por el servidor'],
    [400, 'VALIDATION_ERROR', 'El PDF debe tener una sola página.'],
  ])('muestra el error %s %s y conserva el archivo para reintentar', async (status, codigo, mensaje) => {
    const original = fetchApi.getMockImplementation()!;
    fetchApi.mockImplementation((path, init) => path === escaneo
      ? Promise.resolve(json({ codigo, mensaje: 'El PDF debe tener una sola página.' }, status))
      : original(path, init));
    await abrir();
    await elegir(new File(['demo'], 'receta.pdf', { type: 'application/pdf' }));
    await clic(boton(analizar));
    expect(elemento('[role=alert]').textContent).toContain(mensaje);
    expect(boton(analizar).disabled).toBe(false);
    expect(contenedor.querySelector('.alert-success')).toBeNull();
    expect(pedidos(lista)).toHaveLength(1);
  });

  it.each([
    [true, 'RECETA_VERIFICADA_LEGADA', 'Receta de emisión anterior verificada y vinculada con sus datos originales.'],
    [false, 'RECETA_VERIFICADA', 'Esta receta ya está vinculada a tu cuenta.'],
  ] as const)('muestra el resultado de escaneo con nueva=%s y código %s', async (nueva, codigo, mensaje) => {
    const original = fetchApi.getMockImplementation()!;
    fetchApi.mockImplementation((path, init) => path === escaneo
      ? Promise.resolve(json({ nueva, prescripcion: receta, codigo })) : original(path, init));
    await abrir();
    await elegir(new File(['demo'], 'receta.pdf', { type: 'application/pdf' }));
    await clic(boton(analizar));
    expect(elemento('.alert-success').textContent).toContain(mensaje);
  });

  it('informa los fallos de red en el listado y en la vinculación manual', async () => {
    const original = fetchApi.getMockImplementation()!;
    fetchApi.mockImplementation((path, init) => path === lista || path === manual
      ? Promise.reject(new TypeError('Failed to fetch')) : original(path, init));
    await abrir();
    expect(elemento('[role=alert]').textContent).toContain('No se pudo conectar con MediLink.');
    await ingresar('MLD-DEMO');
    await clic(boton('Vincular receta'));
    expect(contenedor.querySelectorAll('[role=alert]')).toHaveLength(2);
    expect(elemento('.receta-resultado [role=alert]').textContent).toContain('No se pudo conectar con MediLink.');
  });

  it('mantiene visible la presentación si el catálogo público no tiene el medicamento', async () => {
    contenido = [receta];
    const original = fetchApi.getMockImplementation()!;
    fetchApi.mockImplementation((path, init) => path === '/api/presentaciones/9'
      ? Promise.resolve(json({ codigo: 'RESOURCE_NOT_FOUND' }, 404)) : original(path, init));
    await abrir();
    const resumen = elemento('.recetas-lista').textContent;
    expect(resumen).toContain('Medicamento no disponible en el catálogo');
    expect(resumen).toContain('50 mcg · 30 comprimidos');
  });
});
