-- Extension needed for case/accent-insensitive medication search (CV-07).
-- Applied after V1 on both pre-existing and freshly initialized databases.
CREATE EXTENSION IF NOT EXISTS unaccent;
