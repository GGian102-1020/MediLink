import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { ArrowDownRight, ArrowRight, ArrowUpRight, CalendarCheck, CalendarX2, CheckCircle2, ChevronRight, Clock3, History, Minus, PackageOpen, PackagePlus, Plus, RefreshCcw, Search, SlidersHorizontal, Store, XCircle } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '../context/AuthContext';
import { useLive } from '../lib/live';
import { ApiError, get, post, put } from '../lib/api';
import { ago, dateTime, greeting, movimientoLabel, plural, readable, remaining, shortDateTime, today } from '../lib/format';
import { fetchPresentacion, presentacionDetalle, presentacionTitulo, usePresentaciones } from '../lib/presentaciones';
import { useResource } from '../lib/useResource';
import type { EstadoReserva, MovimientoStock, Page, Presentacion, Reserva, Stock } from '../lib/types';
import { Alert, Badge, Button, ConfirmDialog, Empty, ErrorState, Field, IconButton, Kpi, Loading, Modal, PageHeader, Pagination, Panel, Segmented, SkeletonList } from '../components/UI';

const LOW_STOCK = 5;
const ESTADOS: { value: '' | EstadoReserva; label: string }[] = [
  { value: '', label: 'Todas' }, { value: 'ACTIVA', label: 'Activas' }, { value: 'COMPLETADA', label: 'Completadas' }, { value: 'CANCELADA', label: 'Canceladas' }, { value: 'VENCIDA', label: 'Vencidas' },
];
const reservasUrl = (id: number) => `/api/farmacias/${id}/reservas`;
const stockUrl = (id: number) => `/api/farmacias/${id}/stock`;

function usePharmacyId() { const { me } = useAuth(); return me?.perfil.farmaciaId || null; }
function NeedPharmacy() { return <Panel><Empty icon={<Store size={20}/>} title="Sin farmacia asignada" detail="Tu cuenta de operador no tiene una farmacia asociada. Pedile a un administrador que te asigne una."/></Panel>; }
function TimeLeft({ seconds }: { seconds: number }) {
  return <span className={`time-left ${seconds < 3600 ? 'urgent' : ''}`}>{seconds > 0 ? `Vence en ${remaining(seconds)}` : 'Venciendo'}</span>;
}
function StockLevel({ qty }: { qty: number }) {
  if (qty === 0) return <Badge value="SIN_STOCK" tone="danger" label="Sin stock"/>;
  if (qty <= LOW_STOCK) return <Badge value="BAJO" tone="warning" label="Bajo"/>;
  return null;
}

/* =============================== Resumen =============================== */
export function OperatorDashboard() {
  const { me } = useAuth(); const navigate = useNavigate(); const farmId = me?.perfil.farmaciaId; const { version, isNew } = useLive();
  const counts = useResource(async () => {
    if (!farmId) return null;
    const estados: EstadoReserva[] = ['ACTIVA', 'COMPLETADA', 'CANCELADA', 'VENCIDA'];
    const pages = await Promise.all(estados.map(estado => get<Page<Reserva>>(reservasUrl(farmId), { page: 0, size: 1, estado })));
    return Object.fromEntries(estados.map((e, i) => [e, pages[i].totalElements])) as Record<EstadoReserva, number>;
  }, [farmId, version]);
  const activas = useResource(() => farmId ? get<Page<Reserva>>(reservasUrl(farmId), { page: 0, size: 100, estado: 'ACTIVA' }) : Promise.resolve(null), [farmId, version]);
  const stock = useResource(() => farmId ? get<Page<Stock>>(stockUrl(farmId), { page: 0, size: 100 }) : Promise.resolve(null), [farmId, version]);
  const movs = useResource(() => farmId ? get<Page<MovimientoStock>>(`${stockUrl(farmId)}/movimientos`, { page: 0, size: 6 }) : Promise.resolve(null), [farmId, version]);

  const porRetirar = useMemo(() => [...(activas.data?.content || [])].sort((a, b) => a.fechaHoraVencimiento.localeCompare(b.fechaHoraVencimiento)).slice(0, 6), [activas.data]);
  const bajo = useMemo(() => (stock.data?.content || []).filter(s => s.cantidadDisponible <= LOW_STOCK).sort((a, b) => a.cantidadDisponible - b.cantidadDisponible).slice(0, 6), [stock.data]);
  const names = usePresentaciones(bajo.map(s => s.presentacionId));
  const c = counts.data;

  if (!farmId) return <><PageHeader title={`${greeting()}, ${me?.perfil.nombre || ''}`} subtitle={today()}/><NeedPharmacy/></>;
  return <>
    <PageHeader title={`${greeting()}, ${me?.perfil.nombre || ''}`} subtitle={`${today()} · Farmacia #${farmId}`} actions={<Button variant="secondary" onClick={() => navigate('/operacion/stock')}><PackagePlus size={16}/> Ajustar stock</Button>}/>
    <div className="kpi-grid">
      <Kpi label="Por retirar" value={c ? c.ACTIVA : '—'} hint="Reservas activas" icon={<Clock3 size={16}/>} tone="accent" to="/operacion/reservas?estado=ACTIVA"/>
      <Kpi label="Completadas" value={c ? c.COMPLETADA : '—'} hint="Retiros confirmados" icon={<CheckCircle2 size={16}/>} tone="success" to="/operacion/reservas?estado=COMPLETADA"/>
      <Kpi label="Canceladas" value={c ? c.CANCELADA : '—'} hint="Por usuario u operación" icon={<XCircle size={16}/>} tone="danger" to="/operacion/reservas?estado=CANCELADA"/>
      <Kpi label="Vencidas" value={c ? c.VENCIDA : '—'} hint="Sin retirar a tiempo" icon={<CalendarX2 size={16}/>} tone="warning" to="/operacion/reservas?estado=VENCIDA"/>
    </div>
    <div className="grid-2">
      <Panel title="Próximas a vencer" subtitle="Reservas activas ordenadas por vencimiento" action={<Link to="/operacion/reservas?estado=ACTIVA" className="link">Ver todas <ArrowRight size={14}/></Link>}>
        {activas.loading && !activas.data ? <SkeletonList rows={3}/> : activas.error ? <ErrorState error={activas.error} retry={activas.reload}/> : porRetirar.length ? <div className="list">{porRetirar.map(r =>
          <button key={r.id} className={`list-row ${isNew(r.id) ? 'row-new' : ''}`} onClick={() => navigate(`/operacion/reservas?codigo=${encodeURIComponent(r.codigoVisible)}`)}>
            <span className="list-icon"><CalendarCheck size={16}/></span>
            <span className="list-main"><strong title={r.medicamentoNombreSnapshot}>{r.medicamentoNombreSnapshot} <span className="muted" style={{ fontWeight: 400 }}>× {r.cantidad}</span></strong><small>{[r.usuarioNombre, r.usuarioApellido].filter(Boolean).join(' ') || 'Usuario'} · <span className="code">{r.codigoVisible}</span></small></span>
            <span className="list-side"><TimeLeft seconds={r.segundosRestantes}/><small>{shortDateTime(r.fechaHoraVencimiento)}</small></span>
          </button>)}</div> : <Empty compact icon={<CalendarCheck size={20}/>} title="Nada por retirar" detail="Cuando un usuario reserve en tu farmacia, va a aparecer acá."/>}
      </Panel>
      <Panel title="Stock bajo" subtitle={`Productos con ${LOW_STOCK} unidades o menos`} action={<Link to="/operacion/stock" className="link">Ver stock <ArrowRight size={14}/></Link>}>
        {stock.loading && !stock.data ? <SkeletonList rows={3}/> : stock.error ? <ErrorState error={stock.error} retry={stock.reload}/> : bajo.length ? <div className="list">{bajo.map(s =>
          <Link key={s.presentacionId} className="list-row" to={`/operacion/stock?ajustar=${s.presentacionId}`}>
            <span className="list-main"><strong>{presentacionTitulo(names[s.presentacionId], s.presentacionId)}</strong><small>{presentacionDetalle(names[s.presentacionId])}</small></span>
            <span className="qty"><StockLevel qty={s.cantidadDisponible}/><strong>{s.cantidadDisponible}</strong></span>
          </Link>)}</div> : <Empty compact icon={<PackageOpen size={20}/>} title="Stock en orden" detail="Ningún producto está por debajo del mínimo."/>}
      </Panel>
    </div>
    <Panel className="section-gap" title="Actividad reciente" subtitle="Últimos movimientos de stock" action={<Link to="/operacion/movimientos" className="link">Ver historial <ArrowRight size={14}/></Link>}>
      {movs.loading && !movs.data ? <SkeletonList rows={3}/> : movs.error ? <ErrorState error={movs.error} retry={movs.reload}/> : movs.data?.content.length ? <div className="list">{movs.data.content.map(m => <MovementRow key={m.id} m={m}/>)}</div> : <Empty compact icon={<History size={20}/>} title="Sin movimientos todavía"/>}
    </Panel>
  </>;
}
function MovementRow({ m }: { m: MovimientoStock }) {
  const up = m.delta > 0;
  return <div className="list-row">
    <span className={`list-icon ${m.delta === 0 ? '' : up ? 'up' : 'down'}`}>{m.delta === 0 ? <Minus size={16}/> : up ? <ArrowUpRight size={16}/> : <ArrowDownRight size={16}/>}</span>
    <span className="list-main"><strong>{movimientoLabel(m.tipo)}</strong><small>{m.reservaCodigo ? <>Reserva <span className="code">{m.reservaCodigo}</span></> : m.motivo || 'Sin detalle'}</small></span>
    <span className="list-side"><span className={`num ${up ? 'delta-pos' : m.delta < 0 ? 'delta-neg' : ''}`}>{up ? '+' : ''}{m.delta}</span><small>{ago(m.fechaHora)}</small></span>
  </div>;
}

/* =============================== Reservas =============================== */
const causes = [
  ['PRODUCTO_NO_DISPONIBLE', 'Producto no disponible'],
  ['STOCK_INCONSISTENTE', 'Stock inconsistente'],
  ['PRODUCTO_NO_APTO_PARA_ENTREGA', 'Producto no apto para entrega'],
  ['IMPEDIMENTO_OPERATIVO_FARMACIA', 'Impedimento operativo de farmacia'],
  ['OTRA_CAUSA', 'Otra causa'],
] as const;

export function PharmacyReservations() {
  const pharmacyId = usePharmacyId(); const { version, isNew, refresh } = useLive(); const [search, setSearch] = useSearchParams();
  const queryCode = search.get('codigo') || ''; const estado = (search.get('estado') || '') as '' | EstadoReserva;
  const [page, setPage] = useState(0);
  const state = useResource(() => pharmacyId ? get<Page<Reserva>>(reservasUrl(pharmacyId), { page, size: 10, estado }) : Promise.resolve(null), [pharmacyId, page, estado, version]);
  useEffect(() => { setPage(0); }, [estado]);

  function setParam(key: string, value: string) { const next = new URLSearchParams(search); if (value) next.set(key, value); else next.delete(key); setSearch(next, { replace: true }); }
  const rows = state.data?.content || [];

  return <>
    <PageHeader title="Reservas" subtitle={<>Confirmá retiros y gestioná cancelaciones de tu farmacia. <span className="live" title="La lista se actualiza sola cada 10 segundos">En vivo</span></>} actions={<IconButton label="Actualizar" bordered onClick={state.reload}><RefreshCcw size={16} className={state.loading ? 'spin' : ''}/></IconButton>}/>
    {!pharmacyId ? <NeedPharmacy/> : <Panel>
      <div className="toolbar"><Segmented label="Filtrar por estado" options={ESTADOS} value={estado} onChange={v => setParam('estado', v)}/>{state.data && <span className="muted num" style={{ fontSize: 13 }}>{plural(state.data.totalElements, 'reserva', 'reservas')}</span>}</div>
      {state.loading && !state.data ? <SkeletonList rows={5}/> : state.error ? <ErrorState error={state.error} retry={state.reload}/> : rows.length ? <>
        <div className="table-wrap"><table className="table">
          <thead><tr><th>Código</th><th>Medicamento</th><th className="hide-sm">Usuario</th><th>Estado</th><th className="hide-sm">Vencimiento</th><th className="col-action"><span className="sr-only">Abrir</span></th></tr></thead>
          <tbody>{rows.map(r => <tr key={r.id} className={`clickable ${isNew(r.id) ? 'row-new' : ''}`} tabIndex={0} onClick={() => setParam('codigo', r.codigoVisible)} onKeyDown={e => { if (e.key === 'Enter') setParam('codigo', r.codigoVisible); }}>
            <td><span className="code cell-main">{r.codigoVisible}</span><span className="cell-sub">{plural(r.cantidad, 'unidad', 'unidades')}</span></td>
            <td style={{ maxWidth: 280 }}><span className="cell-main truncate" style={{ display: 'block' }} title={r.medicamentoNombreSnapshot}>{r.medicamentoNombreSnapshot}</span><span className="cell-sub truncate" title={r.presentacionDescripcionSnapshot}>{r.presentacionDescripcionSnapshot}</span></td>
            <td className="hide-sm">{[r.usuarioNombre, r.usuarioApellido].filter(Boolean).join(' ') || '—'}</td>
            <td><Badge value={r.estado}/></td>
            <td className="hide-sm">{r.estado === 'ACTIVA' ? <TimeLeft seconds={r.segundosRestantes}/> : <span className="muted">{shortDateTime(r.fechaHoraVencimiento)}</span>}</td>
            <td className="col-action"><ChevronRight size={16} className="chev"/></td>
          </tr>)}</tbody>
        </table></div>
        <Pagination data={state.data!} onChange={setPage}/>
      </> : <Empty icon={<CalendarCheck size={20}/>} title={estado ? `No hay reservas ${ESTADOS.find(e => e.value === estado)?.label.toLowerCase()}` : 'Todavía no hay reservas'} detail={estado ? 'Probá con otro estado.' : 'Las reservas de tu farmacia van a aparecer acá.'}/>}
    </Panel>}
    {pharmacyId && queryCode && <ReservationDetail pharmacyId={pharmacyId} code={queryCode} onClose={() => setParam('codigo', '')} onChanged={() => { state.reload(); refresh(); }} version={version}/>}
  </>;
}

function ReservationDetail({ pharmacyId, code, onClose, onChanged, version }: { pharmacyId: number; code: string; onClose: () => void; onChanged: () => void; version: number }) {
  const detail = useResource(() => get<Reserva>(`${reservasUrl(pharmacyId)}/${encodeURIComponent(code)}`), [pharmacyId, code, version]);
  const [mode, setMode] = useState<'view' | 'cancel' | 'pickup'>('view');
  const [cause, setCause] = useState<string>('PRODUCTO_NO_DISPONIBLE'); const [reason, setReason] = useState(''); const [restitution, setRestitution] = useState('RESTITUIR'); const [realStock, setRealStock] = useState(0);
  const [confirmation, setConfirmation] = useState<'cancelacion' | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [pin, setPin] = useState('');
  const r = detail.data; const active = r?.estado === 'ACTIVA';
  const path = r ? `${reservasUrl(pharmacyId)}/${encodeURIComponent(r.codigoVisible)}` : '';

  async function pickup(e: FormEvent) {
    e.preventDefault(); if (!r || !/^\d{6}$/.test(pin)) return;
    setBusy(true); setError('');
    try {
      await post<Reserva>(`${path}/retiro`, { pin });
      toast.success('Retiro confirmado', { description: `${r.medicamentoNombreSnapshot} · ${r.codigoVisible}` });
      setMode('view'); setPin(''); detail.reload(); onChanged();
    } catch (err) {
      setPin(''); setError(err instanceof Error ? err.message : 'No se pudo confirmar el retiro.');
      if (err instanceof ApiError && err.codigo !== 'INVALID_PICKUP_PIN') { setMode('view'); detail.reload(); }
    }
    finally { setBusy(false); }
  }
  async function cancelReservation() {
    if (!r) return;
    setBusy(true); setError('');
    try {
      await post<Reserva>(`${path}/cancelacion-operativa`, { causa: cause, motivo: cause === 'STOCK_INCONSISTENTE' ? null : reason.trim(), ...(cause === 'STOCK_INCONSISTENTE' ? { cantidadStockReal: realStock } : {}), ...(cause === 'OTRA_CAUSA' ? { restitucionStock: restitution } : {}) });
      toast.success('Reserva cancelada', { description: r.codigoVisible });
      setConfirmation(null); setMode('view'); detail.reload(); onChanged();
    } catch (err) { setConfirmation(null); setError(err instanceof Error ? err.message : 'No se pudo cancelar la reserva.'); detail.reload(); }
    finally { setBusy(false); }
  }
  function go(next: typeof mode) { setMode(next); setError(''); setPin(''); }

  const footer = r && active ? mode === 'view'
    ? <><Button variant="ghost" onClick={() => go('cancel')}>Cancelación operativa</Button><Button onClick={() => go('pickup')}><CheckCircle2 size={16}/> Confirmar retiro</Button></>
    : mode === 'pickup'
      ? <><Button variant="secondary" onClick={() => go('view')} disabled={busy}>Volver</Button><Button type="submit" form="pickup-form" busy={busy} disabled={!/^\d{6}$/.test(pin)}><CheckCircle2 size={16}/> Confirmar retiro</Button></>
      : <><Button variant="secondary" onClick={() => go('view')}>Volver</Button><Button variant="danger" type="submit" form="cancel-form">Cancelar reserva</Button></>
    : undefined;

  return <>
    <Modal eyebrow="Reserva" title={<span className="code">{code}</span>} onClose={onClose} blocked={!!confirmation || busy} footer={footer}>
      {detail.loading && !r ? <Loading/> : detail.error ? <ErrorState error={detail.error} retry={detail.reload}/> : r ? <>
        <div className="hero"><div><h3>{r.medicamentoNombreSnapshot}</h3><p>{r.presentacionDescripcionSnapshot}</p></div><Badge value={r.estado}/></div>
        <div className="facts">
          <div><small>Usuario</small><strong>{[r.usuarioNombre, r.usuarioApellido].filter(Boolean).join(' ') || '—'}</strong></div>
          <div><small>Cantidad</small><strong className="num">{plural(r.cantidad, 'unidad', 'unidades')}</strong></div>
          <div><small>Creada</small><strong>{dateTime(r.fechaHoraCreacion)}</strong></div>
          <div><small>Vence</small><strong>{dateTime(r.fechaHoraVencimiento)}</strong>{active && <div><TimeLeft seconds={r.segundosRestantes}/></div>}</div>
          {r.fechaHoraRetiro && <div><small>Retirada</small><strong>{dateTime(r.fechaHoraRetiro)}</strong></div>}
          {r.fechaHoraCancelacion && <div><small>Cancelada</small><strong>{dateTime(r.fechaHoraCancelacion)}</strong></div>}
          {r.causaCancelacion && <div><small>Causa</small><strong>{readable(r.causaCancelacion)}</strong></div>}
          {r.motivoCancelacion && <div className="span-2"><small>Motivo</small><strong>{r.motivoCancelacion}</strong></div>}
          {r.prescripcionCodigo && <div><small>Receta</small><strong className="code">{r.prescripcionCodigo}</strong></div>}
        </div>
        {active && mode === 'pickup' && <form id="pickup-form" className="stack" onSubmit={pickup}>
          <div className="divider"/>
          <Field label="PIN de retiro" hint="Pedile al paciente el PIN de 6 dígitos que ve en su reserva. Después de 5 intentos incorrectos, la reserva se bloquea para retiro.">
            <input className="pin-input" value={pin} onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))} inputMode="numeric" autoComplete="one-time-code" pattern="\d{6}" maxLength={6} placeholder="••••••" aria-label="PIN de retiro de 6 dígitos" autoFocus required/>
          </Field>
        </form>}
        {error && <Alert>{error}</Alert>}
        {active && mode === 'cancel' &&<form id="cancel-form" className="stack" onSubmit={(e: FormEvent) => { e.preventDefault(); setConfirmation('cancelacion'); }}>
          <div className="divider"/>
          <Field label="Causa"><select value={cause} onChange={e => setCause(e.target.value)}>{causes.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></Field>
          {cause === 'STOCK_INCONSISTENTE'
            ? <Field label="Stock libre real" hint="Cantidad que realmente hay disponible en la farmacia."><input type="number" min={0} value={realStock} onChange={e => setRealStock(Number(e.target.value))} required/></Field>
            : <Field label="Motivo"><textarea value={reason} maxLength={500} onChange={e => setReason(e.target.value)} placeholder="Contá brevemente qué pasó" required/></Field>}
          {cause === 'OTRA_CAUSA' && <Field label="¿Qué hacemos con el stock?"><select value={restitution} onChange={e => setRestitution(e.target.value)}><option value="RESTITUIR">Devolver las unidades al stock</option><option value="NO_RESTITUIR">No devolver las unidades</option></select></Field>}
        </form>}
      </> : null}
    </Modal>
    {confirmation && r && <ConfirmDialog
      title="¿Cancelar la reserva?"
      message="La reserva deja de estar activa y el stock se ajusta según la causa elegida. No se puede deshacer."
      confirmText="Cancelar reserva" danger busy={busy} onClose={() => setConfirmation(null)} onConfirm={cancelReservation}/>}
  </>;
}

/* ================================ Stock ================================ */
export function PharmacyStock() {
  const pharmacyId = usePharmacyId(); const [search, setSearch] = useSearchParams();
  const [page, setPage] = useState(0); const [filter, setFilter] = useState(''); const [onlyLow, setOnlyLow] = useState<'all' | 'low'>('all');
  const state = useResource(() => pharmacyId ? get<Page<Stock>>(stockUrl(pharmacyId), { page, size: 100 }) : Promise.resolve(null), [pharmacyId, page]);
  const items = state.data?.content || [];
  const names = usePresentaciones(items.map(s => s.presentacionId));
  const ajustar = search.get('ajustar'); const creating = search.get('nuevo') === '1';
  const target = ajustar ? items.find(s => s.presentacionId === Number(ajustar)) : undefined;

  const lowCount = items.filter(s => s.cantidadDisponible <= LOW_STOCK).length;
  const visible = useMemo(() => {
    const q = filter.trim().toLocaleLowerCase('es-AR');
    return items.filter(s => onlyLow === 'all' || s.cantidadDisponible <= LOW_STOCK).filter(s => {
      if (!q) return true;
      const p = names[s.presentacionId];
      return `${s.presentacionId} ${p?.medicamentoNombre || ''} ${p ? presentacionDetalle(p) : ''}`.toLocaleLowerCase('es-AR').includes(q);
    }).sort((a, b) => presentacionTitulo(names[a.presentacionId], a.presentacionId).localeCompare(presentacionTitulo(names[b.presentacionId], b.presentacionId), 'es'));
  }, [items, names, filter, onlyLow]);

  function setParam(key: string, value: string) { const next = new URLSearchParams(search); next.delete('ajustar'); next.delete('nuevo'); if (value) next.set(key, value); setSearch(next, { replace: true }); }
  const close = () => setParam('', '');

  return <>
    <PageHeader title="Stock" subtitle="Saldos libres de tu farmacia. Cada ajuste queda registrado." actions={<><IconButton label="Actualizar" bordered onClick={state.reload}><RefreshCcw size={16} className={state.loading ? 'spin' : ''}/></IconButton><Button onClick={() => setParam('nuevo', '1')}><Plus size={16}/> Nuevo saldo</Button></>}/>
    {!pharmacyId ? <NeedPharmacy/> : <Panel>
      <div className="toolbar">
        <Segmented label="Filtrar stock" options={[{ value: 'all', label: 'Todos', count: items.length }, { value: 'low', label: 'Stock bajo', count: lowCount }]} value={onlyLow} onChange={setOnlyLow}/>
        <div className="input-icon search-inline"><Search size={15}/><input className="input" style={{ height: 36 }} value={filter} onChange={e => setFilter(e.target.value)} placeholder="Buscar medicamento" aria-label="Buscar medicamento"/></div>
      </div>
      {state.loading && !state.data ? <SkeletonList rows={6}/> : state.error ? <ErrorState error={state.error} retry={state.reload}/> : visible.length ? <>
        <div className="table-wrap"><table className="table">
          <thead><tr><th>Medicamento</th><th className="col-num">Disponible</th><th className="hide-sm">Actualizado</th><th className="col-action"><span className="sr-only">Acciones</span></th></tr></thead>
          <tbody>{visible.map(s => { const p = names[s.presentacionId]; return <tr key={s.presentacionId}>
            <td style={{ maxWidth: 360 }}><span className="cell-main truncate" style={{ display: 'block' }}>{presentacionTitulo(p, s.presentacionId)}</span><span className="cell-sub truncate">{presentacionDetalle(p)}</span></td>
            <td className="col-num"><span className="qty"><StockLevel qty={s.cantidadDisponible}/><strong>{s.cantidadDisponible}</strong></span></td>
            <td className="hide-sm muted">{ago(s.fechaHoraActualizacion)}</td>
            <td className="col-action"><Button variant="secondary" size="sm" onClick={() => setParam('ajustar', String(s.presentacionId))}><SlidersHorizontal size={14}/> Ajustar</Button></td>
          </tr>; })}</tbody>
        </table></div>
        <Pagination data={state.data!} onChange={setPage}/>
      </> : <Empty icon={<PackageOpen size={20}/>} title={filter || onlyLow === 'low' ? 'Sin coincidencias' : 'Todavía no hay stock cargado'} detail={filter || onlyLow === 'low' ? 'Probá con otra búsqueda o filtro.' : 'Cargá el primer saldo para empezar.'} action={!filter && onlyLow === 'all' ? <Button onClick={() => setParam('nuevo', '1')}><Plus size={16}/> Nuevo saldo</Button> : undefined}/>}
    </Panel>}
    {pharmacyId && (target || creating) && <StockAdjust pharmacyId={pharmacyId} item={target} presentacion={target ? names[target.presentacionId] : undefined} onClose={close} onSaved={() => { close(); state.reload(); }}/>}
  </>;
}

function StockAdjust({ pharmacyId, item, presentacion, onClose, onSaved }: { pharmacyId: number; item?: Stock; presentacion?: Presentacion | null; onClose: () => void; onSaved: () => void }) {
  const creating = !item;
  const [id, setId] = useState(item ? String(item.presentacionId) : ''); const [amount, setAmount] = useState(item?.cantidadDisponible ?? 0); const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [preview, setPreview] = useState<Presentacion | null | undefined>(presentacion);
  useEffect(() => {
    if (!creating) return;
    const n = Number(id); if (!Number.isInteger(n) || n <= 0) { setPreview(undefined); return; }
    let active = true; setPreview(undefined);
    const t = setTimeout(() => { void fetchPresentacion(n).then(p => { if (active) setPreview(p); }); }, 300);
    return () => { active = false; clearTimeout(t); };
  }, [id, creating]);
  const before = item?.cantidadDisponible ?? 0; const delta = amount - before;

  async function save(e: FormEvent) {
    e.preventDefault(); const n = Number(id);
    if (!Number.isInteger(n) || n <= 0) { setError('Ingresá un ID de presentación válido.'); return; }
    if (creating && preview === null) { setError('No existe una presentación con ese ID.'); return; }
    setBusy(true); setError('');
    try {
      await put(`${stockUrl(pharmacyId)}/${n}`, { cantidadDisponible: amount, motivo: reason.trim() });
      toast.success('Stock actualizado', { description: `${presentacionTitulo(preview, n)}: ${amount} ${amount === 1 ? 'unidad' : 'unidades'}` });
      onSaved();
    } catch (err) { setError(err instanceof Error ? err.message : 'No se pudo guardar el ajuste.'); }
    finally { setBusy(false); }
  }

  return <Modal title={creating ? 'Nuevo saldo' : 'Ajustar stock'} onClose={onClose} blocked={busy}
    footer={<><Button variant="secondary" onClick={onClose} disabled={busy}>Cancelar</Button><Button type="submit" form="stock-form" busy={busy} disabled={!creating && delta === 0}>Guardar</Button></>}>
    <form id="stock-form" className="stack" onSubmit={save}>
      {creating ? <Field label="ID de presentación" hint={preview ? `${preview.medicamentoNombre} · ${presentacionDetalle(preview)}` : preview === null ? 'No encontramos esa presentación.' : 'Ingresá el número de la presentación.'}>
        <input type="number" min={1} value={id} onChange={e => setId(e.target.value)} required autoFocus/>
      </Field> : <div className="hero"><div><h3>{presentacionTitulo(presentacion, item!.presentacionId)}</h3><p>{presentacionDetalle(presentacion)}</p></div><span className="num" style={{ fontWeight: 600 }}>{before} u.</span></div>}
      <Field label="Nuevo saldo libre" hint="Este valor reemplaza la cantidad disponible actual.">
        <div className="stepper">
          <IconButton label="Restar uno" bordered onClick={() => setAmount(a => Math.max(0, a - 1))}><Minus size={15}/></IconButton>
          <input type="number" min={0} value={amount} onChange={e => setAmount(Math.max(0, Number(e.target.value)))} required/>
          <IconButton label="Sumar uno" bordered onClick={() => setAmount(a => a + 1)}><Plus size={15}/></IconButton>
        </div>
      </Field>
      {!creating && <div className="diff"><span>{before}</span><ArrowRight size={14}/><strong>{amount}</strong><span className={delta > 0 ? 'delta-pos' : delta < 0 ? 'delta-neg' : 'muted'}>{delta > 0 ? `+${delta}` : delta < 0 ? delta : 'sin cambios'}</span></div>}
      <Field label="Motivo"><input value={reason} onChange={e => setReason(e.target.value)} maxLength={500} placeholder="Ej.: reposición del proveedor" required/></Field>
      {error && <Alert>{error}</Alert>}
    </form>
  </Modal>;
}

/* ============================= Movimientos ============================= */
export function StockMovements() {
  const pharmacyId = usePharmacyId(); const [page, setPage] = useState(0); const [presentacionId, setPresentacionId] = useState('');
  const state = useResource(() => pharmacyId ? get<Page<MovimientoStock>>(`${stockUrl(pharmacyId)}/movimientos`, { page, size: 15, presentacionId }) : Promise.resolve(null), [pharmacyId, page, presentacionId]);
  const stock = useResource(() => pharmacyId ? get<Page<Stock>>(stockUrl(pharmacyId), { page: 0, size: 100 }) : Promise.resolve(null), [pharmacyId]);
  const names = usePresentaciones((stock.data?.content || []).map(s => s.presentacionId));
  const options = useMemo(() => (stock.data?.content || []).map(s => ({ id: s.presentacionId, label: names[s.presentacionId] ? `${names[s.presentacionId]!.medicamentoNombre} · ${names[s.presentacionId]!.concentracionTexto}` : `Presentación #${s.presentacionId}` })).sort((a, b) => a.label.localeCompare(b.label, 'es')), [stock.data, names]);
  const rows = state.data?.content || [];

  return <>
    <PageHeader title="Movimientos" subtitle="Historial de descuentos, devoluciones y ajustes de stock." actions={<IconButton label="Actualizar" bordered onClick={state.reload}><RefreshCcw size={16} className={state.loading ? 'spin' : ''}/></IconButton>}/>
    {!pharmacyId ? <NeedPharmacy/> : <Panel>
      <div className="toolbar">
        <div className="field" style={{ width: 'min(320px, 100%)' }}><select aria-label="Filtrar por medicamento" value={presentacionId} onChange={e => { setPresentacionId(e.target.value); setPage(0); }} style={{ height: 36 }}><option value="">Todos los medicamentos</option>{options.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}</select></div>
        {state.data && <span className="muted num" style={{ fontSize: 13 }}>{plural(state.data.totalElements, 'movimiento', 'movimientos')}</span>}
      </div>
      {state.loading && !state.data ? <SkeletonList rows={6}/> : state.error ? <ErrorState error={state.error} retry={state.reload}/> : rows.length ? <>
        <div className="table-wrap"><table className="table">
          <thead><tr><th>Fecha</th><th>Tipo</th><th className="col-num">Cambio</th><th className="col-num hide-sm">Saldo</th><th className="hide-sm">Detalle</th></tr></thead>
          <tbody>{rows.map(m => <tr key={m.id}>
            <td style={{ whiteSpace: 'nowrap' }}><span className="cell-main">{shortDateTime(m.fechaHora)}</span>{ago(m.fechaHora) !== shortDateTime(m.fechaHora) && <span className="cell-sub">{ago(m.fechaHora)}</span>}</td>
            <td><Badge value={m.tipo} label={movimientoLabel(m.tipo)} dot={false} tone={m.tipo === 'AJUSTE_MANUAL' || m.tipo === 'CORRECCION_STOCK_INCONSISTENTE' ? 'info' : m.delta > 0 ? 'success' : 'neutral'}/></td>
            <td className="col-num"><span className={m.delta > 0 ? 'delta-pos' : m.delta < 0 ? 'delta-neg' : 'muted'}>{m.delta > 0 ? '+' : ''}{m.delta}</span></td>
            <td className="col-num hide-sm"><span className="muted">{m.cantidadAnterior} →</span> <strong>{m.cantidadNueva}</strong></td>
            <td className="hide-sm" style={{ maxWidth: 280 }}>{m.reservaCodigo ? <Link className="link code" to={`/operacion/reservas?codigo=${encodeURIComponent(m.reservaCodigo)}`}>{m.reservaCodigo}</Link> : <span className="truncate" style={{ display: 'block' }} title={m.motivo || ''}>{m.motivo || <span className="muted">—</span>}</span>}</td>
          </tr>)}</tbody>
        </table></div>
        <Pagination data={state.data!} onChange={setPage}/>
      </> : <Empty icon={<History size={20}/>} title="Sin movimientos" detail={presentacionId ? 'Este medicamento todavía no tuvo movimientos.' : 'Los movimientos de stock van a aparecer acá.'}/>}
    </Panel>}
  </>;
}
