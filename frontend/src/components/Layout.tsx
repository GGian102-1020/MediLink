import { Activity, ArrowRight, BookOpen, Boxes, CalendarCheck, ChevronDown, ClipboardList, Cross, HeartHandshake, History, House, LayoutDashboard, LogOut, Menu, Pill, Search, Settings, ShieldCheck, Store, Users, X } from 'lucide-react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../context/AuthContext';

type NavItem = { path: string; label: string; icon: typeof House };
const userNav: NavItem[] = [
  { path: '/panel', label: 'Mi inicio', icon: LayoutDashboard },
  { path: '/catalogo', label: 'Explorar medicamentos', icon: Search },
  { path: '/mis-reservas', label: 'Mis reservas', icon: CalendarCheck },
  { path: '/perfil', label: 'Mi cuenta', icon: Settings },
];
const operatorNav: NavItem[] = [
  { path: '/panel', label: 'Resumen', icon: LayoutDashboard },
  { path: '/operacion/reservas', label: 'Reservas', icon: CalendarCheck },
  { path: '/operacion/stock', label: 'Stock', icon: Boxes },
  { path: '/operacion/movimientos', label: 'Movimientos', icon: History },
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
  return <div className="public-layout"><header className="public-header"><div className="wide-wrap header-inner"><Brand /><button aria-label="Abrir menú" className="mobile-menu" onClick={() => setOpen(v => !v)}>{open ? <X/> : <Menu/>}</button><nav className={open ? 'header-links open' : 'header-links'}><NavLink to="/" onClick={() => setOpen(false)}>Inicio</NavLink><NavLink to="/catalogo" onClick={() => setOpen(false)}>Medicamentos</NavLink><a href="/#como-funciona" onClick={() => setOpen(false)}>Cómo funciona</a></nav><div className="header-actions">{me ? <Link to="/panel" className="btn btn-primary">Ir a mi panel <ArrowRight size={16}/></Link> : <><Link to="/ingresar" className="btn btn-ghost">Ingresar</Link><Link to="/registro" className="btn btn-primary">Crear cuenta <ArrowRight size={16}/></Link></>}</div></div></header><main><Outlet /></main><footer className="site-footer"><div className="wide-wrap footer-inner"><Brand small/><p>Más cerca de lo que necesitás. Información y disponibilidad sujetas a confirmación.</p><span>© {new Date().getFullYear()} MediLink · MVP v1.4.3</span></div></footer></div>;
}
export function AppLayout() {
  const { me, signOut } = useAuth(); const navigate = useNavigate(); const location = useLocation(); const [open, setOpen] = useState(false);
  const profile = me?.perfil;
  const nav = profile?.rol === 'ADMINISTRADOR' ? adminNav : profile?.rol === 'OPERADOR' ? operatorNav : userNav;
  async function exit() { await signOut(); navigate('/'); }
  return <div className="app-layout"><aside className={`sidebar ${open ? 'sidebar-open' : ''}`}><div className="sidebar-brand"><Brand small/><button className="icon-button hide-desktop" aria-label="Cerrar menú" onClick={() => setOpen(false)}><X size={20}/></button></div><div className="sidebar-group-label">ESPACIO DE TRABAJO</div><nav className="sidebar-nav">{nav.map(({ path, label, icon: Icon }) => <NavLink key={path} to={path} onClick={() => setOpen(false)} className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}><Icon size={19} /><span>{label}</span>{path === '/panel' && <span className="sidebar-active-dot" />}</NavLink>)}</nav><div className="sidebar-bottom"><div className="sidebar-help"><div className="sidebar-help-icon"><BookOpen size={21}/></div><strong>Tu espacio MediLink</strong><p>Gestioná cada paso desde un solo lugar.</p></div><button onClick={exit} className="sidebar-link logout"><LogOut size={18}/> Cerrar sesión</button></div></aside><div className="app-main"><header className="app-topbar"><div className="app-top-left"><button className="icon-button hide-desktop" aria-label="Abrir menú" onClick={() => setOpen(true)}><Menu size={22}/></button><span className="breadcrumb">MediLink <span>/</span> {nav.find(x => location.pathname.startsWith(x.path))?.label || 'Detalle'}</span></div><div className="topbar-right"><span className="connection-pill"><span/> Portal conectado</span><span className="topbar-divider"/><div className="user-icon">{profile?.nombre?.[0] || 'M'}</div><div className="user-info"><strong>{profile?.nombre} {profile?.apellido}</strong><small>{profile?.rol === 'ADMINISTRADOR' ? 'Administrador' : profile?.rol === 'OPERADOR' ? 'Operador de farmacia' : 'Usuario'}</small></div><ChevronDown size={15} className="topbar-chevron"/></div></header><main className="app-content"><Outlet/></main><div className="app-footnote"><Activity size={14}/> MediLink MVP v1.4.3 · Datos actualizados desde la API</div></div>{open && <button className="sidebar-overlay" aria-label="Cerrar menú" onClick={() => setOpen(false)}/>}</div>;
}
