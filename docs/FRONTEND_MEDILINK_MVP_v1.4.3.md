# Frontend MediLink - Implementación MVP v1.4.3

## Alcance

Nuevo módulo `frontend/` en React + TypeScript + Vite, con marca visual basada en los colores azul y turquesa del logo existente, interfaz adaptable y navegación por rol. **No se modificaron archivos Java, SQL ni configuración de producción del backend**.

### Cobertura visual

| Especificación / recorrido | Pantallas |
| --- | --- |
| Visitante: búsqueda de medicamentos y presentación | Inicio, catálogo, detalle de presentación |
| Disponibilidad por Farmacia | Lista de Farmacias y unidades libres por Presentación |
| Alta / acceso / cierre de sesión | Registro, login, logout, sesión tab-scoped |
| Usuario: crear Reserva de Venta Libre | Cantidad, Farmacia, confirmación, Idempotency-Key |
| Usuario: crear Reserva Bajo Receta | Código manual de Prescripción, sin listado propio de recetas |
| Usuario: Reservas y cancelación | Dashboard, listado filtrable, detalle, cancelación |
| Usuario / Operador / Admin: cuenta | Datos de perfil, cambio propio de contraseña |
| Operador: Reservas | Listado y consulta de detalle de Farmacia, retiro, cancelación operativa |
| Operador: Stock | Consulta, movimientos, ajuste absoluto con motivo |
| Administrador: gestión | Farmacias, Medicamentos, Presentaciones, Operadores, Usuarios |
| Administrador: Prescripciones | Alta, detalle de ítems, anulación |
| Administrador: seguimiento | Auditoría, historial de Reservas y Suspensiones por Usuario |

### Contratos significativos

- Base de API: `/api` a través de proxy Vite durante desarrollo; se evita modificar CORS en Spring Boot.
- Todas las consultas paginadas usan el envelope `PageDTO<T>` (content, page, size, totalElements, totalPages).
- Las acciones administrativas tienen rutas `/api/admin/**`; operaciones específicas de Farmacia usan `/api/farmacias/{id}/**`.
- `POST /api/reservas` usa `Idempotency-Key` UUID y JSON `farmaciaId`, `presentacionId`, `cantidad`, `codigoPrescripcion?`.
- Los errores de backend usan `codigo`, `mensaje`, `correlationId`; la interfaz enseña `mensaje` y no los datos internos.
- Se respetan causas operativas enumeradas y `restitucionStock: RESTITUIR | NO_RESTITUIR` cuando corresponde.
- La API permanece como autoridad de Stock, permisos, límites, Suspensiones, historial y tiempo de vencimiento.

### Seguridad

- Las rutas privadas comprueban rol en React **solo para la UX**; el backend es responsable de la autorización definitiva (SEC-06).
- Las sesiones usan token Bearer con alcance de pestaña; se limpia ante 401 y logout.
- No hay credenciales ni contraseñas hardcodeadas, ni URLs absolutas de backend en el bundle.
- Las órdenes que modifican datos requieren una interacción explícita del Usuario.

### Validación técnica

Comandos desde `frontend/`:

```bash
npm install
npm run typecheck
npm test
npm run build
```

La versión actual se entrega como **código a validar por CI**: en el entorno de autoría no hubo conectividad al registro npm y no se pudieron instalar las dependencias para ejecutar el build completo. Se realizó validación de sintaxis TypeScript/TSX por el compilador global y revisión estática de contrato y rutas. El workflow `.github/workflows/medlink-frontend.yml` ejecutará el resto en GitHub al abrir el PR.

### Revisión manual de aceptación recomendada

1. Inicio público / catálogo / presentación / disponibilidad sin autenticación.
2. Registro, login y cambio de contraseña con datos válidos e inválidos.
3. Usuario: reservar OTC y BAJO_RECETA, repetir intento y observar idempotencia.
4. Usuario: ver estado, cancelar y verificar Stock liberado; verificar vencimiento.
5. Operador: listar únicamente su farmacia, confirmar retiro, cancelar por causas permitidas.
6. Admin: gestionar entidades, cuentas y Prescripciones; consultar Auditoría, Reservas y Suspensiones.
7. Seguridad: 401/403/404, acceso entre Farmacias y roles, formulario inválido, pérdida de red.
8. Diseño responsive, accesibilidad por teclado y Safari/Firefox/Chrome.

## Archivos nuevos

- `frontend/src/lib/`: contratos TS, cliente API tipado, formateo, hook de carga.
- `frontend/src/context/`: sesión y perfil actual.
- `frontend/src/components/`: navegación y componentes de UI.
- `frontend/src/pages/`: Visitante, Autenticación, Usuario, Operador y Administrador.
- `frontend/src/styles.css`: identidad visual y diseño responsive.
- `frontend/tests/`: pruebas del cliente API.
- `.github/workflows/medlink-frontend.yml`: verificación independiente para React.

**No se alteran el motor de Suspensiones, las migraciones, la reserva de Stock ni ninguna operación Java.**
