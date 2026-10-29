import { useState, type FormEvent } from 'react';
import { ArrowRight, Eye, EyeOff, KeyRound, LockKeyhole, Mail, ShieldCheck, UserRound, UserRoundPlus } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { post, setAccessToken } from '../lib/api';
import { loginNotice } from '../lib/authNotice';
import { Alert, Button, Field, PageTitle, Panel } from '../components/UI';

export function LoginPage() {
  const { me, signIn } = useAuth(); const navigate = useNavigate(); const location = useLocation();
  const notice = loginNotice(location.state);
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [show, setShow] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try { await signIn(email, password); const from = (location.state as { from?: string } | null)?.from; navigate(from?.startsWith('/') && !from.startsWith('//') ? from : '/panel', { replace: true }); }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión'); }
    finally { setBusy(false); }
  }
  if (me) return <div className="auth-center"><Panel><h2>Ya iniciaste sesión</h2><Link className="btn btn-primary" to="/panel">Ir a mi panel <ArrowRight size={17}/></Link></Panel></div>;
  return <div className="auth-page"><div className="auth-side"><div className="auth-side-pattern"/><div className="auth-side-content"><div className="auth-side-symbol"><ShieldCheck size={35}/></div><span>UN ESPACIO MÁS SIMPLE PARA TU SALUD</span><h2>Todo conectado.<br/>Todo más cerca.</h2><p>Consultá disponibilidad, seguí tus reservas y gestioná tu experiencia desde un solo lugar.</p><div className="auth-side-bottom">MEDILINK · TU SALUD, MÁS CERCA</div></div></div><div className="auth-form-side"><div className="auth-form-wrap"><div className="auth-form-icon"><LockKeyhole size={25}/></div><div className="eyebrow">BIENVENIDO DE NUEVO</div><h1>Iniciá sesión</h1><p>Ingresá con las credenciales de tu cuenta MediLink.</p><form onSubmit={submit} className="stack-form auth-form"><Field label="Correo electrónico"><div className="input-with-icon"><Mail size={18}/><input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="tu@correo.com" autoComplete="username" maxLength={254} required/></div></Field><Field label="Contraseña"><div className="input-with-icon"><KeyRound size={18}/><input type={show ? 'text' : 'password'} value={password} onChange={e => setPassword(e.target.value)} placeholder="Ingresá tu contraseña" autoComplete="current-password" required/><button type="button" className="show-password" onClick={() => setShow(v => !v)} aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'}>{show ? <EyeOff size={18}/> : <Eye size={18}/>}</button></div></Field>{notice && <Alert tone="success">{notice}</Alert>}{error && <Alert>{error}</Alert>}<Button type="submit" busy={busy} className="auth-submit">Ingresar a mi cuenta <ArrowRight size={17}/></Button></form><p className="auth-switch">¿Todavía no tenés cuenta? <Link to="/registro">Registrate gratis</Link></p><div className="auth-security"><ShieldCheck size={17}/> Acceso protegido mediante autenticación segura.</div></div></div></div>;
}
export function RegisterPage() {
  const navigate = useNavigate(); const [form, setForm] = useState({ nombre: '', apellido: '', correo: '', contrasena: '', confirmacion: '' }); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  function set(key: keyof typeof form, value: string) { setForm(v => ({ ...v, [key]: value })); }
  async function submit(e: FormEvent) {
    e.preventDefault(); if (form.contrasena !== form.confirmacion) { setError('Las contraseñas no coinciden.'); return; }
    setBusy(true); setError('');
    try { await post('/api/auth/register', { nombre: form.nombre.trim(), apellido: form.apellido.trim(), correo: form.correo.trim(), contrasena: form.contrasena }); navigate('/ingresar', { state: { registered: true } }); }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo crear la cuenta.'); }
    finally { setBusy(false); }
  }
  return <div className="registration-wrap"><div className="registration-copy"><div className="eyebrow">TU SALUD, MÁS CERCA</div><h1>Un espacio para <span>cuidarte mejor.</span></h1><p>Creá tu cuenta para reservar medicamentos, consultar tus movimientos y tener todo organizado.</p><div className="registration-benefits"><span><ShieldCheck size={20}/> Reservas seguras</span><span><UserRound size={20}/> Historial personal</span><span><LockKeyhole size={20}/> Acceso protegido</span></div></div><div className="registration-panel"><div className="auth-form-icon"><UserRoundPlus size={25}/></div><h2>Crear una cuenta</h2><p>Completá tus datos para empezar.</p><form className="stack-form" onSubmit={submit}><div className="form-grid"><Field label="Nombre"><input required maxLength={80} value={form.nombre} onChange={e => set('nombre', e.target.value)} placeholder="Tu nombre"/></Field><Field label="Apellido"><input required maxLength={80} value={form.apellido} onChange={e => set('apellido', e.target.value)} placeholder="Tu apellido"/></Field></div><Field label="Correo electrónico"><input type="email" required maxLength={254} autoComplete="email" value={form.correo} onChange={e => set('correo', e.target.value)} placeholder="tu@correo.com"/></Field><Field label="Contraseña"><input type="password" autoComplete="new-password" minLength={8} required value={form.contrasena} onChange={e => set('contrasena', e.target.value)} placeholder="Elegí una contraseña"/></Field><Field label="Confirmar contraseña"><input type="password" autoComplete="new-password" minLength={8} required value={form.confirmacion} onChange={e => set('confirmacion', e.target.value)} placeholder="Repetí tu contraseña"/></Field>{error && <Alert>{error}</Alert>}<Button type="submit" busy={busy}>Crear mi cuenta <ArrowRight size={17}/></Button></form><p className="auth-switch">¿Ya tenés una cuenta? <Link to="/ingresar">Iniciar sesión</Link></p></div></div>;
}
export function ProfilePage() {
  const { me, refresh } = useAuth(); const navigate = useNavigate(); const [current, setCurrent] = useState(''); const [next, setNext] = useState(''); const [repeat, setRepeat] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const profile = me?.perfil;
  async function change(e: FormEvent) {
    e.preventDefault(); if (next !== repeat) { setError('Las nuevas contraseñas no coinciden.'); return; }
    setBusy(true); setError('');
    try {
      await post<void>('/api/auth/change-password', { contrasenaActual: current, nuevaContrasena: next });
      // SEC-04 invalida el token en el backend. No consultar /me con el JWT viejo.
      setAccessToken(null);
      await refresh();
      navigate('/ingresar', { replace: true, state: { passwordChanged: true } });
    }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo cambiar la contraseña.'); }
    finally { setBusy(false); }
  }
  return <div className="content-narrow"><PageTitle eyebrow="CUENTA / CONFIGURACIÓN" title="Mi perfil" subtitle="Tus datos personales y la seguridad de tu cuenta."/><Panel title="Información de la cuenta"><div className="profile-header"><div className="profile-avatar">{profile?.nombre?.[0]}{profile?.apellido?.[0]}</div><div><h3>{profile?.nombre} {profile?.apellido}</h3><span>{profile?.rol}</span></div></div><div className="info-grid"><div><small>Correo electrónico</small><strong>{profile?.correo}</strong></div><div><small>Estado de cuenta</small><strong>{profile?.estadoCuenta}</strong></div><div><small>ID de cuenta</small><strong>{profile?.cuentaId}</strong></div>{profile?.farmaciaId && <div><small>ID de farmacia</small><strong>{profile.farmaciaId}</strong></div>}</div></Panel><Panel title="Cambiar contraseña" subtitle="Usá una contraseña única y no la compartas."><form className="stack-form" onSubmit={change}><Field label="Contraseña actual"><input type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required/></Field><div className="form-grid"><Field label="Nueva contraseña"><input type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} required minLength={8}/></Field><Field label="Repetir nueva contraseña"><input type="password" autoComplete="new-password" value={repeat} onChange={e => setRepeat(e.target.value)} required minLength={8}/></Field></div>{error && <Alert>{error}</Alert>}<div><Button busy={busy} type="submit">Actualizar contraseña <ArrowRight size={16}/></Button></div></form></Panel></div>;
}
