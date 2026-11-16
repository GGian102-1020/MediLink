import { ChevronDown } from 'lucide-react';
import { initials } from '../lib/format';
import { LiveProvider, useLive } from '../lib/live';
import { IconButton } from './UI';
import { ArrowRight, Boxes, CalendarCheck, ClipboardList, Cross, HeartHandshake, History, House, LayoutDashboard, LogOut, Menu, Pill, Search, Settings, ShieldCheck, Store, Users, X } from 'lucide-react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';

type NavItem = { path: string; label: string; icon: typeof House };
const userNav: NavItem[] = [
  { path: '/panel', label: 'Mi inicio', icon: LayoutDashboard },
  { path: '/catalogo', label: 'Explorar medicamentos', icon: Search },
  { path: '/mis-reservas', label: 'Mis reservas', icon: CalendarCheck },
  { path: '/mis-recetas', label: 'Mis recetas', icon: ClipboardList },
  { path: '/perfil', label: 'Mi cuenta', icon: Settings },
];
const adminNav: NavItem[] = [
  { path: '/panel', label: 'Resumen', icon: LayoutDashboard },
  { path: '/admin/farmacias', label: 'Farmacias', icon: Store },
  { path: '/admin/medicamentos', label: 'Medicamentos', icon: Pill },
  { path: '/admin/presentaciones', label: 'Presentaciones', icon: Boxes },
  { path: '/admin/operadores', label: 'Operadores', icon: HeartHandshake },
  { path: '/admin/usuarios', label: 'Usuarios', icon: Users },
  { path: '/admin/prescripciones', label: 'Prescripciones', icon: ClipboardList },
  { path: '/admin/auditoria', label: 'Auditoría', icon: ShieldCheck },
  { path: '/catalogo', label: 'Catálogo público', icon: Search },
  { path: '/perfil', label: 'Mi cuenta', icon: Settings },
];
function Brand({ small = false }: { small?: boolean }) { return <Link to="/" className={`brand ${small ? 'brand-small' : ''}`} aria-label="MediLink, ir al inicio"><span className="brand-symbol"><Cross size={24} strokeWidth={3}/><span className="brand-pill"/></span><span>Medi<span>Link</span></span></Link>; }
export { Brand };

export function PublicLayout() {
  const { me } = useAuth(); const [open, setOpen] = useState(false);
  return <div className="public-layout"><header className="public-header"><div className="wide-wrap header-inner"><Brand /><button aria-label="Abrir menú" className="mobile-menu" onClick={() => setOpen(v => !v)}>{open ? <X/> : <Menu/>}</button><nav className={open ? 'header-links open' : 'header-links'}><NavLink to="/" onClick={() => setOpen(false)}>Inicio</NavLink><NavLink to="/catalogo" onClick={() => setOpen(false)}>Medicamentos</NavLink><a href="/#como-funciona" onClick={() => setOpen(false)}>Cómo funciona</a></nav><div className="header-actions">{me ? <Link to="/panel" className="btn btn-primary">Ir a mi panel <ArrowRight size={16}/></Link> : <><Link to="/ingresar" className="btn btn-ghost">Ingresar</Link><Link to="/registro" className="btn btn-primary">Crear cuenta <ArrowRight size={16}/></Link></>}</div></div></header><main><Outlet /></main></div>;
}
export function AppLayout() {
  const { me, signOut } = useAuth(); const navigate = useNavigate(); const location = useLocation(); const [open, setOpen] = useState(false);
  const profile = me?.perfil;
  if (profile?.rol === 'OPERADOR') return <OperatorAppLayout/>;
  const nav = profile?.rol === 'ADMINISTRADOR' ? adminNav : userNav;
  async function exit() { await signOut(); navigate('/'); }
  return <div className="app-layout"><aside className={`sidebar ${open ? 'sidebar-open' : ''}`}><div className="sidebar-brand"><Brand small/><button className="icon-button hide-desktop" aria-label="Cerrar menú" onClick={() => setOpen(false)}><X size={20}/></button></div><div className="sidebar-group-label">ESPACIO DE TRABAJO</div><nav className="sidebar-nav">{nav.map(({ path, label, icon: Icon }) => <NavLink key={path} to={path} onClick={() => setOpen(false)} className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}><Icon size={19} /><span>{label}</span>{path === '/panel' && <span className="sidebar-active-dot" />}</NavLink>)}</nav><div className="sidebar-bottom"><button onClick={exit} className="sidebar-link logout"><LogOut size={18}/> Cerrar sesión</button></div></aside><div className="app-main"><header className="app-topbar"><div className="app-top-left"><button className="icon-button hide-desktop" aria-label="Abrir menú" onClick={() => setOpen(true)}><Menu size={22}/></button><span className="breadcrumb">MediLink <span>/</span> {nav.find(x => location.pathname.startsWith(x.path))?.label || 'Detalle'}</span></div><div className="topbar-right"><div className="user-icon">{profile?.nombre?.[0] || 'M'}</div><div className="user-info"><strong>{profile?.nombre} {profile?.apellido}</strong><small>{profile?.rol === 'ADMINISTRADOR' ? 'Administrador' : 'Usuario'}</small></div></div></header><main className="app-content"><Outlet/></main></div>{open && <button className="sidebar-overlay" aria-label="Cerrar menú" onClick={() => setOpen(false)}/>}</div>;
}

// Diseño del panel de operador: no reemplaza las vistas públicas, de pacientes ni administrativas.
type OperatorNavItem = { path: string; label: string; icon: typeof House; count?: 'activas' };
const operatorDashboardNav: OperatorNavItem[] = [
  { path: '/panel', label: 'Resumen', icon: LayoutDashboard },
  { path: '/operacion/reservas', label: 'Reservas', icon: CalendarCheck, count: 'activas' },
  { path: '/operacion/stock', label: 'Stock', icon: Boxes },
  { path: '/operacion/movimientos', label: 'Movimientos', icon: History },
];
const extraTitles: Record<string, string> = { '/perfil': 'Mi cuenta' };

function OperatorBrand() {
  return <Link to="/panel" className="dash-brand" aria-label="MediLink, ir al resumen"><img src="/logo-mark.png" alt="" width={28} height={27}/><span>Medi<b>Link</b></span></Link>;
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
  const { me } = useAuth(); const navigate = useNavigate(); const location = useLocation(); const { activeCount } = useLive();
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
      <nav className="dash-nav">{operatorDashboardNav.map(({ path, label, icon: Icon, count }) => <NavLink key={path} to={path} className={({ isActive }) => `dash-nav-link ${isActive ? 'active' : ''}`}>
        <Icon size={17}/><span>{label}</span>{count && !!activeCount && <span className="nav-count" aria-label={`${activeCount} activas`}>{activeCount}</span>}
      </NavLink>)}</nav>
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
