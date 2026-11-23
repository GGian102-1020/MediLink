-- Carga de medicamentos nuevos (fuente: medicamentos_nuevos.csv de la raíz).
-- Idempotente: no borra nada y omite nombres que ya existen en la base o que se
-- repiten dentro de la lista (comparación sin mayúsculas, tildes ni espacios extra).
-- Los IDs los asigna la identidad de PostgreSQL. Solo carga el catálogo: sin
-- presentaciones ni stock, estos medicamentos aparecen listados pero no reservables.
-- Ejecutar desde la raíz:
--   Get-Content database/06_medicamentos_nuevos.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U medlink -d medlink
BEGIN;

WITH nuevos(nombre, estado) AS (VALUES
    ('Abiraterona acetato', 'ACTIVO'),
    ('Acido folico', 'ACTIVO'),
    ('Acido ursodesoxicolico', 'ACTIVO'),
    ('Acido zoledronico', 'ACTIVO'),
    ('Alirocumab', 'ACTIVO'),
    ('Amlodipina', 'ACTIVO'),
    ('Apremilast', 'ACTIVO'),
    ('Benzocaina - tirotricina', 'ACTIVO'),
    ('Bevacizumab', 'ACTIVO'),
    ('Bicalutamida', 'ACTIVO'),
    ('Bosentan', 'ACTIVO'),
    ('Brimonidina - brinzolamida', 'ACTIVO'),
    ('Calcio gluconato', 'ACTIVO'),
    ('Calcipotriol - betametasona dipropionato', 'ACTIVO'),
    ('Capecitabina', 'ACTIVO'),
    ('Clonazepam', 'ACTIVO'),
    ('Clorhidrato de morfina', 'ACTIVO'),
    ('Clorhidrato de nafazolina - maleato de feniramina', 'ACTIVO'),
    ('Colistimetato sodico', 'ACTIVO'),
    ('Denosumab', 'ACTIVO'),
    ('Dimetilfumarato', 'ACTIVO'),
    ('Donepezilo clorhidrato', 'ACTIVO'),
    ('Doxilamina succinato - piridoxina clorhidrato', 'ACTIVO'),
    ('Eltrombopag', 'ACTIVO'),
    ('Eritropoyetina humana recombinante', 'ACTIVO'),
    ('Extracto seco de cynara scolymus - acido dehidrocolico', 'ACTIVO'),
    ('Fampridina', 'ACTIVO'),
    ('Felodipina', 'ACTIVO'),
    ('Fluconazol', 'ACTIVO'),
    ('Fosfato monosodico - fosfato disodico', 'ACTIVO'),
    ('Fraccion flavonoide purificada y micronizada (diosmina 90 %, flavonoides expresados en hesperidina 10 %)', 'ACTIVO'),
    ('Fulvestrant', 'ACTIVO'),
    ('Gefitinib', 'ACTIVO'),
    ('Gemfibrozil', 'ACTIVO'),
    ('Gentamicina - hidrocortisona acetato - ketoconazol', 'ACTIVO'),
    ('Hidroxizina clorhidrato', 'ACTIVO'),
    ('Hierro sacarato', 'ACTIVO'),
    ('Ibrutinib', 'ACTIVO'),
    ('Ibuprofeno - cafeina', 'ACTIVO'),
    ('Isotretinoina', 'ACTIVO'),
    ('Ketorolaco trometamina', 'ACTIVO'),
    ('Leflunomida', 'ACTIVO'),
    ('Lenalidomida', 'ACTIVO'),
    ('Levofloxacina', 'ACTIVO'),
    ('Mebendazol', 'ACTIVO'),
    ('Memantina clorhidrato - donepezilo clorhidrato', 'ACTIVO'),
    ('Metoprolol succinato', 'ACTIVO'),
    ('Miglustat', 'ACTIVO'),
    ('Misoprostol', 'ACTIVO'),
    ('Nitisinona', 'ACTIVO'),
    ('Oxicodona clorhidrato', 'ACTIVO'),
    ('Pantoprazol', 'ACTIVO'),
    ('Penicilina G benzatina', 'ACTIVO'),
    ('Polietilenglicol 3350', 'ACTIVO'),
    ('Pregabalina', 'ACTIVO'),
    ('Propionato de fluticasona - fumarato de formoterol dihidratado', 'ACTIVO'),
    ('Rosuvastatina', 'ACTIVO'),
    ('Salbutamol - dipropionato de beclometasona', 'ACTIVO'),
    ('Sofosvubir', 'ACTIVO'),
    ('Surfactante pulmonar bovino modificado', 'ACTIVO'),
    ('Tadalafilo', 'ACTIVO'),
    ('Tobramicina - dexametasona', 'ACTIVO'),
    ('Tramadol clorhidrato', 'ACTIVO'),
    ('Travoprost', 'ACTIVO'),
    ('Valsartan', 'ACTIVO'),
    ('Valsartan - amlodipina', 'ACTIVO'),
    ('Valsartan - hidroclorotiazida', 'ACTIVO'),
    ('Vaselina liquida - lanolina liquida anhidra', 'ACTIVO')
), normalizados AS (
    SELECT DISTINCT ON (clave) nombre, estado
    FROM (
        SELECT regexp_replace(btrim(nombre), '[[:space:]]+', ' ', 'g') AS nombre, estado,
               lower(unaccent(regexp_replace(btrim(nombre), '[[:space:]]+', ' ', 'g'))) AS clave,
               row_number() OVER () AS orden
        FROM nuevos
    ) n
    ORDER BY clave, orden
)
INSERT INTO medicamento (nombre, estado)
SELECT n.nombre, n.estado::estado_medicamento
FROM normalizados n
WHERE NOT EXISTS (
    SELECT 1 FROM medicamento m
    WHERE lower(unaccent(btrim(m.nombre))) = lower(unaccent(n.nombre))
)
ORDER BY n.nombre;

COMMIT;
