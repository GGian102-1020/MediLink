-- Datos personales del paciente pedidos en el registro.
-- Todas las columnas son opcionales: los usuarios existentes quedan con NULL
-- y otros clientes que todavia no envian estos datos siguen funcionando.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS dni              VARCHAR(8);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS fecha_nacimiento DATE;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS telefono         VARCHAR(20);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS obra_social      VARCHAR(80);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS numero_afiliado  VARCHAR(30);

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_dni
    CHECK (dni IS NULL OR dni ~ '^[0-9]{7,8}$');
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_fecha_nacimiento
    CHECK (fecha_nacimiento IS NULL OR fecha_nacimiento >= DATE '1900-01-01');
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_telefono
    CHECK (telefono IS NULL OR telefono ~ '^\+?[0-9]{6,15}$');
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_afiliado_requiere_obra_social
    CHECK (numero_afiliado IS NULL OR obra_social IS NOT NULL);

-- Un DNI identifica a una sola persona.
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_dni ON usuario (dni) WHERE dni IS NOT NULL;
