-- =====================================================================
--  04_operadores_farmacias_reales.sql
--  Ejecutar DESPUES de 03_farmacias_reales.sql (que borra los operadores).
--  La disponibilidad publica solo lista farmacias con al menos un Operador
--  con cuenta ACTIVA, asi que cada farmacia necesita uno.
--  - Re-vincula los 6 operadores del seed 02 a las farmacias 1..5.
--  - Crea operador.f<N>@medlink.test para las farmacias 5..26
--    (la 5 tiene a Hernan, cuya cuenta esta DESHABILITADA).
--  Contrasena de todas las cuentas: Password123!  (SOLO demo local)
-- =====================================================================
BEGIN;

INSERT INTO operador (id_cuenta, id_farmacia, nombre, apellido) VALUES
 (2, 1, 'Carla',   'Méndez'),
 (3, 1, 'Diego',   'Suárez'),
 (4, 2, 'Laura',   'Benítez'),
 (5, 3, 'Pablo',   'Romero'),
 (6, 4, 'Valeria', 'Torres'),
 (7, 5, 'Hernán',  'Acosta');

SELECT setval(pg_get_serial_sequence('cuenta', 'id_cuenta'), (SELECT max(id_cuenta) FROM cuenta));
SELECT setval(pg_get_serial_sequence('operador', 'id_operador'), (SELECT coalesce(max(id_operador), 1) FROM operador));

WITH nuevas AS (
    INSERT INTO cuenta (correo, password_hash, rol, estado, fecha_hora_creacion)
    SELECT 'operador.f' || f.id_farmacia || '@medlink.test',
           crypt('Password123!', gen_salt('bf', 10)), 'OPERADOR', 'ACTIVA', now()
    FROM farmacia f
    WHERE f.id_farmacia >= 5
    ORDER BY f.id_farmacia
    RETURNING id_cuenta, correo
)
INSERT INTO operador (id_cuenta, id_farmacia, nombre, apellido)
SELECT n.id_cuenta,
       substring(n.correo FROM 'operador\.f([0-9]+)@')::bigint,
       'Operador',
       'Farmacia ' || substring(n.correo FROM 'operador\.f([0-9]+)@')
FROM nuevas n;

COMMIT;
