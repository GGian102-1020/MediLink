-- Paginated administrative history, ordered across all modules (11.8).
-- V1/V2 remain unchanged for databases already adopted by Flyway.
CREATE INDEX ix_auditoria_fecha ON auditoria (fecha_hora DESC, id_auditoria DESC);
