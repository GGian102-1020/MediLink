-- PIN de retiro: código de 6 dígitos que solo conoce el paciente y que el
-- operador debe ingresar para confirmar el retiro de una Reserva.
--
-- ADD COLUMN con DEFAULT volátil: PostgreSQL calcula un valor distinto por fila
-- al reescribir la tabla, sin ejecutar UPDATE ni disparar trg_reserva_inmutable.
-- Así funciona también sobre bases existentes con Reservas activas o terminales.
-- La API genera su propio PIN con SecureRandom al crear cada Reserva; el DEFAULT
-- cubre las filas insertadas sin PIN (seeds/scripts manuales).
ALTER TABLE reserva
    ADD COLUMN pin_retiro VARCHAR(6) NOT NULL
        DEFAULT lpad((floor(random() * 1000000))::int::text, 6, '0'),
    ADD COLUMN intentos_pin_fallidos SMALLINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_reserva_pin_retiro CHECK (pin_retiro ~ '^[0-9]{6}$'),
    ADD CONSTRAINT ck_reserva_intentos_pin CHECK (intentos_pin_fallidos >= 0);

-- Mismas reglas que V1, con dos cambios:
--  * pin_retiro forma parte de los datos base inmutables;
--  * una Reserva ACTIVA puede actualizar solo intentos_pin_fallidos sin cambiar de estado.
CREATE OR REPLACE FUNCTION medlink_proteger_reserva() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.estado_persistido <> 'ACTIVA' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Reserva terminal inmutable';
    END IF;
    IF (NEW.codigo_visible, NEW.id_usuario, NEW.id_farmacia,
        NEW.id_presentacion, NEW.id_item_prescripcion, NEW.cantidad,
        NEW.fecha_hora_creacion, NEW.fecha_hora_vencimiento,
        NEW.farmacia_nombre_snapshot, NEW.farmacia_direccion_snapshot,
        NEW.medicamento_nombre_snapshot, NEW.presentacion_descripcion_snapshot,
        NEW.pin_retiro)
       IS DISTINCT FROM
       (OLD.codigo_visible, OLD.id_usuario, OLD.id_farmacia,
        OLD.id_presentacion, OLD.id_item_prescripcion, OLD.cantidad,
        OLD.fecha_hora_creacion, OLD.fecha_hora_vencimiento,
        OLD.farmacia_nombre_snapshot, OLD.farmacia_direccion_snapshot,
        OLD.medicamento_nombre_snapshot, OLD.presentacion_descripcion_snapshot,
        OLD.pin_retiro) THEN
        RAISE EXCEPTION 'Datos base de Reserva inmutables';
    END IF;
    IF OLD.estado_persistido = 'ACTIVA'
       AND NEW.estado_persistido = 'ACTIVA'
       AND (to_jsonb(NEW) - 'intentos_pin_fallidos') IS DISTINCT FROM (to_jsonb(OLD) - 'intentos_pin_fallidos') THEN
        RAISE EXCEPTION 'Una Reserva ACTIVA solo puede cambiar de estado';
    END IF;
    IF NEW.intentos_pin_fallidos < OLD.intentos_pin_fallidos THEN
        RAISE EXCEPTION 'El contador de intentos de PIN no puede disminuir';
    END IF;
    RETURN NEW;
END;
$$;
