ALTER TABLE receta_demo
    ADD COLUMN codigo_verificacion VARCHAR(18),
    ADD CONSTRAINT uq_receta_demo_codigo_verificacion UNIQUE (codigo_verificacion),
    ADD CONSTRAINT ck_receta_demo_codigo_verificacion CHECK (
        codigo_verificacion ~ '^MED-[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){2}$'
    );

COMMENT ON COLUMN receta_demo.codigo_verificacion IS
    'Identificador documental de recetas nuevas. NULL identifica recetas emitidas antes de V6.';
