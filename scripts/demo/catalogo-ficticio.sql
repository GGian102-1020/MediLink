-- Datos totalmente ficticios para demostracion universitaria.
-- No crea cuentas, contrasenas, prescripciones ni reservas.
-- Solo ejecutar sobre el compose aislado (medilink-demo).
BEGIN;
INSERT INTO farmacia (id_farmacia, nombre, direccion, telefono, email_contacto, estado, latitud, longitud)
VALUES
 (90001, 'Farmacia Demo Norte', 'Calle Inventada 123 (FICTICIA)', NULL, NULL, 'ACTIVA', NULL, NULL),
 (90002, 'Farmacia Demo Sur', 'Calle Inventada 456 (FICTICIA)', NULL, NULL, 'ACTIVA', NULL, NULL)
ON CONFLICT (id_farmacia) DO NOTHING;

INSERT INTO medicamento (id_medicamento, nombre, estado)
VALUES
 (90001, 'Ibuprofeno (demo)', 'ACTIVO'),
 (90002, 'Paracetamol (demo)', 'ACTIVO'),
 (90003, 'Loratadina (demo)', 'ACTIVO')
ON CONFLICT (id_medicamento) DO NOTHING;

INSERT INTO presentacion_medicamento
  (id_presentacion, id_medicamento, concentracion_texto, forma_farmaceutica,
   cantidad_envase, unidad_expendio, condicion_dispensa, estado)
VALUES
 (90001, 90001, '400 mg', 'Comprimidos', 20, 'Caja', 'VENTA_LIBRE', 'ACTIVA'),
 (90002, 90002, '500 mg', 'Comprimidos', 20, 'Caja', 'VENTA_LIBRE', 'ACTIVA'),
 (90003, 90003, '10 mg', 'Comprimidos', 10, 'Caja', 'VENTA_LIBRE', 'ACTIVA')
ON CONFLICT (id_presentacion) DO NOTHING;

INSERT INTO stock (id_farmacia, id_presentacion, cantidad_disponible, fecha_hora_actualizacion)
VALUES
 (90001, 90001, 20, now()),
 (90001, 90002, 12, now()),
 (90002, 90001, 10, now()),
 (90002, 90003, 15, now())
ON CONFLICT (id_farmacia, id_presentacion) DO NOTHING;
COMMIT;
