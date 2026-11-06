import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Alert, Badge, Button, Empty, ErrorState, Field, Loading, PageTitle, Pagination, Panel } from '../components/UI';
import { dateOnly } from '../lib/format';
import { formatosReceta, mensajeVinculacion, nombresMedicamentos, recetasPropiasApi, validarArchivoReceta } from '../lib/recetas';
import type { VinculacionReceta } from '../lib/recetas';
import type { Prescripcion } from '../lib/types';
import { useResource } from '../lib/useResource';

type Accion = 'escaneo' | 'codigo';
type Resultado = { error: string } | VinculacionReceta;

function mensajeExito(resultado: VinculacionReceta) {
  if (!resultado.nueva) return 'Esta receta ya está vinculada a tu cuenta.';
  if (resultado.codigo === 'RECETA_VERIFICADA') return 'Receta verificada y vinculada.';
  if (resultado.codigo === 'RECETA_VERIFICADA_LEGADA') return 'Receta de emisión anterior verificada y vinculada con sus datos originales.';
  return 'Receta vinculada correctamente.';
}

function DatosReceta({ receta, nombres, detalle = false, reservar = true }: {
  receta: Prescripcion;
  nombres?: ReadonlyMap<number, string> | null;
  detalle?: boolean;
  reservar?: boolean;
}) {
  return <>
    <dl className="receta-ficha">
      <div><dt>Código</dt><dd>{receta.codigoExterno}</dd></div>
      {detalle && <div><dt>Emisión</dt><dd>{dateOnly(receta.fechaEmision)}</dd></div>}
      <div><dt>Vencimiento</dt><dd>{dateOnly(receta.fechaVencimiento)}</dd></div>
      <div><dt>Estado</dt><dd><Badge value={receta.estado}/></dd></div>
    </dl>
    <ul className="receta-items">
      {receta.items.map(item => <li key={item.id}>
        <strong>{nombres?.get(item.presentacionId) ?? (nombres ? 'Medicamento no disponible en el catálogo' : 'Cargando medicamento…')}</strong>
        <span>{item.descripcionPresentacion}</span>
        <span>{detalle && `Autorizada: ${item.cantidadAutorizada} · `}Disponible: {item.cantidadDisponible}</span>
        {reservar && receta.estado === 'ACTIVA' && item.cantidadDisponible > 0 && <Link className="btn btn-primary receta-reservar"
          to={`/presentaciones/${item.presentacionId}`} state={{ codigoReceta: receta.codigoExterno }}>
          Reservar ahora <ArrowRight size={16}/>
        </Link>}
      </li>)}
    </ul>
  </>;
}

function RecetaVinculada({ receta }: { receta: Prescripcion }) {
  const nombres = useResource(() => nombresMedicamentos([receta]), [receta]);
  return <DatosReceta receta={receta} nombres={nombres.data} detalle/>;
}

function DetalleReceta({ id }: { id: number }) {
  const detalle = useResource(async () => {
    const receta = await recetasPropiasApi.detalle(id);
    return { receta, nombres: await nombresMedicamentos([receta]) };
  }, [id]);

  if (detalle.loading) return <Loading text="Cargando detalle de la receta…"/>;
  if (detalle.error) return <ErrorState error={detalle.error} retry={detalle.reload}/>;
  if (!detalle.data) return null;
  return <div className="receta-detalle">
    <h3>Detalle de la receta</h3>
    <DatosReceta {...detalle.data} detalle reservar={false}/>
  </div>;
}

export function MyPrescriptions() {
  const [archivo, setArchivo] = useState<{ file: File; imagen: boolean } | null>(null);
  const [errorArchivo, setErrorArchivo] = useState('');
  const [vistaPrevia, setVistaPrevia] = useState<string | null>(null);
  const [codigo, setCodigo] = useState('');
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const [accion, setAccion] = useState<Accion | null>(null);
  const enviando = useRef(false);
  const [pagina, setPagina] = useState(0);
  const [seleccion, setSeleccion] = useState<number | null>(null);
  const recetas = useResource(async () => {
    const lista = await recetasPropiasApi.listar(pagina);
    return { lista, nombres: await nombresMedicamentos(lista.content) };
  }, [pagina]);

  useEffect(() => {
    if (!archivo?.imagen) {
      setVistaPrevia(null);
      return;
    }
    const url = URL.createObjectURL(archivo.file);
    setVistaPrevia(url);
    return () => URL.revokeObjectURL(url);
  }, [archivo]);

  function elegirArchivo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!file) return;
    const validacion = validarArchivoReceta(file);
    setResultado(null);
    setErrorArchivo(validacion.error ?? '');
    setArchivo(validacion.tipo ? { file, imagen: validacion.tipo.startsWith('image/') } : null);
  }

  async function vincular(event: FormEvent<HTMLFormElement>, tipo: Accion) {
    event.preventDefault();
    if (enviando.current) return;
    const codigoIngresado = codigo.trim();
    if (tipo === 'codigo' && !codigoIngresado) {
      setResultado({ error: 'Ingresá el código de tu receta.' });
      return;
    }
    const file = archivo?.file;
    if (tipo === 'escaneo' && !file) return;

    enviando.current = true;
    setAccion(tipo);
    setResultado(null);
    try {
      const vinculacion = tipo === 'escaneo' && file
        ? await recetasPropiasApi.escanear(file)
        : await recetasPropiasApi.vincular(codigoIngresado);
      setResultado(vinculacion);
      cambiarPagina(0);
      recetas.reload();
    } catch (error) {
      setResultado({ error: mensajeVinculacion(error) });
    } finally {
      enviando.current = false;
      setAccion(null);
    }
  }

  function cambiarPagina(nuevaPagina: number) {
    setSeleccion(null);
    setPagina(nuevaPagina);
  }

  const ocupado = accion !== null;
  return <>
    <PageTitle title="Mis recetas — Prueba de IA" eyebrow="MEDILINK / MIS RECETAS"
      subtitle="Vinculá una receta ficticia de demostración y consultá tus prescripciones."/>
    <div className="recetas-formularios">
      <Panel title="Escaneá una receta" subtitle="Usá exclusivamente recetas ficticias de MediLink. El servicio de IA lee el documento y MediLink compara los datos visibles con su registro.">
        <form className="receta-formulario" onSubmit={event => void vincular(event, 'escaneo')} aria-busy={accion === 'escaneo'}>
          <Field label="Elegir archivo" hint="PDF de una página, JPG, PNG o WebP. Máximo 6 MiB.">
            <input type="file" accept={formatosReceta} onChange={elegirArchivo} disabled={ocupado}/>
          </Field>
          <Field label="Tomar una foto" hint="Usa la cámara cuando el navegador lo permite.">
            <input type="file" accept="image/*" capture="environment" onChange={elegirArchivo} disabled={ocupado}/>
          </Field>
          {errorArchivo && <Alert>{errorArchivo}</Alert>}
          {archivo && <p className="receta-archivo" role="status">Archivo seleccionado: <strong>{archivo.file.name}</strong></p>}
          {vistaPrevia && <img className="receta-vista-previa" src={vistaPrevia} alt="Vista previa de la receta"/>}
          <Button type="submit" disabled={!archivo || ocupado} busy={accion === 'escaneo'}>
            {accion === 'escaneo' ? 'Analizando…' : 'Analizar y verificar receta con IA'}
          </Button>
        </form>
        {accion === 'escaneo' && <p className="section-gap" role="status">
          Analizando los datos y verificando la coherencia de la receta…
        </p>}
      </Panel>
      <Panel title="¿Tenés el código de tu receta?" subtitle="El ingreso manual comprueba el código principal y las reglas de vinculación de MediLink. Para comparar el contenido del documento, usá el escaneo.">
        <form className="receta-formulario" onSubmit={event => void vincular(event, 'codigo')} aria-busy={accion === 'codigo'}>
          <Field label="Código de receta">
            <input value={codigo} onChange={event => setCodigo(event.target.value)} placeholder="MLD-…" autoCapitalize="characters"
              autoComplete="off" spellCheck={false} disabled={ocupado}/>
          </Field>
          <Button type="submit" variant="secondary" disabled={ocupado} busy={accion === 'codigo'}>
            {accion === 'codigo' ? 'Vinculando receta…' : 'Vincular receta'}
          </Button>
        </form>
      </Panel>
    </div>
    {resultado && ('error' in resultado
      ? <div className="receta-resultado"><Alert>{resultado.error}</Alert></div>
      : <Panel title="Resultado de la vinculación" className="receta-resultado">
        <Alert tone="success">{mensajeExito(resultado)}</Alert>
        <RecetaVinculada key={resultado.prescripcion.id} receta={resultado.prescripcion}/>
      </Panel>)}
    <Panel title="Tus prescripciones" action={<Button type="button" variant="secondary" disabled={recetas.loading} onClick={recetas.reload}>
      Recargar recetas
    </Button>}>
      {recetas.loading && <Loading text="Cargando tus prescripciones…"/>}
      {recetas.error && <ErrorState error={recetas.error}/>}
      {!recetas.loading && recetas.data && (recetas.data.lista.content.length
        ? <ul className="recetas-lista">
          {recetas.data.lista.content.map(receta => {
            const abierta = seleccion === receta.id;
            const accionDetalle = abierta ? 'Cerrar detalle' : 'Ver detalle';
            return <li key={receta.id}>
              <DatosReceta receta={receta} nombres={recetas.data?.nombres}/>
              <Button type="button" variant="ghost" aria-expanded={abierta} aria-label={`${accionDetalle} de ${receta.codigoExterno}`}
                onClick={() => setSeleccion(abierta ? null : receta.id)}>
                {accionDetalle}
              </Button>
              {abierta && <DetalleReceta id={receta.id}/>}
            </li>;
          })}
        </ul>
        : <Empty title="Aún no tenés prescripciones" detail="Cuando vincules una receta aparecerá acá."/>)}
      {!recetas.loading && recetas.data && <Pagination data={recetas.data.lista} onChange={cambiarPagina}/>}
    </Panel>
  </>;
}
