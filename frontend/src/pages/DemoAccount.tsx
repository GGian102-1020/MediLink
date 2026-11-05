import { useEffect, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { get, post } from '../lib/api';
import type { Reserva } from '../lib/types';
import { Alert, BackLink, Badge, Button, Field, Loading, Panel } from '../components/UI';

export function LoginPage() {
  const [correo, setCorreo] = useState(''); const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const { login, me, loading } = useAuth(); const navigate = useNavigate(); const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  if (loading) return <Loading/>;
  if (me) return <Navigate to={from?.startsWith('/') && !from.startsWith('//') ? from : '/catalogo'} replace/>;
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try { await login(correo.trim(), contrasena); navigate(from?.startsWith('/') && !from.startsWith('//') ? from : '/catalogo', { replace: true }); }
    catch (err) { setError(err instanceof Error ? err.message : 'No fue posible iniciar sesión.'); }
    finally { setBusy(false); }
  }
  return <div className="wide-wrap demo-auth"><Panel title="Ingresar" subtitle="Iniciá sesión para confirmar tus reservas."><form className="stack-form" onSubmit={submit}><Field label="Correo"><input type="email" value={correo} onChange={e => setCorreo(e.target.value)} required/></Field><Field label="Contraseña"><input type="password" value={contrasena} onChange={e => setContrasena(e.target.value)} required/></Field>{error && <Alert>{error}</Alert>}<Button busy={busy}>Ingresar</Button></form><p>¿No tenés cuenta? <Link to="/registro">Registrate</Link>.</p></Panel></div>;
}
export function RegisterPage() {
  const [nombre, setNombre] = useState(''); const [apellido, setApellido] = useState('');
  const [correo, setCorreo] = useState(''); const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const navigate = useNavigate();
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try { await post('/api/auth/register', { nombre, apellido, correo, contrasena }); navigate('/ingresar', { replace: true }); }
    catch (err) { setError(err instanceof Error ? err.message : 'No fue posible registrar la cuenta.'); }
    finally { setBusy(false); }
  }
  return <div className="wide-wrap demo-auth"><Panel title="Crear cuenta" subtitle="Completá tus datos para registrarte."><form className="stack-form" onSubmit={submit}><Field label="Nombre"><input value={nombre} onChange={e => setNombre(e.target.value)} required/></Field><Field label="Apellido"><input value={apellido} onChange={e => setApellido(e.target.value)} required/></Field><Field label="Correo"><input type="email" value={correo} onChange={e => setCorreo(e.target.value)} required/></Field><Field label="Contraseña"><input type="password" value={contrasena} onChange={e => setContrasena(e.target.value)} minLength={8} required/></Field>{error && <Alert>{error}</Alert>}<Button busy={busy}>Crear cuenta</Button></form></Panel></div>;
}
export function ReservationDetail() {
  const { codigo } = useParams(); const { me, loading: authLoading } = useAuth();
  if (authLoading) return <Loading/>;
  if (!me) return <Navigate to="/ingresar" state={{ from: `/mis-reservas/${codigo}` }} replace/>;
  return codigo ? <ReservationLoaded key={codigo} codigo={codigo}/> : <Alert>Falta el código de reserva.</Alert>;
}
function ReservationLoaded({ codigo }: { codigo: string }) {
  const [reserva, setReserva] = useState<Reserva | null>(null); const [error, setError] = useState(''); const [busy, setBusy] = useState(true);
  const [cancelling, setCancelling] = useState(false);
  useEffect(() => {
    let active = true;
    get<Reserva>(`/api/reservas/mias/${encodeURIComponent(codigo)}`)
      .then(data => { if (active) setReserva(data); })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [codigo]);
  async function cancel() {
    if (!reserva || !window.confirm('¿Cancelar esta reserva?')) return;
    setCancelling(true); setError('');
    try { setReserva(await post<Reserva>(`/api/reservas/${encodeURIComponent(codigo)}/cancelacion`)); }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo cancelar.'); }
    finally { setCancelling(false); }
  }
  return <div className="wide-wrap detail-page"><BackLink to="/catalogo" label="Volver al catálogo"/><Panel title="Detalle de reserva" subtitle="Consultá el estado de tu reserva.">{busy ? <Loading/> : error ? <Alert>{error}</Alert> : reserva ? <div className="stack-form"><p><strong>Código:</strong> {reserva.codigoVisible}</p><p><Badge value={reserva.estado}/></p><p><strong>Medicamento:</strong> {reserva.medicamentoNombreSnapshot}</p><p><strong>Farmacia:</strong> {reserva.farmaciaNombreSnapshot}</p><p><strong>Dirección:</strong> {reserva.farmaciaDireccionSnapshot}</p><p><strong>Cantidad:</strong> {reserva.cantidad}</p>{reserva.estado === 'ACTIVA' && <Button type="button" onClick={cancel} busy={cancelling}>Cancelar reserva</Button>}</div> : <Alert>No se encontró esta reserva.</Alert>}</Panel></div>;
}
