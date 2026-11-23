import { useState, type FormEvent } from 'react';
import { ArrowRight, Eye, EyeOff } from 'lucide-react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
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
      if (perfil.rol !== 'OPERADOR') { await signOut(); setError(perfil.rol === 'USUARIO' ? 'Esta web es para farmacias. Los pacientes ingresan desde la app MediFind.' : 'Esta web es solo para cuentas de farmacia.'); return; }
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
      <h1>Medi<span>Find</span></h1>
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
      <Link to="/recuperar-clave" className="recovery-link">¿Olvidaste tu contraseña?</Link>
      <Button type="submit" busy={busy} className="login-submit">Ingresar</Button>
    </form>
  </div>;
}
export function RecoverPasswordPage() {
  const [email, setEmail] = useState(''); const [sent, setSent] = useState(false);
  const [code, setCode] = useState(''); const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState(''); const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false); const [error, setError] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try {
      if (sent) {
        if (password !== repeat) throw new Error('Las contraseñas no coinciden.');
        if (password.length < 8 || new TextEncoder().encode(password).length > 72)
          throw new Error('Usá al menos 8 caracteres y hasta 72 bytes.');
        await post<void>('/api/auth/password-reset', { correo: email.trim(), codigo: code, nuevaContrasena: password });
        setPassword(''); setRepeat(''); setCode(''); setDone(true);
      } else {
        await post('/api/auth/password-recovery', { correo: email.trim() }); setSent(true);
      }
    } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo recuperar la contraseña.'); }
    finally { setBusy(false); }
  }
  return <div className="login-only"><form onSubmit={submit} className="login-card recovery-card">
    <h1>Medi<span>Find</span></h1><h2>Recuperar contraseña</h2>
    {done ? <><Alert tone="success">Tu contraseña se actualizó. Iniciá sesión con la nueva contraseña.</Alert>
      <Link to="/ingresar" className="recovery-link">Volver al inicio de sesión</Link></> : <>
      <p>{sent ? 'Si tu cuenta está activa, recibirás un código de 8 dígitos. Revisá también el correo no deseado. Vence en 15 minutos.'
        : 'Ingresá el correo de tu cuenta para recibir un código de recuperación.'}</p>
      <label className="login-field"><span>Correo electrónico</span><input type="email" autoComplete="email"
        value={email} onChange={e => setEmail(e.target.value)} disabled={busy || sent} maxLength={254} required/></label>
      {sent && <>
        <label className="login-field"><span>Código de recuperación</span><input inputMode="numeric" autoComplete="one-time-code"
          value={code} onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 8))} pattern="[0-9]{8}" required disabled={busy}/></label>
        <label className="login-field"><span>Nueva contraseña</span><input type="password" autoComplete="new-password"
          value={password} onChange={e => setPassword(e.target.value)} minLength={8} required disabled={busy}/></label>
        <small>Usá al menos 8 caracteres (máximo 72 bytes).</small>
        <label className="login-field"><span>Repetir contraseña</span><input type="password" autoComplete="new-password"
          value={repeat} onChange={e => setRepeat(e.target.value)} minLength={8} required disabled={busy}/></label>
      </>}
      {error && <Alert>{error}</Alert>}
      <Button type="submit" busy={busy} className="login-submit">{sent ? 'Guardar nueva contraseña' : 'Enviar código'}</Button>
      {sent && <button type="button" className="recovery-link" disabled={busy} onClick={() => {
        setSent(false); setCode(''); setPassword(''); setRepeat(''); setError('');
      }}>Cambiar correo o solicitar otro código</button>}
      <Link to="/ingresar" className="recovery-link">Volver al inicio de sesión</Link>
    </>}
  </form></div>;
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
