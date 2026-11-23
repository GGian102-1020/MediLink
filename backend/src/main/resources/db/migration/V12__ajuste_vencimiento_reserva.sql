-- Ajuste del vencimiento de una Reserva por el operador de la farmacia.
--
-- Hasta V11 el vencimiento era fijo: inicio + 4 horas, donde inicio es la
-- confirmación (si la Reserva la requiere) o la creación. Ahora el operador
-- puede moverlo (extender o acortar) dentro de un tope de 24 horas desde ese
-- mismo inicio. Las Reservas existentes no cambian: inicio + 4 h ya cumple.
--
-- Una Reserva PENDIENTE (requiere confirmación y todavía no la tiene) no se
-- ajusta: su plazo de retiro recién empieza al confirmarla.
ALTER TABLE reserva DROP CONSTRAINT ck_reserva_duracion;
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_duracion CHECK (
    fecha_hora_vencimiento > coalesce(fecha_hora_confirmacion, fecha_hora_creacion)
    AND fecha_hora_vencimiento <= coalesce(fecha_hora_confirmacion, fecha_hora_creacion) + INTERVAL '24 hours'
);

ALTER TYPE accion_auditoria ADD VALUE 'AJUSTAR_VENCIMIENTO_RESERVA';

-- Mismas reglas que V11, más una transición ACTIVA -> ACTIVA permitida:
-- ajustar el vencimiento de una Reserva no pendiente sin tocar la confirmación.
CREATE OR REPLACE FUNCTION medlink_proteger_reserva() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    confirmando BOOLEAN;
    ajustando BOOLEAN;
BEGIN
    IF OLD.estado_persistido <> 'ACTIVA' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Reserva terminal inmutable';
    END IF;
    confirmando := OLD.estado_persistido = 'ACTIVA' AND NEW.estado_persistido = 'ACTIVA'
        AND OLD.requiere_confirmacion
        AND OLD.fecha_hora_confirmacion IS NULL
        AND NEW.fecha_hora_confirmacion IS NOT NULL
        AND NEW.fecha_hora_confirmacion < OLD.fecha_hora_vencimiento;
    ajustando := OLD.estado_persistido = 'ACTIVA' AND NEW.estado_persistido = 'ACTIVA'
        AND NOT (OLD.requiere_confirmacion AND OLD.fecha_hora_confirmacion IS NULL)
        AND NEW.fecha_hora_confirmacion IS NOT DISTINCT FROM OLD.fecha_hora_confirmacion;
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
    IF NOT confirmando AND NOT ajustando
       AND (NEW.fecha_hora_vencimiento, NEW.fecha_hora_confirmacion)
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
