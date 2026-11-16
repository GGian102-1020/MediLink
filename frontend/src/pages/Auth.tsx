import { useState, type FormEvent } from 'react';
import { ArrowRight, Eye, EyeOff } from 'lucide-react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { post } from '../lib/api';
import { Alert, Button, Field, PageTitle, Panel } from '../components/UI';

export function LoginPage() {
  const { me, signIn, signOut } = useAuth(); const navigate = useNavigate(); const location = useLocation();
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [show, setShow] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try {
      const perfil = await signIn(email, password);
      // La web es solo para el personal de farmacia: los pacientes usan la app.
      if (perfil.rol !== 'OPERADOR') { await signOut(); setError(perfil.rol === 'USUARIO' ? 'Esta web es para farmacias. Los pacientes ingresan desde la app MediLink.' : 'Esta web es solo para cuentas de farmacia.'); return; }
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from?.startsWith('/') && !from.startsWith('//') ? from : '/panel', { replace: true });
    }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión'); }
    finally { setBusy(false); }
  }
  if (me?.perfil.rol === 'OPERADOR') return <Navigate to="/panel" replace/>;
  return <div className="login-only">
    <form onSubmit={submit} className="login-card">
      <img src="/logo-mark.png" alt="" width={56} height={54} className="login-logo"/>
      <h1>Medi<span>Link</span></h1>
      <label className="login-field"><span>Usuario</span>
        <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="correo@farmacia.com" autoComplete="username" maxLength={254} required autoFocus/>
      </label>
      <label className="login-field"><span>Contraseña</span>
        <div className="login-password">
          <input type={show ? 'text' : 'password'} value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required/>
          <button type="button" onClick={() => setShow(v => !v)} aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'}>{show ? <EyeOff size={18}/> : <Eye size={18}/>}</button>
        </div>
      </label>
      {error && <Alert>{error}</Alert>}
      <Button type="submit" busy={busy} className="login-submit">Ingresar</Button>
    </form>
  </div>;
}
export function ProfilePage() {
  const { me, refresh } = useAuth(); const [current, setCurrent] = useState(''); const [next, setNext] = useState(''); const [repeat, setRepeat] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [message, setMessage] = useState('');
  const profile = me?.perfil;
  async function change(e: FormEvent) {
    e.preventDefault(); if (next !== repeat) { setError('Las nuevas contraseñas no coinciden.'); return; }
    setBusy(true); setError(''); setMessage('');
    try { await post<void>('/api/auth/change-password', { contrasenaActual: current, nuevaContrasena: next }); setCurrent(''); setNext(''); setRepeat(''); setMessage('Contraseña actualizada. Por seguridad, volvé a iniciar sesión cuando se cierre tu sesión actual.'); await refresh(); }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo cambiar la contraseña.'); }
    finally { setBusy(false); }
  }
  return <div className="content-narrow"><PageTitle eyebrow="CUENTA / CONFIGURACIÓN" title="Mi perfil" subtitle="Tus datos personales y la seguridad de tu cuenta."/><Panel title="Información de la cuenta"><div className="profile-header"><div className="profile-avatar">{profile?.nombre?.[0]}{profile?.apellido?.[0]}</div><div><h3>{profile?.nombre} {profile?.apellido}</h3><span>{profile?.rol}</span></div></div><div className="info-grid"><div><small>Correo electrónico</small><strong>{profile?.correo}</strong></div><div><small>Estado de cuenta</small><strong>{profile?.estadoCuenta}</strong></div><div><small>ID de cuenta</small><strong>{profile?.cuentaId}</strong></div>{profile?.farmaciaId && <div><small>ID de farmacia</small><strong>{profile.farmaciaId}</strong></div>}</div></Panel><Panel title="Cambiar contraseña" subtitle="Usá una contraseña única y no la compartas."><form className="stack-form" onSubmit={change}><Field label="Contraseña actual"><input type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required/></Field><div className="form-grid"><Field label="Nueva contraseña"><input type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} required minLength={8}/></Field><Field label="Repetir nueva contraseña"><input type="password" autoComplete="new-password" value={repeat} onChange={e => setRepeat(e.target.value)} required minLength={8}/></Field></div>{error && <Alert>{error}</Alert>}{message && <Alert tone="success">{message}</Alert>}<div><Button busy={busy} type="submit">Actualizar contraseña <ArrowRight size={16}/></Button></div></form></Panel></div>;
}
