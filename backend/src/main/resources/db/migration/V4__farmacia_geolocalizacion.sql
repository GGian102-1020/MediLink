-- Geolocalizacion de farmacias (busqueda de farmacias cercanas).
-- Columnas opcionales: farmacias existentes quedan con NULL hasta cargarlas.
ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS latitud  NUMERIC(10, 7);
ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS longitud NUMERIC(10, 7);
