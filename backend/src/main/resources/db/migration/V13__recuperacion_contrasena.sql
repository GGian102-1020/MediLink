-- Solo hashes de códigos de un uso. La contraseña y el código no se almacenan en claro.
CREATE TABLE recuperacion_contrasena (
    id_cuenta BIGINT PRIMARY KEY REFERENCES cuenta(id_cuenta),
    codigo_hash VARCHAR(64) NOT NULL,
    creado TIMESTAMPTZ NOT NULL,
    vence TIMESTAMPTZ NOT NULL,
    intentos INTEGER NOT NULL DEFAULT 0 CHECK (intentos BETWEEN 0 AND 5),
    token_version BIGINT NOT NULL,
    CHECK (vence > creado)
);
