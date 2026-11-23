import { ArrowLeft, Lock, MessageCircle, RefreshCcw, Search, Send } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Empty, ErrorState, IconButton, PageHeader, Panel, SkeletonList } from '../components/UI';
import { useAuth } from '../context/AuthContext';
import { get, post } from '../lib/api';
import { ago, dateTime, initials, shortDateTime } from '../lib/format';
import { useLive } from '../lib/live';
import type { ConversacionFarmacia, MensajeChat } from '../lib/types';

/** Polling: la conversación abierta se refresca más seguido que la lista. */
const POLL_MENSAJES_MS = 4_000;
const POLL_LISTA_MS = 10_000;
const MAX_TEXTO = 1000;

const nombreCompleto = (c: Pick<ConversacionFarmacia, 'nombre' | 'apellido'>) => `${c.nombre} ${c.apellido}`.trim();
/** La conversación se nombra por el medicamento de su reserva; el paciente y el código van debajo. */
const tituloConversacion = (c: ConversacionFarmacia) => c.medicamento || nombreCompleto(c);
const detalleConversacion = (c: ConversacionFarmacia) => [nombreCompleto(c), c.codigoReserva && `Reserva ${c.codigoReserva}`].filter(Boolean).join(' · ');

/**
 * Chat de la farmacia con sus pacientes. Solo aparecen pacientes con al menos una reserva en
 * esta farmacia (regla del backend); el paciente responde desde la app Android.
 */
export function ChatPage() {
  const { me } = useAuth(); const farmId = me?.perfil.farmaciaId;
  const { refreshChat } = useLive();
  const [params, setParams] = useSearchParams();
  const seleccionado = Number(params.get('paciente')) || null;
  const [q, setQ] = useState('');
  const [lista, setLista] = useState<ConversacionFarmacia[] | null>(null);
  const [errorLista, setErrorLista] = useState<string | null>(null);
  const [cargandoLista, setCargandoLista] = useState(false);

  const cargarLista = useCallback(async () => {
    if (!farmId) return;
    setCargandoLista(true);
    try { setLista(await get<ConversacionFarmacia[]>(`/api/farmacias/${farmId}/chats`, { q: q.trim() || undefined })); setErrorLista(null); }
    catch (e) { setErrorLista(e instanceof Error ? e.message : 'No se pudieron cargar las conversaciones.'); }
    finally { setCargandoLista(false); }
  }, [farmId, q]);

  useEffect(() => {
    const t = window.setTimeout(() => { void cargarLista(); }, q ? 300 : 0);
    const timer = window.setInterval(() => { if (!document.hidden) void cargarLista(); }, POLL_LISTA_MS);
    return () => { window.clearTimeout(t); window.clearInterval(timer); };
  }, [cargarLista, q]);

  const abrir = (usuarioId: number | null) => setParams(usuarioId ? { paciente: String(usuarioId) } : {}, { replace: true });
  const actual = lista?.find(c => c.usuarioId === seleccionado) || null;

  // Al leer una conversación se marcan sus mensajes: actualizar contadores (lista y barra lateral).
  const alLeer = useCallback(() => {
    setLista(prev => prev?.map(c => c.usuarioId === seleccionado ? { ...c, noLeidos: 0 } : c) ?? prev);
    refreshChat();
  }, [seleccionado, refreshChat]);

  return <>
    <PageHeader title="Chat" subtitle="Hablá con los pacientes que reservaron en tu farmacia. Responden desde la app MediFarm."
      actions={<IconButton label="Actualizar" bordered onClick={() => void cargarLista()}><RefreshCcw size={16} className={cargandoLista ? 'spin' : ''}/></IconButton>}/>
    <Panel className="chat-panel">
      <div className={`chat ${seleccionado ? 'chat-abierto' : ''}`}>
        <aside className="chat-lista" aria-label="Conversaciones">
          <label className="chat-buscar"><Search size={15}/>
            <input value={q} onChange={e => setQ(e.target.value)} placeholder="Buscar paciente" aria-label="Buscar paciente" autoComplete="off"/>
          </label>
          <div className="chat-items">
            {lista === null && !errorLista ? <SkeletonList rows={5}/>
              : errorLista && !lista ? <ErrorState error={errorLista} retry={() => void cargarLista()}/>
              : lista!.length === 0 ? <Empty compact icon={<MessageCircle size={20}/>} title={q ? 'Sin coincidencias' : 'Todavía no hay pacientes'} detail={q ? 'Probá con otro nombre.' : 'Cuando un paciente reserve en tu farmacia vas a poder escribirle desde acá.'}/>
              : lista!.map(c => <button key={c.usuarioId} className={`chat-item ${c.usuarioId === seleccionado ? 'active' : ''}`} onClick={() => abrir(c.usuarioId)} aria-current={c.usuarioId === seleccionado}>
                  <span className="avatar">{initials(c.nombre, c.apellido)}</span>
                  <span className="chat-item-body">
                    <span className="chat-item-top"><strong className="truncate">{tituloConversacion(c)}</strong>{c.ultimaActividad && <small>{ago(c.ultimaActividad)}</small>}</span>
                    <small className="truncate muted">{detalleConversacion(c)}{!c.abierta && ' · Cerrada'}</small>
                    <span className="chat-item-bottom">
                      <span className={`truncate ${c.noLeidos ? 'unread' : ''}`}>{c.ultimoMensaje ? `${c.ultimoAutor === 'FARMACIA' ? 'Vos: ' : ''}${c.ultimoMensaje}` : <em>Sin mensajes · tiene reservas</em>}</span>
                      {c.noLeidos > 0 && <span className="chat-badge" aria-label={`${c.noLeidos} sin leer`}>{c.noLeidos}</span>}
                    </span>
                  </span>
                </button>)}
          </div>
        </aside>
        <section className="chat-hilo">
          {seleccionado && farmId
            ? <Conversacion key={seleccionado} farmId={farmId} usuarioId={seleccionado} titulo={actual ? tituloConversacion(actual) : 'Paciente'} detalle={actual ? detalleConversacion(actual) : 'Paciente'} abierta={actual?.abierta ?? true} borrado={actual?.fechaHoraBorrado ?? null} iniciales={actual ? initials(actual.nombre, actual.apellido) : '·'} onVolver={() => abrir(null)} onLeido={alLeer} onEnviado={() => void cargarLista()}/>
            : <div className="chat-vacio"><Empty icon={<MessageCircle size={22}/>} title="Elegí una conversación" detail="Seleccioná un paciente de la lista para ver los mensajes y responder."/></div>}
        </section>
      </div>
    </Panel>
  </>;
}

function Conversacion({ farmId, usuarioId, titulo, detalle, abierta, borrado, iniciales, onVolver, onLeido, onEnviado }: {
  farmId: number; usuarioId: number; titulo: string; detalle: string; abierta: boolean; borrado: string | null; iniciales: string;
  onVolver: () => void; onLeido: () => void; onEnviado: () => void;
}) {
  const url = `/api/farmacias/${farmId}/chats/${usuarioId}/mensajes`;
  const [mensajes, setMensajes] = useState<MensajeChat[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const fin = useRef<HTMLDivElement>(null);
  const corriendo = useRef(false);
  const ultimoDelPaciente = useRef(0);

  // Se trae la conversación completa (máx. 200 mensajes): así también se actualiza el "Leído".
  const cargar = useCallback(async (primera: boolean) => {
    if (corriendo.current) return;
    corriendo.current = true;
    try {
      const lista = await get<MensajeChat[]>(url);
      setMensajes(lista);
      const ultimo = lista.filter(m => m.autor === 'USUARIO').at(-1)?.id ?? 0;
      if (primera || ultimo > ultimoDelPaciente.current) onLeido();
      ultimoDelPaciente.current = ultimo;
      setError(null);
    } catch (e) { if (primera) setError(e instanceof Error ? e.message : 'No se pudieron cargar los mensajes.'); }
    finally { corriendo.current = false; }
  }, [url, onLeido]);

  useEffect(() => {
    ultimoDelPaciente.current = 0;
    void cargar(true);
    const timer = window.setInterval(() => { if (!document.hidden) void cargar(false); }, POLL_MENSAJES_MS);
    return () => window.clearInterval(timer);
    // onLeido cambia de identidad con la lista; solo reiniciar al cambiar de conversación.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  useEffect(() => { fin.current?.scrollIntoView({ block: 'end' }); }, [mensajes?.length]);

  async function enviar(e?: FormEvent) {
    e?.preventDefault();
    const limpio = texto.trim();
    if (!limpio || enviando) return;
    setEnviando(true);
    try {
      const m = await post<MensajeChat>(url, { texto: limpio });
      setMensajes(prev => [...(prev || []), m]);
      setTexto(''); onEnviado();
    } catch (err) { toast.error(err instanceof Error ? err.message : 'No se pudo enviar el mensaje.'); onEnviado(); }
    finally { setEnviando(false); }
  }
  function teclas(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter envía; Shift+Enter agrega un salto de línea.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); void enviar(); }
  }

  return <>
    <header className="chat-hilo-head">
      <IconButton label="Volver a la lista" className="only-mobile" onClick={onVolver}><ArrowLeft size={18}/></IconButton>
      <span className="avatar">{iniciales}</span>
      <div><strong>{titulo}</strong><small>{detalle}</small></div>
    </header>
    <div className="chat-mensajes" aria-live="polite">
      {mensajes === null && !error ? <SkeletonList rows={3}/>
        : error ? <ErrorState error={error} retry={() => void cargar(true)}/>
        : mensajes!.length === 0 ? <Empty compact icon={<MessageCircle size={20}/>} title="Empezá la conversación" detail="Por ejemplo, avisale que su pedido está listo o consultale un dato de la receta."/>
        : mensajes!.map((m, i) => {
            const propio = m.autor === 'FARMACIA';
            const nuevoDia = i === 0 || new Date(mensajes![i - 1].fechaHora).toDateString() !== new Date(m.fechaHora).toDateString();
            return <div key={m.id} className="chat-fila">
              {nuevoDia && <div className="chat-dia">{new Intl.DateTimeFormat('es-AR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date(m.fechaHora))}</div>}
              <div className={`chat-burbuja ${propio ? 'propio' : ''}`}>
                <p>{m.texto}</p>
                <small title={shortDateTime(m.fechaHora)}>{new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date(m.fechaHora))}{propio && (m.leido ? ' · Leído' : ' · Enviado')}</small>
              </div>
            </div>;
          })}
      <div ref={fin}/>
    </div>
    {!abierta ? <div className="chat-enviar chat-cerrado" role="status"><Lock size={15}/><span>La reserva terminó: la conversación quedó cerrada y ya no se pueden enviar mensajes.{borrado && ` El historial se borra automáticamente el ${dateTime(borrado)}.`}</span></div>
    : <form className="chat-enviar" onSubmit={enviar}>
      <textarea value={texto} onChange={e => setTexto(e.target.value.slice(0, MAX_TEXTO))} onKeyDown={teclas} rows={1}
        placeholder="Escribí un mensaje…" aria-label="Mensaje" disabled={mensajes === null && !error}/>
      <button type="submit" className="chat-enviar-btn" disabled={!texto.trim() || enviando} aria-label="Enviar"><Send size={17}/></button>
    </form>}
  </>;
}
