import { lazy, Suspense, useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowRight, BadgeCheck, CalendarCheck2, Car, ChevronRight, Clock3, Cross, ExternalLink, Footprints, HeartPulse, LoaderCircle, LocateFixed, MapPin, Navigation, Pill, Search, ShieldCheck, Sparkles, Store, Stethoscope, X } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { get, pageQuery, post } from '../lib/api';
import { presentationLabel, number } from '../lib/format';
import type { Disponibilidad, Medicamento, Page, Presentacion, Reserva } from '../lib/types';
import { buscarDireccion, calcularRuta, coords, enlaceComoLlegar, formatoDistancia, formatoDuracion, guardarOrigen, leerOrigenGuardado, ubicacionActual, type Encuadre, type ModoViaje, type Origen, type ResultadoDireccion, type Ruta } from '../lib/geo';
import { Alert, BackLink, Badge, Button, Empty, ErrorState, Field, Loading, Pagination, Panel, PageTitle } from '../components/UI';

export function Home() {
  return <>
    <section className="hero"><div className="wide-wrap hero-grid"><div className="hero-copy"><div className="hero-kicker"><Sparkles size={15}/> UNA FORMA MÁS SIMPLE DE CUIDARTE</div><h1>Tu salud, <span>más cerca.</span></h1><p>Encontrá los medicamentos que necesitás, consultá su disponibilidad en farmacias y reservá en minutos. Todo desde un mismo lugar.</p><div className="hero-actions"><Link className="btn btn-primary btn-lg" to="/catalogo"><Search size={19}/> Buscar medicamentos <ArrowRight size={18}/></Link><Link className="btn btn-outline btn-lg" to="/registro">Crear mi cuenta</Link></div><div className="hero-trust"><div className="trust-avatars"><span><Pill size={15}/></span><span><HeartPulse size={15}/></span><span><Cross size={15}/></span></div><div><strong>Más simple. Más claro. Más cerca.</strong><small>Disponibilidad e información en un solo lugar</small></div></div></div><div className="hero-art" aria-label="Ilustración de búsqueda y reserva de medicamentos"><div className="hero-orbit orbit-one"/><div className="hero-orbit orbit-two"/><div className="hero-float hero-float-top"><span><BadgeCheck size={18}/></span> Reserva confirmada</div><div className="hero-device"><div className="device-top"><div className="device-brand"><div className="device-cross"><Cross size={16}/></div> MediLink</div><span className="device-dots">•••</span></div><div className="device-overline">TU BIENESTAR EN UN LUGAR</div><h3>¿Qué necesitás hoy?</h3><div className="device-search"><Search size={17}/> Buscar un medicamento…</div><div className="device-product"><div className="device-product-icon"><Pill size={27}/></div><div><strong>Medicamentos</strong><small>Explorá presentaciones</small></div><ChevronRight size={18}/></div><div className="device-product"><div className="device-product-icon mint"><Store size={27}/></div><div><strong>Farmacias</strong><small>Disponibilidad por sucursal</small></div><ChevronRight size={18}/></div><div className="device-cta">Encontrá lo que buscás <ArrowRight size={15}/></div></div><div className="hero-float hero-float-bottom"><span><MapPin size={19}/></span><div><strong>Farmacias cerca</strong><small>Consultá disponibilidad</small></div></div><div className="hero-spark hero-spark-one">✳</div><div className="hero-spark hero-spark-two">✦</div></div></div></section>
    <section className="features-section" id="como-funciona"><div className="wide-wrap"><div className="section-heading center"><div className="eyebrow">ASÍ DE FÁCIL</div><h2>Todo lo que necesitás,<br/><span>en tres pasos.</span></h2><p>Menos vueltas, más tiempo para lo importante.</p></div><div className="features-grid"><div className="feature-card"><span className="feature-number">01</span><div className="feature-icon blue"><Search size={27}/></div><h3>Buscá tu medicamento</h3><p>Explorá el catálogo y encontrá la presentación que necesitás.</p></div><div className="feature-card"><span className="feature-number">02</span><div className="feature-icon teal"><MapPin size={27}/></div><h3>Elegí tu farmacia</h3><p>Consultá qué farmacias tienen unidades disponibles antes de reservar.</p></div><div className="feature-card"><span className="feature-number">03</span><div className="feature-icon violet"><CalendarCheck2 size={27}/></div><h3>Reservá y retirá</h3><p>Confirmá tu reserva online y acercate a la farmacia dentro del plazo.</p></div></div></div></section>
    <section className="confidence-section"><div className="wide-wrap confidence-grid"><div className="confidence-visual"><div className="confidence-big-icon"><ShieldCheck size={70}/></div><span className="visual-satellite sat-a"><Pill size={26}/></span><span className="visual-satellite sat-b"><HeartPulse size={28}/></span><span className="visual-satellite sat-c"><Stethoscope size={23}/></span></div><div className="confidence-copy"><div className="eyebrow">PENSADO PARA VOS</div><h2>La tranquilidad de saber <span>dónde encontrarlo.</span></h2><p>Información clara, disponibilidad por farmacia y seguimiento de tus reservas. MediLink conecta cada paso para que vos te enfoques en lo importante.</p><div className="confidence-points"><span><BadgeCheck size={20}/> Información de medicamentos organizada</span><span><BadgeCheck size={20}/> Reservas con seguimiento de estado</span><span><BadgeCheck size={20}/> Acceso seguro según tu perfil</span></div><Link className="text-link" to="/catalogo">Explorar el catálogo <ArrowRight size={17}/></Link></div></div></section>
    <section className="final-cta"><div className="wide-wrap final-cta-inner"><div><span>EMPEZÁ HOY</span><h2>Lo que necesitás, a un clic.</h2><p>Encontrá tu medicamento y elegí dónde retirarlo.</p></div><Link className="btn btn-light btn-lg" to="/catalogo">Buscar medicamentos <ArrowRight size={18}/></Link></div></section>
  </>;
}
export function Catalog() {
  const [draft, setDraft] = useState(''); const [term, setTerm] = useState(''); const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Medicamento> | null>(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(true); const [retryVersion, setRetryVersion] = useState(0);
  useEffect(() => { let active = true; setLoading(true); setError(''); get<Page<Medicamento>>('/api/medicamentos', pageQuery(page, { nombre: term })).then(d => { if (active) setData(d); }).catch((e: Error) => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, [page, term, retryVersion]);
  function submit(e: FormEvent) { e.preventDefault(); setPage(0); setTerm(draft.trim()); }
  return <div className="wide-wrap catalog-page"><PageTitle eyebrow="EXPLORAR / CATÁLOGO" title="Encontrá lo que necesitás" subtitle="Consultá medicamentos y sus presentaciones disponibles."/><div className="catalog-search-panel"><form onSubmit={submit} className="catalog-search"><Search size={22}/><input value={draft} onChange={e => setDraft(e.target.value)} placeholder="Buscar por nombre del medicamento…" aria-label="Nombre del medicamento"/><Button>Buscar <ArrowRight size={16}/></Button></form><div className="catalog-search-note"><ShieldCheck size={16}/> La disponibilidad se confirma al momento de reservar.</div></div><div className="catalog-section-top"><h2>{term ? `Resultados para “${term}”` : 'Medicamentos del catálogo'}</h2>{data && <span>{number(data.totalElements)} resultados</span>}</div>{loading ? <Loading/> : error ? <ErrorState error={error} retry={() => setRetryVersion(v => v + 1)}/> : !data?.content.length ? <Empty title="No encontramos medicamentos" detail="Probá con otra palabra o revisá la ortografía."/> : <><div className="catalog-presentation-list">{data.content.map(m => <MedicineRow key={m.id} med={m}/>)}</div><Pagination data={data} onChange={setPage}/></>}</div>;
}
function MedicineRow({ med }: { med: Medicamento }) {
  const [open, setOpen] = useState(false); const [data, setData] = useState<Page<Presentacion> | null>(null); const [error, setError] = useState(''); const [page, setPage] = useState(0);
  useEffect(() => { if (!open) return; let active = true; get<Page<Presentacion>>(`/api/medicamentos/${med.id}/presentaciones`, pageQuery(page)).then(d => { if (active) setData(d); }).catch((e: Error) => { if (active) setError(e.message); }); return () => { active = false; }; }, [open, page, med.id]);
  return <div className="medicine-row" id={`row-${med.id}`}><button id={`med-${med.id}`} type="button" className="medicine-toggle" onClick={() => setOpen(v => !v)} aria-expanded={open}><span className="medicine-row-icon"><Pill size={20}/></span><span><strong>{med.nombre}</strong><small>Ver presentaciones y disponibilidad</small></span><ChevronRight size={20} className={open ? 'rotate90' : ''}/></button>{open && <div className="medicine-content">{error ? <Alert>{error}</Alert> : !data ? <Loading/> : !data.content.length ? <Empty title="Sin presentaciones activas" detail="No hay presentaciones disponibles para este medicamento."/> : <><div className="presentations-grid">{data.content.map(p => <Link to={`/presentaciones/${p.id}`} className="presentation-row" key={p.id}><span><strong>{presentationLabel(p)}</strong><small><Badge value={p.condicionDispensa}/></small></span><span className="presentation-arrow">Consultar <ArrowRight size={15}/></span></Link>)}</div><Pagination data={data} onChange={setPage}/></>}</div>}</div>;
}
// Leaflet se descarga solo al abrir una Presentacion con stock.
const PharmacyMap = lazy(() => import('../components/PharmacyMap'));
const PAGINA_FARMACIAS = 12;
function cercanas(lista: Disponibilidad[], n: number): [number, number][] {
  return lista.flatMap(f => { const c = coords(f); return c ? [[c.lat, c.lng] as [number, number]] : []; }).slice(0, n);
}
export function PresentationDetail() {
  const { id } = useParams(); const navigate = useNavigate(); const { me } = useAuth();
  const [presentation, setPresentation] = useState<Presentacion | null>(null); const [avail, setAvail] = useState<Disponibilidad[]>([]); const [error, setError] = useState(''); const [loading, setLoading] = useState(true); const [retryVersion, setRetryVersion] = useState(0);
  const [selected, setSelected] = useState<Disponibilidad | null>(null); const [quantity, setQuantity] = useState(1); const [rxCode, setRxCode] = useState(''); const [busy, setBusy] = useState(false); const [actionError, setActionError] = useState('');
  const attempt = useRef<{ fingerprint: string; key: string } | null>(null);
  // Mapa: origen del usuario, busqueda de direcciones y recorrido hasta la farmacia elegida.
  const [origen, setOrigen] = useState<Origen | null>(() => leerOrigenGuardado());
  const [locating, setLocating] = useState(false); const [geoError, setGeoError] = useState(''); const [sorting, setSorting] = useState(false);
  const [address, setAddress] = useState(''); const [results, setResults] = useState<ResultadoDireccion[]>([]); const [searching, setSearching] = useState(false);
  const [modo, setModo] = useState<ModoViaje>('pie'); const [ruta, setRuta] = useState<Ruta | null>(null); const [rutaLoading, setRutaLoading] = useState(false); const [rutaError, setRutaError] = useState(''); const [showSteps, setShowSteps] = useState(false);
  const [encuadre, setEncuadre] = useState<Encuadre | null>(null); const [visible, setVisible] = useState(PAGINA_FARMACIAS);
  const bookingRef = useRef<HTMLDivElement>(null);
  const origenRef = useRef(origen); origenRef.current = origen;
  const primerOrigen = useRef(true);
  const origenQuery = (o: Origen | null) => (o ? { lat: o.lat, lng: o.lng } : undefined);

  useEffect(() => { let active = true; setLoading(true); setError(''); if (!id || !/^\d+$/.test(id)) { setError('Presentación inválida.'); setLoading(false); return; } const o = origenRef.current; Promise.all([get<Presentacion>(`/api/presentaciones/${id}`), get<Disponibilidad[]>(`/api/presentaciones/${id}/disponibilidad`, origenQuery(o))]).then(([p, a]) => { if (active) { setPresentation(p); setAvail(a); if (o) setEncuadre({ clave: `inicio-${o.lat},${o.lng}`, puntos: [[o.lat, o.lng], ...cercanas(a, 6)] }); } }).catch((e: Error) => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, [id, retryVersion]);

  // Al cambiar el origen se pide de nuevo la disponibilidad, ahora ordenada por cercania.
  useEffect(() => {
    if (primerOrigen.current) { primerOrigen.current = false; return; }
    if (!id || !/^\d+$/.test(id)) return;
    let active = true; setSorting(true);
    get<Disponibilidad[]>(`/api/presentaciones/${id}/disponibilidad`, origenQuery(origen)).then(a => {
      if (!active) return;
      setAvail(a); setVisible(PAGINA_FARMACIAS);
      setSelected(prev => (prev ? a.find(x => x.farmaciaId === prev.farmaciaId) ?? null : null));
      if (origen) setEncuadre({ clave: `origen-${origen.lat},${origen.lng}-${Date.now()}`, puntos: [[origen.lat, origen.lng], ...cercanas(a, 6)] });
    }).catch((e: Error) => { if (active) setGeoError(e.message); }).finally(() => { if (active) setSorting(false); });
    return () => { active = false; };
  }, [origen]);

  const destino = selected ? coords(selected) : null;
  useEffect(() => {
    setRuta(null); setRutaError(''); setShowSteps(false);
    if (!origen || !destino || !selected) return;
    const ctrl = new AbortController(); setRutaLoading(true);
    calcularRuta(origen, destino, modo, ctrl.signal)
      .then(r => { setRuta(r); setEncuadre({ clave: `ruta-${selected.farmaciaId}-${modo}-${origen.lat},${origen.lng}`, puntos: r.puntos }); })
      .catch((e: Error) => { if (e.name !== 'AbortError') setRutaError(e.message); })
      .finally(() => { if (!ctrl.signal.aborted) setRutaLoading(false); });
    return () => { ctrl.abort(); setRutaLoading(false); };
  }, [selected?.farmaciaId, origen?.lat, origen?.lng, modo]);

  function choose(f: Disponibilidad) {
    setSelected(f); setActionError('');
    const c = coords(f); if (!c) return;
    setEncuadre({ clave: `farmacia-${f.farmaciaId}-${Date.now()}`, puntos: origen ? [[origen.lat, origen.lng], [c.lat, c.lng]] : [[c.lat, c.lng]] });
    const idx = avail.findIndex(x => x.farmaciaId === f.farmaciaId);
    if (idx >= visible) setVisible(idx + 1);
  }
  function changeOrigin(o: Origen | null) { setOrigen(o); guardarOrigen(o); setResults([]); setGeoError(''); }
  async function locate() {
    setLocating(true); setGeoError(''); setResults([]);
    try { const p = await ubicacionActual(); changeOrigin({ ...p, etiqueta: 'Tu ubicación', fuente: 'gps' }); }
    catch (e) { setGeoError(e instanceof Error ? e.message : 'No pudimos obtener tu ubicación.'); }
    finally { setLocating(false); }
  }
  async function searchAddress(e: FormEvent) {
    e.preventDefault(); const q = address.trim();
    if (q.length < 3) { setGeoError('Escribí una dirección, por ejemplo "Av. Corrientes 1500".'); return; }
    setSearching(true); setGeoError(''); setResults([]);
    try {
      const r = await buscarDireccion(q);
      if (!r.length) setGeoError('No encontramos esa dirección en CABA. Probá con calle y altura.');
      else if (r.length === 1) changeOrigin({ ...r[0], fuente: 'direccion' });
      else setResults(r);
    } catch (err) { setGeoError(err instanceof Error ? err.message : 'No se pudo buscar la dirección.'); }
    finally { setSearching(false); }
  }

  async function reserve(e: FormEvent) {
    e.preventDefault(); if (!presentation || !selected) return;
    if (!me) { navigate('/ingresar', { state: { from: `/presentaciones/${id}` } }); return; }
    if (me.perfil.rol !== 'USUARIO') { setActionError('Solo los usuarios pueden crear reservas.'); return; }
    if (me.suspensionVigente?.vigente) { setActionError('Tu cuenta tiene una suspensión vigente y no puede realizar nuevas reservas.'); return; }
    if (quantity < 1 || !Number.isInteger(quantity) || quantity > selected.cantidadDisponible) { setActionError('Ingresá una cantidad válida según la disponibilidad informada.'); return; }
    if (presentation.condicionDispensa === 'BAJO_RECETA' && !rxCode.trim()) { setActionError('Ingresá el código de Prescripción.'); return; }
    const payload = { farmaciaId: selected.farmaciaId, presentacionId: presentation.id, cantidad: quantity, ...(presentation.condicionDispensa === 'BAJO_RECETA' ? { codigoPrescripcion: rxCode.trim() } : {}) };
    const fingerprint = JSON.stringify(payload);
    if (attempt.current?.fingerprint !== fingerprint) attempt.current = { fingerprint, key: crypto.randomUUID() };
    setBusy(true); setActionError('');
    try { const reservation = await post<Reserva>('/api/reservas', payload, attempt.current.key); attempt.current = null; navigate(`/mis-reservas/${encodeURIComponent(reservation.codigoVisible)}`, { state: { created: true } }); }
    catch (e) { setActionError(e instanceof Error ? e.message : 'No se pudo reservar.'); } finally { setBusy(false); }
  }

  const mapCard = presentation && <section className="map-card" aria-labelledby="map-title">
    <div className="map-card-head">
      <div className="map-card-copy"><div className="eyebrow">MAPA DE FARMACIAS</div><h2 id="map-title">¿Dónde te queda más cómodo retirarlo?</h2><p>{origen ? <>Farmacias con stock ordenadas por cercanía a <strong>{origen.etiqueta}</strong>.</> : 'Compartí tu ubicación o buscá una dirección para ver las farmacias más cercanas y cómo llegar.'}</p></div>
      <div className="map-toolbar">
        <button type="button" className="btn btn-primary" onClick={locate} disabled={locating}>{locating ? <LoaderCircle size={16} className="spin"/> : <LocateFixed size={16}/>} Usar mi ubicación</button>
        <form className="map-search" onSubmit={searchAddress} role="search">
          <Search size={16}/><input value={address} onChange={e => setAddress(e.target.value)} placeholder="o buscá una dirección en CABA" aria-label="Dirección de partida" maxLength={120}/>
          <button type="submit" className="map-search-btn" disabled={searching} aria-label="Buscar dirección">{searching ? <LoaderCircle size={16} className="spin"/> : <ArrowRight size={16}/>}</button>
        </form>
      </div>
    </div>
    {(origen || results.length > 0 || geoError) && <div className="map-status-row">
      {origen && <span className="map-chip"><MapPin size={14}/> {origen.etiqueta}<button type="button" onClick={() => changeOrigin(null)} aria-label="Quitar ubicación"><X size={13}/></button></span>}
      {results.length > 0 && <div className="map-results"><span>¿Cuál de estas?</span>{results.map(r => <button type="button" key={`${r.lat},${r.lng}`} onClick={() => changeOrigin({ ...r, fuente: 'direccion' })}><MapPin size={13}/>{r.etiqueta}</button>)}</div>}
      {geoError && <span className="map-error">{geoError}</span>}
    </div>}
    <div className={`map-frame ${selected ? 'has-card' : ''}`}>
      <Suspense fallback={<div className="pharmacy-map map-placeholder"><LoaderCircle size={20} className="spin"/> Cargando mapa…</div>}><PharmacyMap farmacias={avail} seleccionada={selected} origen={origen} ruta={ruta} encuadre={encuadre} onSeleccionar={choose}/></Suspense>
      {sorting && <div className="map-loading"><LoaderCircle size={15} className="spin"/> Buscando las más cercanas…</div>}
      <div className="map-legend" aria-hidden="true"><span><i className="dot ok"/>Con stock</span><span><i className="dot low"/>Últimas unidades</span>{origen && <span><i className="dot me"/>Vos</span>}</div>
      {selected && <div className="route-card" aria-live="polite">
        <div className="route-card-head"><span className="route-card-icon"><Store size={19}/></span><div><strong>{selected.nombre}</strong><small>{selected.direccion}</small></div><button type="button" className="route-close" onClick={() => setSelected(null)} aria-label="Cerrar"><X size={16}/></button></div>
        <div className="route-stats"><span><b>{selected.cantidadDisponible}</b> {selected.cantidadDisponible === 1 ? 'unidad' : 'unidades'}</span>{selected.distanciaKm != null && <span><b>{formatoDistancia(selected.distanciaKm * 1000)}</b> en línea recta</span>}</div>
        {origen && destino ? <>
          <div className="mode-toggle" role="group" aria-label="Cómo vas a ir">
            <button type="button" className={modo === 'pie' ? 'active' : ''} aria-pressed={modo === 'pie'} onClick={() => setModo('pie')}><Footprints size={15}/> A pie</button>
            <button type="button" className={modo === 'auto' ? 'active' : ''} aria-pressed={modo === 'auto'} onClick={() => setModo('auto')}><Car size={15}/> En auto</button>
          </div>
          {rutaLoading ? <p className="route-status"><LoaderCircle size={14} className="spin"/> Calculando recorrido…</p>
            : rutaError ? <p className="route-status error">{rutaError}</p>
            : ruta && <>
              <div className="route-summary"><Clock3 size={17}/><strong>{formatoDuracion(ruta.duracionS)}</strong><span>{formatoDistancia(ruta.distanciaM)} {modo === 'pie' ? 'caminando' : 'en auto'}</span></div>
              <button type="button" className="route-steps-toggle" onClick={() => setShowSteps(v => !v)} aria-expanded={showSteps}><Navigation size={14}/> {showSteps ? 'Ocultar indicaciones' : `Ver indicaciones (${ruta.pasos.length})`}<ChevronRight size={14} className={showSteps ? 'rotate90' : ''}/></button>
              {showSteps && <ol className="route-steps">{ruta.pasos.map((p, i) => <li key={i}><span>{p.texto}</span>{p.distanciaM > 0 && <small>{formatoDistancia(p.distanciaM)}</small>}</li>)}</ol>}
            </>}
        </> : <p className="route-status">Compartí tu ubicación para ver cómo llegar.</p>}
        <div className="route-actions">
          <button type="button" className="btn btn-primary" onClick={() => bookingRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>Reservar acá <ArrowRight size={15}/></button>
          {destino && <a className="btn btn-outline" href={enlaceComoLlegar(destino, modo)} target="_blank" rel="noreferrer">Google Maps <ExternalLink size={13}/></a>}
        </div>
      </div>}
    </div>
  </section>;

  return <div className="wide-wrap detail-page"><BackLink to="/catalogo" label="Volver al catálogo"/>{loading ? <Loading/> : error ? <ErrorState error={error} retry={() => setRetryVersion(v => v + 1)}/> : presentation && <><div className="detail-intro"><div className="detail-drug-icon"><Pill size={48}/></div><div><div className="eyebrow">MEDICAMENTOS / PRESENTACIÓN</div><h1>{presentation.medicamentoNombre}</h1><p>{presentationLabel(presentation)}</p><Badge value={presentation.condicionDispensa}/></div></div>
    {avail.length > 0 && mapCard}
    <div className="detail-grid"><Panel title="Disponibilidad por farmacia" subtitle={origen ? 'Las más cercanas primero.' : 'Elegí dónde querés retirar tu medicamento.'}><p className="muted small-text">{avail.length > 0 && <>{number(avail.length)} {avail.length === 1 ? 'farmacia' : 'farmacias'} con stock. </>}El stock mostrado puede cambiar hasta confirmar la reserva.</p>{!avail.length ? <Empty title="Sin disponibilidad por ahora" detail="Volvé a consultar más tarde."/> : <><div className="availability-list">{avail.slice(0, visible).map(f => <button type="button" key={f.farmaciaId} onClick={() => choose(f)} disabled={f.cantidadDisponible <= 0} className={`pharmacy-option ${selected?.farmaciaId === f.farmaciaId ? 'selected' : ''}`}><span className="pharmacy-marker"><Store size={22}/></span><span className="pharmacy-meta"><strong>{f.nombre}</strong><small><MapPin size={13}/>{f.direccion}</small>{f.distanciaKm != null && <span className="distance-chip"><Navigation size={11}/> a {formatoDistancia(f.distanciaKm * 1000)}</span>}</span><span className={`stock-count ${f.cantidadDisponible > 0 ? '' : 'sold-out'}`}><strong>{f.cantidadDisponible}</strong><small>unidades</small></span><span className="radio-indicator"/></button>)}</div>{visible < avail.length && <button type="button" className="btn btn-outline load-more" onClick={() => setVisible(v => v + PAGINA_FARMACIAS)}>Ver más farmacias <span>({number(avail.length - visible)} restantes)</span></button>}</>}</Panel><div ref={bookingRef} className="booking-anchor"><Panel title="Tu reserva" subtitle="Confirmá los detalles antes de continuar."><div className="booking-summary"><span><Pill size={17}/> {presentation.medicamentoNombre}</span><strong>{presentationLabel(presentation)}</strong><div className="summary-divider"/><span><Store size={17}/> Farmacia seleccionada</span><strong>{selected ? selected.nombre : 'Elegí una farmacia de la lista o del mapa'}</strong>{selected && <small className="booking-pharmacy-detail">{selected.direccion}{selected.distanciaKm != null && <> · a {formatoDistancia(selected.distanciaKm * 1000)}</>}</small>}</div><form onSubmit={reserve} className="stack-form"><Field label="Cantidad"><input type="number" min="1" max={selected?.cantidadDisponible || 1} value={quantity} onChange={e => setQuantity(Number(e.target.value))} required/></Field>{presentation.condicionDispensa === 'BAJO_RECETA' && <Field label="Código de Prescripción" hint="El código debe pertenecer a una Prescripción activa y válida a tu nombre."><input maxLength={80} value={rxCode} onChange={e => setRxCode(e.target.value)} placeholder="Ingresá el código de tu receta" required/></Field>}{!me ? <Alert tone="info">Necesitás iniciar sesión como Usuario para reservar.</Alert> : me.perfil.rol !== 'USUARIO' ? <Alert tone="info">La creación de reservas está disponible solo para cuentas de Usuario.</Alert> : me.suspensionVigente?.vigente ? <Alert>Tu cuenta está suspendida hasta {new Date(me.suspensionVigente.fechaHoraFin).toLocaleDateString('es-AR')}.</Alert> : null}{actionError && <Alert>{actionError}</Alert>}<Button type="submit" disabled={!selected || (me !== null && me.perfil.rol !== 'USUARIO') || Boolean(me?.suspensionVigente?.vigente)} busy={busy}> {me ? 'Confirmar reserva' : 'Ingresar para reservar'} <ArrowRight size={17}/></Button><p className="booking-disclaimer"><Clock3 size={15}/> Si se confirma, verás el plazo exacto para retirar.</p></form></Panel></div></div></>}</div>;
}
