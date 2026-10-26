-- Read-only fingerprints of every public table, independent of physical row order.
-- Output contains table names, counts and hashes, never the actual row contents.
SELECT format(
    'SELECT %L || ''|'' || count(*) || ''|'' || md5(coalesce(string_agg(row_to_json(t)::text, E''\n'' ORDER BY row_to_json(t)::text), '''')) FROM %I.%I t;',
    tablename, schemaname, tablename)
FROM pg_tables WHERE schemaname='public' ORDER BY tablename
\gexec
