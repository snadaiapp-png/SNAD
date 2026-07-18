-- H2 compatibility marker for CRM-G1 production reconciliation.
--
-- The real forward-only repair is PostgreSQL-specific and is loaded from
-- db/vendor/postgresql. H2 already receives the CRM-G1 objects from the
-- original shared migrations, so this vendor-specific migration preserves
-- version parity without executing PostgreSQL PL/pgSQL.
SELECT 1;
