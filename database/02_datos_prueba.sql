-- =====================================================================
--  MEDLINK v1.4.3 - Datos de prueba (MVP)
--  Se ejecuta DESPUES de 01_medlink_esquema.sql.
--  Todas las cuentas usan la contraseña:  Password123!
--  (hash bcrypt $2a$, compatible con BCrypt de Spring / bcryptjs / passlib)
--  Las fechas son relativas al momento de inicialización (now()), así
--  las reservas ACTIVAS siempre arrancan vigentes (duración fija: 4 h).
-- =====================================================================
SET timezone TO 'America/Argentina/Buenos_Aires';

-- pgcrypto: solo para generar los hashes bcrypt de los datos de prueba
CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

-- ---------------------------------------------------------------------
--  Cuentas (correo_normalizado lo calcula la BD)
--   1      administrador
--   2..7   operadores (id_cuenta = id_operador + 1)
--   8..12  usuarios    (id_cuenta = id_usuario + 7)
--  Cuenta y perfil van en la misma transacción (triggers diferidos).
-- ---------------------------------------------------------------------
INSERT INTO cuenta (id_cuenta, correo, password_hash, rol, estado, fecha_hora_creacion)
SELECT v.id, v.correo, crypt('Password123!', gen_salt('bf', 10)),
       v.rol::rol_cuenta, v.estado::estado_cuenta, now() - v.antiguedad
FROM (VALUES
 (1,  'admin@medlink.test',                 'ADMINISTRADOR', 'ACTIVA',        interval '90 days'),
 (2,  'carla.mendez@farmacia-central.test', 'OPERADOR',      'ACTIVA',        interval '79 days'),
 (3,  'diego.suarez@farmacia-central.test', 'OPERADOR',      'ACTIVA',        interval '40 days'),
 (4,  'laura.benitez@fsanmartin.test',      'OPERADOR',      'ACTIVA',        interval '74 days'),
 (5,  'pablo.romero@delcentro.test',        'OPERADOR',      'ACTIVA',        interval '69 days'),
 (6,  'valeria.torres@salud.test',          'OPERADOR',      'ACTIVA',        interval '59 days'),
 (7,  'hernan.acosta@flores.test',          'OPERADOR',      'DESHABILITADA', interval '49 days'),
 (8,  'juan.perez@mail.test',               'USUARIO',       'ACTIVA',        interval '45 days'),
 (9,  'maria.gonzalez@mail.test',           'USUARIO',       'ACTIVA',        interval '40 days'),
 (10, 'lucas.fernandez@mail.test',          'USUARIO',       'ACTIVA',        interval '35 days'),
 (11, 'Sofia.Rodriguez@Mail.test',          'USUARIO',       'ACTIVA',        interval '30 days'),   -- correo con mayúsculas: se loguea igual
 (12, 'martin.lopez@mail.test',             'USUARIO',       'DESHABILITADA', interval '25 days')
) AS v(id, correo, rol, estado, antiguedad);

INSERT INTO administrador (id_administrador, id_cuenta, nombre, apellido) VALUES
 (1, 1, 'Admin', 'Sistema');

-- ---------------------------------------------------------------------
--  Farmacias (CABA)
-- ---------------------------------------------------------------------
INSERT INTO farmacia (id_farmacia, nombre, direccion, telefono, email_contacto, estado) VALUES
 (1, 'Farmacia Central Palermo', 'Av. Santa Fe 3200, Palermo, CABA',       '011 4821-1000', 'palermo@farmacia-central.test', 'ACTIVA'),
 (2, 'Farmacia San Martín',      'Av. Rivadavia 5100, Caballito, CABA',    '011 4902-2000', 'contacto@fsanmartin.test',      'ACTIVA'),
 (3, 'Farmacia del Centro',      'Av. Corrientes 1200, San Nicolás, CABA', '011 4381-3000', 'info@delcentro.test',           'ACTIVA'),
 (4, 'Farmacia Belgrano Salud',  'Av. Cabildo 2100, Belgrano, CABA',       '011 4784-4000', 'belgrano@salud.test',           'ACTIVA'),
 (5, 'Farmacia Flores',          'Av. Rivadavia 7000, Flores, CABA',       NULL,            NULL,                            'INACTIVA');

-- ---------------------------------------------------------------------
--  Operadores de farmacia
-- ---------------------------------------------------------------------
INSERT INTO operador (id_operador, id_cuenta, id_farmacia, nombre, apellido) VALUES
 (1, 2, 1, 'Carla',   'Méndez'),
 (2, 3, 1, 'Diego',   'Suárez'),
 (3, 4, 2, 'Laura',   'Benítez'),
 (4, 5, 3, 'Pablo',   'Romero'),
 (5, 6, 4, 'Valeria', 'Torres'),
 (6, 7, 5, 'Hernán',  'Acosta');    -- cuenta deshabilitada

-- ---------------------------------------------------------------------
--  Usuarios / Pacientes
-- ---------------------------------------------------------------------
INSERT INTO usuario (id_usuario, id_cuenta, nombre, apellido) VALUES
 (1, 8,  'Juan',   'Pérez'),
 (2, 9,  'María',  'González'),
 (3, 10, 'Lucas',  'Fernández'),
 (4, 11, 'Sofía',  'Rodríguez'),   -- suspendida (ver tabla suspension)
 (5, 12, 'Martín', 'López');       -- cuenta deshabilitada

-- ---------------------------------------------------------------------
--  Medicamentos
-- ---------------------------------------------------------------------
INSERT INTO medicamento (id_medicamento, nombre, estado) VALUES
 (1,  'Ibuprofeno',        'ACTIVO'),
 (2,  'Paracetamol',       'ACTIVO'),
 (3,  'Amoxicilina',       'ACTIVO'),
 (4,  'Enalapril',         'ACTIVO'),
 (5,  'Levotiroxina',      'ACTIVO'),
 (6,  'Omeprazol',         'ACTIVO'),
 (7,  'Losartán',          'ACTIVO'),
 (8,  'Metformina',        'ACTIVO'),
 (9,  'Salbutamol',        'ACTIVO'),
 (10, 'Atorvastatina',     'ACTIVO'),
 (11, 'Loratadina',        'ACTIVO'),
 (12, 'Insulina glargina', 'ACTIVO'),
 (13, 'Ranitidina',        'INACTIVO');   -- discontinuado

-- ---------------------------------------------------------------------
--  Presentaciones  (cantidad_envase = comprimidos / ml / dosis por envase)
-- ---------------------------------------------------------------------
INSERT INTO presentacion_medicamento (id_presentacion, id_medicamento, concentracion_texto, forma_farmaceutica, cantidad_envase, unidad_expendio, condicion_dispensa, estado) VALUES
 (1,  1,  '400 mg',        'Comprimidos',             20,  'Caja',      'VENTA_LIBRE', 'ACTIVA'),
 (2,  1,  '600 mg',        'Comprimidos',             10,  'Caja',      'VENTA_LIBRE', 'ACTIVA'),
 (3,  1,  '100 mg/5 ml',   'Suspensión oral',         90,  'Frasco',    'VENTA_LIBRE', 'ACTIVA'),
 (4,  2,  '500 mg',        'Comprimidos',             20,  'Caja',      'VENTA_LIBRE', 'ACTIVA'),
 (5,  2,  '1 g',           'Comprimidos',             8,   'Caja',      'VENTA_LIBRE', 'ACTIVA'),
 (6,  3,  '500 mg',        'Comprimidos',             16,  'Caja',      'BAJO_RECETA', 'ACTIVA'),
 (7,  3,  '250 mg/5 ml',   'Polvo para suspensión',   60,  'Frasco',    'BAJO_RECETA', 'ACTIVA'),
 (8,  4,  '10 mg',         'Comprimidos',             30,  'Caja',      'BAJO_RECETA', 'ACTIVA'),
 (9,  5,  '100 mcg',       'Comprimidos',             50,  'Caja',      'BAJO_RECETA', 'ACTIVA'),
 (10, 6,  '20 mg',         'Cápsulas',                14,  'Caja',      'VENTA_LIBRE', 'ACTIVA'),
 (11, 7,  '50 mg',         'Comprimidos recubiertos', 30,  'Caja',      'BAJO_RECETA', 'ACTIVA'),
 (12, 8,  '850 mg',        'Comprimidos',             60,  'Caja',      'BAJO_RECETA', 'ACTIVA'),
 (13, 9,  '100 mcg/dosis', 'Aerosol para inhalación', 200, 'Inhalador', 'BAJO_RECETA', 'ACTIVA'),
 (14, 10, '20 mg',         'Comprimidos recubiertos', 30,  'Caja',      'BAJO_RECETA', 'ACTIVA'),
 (15, 11, '10 mg',         'Comprimidos',             10,  'Caja',      'VENTA_LIBRE', 'ACTIVA'),
 (16, 12, '100 UI/ml',     'Solución inyectable',     3,   'Lapicera',  'BAJO_RECETA', 'ACTIVA'),
 (17, 13, '150 mg',        'Comprimidos',             20,  'Caja',      'VENTA_LIBRE', 'INACTIVA');

-- ---------------------------------------------------------------------
--  Stock disponible para reserva
--  (ya descontadas las unidades de las reservas ACTIVAS cargadas abajo)
-- ---------------------------------------------------------------------
INSERT INTO stock (id_farmacia, id_presentacion, cantidad_disponible, fecha_hora_actualizacion) VALUES
 -- Farmacia 1 - Central Palermo
 (1, 1, 15, now() - interval '1 day'),     -- ajuste manual 10 -> 15
 (1, 2,  8, now() - interval '1 day'),
 (1, 4, 18, now() - interval '2 hours'),   -- 20 - 2 (reserva 1 activa)
 (1, 5, 10, now() - interval '1 day'),
 (1, 6,  4, now() - interval '1 day'),
 (1, 8,  6, now() - interval '1 day'),
 (1, 10, 12, now() - interval '1 day'),
 (1, 11, 5, now() - interval '1 day'),
 (1, 12, 7, now() - interval '1 day'),
 (1, 15, 9, now() - interval '1 day'),
 (1, 16, 0, now() - interval '3 days'),    -- sin stock
 -- Farmacia 2 - San Martín
 (2, 1, 10, now() - interval '2 days'),
 (2, 3,  4, now() - interval '2 days'),
 (2, 4,  6, now() - interval '2 days'),
 (2, 6,  1, now() - interval '1 hour'),    -- 2 - 1 (reserva 2 activa) -> última unidad
 (2, 7,  3, now() - interval '2 days'),
 (2, 9,  5, now() - interval '2 days'),
 (2, 10, 8, now() - interval '2 days'),
 (2, 13, 2, now() - interval '2 days'),
 (2, 14, 4, now() - interval '2 days'),
 -- Farmacia 3 - del Centro
 (3, 1,  5, now() - interval '1 day'),
 (3, 4, 12, now() - interval '1 day'),
 (3, 8,  3, now() - interval '8 days'),    -- 5 - 2 (reserva 11 completada)
 (3, 11, 2, now() - interval '1 day'),
 (3, 12, 10, now() - interval '1 day'),
 (3, 15, 6, now() - interval '1 day'),
 (3, 16, 1, now() - interval '3 days'),    -- insulina: única farmacia con stock
 -- Farmacia 4 - Belgrano Salud
 (4, 2,  7, now() - interval '1 day'),
 (4, 3,  2, now() - interval '1 day'),
 (4, 5,  4, now() - interval '1 day'),
 (4, 9,  3, now() - interval '1 day'),
 (4, 10, 0, now() - interval '1 day'),
 (4, 13, 1, now() - interval '1 day'),
 (4, 14, 6, now() - interval '1 day'),
 -- Farmacia 5 - Flores (INACTIVA: no debería aparecer en búsquedas)
 (5, 1, 10, now() - interval '50 days'),
 (5, 4, 10, now() - interval '50 days');

-- ---------------------------------------------------------------------
--  Prescripciones simuladas (servicio Mock)
--  La vigencia se deriva de fecha_vencimiento.
-- ---------------------------------------------------------------------
INSERT INTO prescripcion (id_prescripcion, codigo_externo, id_usuario, fecha_emision, fecha_vencimiento, estado, fecha_hora_creacion) VALUES
 (1, 'RX-2026-000001', 1, current_date - 10, current_date + 20, 'ACTIVA',  now() - interval '10 days'),  -- Juan: amoxicilina + enalapril
 (2, 'RX-2026-000002', 1, current_date - 60, current_date - 30, 'ACTIVA',  now() - interval '60 days'),  -- Juan: vencida por fecha
 (3, 'RX-2026-000003', 2, current_date - 5,  current_date + 25, 'ACTIVA',  now() - interval '5 days'),   -- María: insulina + metformina
 (4, 'RX-2026-000004', 2, current_date - 15, current_date + 15, 'ANULADA', now() - interval '15 days'),  -- María: anulada
 (5, 'RX-2026-000005', 3, current_date - 2,  current_date + 28, 'ACTIVA',  now() - interval '2 days'),   -- Lucas: salbutamol
 (6, 'RX-2026-000006', 3, current_date - 20, current_date + 10, 'ACTIVA',  now() - interval '20 days'),  -- Lucas: ya totalmente utilizada
 (7, 'RX-2026-000007', 4, current_date - 3,  current_date + 27, 'ACTIVA',  now() - interval '3 days'),   -- Sofía (suspendida)
 (8, 'RX-2026-000008', 1, current_date - 1,  current_date + 29, 'ACTIVA',  now() - interval '1 day');    -- Juan: losartán sin usar

INSERT INTO item_prescripcion (id_item_prescripcion, id_prescripcion, id_presentacion, cantidad_autorizada, cantidad_comprometida, cantidad_utilizada) VALUES
 (1,  1, 6,  1, 1, 0),   -- Amoxicilina 500 mg x1  (comprometida por reserva 2 activa)
 (2,  1, 8,  2, 0, 2),   -- Enalapril 10 mg x2     (utilizada por reserva 11)
 (3,  2, 11, 1, 0, 0),   -- Losartán 50 mg         (receta vencida)
 (4,  3, 16, 1, 0, 1),   -- Insulina glargina x1   (utilizada por reserva 3)
 (5,  3, 12, 3, 0, 0),   -- Metformina 850 mg x3   (libre)
 (6,  4, 9,  1, 0, 0),   -- Levotiroxina           (receta anulada)
 (7,  5, 13, 1, 0, 0),   -- Salbutamol x1          (libre)
 (8,  6, 14, 1, 0, 1),   -- Atorvastatina x1       (utilizada por reserva 5)
 (9,  7, 9,  2, 0, 0),   -- Levotiroxina x2        (usuaria suspendida)
 (10, 8, 11, 2, 0, 0);   -- Losartán 50 mg x2      (libre)

-- ---------------------------------------------------------------------
--  Reservas (duración fija del MVP: 4 h)
--  codigo_visible: 12 caracteres Crockford Base32 en mayúsculas (sin I, L, O, U)
--  Los snapshots se toman de farmacia / medicamento / presentación.
-- ---------------------------------------------------------------------
INSERT INTO reserva (id_reserva, codigo_visible, id_usuario, id_farmacia, id_presentacion, id_item_prescripcion,
                     cantidad, estado_persistido, fecha_hora_creacion, fecha_hora_vencimiento,
                     fecha_hora_cancelacion, fecha_hora_retiro, origen_cancelacion, causa_cancelacion, motivo_cancelacion,
                     farmacia_nombre_snapshot, farmacia_direccion_snapshot,
                     medicamento_nombre_snapshot, presentacion_descripcion_snapshot)
SELECT r.id, r.codigo, r.id_usuario, r.id_farmacia, r.id_presentacion, r.id_item,
       r.cantidad, r.estado::estado_reserva, r.creacion, r.creacion + interval '4 hours',
       r.creacion + r.cancelacion, r.creacion + r.retiro,
       r.origen::origen_cancelacion, r.causa::causa_cancelacion, r.motivo,
       f.nombre, f.direccion,
       m.nombre, p.concentracion_texto || ' - ' || p.forma_farmaceutica || ' - ' || p.unidad_expendio
                 || ' x ' || p.cantidad_envase
FROM (VALUES
 -- ACTIVAS (vencen dentro de 2 h y 3 h)
 (1,  '7K2Q9MA4BC01', 1, 1, 4,  NULL::bigint, 2, 'ACTIVA',     now() - interval '2 hours',  NULL::interval, NULL::interval, NULL, NULL, NULL::varchar),
 (2,  '3HX8PVD5EF02', 1, 2, 6,  1,            1, 'ACTIVA',     now() - interval '1 hour',   NULL, NULL, NULL, NULL, NULL),
 -- COMPLETADAS (retiro antes del vencimiento)
 (3,  '9TB4KCG6HJ03', 2, 3, 16, 4,            1, 'COMPLETADA', now() - interval '3 days',   NULL, interval '2 hours', NULL, NULL, NULL),
 (5,  '2MF6WDP8QR05', 3, 4, 14, 8,            1, 'COMPLETADA', now() - interval '15 days',  NULL, interval '3 hours', NULL, NULL, NULL),
 (11, '5RT8VFJ4KM11', 1, 3, 8,  2,            2, 'COMPLETADA', now() - interval '8 days',   NULL, interval '1 hour',  NULL, NULL, NULL),
 -- CANCELADAS
 (4,  '5QN1ZRK7MN04', 2, 1, 10, NULL,         1, 'CANCELADA',  now() - interval '4 days',   interval '1 hour', NULL,
      'USUARIO',  'CANCELACION_USUARIO',           'Ya no lo necesito'),                                      -- cancelación oportuna
 (10, '4JW7YTF3GH10', 2, 4, 2,  NULL,         1, 'CANCELADA',  now() - interval '7 days',   interval '3 hours', NULL,
      'OPERADOR', 'PRODUCTO_NO_APTO_PARA_ENTREGA', 'Unidad dañada detectada en el control de la farmacia'),   -- no penaliza
 -- VENCIDAS
 (6,  '6PB2NHS9TV06', 3, 2, 1,  NULL,         1, 'VENCIDA',    now() - interval '10 days',  NULL, NULL, NULL, NULL, NULL),
 (7,  '1CG5XEW0XY07', 4, 1, 15, NULL,         1, 'VENCIDA',    now() - interval '20 days',  NULL, NULL, NULL, NULL, NULL),
 (8,  '3ZM9QAZ1AB08', 4, 3, 4,  NULL,         2, 'VENCIDA',    now() - interval '12 days',  NULL, NULL, NULL, NULL, NULL),
 (9,  '8VD3KSC2DE09', 4, 2, 10, NULL,         1, 'VENCIDA',    now() - interval '6 days',   NULL, NULL, NULL, NULL, NULL)
) AS r(id, codigo, id_usuario, id_farmacia, id_presentacion, id_item, cantidad, estado, creacion,
       cancelacion, retiro, origen, causa, motivo)
JOIN farmacia                 f ON f.id_farmacia     = r.id_farmacia
JOIN presentacion_medicamento p ON p.id_presentacion = r.id_presentacion
JOIN medicamento              m ON m.id_medicamento  = p.id_medicamento;

-- ---------------------------------------------------------------------
--  Movimientos de stock (coherentes con el stock actual cargado arriba)
--  AJUSTE_MANUAL nunca lleva reserva; el resto siempre.
-- ---------------------------------------------------------------------
INSERT INTO movimiento_stock (id_stock, id_reserva, tipo, cantidad_anterior, cantidad_nueva, delta, fecha_hora, actor_cuenta_id, motivo)
SELECT s.id_stock, v.id_reserva, v.tipo::tipo_movimiento_stock, v.anterior, v.nueva, v.nueva - v.anterior,
       v.fecha_hora, v.actor, v.motivo
FROM (VALUES
 -- Ajuste manual de Carla (F1, Ibuprofeno 400)
 (1, 1,  NULL::bigint, 'AJUSTE_MANUAL',           10, 15, now() - interval '1 day',  2::bigint, 'Reposición'::varchar),
 -- Reserva 1 activa (Juan, F1, Paracetamol 500)
 (1, 4,  1,            'RESERVA_CONFIRMADA',      20, 18, now() - interval '2 hours',  8,    NULL),
 -- Reserva 2 activa (Juan, F2, Amoxicilina) -> queda la última unidad
 (2, 6,  2,            'RESERVA_CONFIRMADA',       2,  1, now() - interval '1 hour',   8,    NULL),
 -- Reserva 3 completada (María, F3, Insulina): el retiro no devuelve stock
 (3, 16, 3,            'RESERVA_CONFIRMADA',       2,  1, now() - interval '3 days',   9,    NULL),
 -- Reserva 11 completada (Juan, F3, Enalapril x2)
 (3, 8,  11,           'RESERVA_CONFIRMADA',       5,  3, now() - interval '8 days',   8,    NULL),
 -- Reserva 4 cancelada por María (F1, Omeprazol)
 (1, 10, 4,            'RESERVA_CONFIRMADA',      12, 11, now() - interval '4 days',   9,    NULL),
 (1, 10, 4,            'CANCELACION_RESTITUCION', 11, 12, now() - interval '4 days' + interval '1 hour', 9, NULL),
 -- Reserva 5 completada (Lucas, F4, Atorvastatina): al retirar no cambia stock
 (4, 14, 5,            'RESERVA_CONFIRMADA',       7,  6, now() - interval '15 days', 10, NULL),
 -- Reserva 6 vencida (Lucas, F2, Ibuprofeno 400)
 (2, 1,  6,            'RESERVA_CONFIRMADA',      10,  9, now() - interval '10 days', 10, NULL),
 (2, 1,  6,            'VENCIMIENTO',              9, 10, now() - interval '10 days' + interval '4 hours', NULL, NULL),
 -- Reserva 7 vencida (Sofía, F1, Loratadina)
 (1, 15, 7,            'RESERVA_CONFIRMADA',       9,  8, now() - interval '20 days', 11, NULL),
 (1, 15, 7,            'VENCIMIENTO',              8,  9, now() - interval '20 days' + interval '4 hours', NULL, NULL),
 -- Reserva 8 vencida (Sofía, F3, Paracetamol x2)
 (3, 4,  8,            'RESERVA_CONFIRMADA',      12, 10, now() - interval '12 days', 11, NULL),
 (3, 4,  8,            'VENCIMIENTO',             10, 12, now() - interval '12 days' + interval '4 hours', NULL, NULL),
 -- Reserva 9 vencida (Sofía, F2, Omeprazol): tercera expiración, dispara suspensión
 (2, 10, 9,            'RESERVA_CONFIRMADA',       8,  7, now() - interval '6 days',  11, NULL),
 (2, 10, 9,            'VENCIMIENTO',              7,  8, now() - interval '6 days' + interval '4 hours', NULL, NULL),
 -- Reserva 10 cancelada por el Operador (F4, Ibuprofeno 600): sin restitución
 (4, 2, 10,            'RESERVA_CONFIRMADA',       8,  7, now() - interval '7 days',  9, NULL)
) AS v(id_farmacia, id_presentacion, id_reserva, tipo, anterior, nueva, fecha_hora, actor, motivo)
JOIN stock s ON s.id_farmacia = v.id_farmacia AND s.id_presentacion = v.id_presentacion
ORDER BY v.fecha_hora;

-- ---------------------------------------------------------------------
--  Suspensiones (duración normativa: 7 días exactos = 168 h)
--  RN-31..36: 3 vencimientos computables dentro de 30 días; cancelar no penaliza.
--  Sofía: reservas 7, 8 y 9 vencidas; el tercer vencimiento inicia la suspensión.
-- ---------------------------------------------------------------------
INSERT INTO suspension (id_suspension, id_usuario, fecha_hora_inicio, fecha_hora_fin, motivo) VALUES
 (1, 4, now() - interval '6 days' + interval '4 hours',
        now() - interval '6 days' + interval '4 hours' + interval '168 hours', 'INCUMPLIMIENTOS_REITERADOS');

-- ---------------------------------------------------------------------
--  Auditoría (resumen_posterior obligatorio, RN-44)
-- ---------------------------------------------------------------------
INSERT INTO auditoria (fecha_hora, actor_cuenta_id, actor_rol, accion, tipo_entidad, entidad_id, usuario_relacionado_id, motivo, resumen_anterior, resumen_posterior) VALUES
 (now() - interval '80 days', 1, 'ADMINISTRADOR', 'CREAR_FARMACIA', 'FARMACIA', 1, NULL, NULL,
  NULL, '{"nombre": "Farmacia Central Palermo", "estado": "ACTIVA"}'),
 (now() - interval '79 days', 1, 'ADMINISTRADOR', 'CREAR_OPERADOR', 'OPERADOR', 1, NULL, NULL,
  NULL, '{"nombre": "Carla Méndez", "id_farmacia": 1}'),
 (now() - interval '50 days', 1, 'ADMINISTRADOR', 'DESACTIVAR_FARMACIA', 'FARMACIA', 5, NULL, 'Baja solicitada por el establecimiento',
  '{"estado": "ACTIVA"}', '{"estado": "INACTIVA"}'),
 (now() - interval '49 days', 1, 'ADMINISTRADOR', 'DESHABILITAR_CUENTA', 'CUENTA', 7, NULL, 'Farmacia dada de baja',
  '{"estado": "ACTIVA"}', '{"estado": "DESHABILITADA"}'),
 (now() - interval '14 days', 1, 'ADMINISTRADOR', 'ANULAR_PRESCRIPCION', 'PRESCRIPCION', 4, 2, 'Receta emitida con error',
  '{"estado": "ACTIVA"}', '{"estado": "ANULADA"}'),
 (now() - interval '10 days', 1, 'ADMINISTRADOR', 'DESHABILITAR_CUENTA', 'CUENTA', 12, 5, 'Solicitud del titular',
  '{"estado": "ACTIVA"}', '{"estado": "DESHABILITADA"}'),
 (now() - interval '3 days' + interval '2 hours', 5, 'OPERADOR', 'CONFIRMAR_RETIRO', 'RESERVA', 3, 2, NULL,
  '{"estado": "ACTIVA"}', '{"estado": "COMPLETADA"}'),
 (now() - interval '15 days' + interval '3 hours', 6, 'OPERADOR', 'CONFIRMAR_RETIRO', 'RESERVA', 5, 3, NULL,
  '{"estado": "ACTIVA"}', '{"estado": "COMPLETADA"}'),
 (now() - interval '8 days' + interval '1 hour', 5, 'OPERADOR', 'CONFIRMAR_RETIRO', 'RESERVA', 11, 1, NULL,
  '{"estado": "ACTIVA"}', '{"estado": "COMPLETADA"}'),
 (now() - interval '7 days' + interval '3 hours', 6, 'OPERADOR', 'CANCELAR_RESERVA_OPERATIVA', 'RESERVA', 10, 2,
  'Unidad dañada detectada en el control de la farmacia',
  '{"estado": "ACTIVA"}', '{"estado": "CANCELADA", "causa": "PRODUCTO_NO_APTO_PARA_ENTREGA"}'),
 (now() - interval '1 day', 2, 'OPERADOR', 'AJUSTAR_STOCK', 'STOCK',
  (SELECT id_stock FROM stock WHERE id_farmacia = 1 AND id_presentacion = 1), NULL, 'Reposición',
  '{"cantidad_disponible": 10}', '{"cantidad_disponible": 15}');

-- Auditoria del sistema: tomar las fechas ya persistidas evita divergencias
-- de precision (TIMESTAMPTZ(3)) entre la Suspension y su snapshot.
INSERT INTO auditoria (fecha_hora, actor_cuenta_id, actor_rol, accion, tipo_entidad, entidad_id,
                      usuario_relacionado_id, motivo, resumen_anterior, resumen_posterior)
SELECT s.fecha_hora_inicio, NULL, 'SISTEMA', 'CREAR_SUSPENSION', 'SUSPENSION', s.id_suspension,
       s.id_usuario, NULL, NULL,
       jsonb_build_object('fechaHoraInicio', s.fecha_hora_inicio, 'fechaHoraFin', s.fecha_hora_fin)
FROM suspension s WHERE s.id_suspension = 1 AND s.id_usuario = 4;

-- ---------------------------------------------------------------------
--  Sincronizar secuencias IDENTITY con los ids cargados a mano
-- ---------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('cuenta',                   'id_cuenta'),            (SELECT max(id_cuenta)            FROM cuenta));
SELECT setval(pg_get_serial_sequence('administrador',            'id_administrador'),     (SELECT max(id_administrador)     FROM administrador));
SELECT setval(pg_get_serial_sequence('farmacia',                 'id_farmacia'),          (SELECT max(id_farmacia)          FROM farmacia));
SELECT setval(pg_get_serial_sequence('operador',                 'id_operador'),          (SELECT max(id_operador)          FROM operador));
SELECT setval(pg_get_serial_sequence('usuario',                  'id_usuario'),           (SELECT max(id_usuario)           FROM usuario));
SELECT setval(pg_get_serial_sequence('medicamento',              'id_medicamento'),       (SELECT max(id_medicamento)       FROM medicamento));
SELECT setval(pg_get_serial_sequence('presentacion_medicamento', 'id_presentacion'),      (SELECT max(id_presentacion)      FROM presentacion_medicamento));
SELECT setval(pg_get_serial_sequence('prescripcion',             'id_prescripcion'),      (SELECT max(id_prescripcion)      FROM prescripcion));
SELECT setval(pg_get_serial_sequence('item_prescripcion',        'id_item_prescripcion'), (SELECT max(id_item_prescripcion) FROM item_prescripcion));
SELECT setval(pg_get_serial_sequence('reserva',                  'id_reserva'),           (SELECT max(id_reserva)           FROM reserva));
SELECT setval(pg_get_serial_sequence('suspension',               'id_suspension'),        (SELECT max(id_suspension)        FROM suspension));

COMMIT;

-- Estadísticas para el planificador (y para que DBeaver muestre filas estimadas)
ANALYZE;
