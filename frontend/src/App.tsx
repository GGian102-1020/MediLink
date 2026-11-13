import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from './context/AuthContext';
import { Button, Empty, Loading } from './components/UI';
import { AppLayout } from './components/Layout';
import { LoginPage, ProfilePage } from './pages/Auth';
import { OperatorDashboard, PharmacyReservations, PharmacyStock, StockMovements } from './pages/Operator';

/** Consola exclusiva para operadores de farmacia. */
function Protected() {
  const { me, loading, signOut } = useAuth(); const location = useLocation();
  if (loading) return <div className="full-center"><Loading text="Restaurando tu sesión…"/></div>;
  if (!me) return <Navigate to="/ingresar" state={{ from: location.pathname + location.search }} replace/>;
  if (me.perfil.rol !== 'OPERADOR') return <div className="full-center"><div className="panel" style={{ maxWidth: 400 }}><Empty icon={<ShieldAlert size={20}/>} title="Acceso restringido" detail="Este panel es exclusivo para operadores de farmacia." action={<Button variant="secondary" onClick={() => void signOut()}>Cerrar sesión</Button>}/></div></div>;
  return <AppLayout/>;
}
function NotFound() { return <div className="notfound"><span>404</span><h1>No encontramos esta página</h1><p>Puede que el enlace haya cambiado.</p><a className="btn btn-secondary" href="/panel">Volver al resumen</a></div>; }
export default function App() {
  return <Routes>
    <Route index element={<Navigate to="/panel" replace/>}/>
    <Route path="ingresar" element={<LoginPage/>}/>
    <Route element={<Protected/>}>
      <Route path="panel" element={<OperatorDashboard/>}/>
      <Route path="operacion/reservas" element={<PharmacyReservations/>}/>
      <Route path="operacion/stock" element={<PharmacyStock/>}/>
      <Route path="operacion/movimientos" element={<StockMovements/>}/>
      <Route path="perfil" element={<ProfilePage/>}/>
    </Route>
    <Route path="*" element={<NotFound/>}/>
  </Routes>;
}
