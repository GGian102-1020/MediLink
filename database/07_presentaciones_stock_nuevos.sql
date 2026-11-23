-- Presentaciones y stock de prueba para los medicamentos de 06_medicamentos_nuevos.sql.
-- Solo datos de DEMO (ficticios): no reflejan stock real de ninguna farmacia.
-- Idempotente:
--   * crea una presentación solo para medicamentos ACTIVOS que todavía no tienen ninguna;
--   * carga stock solo para presentaciones que todavía no tienen ninguna fila de stock.
-- Stock: ~45% de las farmacias activas, entre 1 y 30 unidades (mismo criterio que el seed 05),
-- y la Farmacia 1 (operadora de prueba carla.mendez) recibe stock de todas para el dashboard.
-- Ejecutar desde la raíz (después del 06):
--   Get-Content -Encoding utf8 database/07_presentaciones_stock_nuevos.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U medlink -d medlink
BEGIN;

WITH datos(nombre, concentracion, forma, cantidad, unidad, condicion) AS (VALUES
    ('Abiraterona acetato', '250 mg', 'Comprimidos', 120, 'Frasco', 'BAJO_RECETA'),
    ('Acido folico', '5 mg', 'Comprimidos', 30, 'Caja', 'VENTA_LIBRE'),
    ('Acido ursodesoxicolico', '300 mg', 'Comprimidos', 30, 'Caja', 'BAJO_RECETA'),
    ('Acido zoledronico', '5 mg/100 ml', 'Solución para infusión', 1, 'Frasco ampolla', 'BAJO_RECETA'),
    ('Alirocumab', '150 mg/ml', 'Solución inyectable', 2, 'Lapicera', 'BAJO_RECETA'),
    ('Amlodipina', '5 mg', 'Comprimidos', 30, 'Caja', 'BAJO_RECETA'),
    ('Apremilast', '30 mg', 'Comprimidos recubiertos', 56, 'Caja', 'BAJO_RECETA'),
    ('Benzocaina - tirotricina', '5 mg / 1 mg', 'Pastillas para chupar', 24, 'Caja', 'VENTA_LIBRE'),
    ('Bevacizumab', '400 mg/16 ml', 'Solución para infusión', 1, 'Frasco ampolla', 'BAJO_RECETA'),
    ('Bicalutamida', '50 mg', 'Comprimidos recubiertos', 28, 'Caja', 'BAJO_RECETA'),
    ('Bosentan', '125 mg', 'Comprimidos recubiertos', 56, 'Caja', 'BAJO_RECETA'),
    ('Brimonidina - brinzolamida', '2 mg/ml / 10 mg/ml', 'Suspensión oftálmica', 5, 'Frasco gotero ml', 'BAJO_RECETA'),
    ('Calcio gluconato', '10%', 'Solución inyectable', 10, 'Caja de ampollas', 'BAJO_RECETA'),
    ('Calcipotriol - betametasona dipropionato', '50 mcg/g / 0,5 mg/g', 'Gel', 60, 'Pomo g', 'BAJO_RECETA'),
    ('Capecitabina', '500 mg', 'Comprimidos recubiertos', 120, 'Caja', 'BAJO_RECETA'),
    ('Clonazepam', '0,5 mg', 'Comprimidos', 30, 'Caja', 'BAJO_RECETA'),
    ('Clorhidrato de morfina', '10 mg/ml', 'Solución inyectable', 5, 'Caja de ampollas', 'BAJO_RECETA'),
    ('Clorhidrato de nafazolina - maleato de feniramina', '0,25 mg/ml / 3 mg/ml', 'Colirio', 15, 'Frasco gotero ml', 'VENTA_LIBRE'),
    ('Colistimetato sodico', '1.000.000 UI', 'Polvo para solución inyectable', 1, 'Frasco ampolla', 'BAJO_RECETA'),
    ('Denosumab', '60 mg/ml', 'Solución inyectable', 1, 'Jeringa prellenada', 'BAJO_RECETA'),
    ('Dimetilfumarato', '240 mg', 'Cápsulas gastrorresistentes', 56, 'Caja', 'BAJO_RECETA'),
    ('Donepezilo clorhidrato', '10 mg', 'Comprimidos recubiertos', 28, 'Caja', 'BAJO_RECETA'),
    ('Doxilamina succinato - piridoxina clorhidrato', '10 mg / 10 mg', 'Comprimidos de liberación retardada', 20, 'Caja', 'BAJO_RECETA'),
    ('Eltrombopag', '50 mg', 'Comprimidos recubiertos', 28, 'Caja', 'BAJO_RECETA'),
    ('Eritropoyetina humana recombinante', '4000 UI', 'Solución inyectable', 1, 'Jeringa prellenada', 'BAJO_RECETA'),
    ('Extracto seco de cynara scolymus - acido dehidrocolico', '200 mg / 50 mg', 'Comprimidos', 30, 'Caja', 'VENTA_LIBRE'),
    ('Fampridina', '10 mg', 'Comprimidos de liberación prolongada', 56, 'Caja', 'BAJO_RECETA'),
    ('Felodipina', '5 mg', 'Comprimidos de liberación prolongada', 30, 'Caja', 'BAJO_RECETA'),
    ('Fluconazol', '150 mg', 'Cápsulas', 1, 'Caja', 'BAJO_RECETA'),
    ('Fosfato monosodico - fosfato disodico', '16 g / 6 g', 'Enema', 1, 'Frasco', 'VENTA_LIBRE'),
    ('Fraccion flavonoide purificada y micronizada (diosmina 90 %, flavonoides expresados en hesperidina 10 %)', '500 mg', 'Comprimidos recubiertos', 30, 'Caja', 'VENTA_LIBRE'),
    ('Fulvestrant', '250 mg/5 ml', 'Solución inyectable', 2, 'Jeringa prellenada', 'BAJO_RECETA'),
    ('Gefitinib', '250 mg', 'Comprimidos recubiertos', 30, 'Caja', 'BAJO_RECETA'),
    ('Gemfibrozil', '600 mg', 'Comprimidos recubiertos', 30, 'Caja', 'BAJO_RECETA'),
    ('Gentamicina - hidrocortisona acetato - ketoconazol', '1 mg/g / 10 mg/g / 20 mg/g', 'Crema', 15, 'Pomo g', 'BAJO_RECETA'),
    ('Hidroxizina clorhidrato', '25 mg', 'Comprimidos', 20, 'Caja', 'BAJO_RECETA'),
    ('Hierro sacarato', '100 mg/5 ml', 'Solución inyectable', 5, 'Caja de ampollas', 'BAJO_RECETA'),
    ('Ibrutinib', '140 mg', 'Cápsulas', 90, 'Frasco', 'BAJO_RECETA'),
    ('Ibuprofeno - cafeina', '400 mg / 100 mg', 'Comprimidos recubiertos', 10, 'Caja', 'VENTA_LIBRE'),
    ('Isotretinoina', '20 mg', 'Cápsulas blandas', 30, 'Caja', 'BAJO_RECETA'),
    ('Ketorolaco trometamina', '10 mg', 'Comprimidos', 10, 'Caja', 'BAJO_RECETA'),
    ('Leflunomida', '20 mg', 'Comprimidos recubiertos', 30, 'Caja', 'BAJO_RECETA'),
    ('Lenalidomida', '10 mg', 'Cápsulas', 21, 'Caja', 'BAJO_RECETA'),
    ('Levofloxacina', '500 mg', 'Comprimidos recubiertos', 7, 'Caja', 'BAJO_RECETA'),
    ('Mebendazol', '100 mg', 'Comprimidos', 6, 'Caja', 'VENTA_LIBRE'),
    ('Memantina clorhidrato - donepezilo clorhidrato', '28 mg / 10 mg', 'Cápsulas de liberación prolongada', 30, 'Caja', 'BAJO_RECETA'),
    ('Metoprolol succinato', '50 mg', 'Comprimidos de liberación prolongada', 30, 'Caja', 'BAJO_RECETA'),
    ('Miglustat', '100 mg', 'Cápsulas', 84, 'Caja', 'BAJO_RECETA'),
    ('Misoprostol', '200 mcg', 'Comprimidos', 28, 'Caja', 'BAJO_RECETA'),
    ('Nitisinona', '10 mg', 'Cápsulas', 60, 'Frasco', 'BAJO_RECETA'),
    ('Oxicodona clorhidrato', '10 mg', 'Comprimidos de liberación prolongada', 30, 'Caja', 'BAJO_RECETA'),
    ('Pantoprazol', '40 mg', 'Comprimidos gastrorresistentes', 28, 'Caja', 'BAJO_RECETA'),
    ('Penicilina G benzatina', '2.400.000 UI', 'Polvo para suspensión inyectable', 1, 'Frasco ampolla', 'BAJO_RECETA'),
    ('Polietilenglicol 3350', '17 g', 'Polvo para solución oral', 14, 'Caja de sobres', 'VENTA_LIBRE'),
    ('Pregabalina', '75 mg', 'Cápsulas', 28, 'Caja', 'BAJO_RECETA'),
    ('Propionato de fluticasona - fumarato de formoterol dihidratado', '125 mcg / 5 mcg por dosis', 'Aerosol para inhalación', 120, 'Inhalador', 'BAJO_RECETA'),
    ('Rosuvastatina', '10 mg', 'Comprimidos recubiertos', 30, 'Caja', 'BAJO_RECETA'),
    ('Salbutamol - dipropionato de beclometasona', '100 mcg / 50 mcg por dosis', 'Aerosol para inhalación', 200, 'Inhalador', 'BAJO_RECETA'),
    ('Sofosvubir', '400 mg', 'Comprimidos recubiertos', 28, 'Frasco', 'BAJO_RECETA'),
    ('Surfactante pulmonar bovino modificado', '25 mg/ml', 'Suspensión para administración intratraqueal', 8, 'Frasco ampolla ml', 'BAJO_RECETA'),
    ('Tadalafilo', '5 mg', 'Comprimidos recubiertos', 30, 'Caja', 'BAJO_RECETA'),
    ('Tobramicina - dexametasona', '3 mg/ml / 1 mg/ml', 'Suspensión oftálmica', 5, 'Frasco gotero ml', 'BAJO_RECETA'),
    ('Tramadol clorhidrato', '50 mg', 'Cápsulas', 20, 'Caja', 'BAJO_RECETA'),
    ('Travoprost', '40 mcg/ml', 'Solución oftálmica', 2, 'Frasco gotero ml', 'BAJO_RECETA'),
    ('Valsartan', '160 mg', 'Comprimidos recubiertos', 28, 'Caja', 'BAJO_RECETA'),
    ('Valsartan - amlodipina', '160 mg / 5 mg', 'Comprimidos recubiertos', 28, 'Caja', 'BAJO_RECETA'),
    ('Valsartan - hidroclorotiazida', '160 mg / 12,5 mg', 'Comprimidos recubiertos', 28, 'Caja', 'BAJO_RECETA'),
    ('Vaselina liquida - lanolina liquida anhidra', '60% / 3%', 'Emulsión tópica', 120, 'Frasco ml', 'VENTA_LIBRE')
)
INSERT INTO presentacion_medicamento
    (id_medicamento, concentracion_texto, forma_farmaceutica, cantidad_envase, unidad_expendio, condicion_dispensa)
SELECT m.id_medicamento, d.concentracion, d.forma, d.cantidad, d.unidad, d.condicion::condicion_dispensa
FROM datos d
JOIN medicamento m ON lower(unaccent(btrim(m.nombre))) = lower(unaccent(d.nombre))
WHERE m.estado = 'ACTIVO'
  AND NOT EXISTS (SELECT 1 FROM presentacion_medicamento p WHERE p.id_medicamento = m.id_medicamento)
ORDER BY m.nombre;

-- Presentaciones activas sin ninguna fila de stock (las recién creadas).
CREATE TEMP TABLE presentaciones_sin_stock ON COMMIT DROP AS
SELECT p.id_presentacion
FROM presentacion_medicamento p
WHERE p.estado = 'ACTIVA'
  AND NOT EXISTS (SELECT 1 FROM stock s WHERE s.id_presentacion = p.id_presentacion);

SELECT setseed(0.77);
INSERT INTO stock (id_farmacia, id_presentacion, cantidad_disponible, fecha_hora_actualizacion)
SELECT f.id_farmacia, p.id_presentacion, 1 + floor(random() * 30)::int,
       now() - make_interval(days => floor(random() * 7)::int)
FROM farmacia f
CROSS JOIN presentaciones_sin_stock p
WHERE f.estado = 'ACTIVA'
  AND (f.id_farmacia = 1 OR random() < 0.45)
ORDER BY f.id_farmacia, p.id_presentacion;

SELECT count(*) AS presentaciones_con_stock_nuevo FROM presentaciones_sin_stock;

COMMIT;
