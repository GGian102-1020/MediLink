import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Catalog, Home, PresentationDetail } from './pages/Public';
import { LoginPage, RegisterPage, ReservationDetail } from './pages/DemoAccount';
import './styles.css';

function Layout() {
  const { me, logout } = useAuth();
  return <>
    <header className="public-header"><div className="wide-wrap header-inner">
      <Link className="brand" to="/">✚ MediLink</Link>
      <nav className="demo-header-nav" aria-label="Navegación"><Link to="/catalogo">Catálogo</Link>{me ? <button type="button" onClick={() => void logout()}>Salir ({me.perfil.nombre})</button> : <Link to="/ingresar">Ingresar</Link>}</nav>
    </div></header>
    <main><Routes>
      <Route path="/" element={<Home/>}/>
      <Route path="/catalogo" element={<Catalog/>}/>
      <Route path="/presentaciones/:id" element={<PresentationDetail/>}/>
      <Route path="/ingresar" element={<LoginPage/>}/>
      <Route path="/registro" element={<RegisterPage/>}/>
      <Route path="/mis-reservas/:codigo" element={<ReservationDetail/>}/>
      <Route path="*" element={<div className="wide-wrap notfound"><h1>Página no encontrada</h1><Link to="/catalogo">Volver al catálogo</Link></div>}/>
    </Routes></main>
  </>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><BrowserRouter><AuthProvider><Layout/></AuthProvider></BrowserRouter></StrictMode>);
