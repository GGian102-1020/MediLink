import { ChevronDown } from 'lucide-react';
import { initials } from '../lib/format';
import { LiveProvider, useLive } from '../lib/live';
import { IconButton } from './UI';
import { Boxes, CalendarCheck, History, House, LayoutDashboard, LogOut, Menu, MessageCircle, Search, Settings, Store, X } from 'lucide-react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';

/** Layout del dashboard de farmacia (único rol de la web). */
export function AppLayout() { return <OperatorAppLayout/>; }

// Diseño del panel de operador: no reemplaza las vistas públicas, de pacientes ni administrativas.
type OperatorNavItem = { path: string; label: string; icon: typeof House; count?: 'activas' | 'chat' };
const operatorDashboardNav: OperatorNavItem[] = [
  { path: '/panel', label: 'Resumen', icon: LayoutDashboard },
  { path: '/operacion/reservas', label: 'Reservas', icon: CalendarCheck, count: 'activas' },
  { path: '/operacion/stock', label: 'Stock', icon: Boxes },
  { path: '/operacion/movimientos', label: 'Movimientos', icon: History },
  { path: '/chat', label: 'Chat', icon: MessageCircle, count: 'chat' },
];
const extraTitles: Record<string, string> = { '/perfil': 'Mi cuenta' };

function OperatorBrand() {
  return <Link to="/panel" className="dash-brand" aria-label="MediFarm, ir al resumen"><img src="/logo-mark.png" alt="" width={28} height={27}/><span>Medi<b>Farm</b></span></Link>;
}

function ProfileMenu() {
  const { me, signOut } = useAuth(); const navigate = useNavigate(); const [open, setOpen] = useState(false); const ref = useRef<HTMLDivElement>(null);
  const p = me?.perfil;
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close); document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, [open]);
  async function exit() { setOpen(false); await signOut(); navigate('/ingresar'); }
  return <div className="dash-profile" ref={ref}>
    <button className="dash-profile-trigger" onClick={() => setOpen(v => !v)} aria-expanded={open} aria-haspopup="menu">
      <span className="avatar">{initials(p?.nombre, p?.apellido)}</span>
      <span className="dash-profile-name truncate" style={{ maxWidth: 160 }}>{p?.nombre} {p?.apellido}</span>
      <ChevronDown size={15}/>
    </button>
    {open && <div className="menu" role="menu">
      <div className="menu-head"><span className="avatar">{initials(p?.nombre, p?.apellido)}</span><div><strong>{p?.nombre} {p?.apellido}</strong><small>{p?.correo}</small></div></div>
      <Link role="menuitem" className="menu-item" to="/perfil" onClick={() => setOpen(false)}><Settings size={16}/> Mi cuenta</Link>
      <button role="menuitem" className="menu-item danger" onClick={exit}><LogOut size={16}/> Cerrar sesión</button>
    </div>}
  </div>;
}

function OperatorAppLayout() {
  const { me } = useAuth();
  return <LiveProvider farmId={me?.perfil.farmaciaId}><Shell/></LiveProvider>;
}
function Shell() {
  const { me } = useAuth(); const navigate = useNavigate(); const location = useLocation(); const { activeCount, chatUnread } = useLive();
  const [open, setOpen] = useState(false); const [code, setCode] = useState(''); const searchRef = useRef<HTMLInputElement>(null);
  const farmId = me?.perfil.farmaciaId;
  const title = operatorDashboardNav.find(x => location.pathname.startsWith(x.path))?.label || extraTitles[location.pathname] || '';

  useEffect(() => { setOpen(false); }, [location.pathname]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      const typing = t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable;
      if ((e.key === '/' && !typing) || (e.key.toLowerCase() === 'k' && (e.ctrlKey || e.metaKey))) { e.preventDefault(); searchRef.current?.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);
  function search(e: FormEvent) {
    e.preventDefault(); const c = code.trim().toUpperCase(); if (!c) return;
    navigate(`/operacion/reservas?codigo=${encodeURIComponent(c)}`); setCode(''); searchRef.current?.blur();
  }

  return <div className="dash">
    <aside className={`dash-sidebar ${open ? 'open' : ''}`} aria-label="Navegación principal">
      <div className="dash-sidebar-head"><OperatorBrand/><IconButton label="Cerrar menú" className="only-mobile" onClick={() => setOpen(false)}><X size={18}/></IconButton></div>
      <div className="nav-label">Operación</div>
      <nav className="dash-nav">{operatorDashboardNav.map(({ path, label, icon: Icon, count }) => {
        const n = count === 'activas' ? activeCount : count === 'chat' ? chatUnread : null;
        return <NavLink key={path} to={path} className={({ isActive }) => `dash-nav-link ${isActive ? 'active' : ''}`}>
          <Icon size={17}/><span>{label}</span>{!!n && <span className={`nav-count ${count === 'chat' ? 'nav-count-alert' : ''}`} aria-label={count === 'chat' ? `${n} sin leer` : `${n} activas`}>{n}</span>}
        </NavLink>;
      })}</nav>
      <div className="sidebar-foot"><span className="pharmacy-icon"><Store size={16}/></span><div className="truncate"><strong>Farmacia #{farmId ?? '—'}</strong><small>Operador</small></div></div>
    </aside>
    {open && <button className="dash-overlay" aria-label="Cerrar menú" onClick={() => setOpen(false)}/>}
    <div className="dash-main">
      <header className="dash-topbar">
        <IconButton label="Abrir menú" className="only-mobile" onClick={() => setOpen(true)}><Menu size={20}/></IconButton>
        {title && <span className="dash-title">{title}</span>}
        <form className="dash-search" onSubmit={search} role="search">
          <Search size={16}/>
          <input ref={searchRef} value={code} onChange={e => setCode(e.target.value)} placeholder="Buscar reserva por código" aria-label="Buscar reserva por código" spellCheck={false} autoComplete="off"/>
          <kbd>/</kbd>
        </form>
        <ProfileMenu/>
      </header>
      <main className="dash-content"><Outlet/></main>
    </div>
  </div>;
}
