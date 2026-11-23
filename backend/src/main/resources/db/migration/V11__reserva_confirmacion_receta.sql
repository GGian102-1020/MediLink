-- Confirmación de receta por la farmacia.
--
-- Una Reserva BAJO_RECETA nace "pendiente": estado_persistido = 'ACTIVA',
-- requiere_confirmacion = true y fecha_hora_confirmacion = NULL. La farmacia
-- revisa la receta (adjunta o código) y la confirma; recién ahí el paciente ve
-- su PIN y corre el plazo de retiro de 4 horas, contado desde la confirmación.
-- Si la farmacia no la revisa dentro de las 4 horas desde la creación, vence
-- sin contar como incumplimiento del paciente.
--
-- Las Reservas existentes quedan con requiere_confirmacion = false: no cambian.
ALTER TABLE reserva
    ADD COLUMN requiere_confirmacion BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN fecha_hora_confirmacion TIMESTAMPTZ(3);

-- La duración sigue siendo 4 horas, pero se cuenta desde la confirmación si existe.
ALTER TABLE reserva DROP CONSTRAINT ck_reserva_duracion;
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_duracion CHECK (
    fecha_hora_vencimiento = coalesce(fecha_hora_confirmacion, fecha_hora_creacion) + INTERVAL '4 hours'
);
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_confirmacion CHECK (
    fecha_hora_confirmacion IS NULL
    OR (requiere_confirmacion AND fecha_hora_confirmacion >= fecha_hora_creacion)
);

ALTER TYPE accion_auditoria ADD VALUE 'CONFIRMAR_RESERVA';

-- Mismas reglas que V7, más una única transición ACTIVA -> ACTIVA permitida:
-- confirmar una Reserva pendiente (fija fecha_hora_confirmacion y recalcula el vencimiento).
CREATE OR REPLACE FUNCTION medlink_proteger_reserva() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    confirmando BOOLEAN;
BEGIN
    IF OLD.estado_persistido <> 'ACTIVA' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Reserva terminal inmutable';
    END IF;
    confirmando := OLD.estado_persistido = 'ACTIVA' AND NEW.estado_persistido = 'ACTIVA'
        AND OLD.requiere_confirmacion
        AND OLD.fecha_hora_confirmacion IS NULL
        AND NEW.fecha_hora_confirmacion IS NOT NULL
        AND NEW.fecha_hora_confirmacion < OLD.fecha_hora_vencimiento;
    IF (NEW.codigo_visible, NEW.id_usuario, NEW.id_farmacia,
        NEW.id_presentacion, NEW.id_item_prescripcion, NEW.cantidad,
        NEW.fecha_hora_creacion,
        NEW.farmacia_nombre_snapshot, NEW.farmacia_direccion_snapshot,
        NEW.medicamento_nombre_snapshot, NEW.presentacion_descripcion_snapshot,
        NEW.pin_retiro, NEW.requiere_confirmacion)
       IS DISTINCT FROM
       (OLD.codigo_visible, OLD.id_usuario, OLD.id_farmacia,
        OLD.id_presentacion, OLD.id_item_prescripcion, OLD.cantidad,
        OLD.fecha_hora_creacion,
        OLD.farmacia_nombre_snapshot, OLD.farmacia_direccion_snapshot,
        OLD.medicamento_nombre_snapshot, OLD.presentacion_descripcion_snapshot,
        OLD.pin_retiro, OLD.requiere_confirmacion) THEN
        RAISE EXCEPTION 'Datos base de Reserva inmutables';
    END IF;
    IF NOT confirmando AND (NEW.fecha_hora_vencimiento, NEW.fecha_hora_confirmacion)
        IS DISTINCT FROM (OLD.fecha_hora_vencimiento, OLD.fecha_hora_confirmacion) THEN
        RAISE EXCEPTION 'Vencimiento y confirmación de Reserva inmutables';
    END IF;
    IF OLD.estado_persistido = 'ACTIVA'
       AND NEW.estado_persistido = 'ACTIVA'
       AND (to_jsonb(NEW) - 'intentos_pin_fallidos' - 'fecha_hora_vencimiento' - 'fecha_hora_confirmacion')
           IS DISTINCT FROM
           (to_jsonb(OLD) - 'intentos_pin_fallidos' - 'fecha_hora_vencimiento' - 'fecha_hora_confirmacion') THEN
        RAISE EXCEPTION 'Una Reserva ACTIVA solo puede cambiar de estado';
    END IF;
    IF NEW.intentos_pin_fallidos < OLD.intentos_pin_fallidos THEN
        RAISE EXCEPTION 'El contador de intentos de PIN no puede disminuir';
    END IF;
    RETURN NEW;
END;
$$;
