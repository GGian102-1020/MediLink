import { useState, type FormEvent } from 'react';
import { Eye, EyeOff, KeyRound, Mail, ShieldCheck } from 'lucide-react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '../context/AuthContext';
import { post } from '../lib/api';
import { initials } from '../lib/format';
import { Alert, Badge, Button, Field, IconButton, PageHeader, Panel } from '../components/UI';

export function LoginPage() {
  const { me, signIn, signOut } = useAuth(); const navigate = useNavigate(); const location = useLocation();
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [show, setShow] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try {
      const perfil = await signIn(email, password);
      if (perfil.rol !== 'OPERADOR') { await signOut(); setError('Este panel es exclusivo para operadores de farmacia.'); return; }
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from?.startsWith('/') && !from.startsWith('//') ? from : '/panel', { replace: true });
    }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión.'); }
    finally { setBusy(false); }
  }
  if (me?.perfil.rol === 'OPERADOR') return <Navigate to="/panel" replace/>;
  return <div className="login-screen"><div className="login-card">
    <div className="login-brand"><img src="/logo-mark.png" alt="MediFind" width={52} height={50}/><h1>Ingresá a MediFind</h1><p>Panel de operadores de farmacia</p></div>
    <div className="login-box">
      <form onSubmit={submit} noValidate={false}>
        <Field label="Correo electrónico"><div className="input-icon"><Mail size={16}/><input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="operador@farmacia.com" autoComplete="username" maxLength={254} required autoFocus/></div></Field>
        <Field label="Contraseña"><div className="input-icon"><KeyRound size={16}/><input type={show ? 'text' : 'password'} value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" autoComplete="current-password" required style={{ paddingRight: 44 }}/><IconButton className="input-action" label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'} onClick={() => setShow(v => !v)} style={{ width: 32, height: 32 }}>{show ? <EyeOff size={16}/> : <Eye size={16}/>}</IconButton></div></Field>
        {error && <Alert>{error}</Alert>}
        <Button type="submit" busy={busy} block>Ingresar</Button>
      </form>
    </div>
    <div className="login-foot"><ShieldCheck size={14}/> Conexión segura · Solo personal autorizado</div>
  </div></div>;
}

export function ProfilePage() {
  const { me, refresh } = useAuth(); const [current, setCurrent] = useState(''); const [next, setNext] = useState(''); const [repeat, setRepeat] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const p = me?.perfil;
  const mismatch = repeat.length > 0 && next !== repeat;
  async function change(e: FormEvent) {
    e.preventDefault(); if (next !== repeat) { setError('Las contraseñas nuevas no coinciden.'); return; }
    setBusy(true); setError('');
    try {
      await post<void>('/api/auth/change-password', { contrasenaActual: current, nuevaContrasena: next });
      setCurrent(''); setNext(''); setRepeat('');
      toast.success('Contraseña actualizada', { description: 'Las demás sesiones abiertas se van a cerrar.' });
      await refresh();
    }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo cambiar la contraseña.'); }
    finally { setBusy(false); }
  }
  return <div className="content-narrow">
    <PageHeader title="Mi cuenta" subtitle="Tus datos y la seguridad de tu acceso."/>
    <Panel>
      <div className="panel-body"><div className="profile-card"><span className="avatar lg">{initials(p?.nombre, p?.apellido)}</span><div style={{ minWidth: 0 }}><h2>{p?.nombre} {p?.apellido}</h2><p style={{ overflowWrap: 'anywhere' }}>{p?.correo}</p></div></div></div>
      <div className="panel-foot"><div className="facts" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))' }}>
        <div><small>Rol</small><strong>Operador</strong></div>
        <div><small>Farmacia</small><strong className="num">#{p?.farmaciaId ?? '—'}</strong></div>
        <div><small>Estado</small><Badge value={p?.estadoCuenta || '—'} tone={['ACTIVA', 'ACTIVO', 'HABILITADA'].includes(p?.estadoCuenta || '') ? 'success' : 'danger'}/></div>
        <div><small>ID de cuenta</small><strong className="num">{p?.cuentaId}</strong></div>
      </div></div>
    </Panel>
    <Panel title="Cambiar contraseña" subtitle="Usá al menos 8 caracteres. Al cambiarla se cierran tus otras sesiones.">
      <form className="panel-body stack" onSubmit={change}>
        <Field label="Contraseña actual"><input type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required/></Field>
        <div className="form-grid">
          <Field label="Nueva contraseña"><input type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} required minLength={8} maxLength={72}/></Field>
          <Field label="Repetir nueva contraseña" hint={mismatch ? 'No coincide con la nueva contraseña.' : undefined}><input type="password" autoComplete="new-password" value={repeat} onChange={e => setRepeat(e.target.value)} required minLength={8} maxLength={72} aria-invalid={mismatch}/></Field>
        </div>
        {error && <Alert>{error}</Alert>}
        <div><Button busy={busy} type="submit" disabled={mismatch}>Actualizar contraseña</Button></div>
      </form>
    </Panel>
  </div>;
}
