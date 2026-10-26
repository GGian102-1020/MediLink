-- Comprobación de la fixture, no instala ni modifica reglas funcionales.
-- Ejecutar luego de inicializar database/01 y 02, en base de PRUEBAS.
-- Si una aserción falla, psql aborta con ON_ERROR_STOP=1.
DO $$
DECLARE
    third_expiration timestamptz;
    suspension_start timestamptz;
    suspension_end timestamptz;
    pending_count integer;
BEGIN
    SELECT COUNT(*) INTO pending_count
    FROM reserva
    WHERE id_usuario=4 AND estado_persistido='VENCIDA';
    IF pending_count <> 3 THEN
        RAISE EXCEPTION 'RN-31..36: la fixture de Sofía requiere 3 reservas vencidas, hay %', pending_count;
    END IF;

    SELECT MAX(fecha_hora_vencimiento) INTO third_expiration
    FROM reserva WHERE id_usuario=4 AND estado_persistido='VENCIDA';
    SELECT COUNT(*) INTO pending_count FROM suspension WHERE id_usuario=4;
    IF pending_count <> 1 THEN
        RAISE EXCEPTION 'RN-35: la fixture de Sofía debe tener una sola suspensión, hay %', pending_count;
    END IF;
    SELECT fecha_hora_inicio, fecha_hora_fin INTO suspension_start, suspension_end
    FROM suspension WHERE id_usuario=4;
    IF suspension_start IS DISTINCT FROM third_expiration
       OR suspension_end IS DISTINCT FROM third_expiration + interval '168 hours' THEN
        RAISE EXCEPTION 'RN-33: la suspensión no coincide con el tercer vencimiento y 7 días exactos';
    END IF;
    IF EXISTS (
        SELECT 1 FROM (
            SELECT MIN(fecha_hora_vencimiento) first_at, MAX(fecha_hora_vencimiento) last_at
            FROM reserva WHERE id_usuario=4 AND estado_persistido='VENCIDA'
        ) x WHERE last_at-first_at > interval '30 days'
    ) THEN
        RAISE EXCEPTION 'RN-32: tres vencimientos fuera de la ventana de 30 días';
    END IF;
    -- Cada reserva de ejemplo fue descontada una vez. Cada vencida,
    -- restituida exactamente una vez; la cancelación operativa sin
    -- restitución NO debe generar una restitución ficticia.
    IF EXISTS (
        SELECT 1 FROM reserva r WHERE NOT EXISTS (
            SELECT 1 FROM movimiento_stock m
            WHERE m.id_reserva=r.id_reserva AND m.tipo='RESERVA_CONFIRMADA'
        )
    ) THEN
        RAISE EXCEPTION 'RN-05: reserva sin movimiento de confirmación';
    END IF;
    IF EXISTS (
        SELECT 1 FROM reserva r WHERE r.estado_persistido='VENCIDA' AND NOT EXISTS (
            SELECT 1 FROM movimiento_stock m
            WHERE m.id_reserva=r.id_reserva AND m.tipo='VENCIMIENTO'
        )
    ) THEN
        RAISE EXCEPTION 'RN-05: vencimiento sin movimiento de restitución';
    END IF;
    IF EXISTS (
        SELECT 1 FROM movimiento_stock WHERE id_reserva=10
        AND tipo IN ('VENCIMIENTO','CANCELACION_RESTITUCION')
    ) THEN
        RAISE EXCEPTION 'RN-05/6.3: cancelación sin restitución inventada';
    END IF;
    -- La presencia de un evento no basta: comprobar cantidad exacta y
    -- ausencia de duplicados para toda reserva precargada.
    IF EXISTS (
        SELECT 1
        FROM reserva r
        LEFT JOIN movimiento_stock m
          ON m.id_reserva=r.id_reserva AND m.tipo='RESERVA_CONFIRMADA'
        GROUP BY r.id_reserva, r.cantidad
        HAVING COUNT(m.id_movimiento) <> 1 OR MIN(m.delta) <> -r.cantidad
    ) THEN
        RAISE EXCEPTION 'RN-05: confirmación ausente, duplicada o con cantidad incorrecta';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM reserva r
        LEFT JOIN movimiento_stock m
          ON m.id_reserva=r.id_reserva AND m.tipo='VENCIMIENTO'
        WHERE r.estado_persistido='VENCIDA'
        GROUP BY r.id_reserva, r.cantidad
        HAVING COUNT(m.id_movimiento) <> 1 OR MIN(m.delta) <> r.cantidad
    ) THEN
        RAISE EXCEPTION 'RN-05: restitución por vencimiento incorrecta';
    END IF;
    IF EXISTS (
        SELECT 1 FROM reserva r JOIN movimiento_stock m ON m.id_reserva=r.id_reserva
        WHERE (r.estado_persistido <> 'VENCIDA' AND m.tipo='VENCIMIENTO')
           OR (r.id_reserva <> 4 AND m.tipo='CANCELACION_RESTITUCION')
    ) THEN
        RAISE EXCEPTION 'RN-05/6.3: restitución no esperada en la fixture';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM movimiento_stock m
        WHERE m.id_reserva=4 AND m.tipo='CANCELACION_RESTITUCION' AND m.delta=1
    ) THEN
        RAISE EXCEPTION 'RN-16: falta restitución de la cancelación de Usuario';
    END IF;
    -- Verificar continuidad y saldo final de los stocks que tienen historia;
    -- las filas sin movimiento precargado no se inventan en este control.
    IF EXISTS (
        SELECT 1 FROM (
            SELECT m.cantidad_anterior,
                   LAG(m.cantidad_nueva) OVER (
                       PARTITION BY m.id_stock ORDER BY m.fecha_hora, m.id_movimiento
                   ) AS saldo_previo
            FROM movimiento_stock m
        ) historial
        WHERE historial.saldo_previo IS NOT NULL
          AND historial.cantidad_anterior <> historial.saldo_previo
    ) THEN
        RAISE EXCEPTION 'RN-05: movimientos no encadenan sus saldos';
    END IF;
    IF EXISTS (
        SELECT 1 FROM stock s
        JOIN LATERAL (
            SELECT m.cantidad_nueva
            FROM movimiento_stock m
            WHERE m.id_stock=s.id_stock
            ORDER BY m.fecha_hora DESC, m.id_movimiento DESC
            LIMIT 1
        ) ultimo ON TRUE
        WHERE s.cantidad_disponible <> ultimo.cantidad_nueva
    ) THEN
        RAISE EXCEPTION 'RN-05: ultimo saldo no coincide con Stock precargado';
    END IF;
    -- Auditoría normativa de todos los retiros de la fixture.
    IF EXISTS (
        SELECT 1 FROM reserva r
        LEFT JOIN auditoria a
          ON a.tipo_entidad='RESERVA' AND a.entidad_id=r.id_reserva
         AND a.accion='CONFIRMAR_RETIRO' AND a.usuario_relacionado_id=r.id_usuario
        WHERE r.estado_persistido='COMPLETADA'
        GROUP BY r.id_reserva
        HAVING COUNT(a.id_auditoria) <> 1
    ) THEN
        RAISE EXCEPTION 'RN-44: retiro sin auditoría o auditoría duplicada';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM suspension s JOIN auditoria a
          ON a.tipo_entidad='SUSPENSION' AND a.entidad_id=s.id_suspension
         AND a.accion='CREAR_SUSPENSION' AND a.actor_rol='SISTEMA'
         AND a.usuario_relacionado_id=s.id_usuario
        WHERE s.id_usuario=4 AND a.fecha_hora=s.fecha_hora_inicio
          -- Same instant may be serialized with a different session timezone.
          AND (a.resumen_posterior->>'fechaHoraInicio')::timestamptz=s.fecha_hora_inicio
          AND (a.resumen_posterior->>'fechaHoraFin')::timestamptz=s.fecha_hora_fin
    ) THEN
        RAISE EXCEPTION 'RN-44/CV-02: fechas de auditoría de suspensión incoherentes';
    END IF;
END $$;
SELECT 'Fixture v1.4.3 verificada' AS resultado;
