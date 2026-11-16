import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './context/AuthContext';
import { Loading } from './components/UI';
import { AppLayout, PublicLayout } from './components/Layout';
import { Home, Catalog, PresentationDetail } from './pages/Public';
import { LoginPage, RegisterPage, ProfilePage } from './pages/Auth';
import { Dashboard, MyReservations, ReservationDetail } from './pages/User';
import { MyPrescriptions } from './pages/MyPrescriptions';
import { OperatorDashboard, PharmacyReservations, PharmacyStock, StockMovements } from './pages/Operator';
import { PharmacyReservations as LegacyReservations, PharmacyStock as LegacyStock, StockMovements as LegacyMovements } from './pages/OperatorLegacy';
import { AdminFarmacias, AdminMedicamentos, AdminPresentaciones, AdminOperadores, AdminUsuarios, AdminPrescripciones, AdminAuditoria, AdminUsuarioDetalle } from './pages/Admin';

type Rol = 'USUARIO' | 'OPERADOR' | 'ADMINISTRADOR';
function Protected({ roles }: { roles?: Rol[] }) {
  const { me, loading } = useAuth(); const location = useLocation();
  if (loading) return <Loading text="Restaurando tu sesión…"/>;
  if (!me) return <Navigate to="/ingresar" state={{ from: location.pathname }} replace/>;
  if (roles && !roles.includes(me.perfil.rol)) return <Navigate to="/panel" replace/>;
  return <AppLayout/>;
}
function DashboardHome() {
  const { me } = useAuth();
  return me?.perfil.rol === 'OPERADOR' ? <OperatorDashboard/> : <Dashboard/>;
}
/** Los administradores conservan las pantallas operativas preexistentes. */
function Operation({ current: Current, legacy: Legacy }: { current: () => ReactNode; legacy: () => ReactNode }) {
  const { me } = useAuth();
  return me?.perfil.rol === 'OPERADOR' ? <Current/> : <Legacy/>;
}
function NotFound() { return <div className="notfound"><span>404</span><h1>No encontramos esta página</h1><p>Puede que el enlace haya cambiado.</p><a href="/">Volver al inicio</a></div>; }
export default function App() {
  return <Routes>
    <Route element={<PublicLayout/>}>
      <Route index element={<Home/>}/><Route path="catalogo" element={<Catalog/>}/><Route path="presentaciones/:id" element={<PresentationDetail/>}/>
      <Route path="ingresar" element={<LoginPage/>}/><Route path="registro" element={<RegisterPage/>}/>
    </Route>
    <Route element={<Protected/>}>
      <Route path="panel" element={<DashboardHome/>}/><Route path="perfil" element={<ProfilePage/>}/>
    </Route>
    <Route element={<Protected roles={['USUARIO']}/> }>
      <Route path="mis-reservas" element={<MyReservations/>}/><Route path="mis-reservas/:codigo" element={<ReservationDetail/>}/>
      <Route path="mis-recetas" element={<MyPrescriptions/>}/>
    </Route>
    <Route element={<Protected roles={['OPERADOR', 'ADMINISTRADOR']}/> }>
      <Route path="operacion/reservas" element={<Operation current={PharmacyReservations} legacy={LegacyReservations}/>}/><Route path="operacion/stock" element={<Operation current={PharmacyStock} legacy={LegacyStock}/>}/><Route path="operacion/movimientos" element={<Operation current={StockMovements} legacy={LegacyMovements}/>}/>
      <Route path="farmacias/:farmaciaId/reservas" element={<Operation current={PharmacyReservations} legacy={LegacyReservations}/>}/><Route path="farmacias/:farmaciaId/stock" element={<Operation current={PharmacyStock} legacy={LegacyStock}/>}/><Route path="farmacias/:farmaciaId/movimientos" element={<Operation current={StockMovements} legacy={LegacyMovements}/>}/>
    </Route>
    <Route element={<Protected roles={['ADMINISTRADOR']}/> }>
      <Route path="admin/farmacias" element={<AdminFarmacias/>}/><Route path="admin/medicamentos" element={<AdminMedicamentos/>}/><Route path="admin/presentaciones" element={<AdminPresentaciones/>}/><Route path="admin/operadores" element={<AdminOperadores/>}/>
      <Route path="admin/usuarios" element={<AdminUsuarios/>}/><Route path="admin/usuarios/:id" element={<AdminUsuarioDetalle/>}/><Route path="admin/prescripciones" element={<AdminPrescripciones/>}/><Route path="admin/auditoria" element={<AdminAuditoria/>}/>
    </Route>
    <Route path="*" element={<NotFound/>}/>
  </Routes>;
}
