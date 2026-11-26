-- Existing Neon neondb only; requires a verified full backup before execution.
-- Source: https://apis.datos.gob.ar/georef/api/direcciones (provincia=02), 2026-11-26.
-- Street/height matches unique. Santa Fe 3200 is Comuna 2; user explicitly chose
-- its street/height coordinates while preserving the existing Palermo address text.
-- Run this entire file in ONE transaction. Re-execution refuses non-null coordinates.
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
CREATE TEMP TABLE geography_before (table_name text PRIMARY KEY, n bigint, hash text) ON COMMIT DROP;
DO $$
DECLARE t record; n bigint; h text; expr text;
BEGIN
    IF current_database() <> 'neondb' THEN RAISE EXCEPTION 'Unexpected database'; END IF;
    IF (SELECT count(*) FROM public.farmacia) <> 5 OR
       (SELECT count(*) FROM public.farmacia WHERE id_farmacia BETWEEN 1 AND 5 AND latitud IS NULL AND longitud IS NULL) <> 5
    THEN RAISE EXCEPTION 'Unexpected pharmacy inventory or existing coordinates'; END IF;
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename LOOP
        expr := CASE WHEN t.tablename='farmacia' THEN '(to_jsonb(r)-''latitud''-''longitud'')' ELSE 'to_jsonb(r)' END;
        EXECUTE format('SELECT count(*), md5(coalesce(string_agg(%s::text,E''\n'' ORDER BY %s::text COLLATE "C"),'''')) FROM public.%I r',expr,expr,t.tablename) INTO n,h;
        INSERT INTO geography_before VALUES(t.tablename,n,h);
    END LOOP;
    IF (SELECT count(*) FROM geography_before) <> 22 THEN RAISE EXCEPTION 'Unexpected schema inventory'; END IF;
END $$;
DO $$
DECLARE changed integer;
BEGIN
    UPDATE public.farmacia f SET latitud=v.lat::numeric(10,7), longitud=v.lon::numeric(10,7)
    FROM (VALUES
      (1::bigint,'Av. Santa Fe 3200, Palermo, CABA',-34.5890572116575,-58.4100811564799),
      (2::bigint,'Av. Rivadavia 5100, Caballito, CABA',-34.618873264346,-58.4377466252689),
      (3::bigint,'Av. Corrientes 1200, San Nicolás, CABA',-34.6038282526684,-58.3837116180876),
      (4::bigint,'Av. Cabildo 2100, Belgrano, CABA',-34.5620357672593,-58.4566933254415),
      (5::bigint,'Av. Rivadavia 7000, Flores, CABA',-34.6292517072598,-58.4641120412295)
    ) v(id,address,lat,lon)
    WHERE f.id_farmacia=v.id AND f.direccion=v.address AND f.latitud IS NULL AND f.longitud IS NULL;
    GET DIAGNOSTICS changed=ROW_COUNT;
    IF changed<>5 THEN RAISE EXCEPTION 'Expected exactly five geography updates'; END IF;
END $$;
DO $$
DECLARE t record; n bigint; h text; expr text;
BEGIN
    FOR t IN SELECT * FROM geography_before ORDER BY table_name LOOP
        expr := CASE WHEN t.table_name='farmacia' THEN '(to_jsonb(r)-''latitud''-''longitud'')' ELSE 'to_jsonb(r)' END;
        EXECUTE format('SELECT count(*), md5(coalesce(string_agg(%s::text,E''\n'' ORDER BY %s::text COLLATE "C"),'''')) FROM public.%I r',expr,expr,t.table_name) INTO n,h;
        IF n<>t.n OR h<>t.hash THEN RAISE EXCEPTION 'Non-geographic data changed in %',t.table_name; END IF;
    END LOOP;
    IF EXISTS(SELECT 1 FROM public.farmacia WHERE latitud IS NULL OR longitud IS NULL OR latitud NOT BETWEEN -90 AND 90 OR longitud NOT BETWEEN -180 AND 180)
    THEN RAISE EXCEPTION 'Invalid coordinates'; END IF;
END $$;
SELECT count(*) AS tables_verified_non_geography_unchanged FROM geography_before;
SELECT id_farmacia,nombre,direccion,latitud,longitud,estado FROM public.farmacia ORDER BY id_farmacia;
