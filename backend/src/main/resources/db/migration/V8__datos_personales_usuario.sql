-- Datos personales que la app Android pide en el registro de pacientes.
-- Las columnas son NULL a nivel base porque los usuarios ya existentes (seed y
-- registros anteriores) no los tienen; el backend los exige para registros nuevos.
ALTER TABLE usuario
    ADD COLUMN dni VARCHAR(8),
    ADD COLUMN fecha_nacimiento DATE,
    ADD COLUMN telefono VARCHAR(20),
    ADD COLUMN obra_social VARCHAR(80),
    -- Opcional y provisorio: se prevé eliminarlo más adelante.
    ADD COLUMN numero_afiliado VARCHAR(30);

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_dni CHECK (dni IS NULL OR dni ~ '^[0-9]{7,8}$'),
    ADD CONSTRAINT ck_usuario_telefono CHECK (
        telefono IS NULL OR length(btrim(telefono)) BETWEEN 1 AND 20),
    ADD CONSTRAINT ck_usuario_obra_social CHECK (
        obra_social IS NULL OR length(btrim(obra_social)) BETWEEN 1 AND 80),
    ADD CONSTRAINT ck_usuario_numero_afiliado CHECK (
        numero_afiliado IS NULL OR length(btrim(numero_afiliado)) BETWEEN 1 AND 30),
    -- Defensa en base: la regla de negocio (mayor de 18) se valida en el backend
    -- con la fecha de Argentina; acá solo se impide una fecha futura.
    ADD CONSTRAINT ck_usuario_fecha_nacimiento CHECK (
        fecha_nacimiento IS NULL OR fecha_nacimiento <= CURRENT_DATE);

-- Un DNI solo puede estar asociado a una cuenta de paciente.
CREATE UNIQUE INDEX ux_usuario_dni ON usuario (dni) WHERE dni IS NOT NULL;
