import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { Loading } from './components/UI';
import { AppLayout } from './components/Layout';
import { LoginPage, ProfilePage } from './pages/Auth';
import { OperatorDashboard, PharmacyReservations, PharmacyStock, StockMovements } from './pages/Operator';

/*
 * Web de MediLink = dashboard de farmacia. Solo cuentas OPERADOR.
 * Los pacientes usan la app Android. Sin sesión solo se ve el login.
 */
function Protected() {
  const { me, loading } = useAuth(); const location = useLocation();
  if (loading) return <Loading text="Restaurando tu sesión…"/>;
  if (me?.perfil.rol !== 'OPERADOR') return <Navigate to="/ingresar" state={{ from: location.pathname }} replace/>;
  return <AppLayout/>;
}
function Inicio() {
  const { me, loading } = useAuth();
  if (loading) return <Loading text="Restaurando tu sesión…"/>;
  return <Navigate to={me?.perfil.rol === 'OPERADOR' ? '/panel' : '/ingresar'} replace/>;
}
export default function App() {
  return <Routes>
    <Route index element={<Inicio/>}/>
    <Route path="ingresar" element={<LoginPage/>}/>
    <Route element={<Protected/>}>
      <Route path="panel" element={<OperatorDashboard/>}/>
      <Route path="perfil" element={<ProfilePage/>}/>
      <Route path="operacion/reservas" element={<PharmacyReservations/>}/>
      <Route path="operacion/stock" element={<PharmacyStock/>}/>
      <Route path="operacion/movimientos" element={<StockMovements/>}/>
    </Route>
    <Route path="*" element={<Navigate to="/" replace/>}/>
  </Routes>;
}
