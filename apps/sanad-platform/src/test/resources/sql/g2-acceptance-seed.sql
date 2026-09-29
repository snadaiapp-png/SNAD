-- G2 authenticated acceptance fixture entrypoint.
-- Preserve the established legacy fixture unchanged, then supplement it with
-- the canonical Person -> Employment -> Assignment graph used by HR scoping.
\set ON_ERROR_STOP on
\ir g2-acceptance-seed-legacy.sql
\ir g2-acceptance-canonical-seed.sql
