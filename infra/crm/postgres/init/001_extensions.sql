\set ON_ERROR_STOP on

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

COMMENT ON EXTENSION vector IS 'CRM semantic vectors and similarity search';
COMMENT ON EXTENSION pg_trgm IS 'CRM fuzzy name, email, and company search';
COMMENT ON EXTENSION unaccent IS 'CRM locale-aware text normalization support';
COMMENT ON EXTENSION pgcrypto IS 'UUID generation and cryptographic helpers for local CRM runtime';
