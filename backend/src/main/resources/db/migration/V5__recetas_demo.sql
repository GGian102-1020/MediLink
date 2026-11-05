CREATE TABLE receta_demo (
    id_receta_demo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lote UUID NOT NULL,
    codigo VARCHAR(36) NOT NULL UNIQUE CHECK (codigo ~ '^MLD-[0-9A-F]{32}$'),
    estado VARCHAR(10) NOT NULL DEFAULT 'DISPONIBLE',
    fecha_emision DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL CHECK (fecha_vencimiento >= fecha_emision),
    fecha_creacion TIMESTAMPTZ(3) NOT NULL,
    id_presentacion BIGINT NOT NULL REFERENCES presentacion_medicamento(id_presentacion),
    medicamento VARCHAR(160) NOT NULL,
    concentracion VARCHAR(80) NOT NULL,
    presentacion VARCHAR(255) NOT NULL,
    cantidad_autorizada INTEGER NOT NULL DEFAULT 1 CHECK (cantidad_autorizada = 1),
    id_usuario BIGINT REFERENCES usuario(id_usuario),
    id_prescripcion BIGINT UNIQUE REFERENCES prescripcion(id_prescripcion),
    fecha_asignacion TIMESTAMPTZ(3),
    CONSTRAINT ck_receta_demo_asignacion CHECK (
        (estado = 'DISPONIBLE' AND id_usuario IS NULL AND id_prescripcion IS NULL AND fecha_asignacion IS NULL)
        OR
        (estado = 'ASIGNADA' AND id_usuario IS NOT NULL AND id_prescripcion IS NOT NULL AND fecha_asignacion IS NOT NULL)
    )
);

CREATE INDEX ix_receta_demo_lote ON receta_demo (lote, id_receta_demo);
ALTER TYPE tipo_entidad_auditoria ADD VALUE 'RECETA_DEMO';
ALTER TYPE accion_auditoria ADD VALUE 'GENERAR_RECETA_DEMO';
ALTER TYPE accion_auditoria ADD VALUE 'VINCULAR_RECETA_DEMO';
