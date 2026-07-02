-- ============================================================
-- SANAD Platform - Flyway migration V27 (H2 + PostgreSQL)
-- ------------------------------------------------------------
-- Stage 05A.1 §8 — Create linear, concurrency-safe audit hash chain.
--
-- V27 introduces:
--   1. audit_chain_heads table — one row per tenant, tracking the
--      current head_sequence and head_hash.
--   2. sequence_number column on audit_events — monotonically
--      increasing per tenant, enforced by a unique constraint.
--
-- The application (AuditService) uses SELECT ... FOR UPDATE on
-- audit_chain_heads to serialize chain extension within a tenant.
--
-- Compatible with PostgreSQL 14+ and H2 (MODE=PostgreSQL).
-- RLS and GRANT statements are in V28 (pg-only).
-- ============================================================

-- Add sequence_number to audit_events
ALTER TABLE audit_events ADD COLUMN sequence_number BIGINT;

-- Backfill existing rows with a synthetic sequence (1..N per tenant)
-- using row_number(). This is safe because existing rows are few
-- (test fixtures only at this stage of the migration).
UPDATE audit_events ae
SET sequence_number = sub.rn
FROM (
    SELECT id, tenant_id,
           ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY occurred_at ASC, id ASC) AS rn
    FROM audit_events
) sub
WHERE ae.id = sub.id;

-- Make sequence_number NOT NULL after backfill
ALTER TABLE audit_events ALTER COLUMN sequence_number SET NOT NULL;

-- Unique constraint: one sequence per tenant
ALTER TABLE audit_events
    ADD CONSTRAINT uk_audit_events_tenant_sequence
    UNIQUE (tenant_id, sequence_number);

-- Create the chain heads table
CREATE TABLE audit_chain_heads (
    tenant_id       UUID            NOT NULL,
    head_sequence   BIGINT          NOT NULL DEFAULT 0,
    head_hash       VARCHAR(64),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_audit_chain_heads PRIMARY KEY (tenant_id),
    CONSTRAINT fk_audit_chain_heads_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- Initialize chain heads for tenants that already have audit events
INSERT INTO audit_chain_heads (tenant_id, head_sequence, head_hash, updated_at)
SELECT tenant_id,
       COALESCE(MAX(sequence_number), 0),
       (SELECT event_hash FROM audit_events e2
        WHERE e2.tenant_id = audit_events.tenant_id
        ORDER BY sequence_number DESC LIMIT 1),
       NOW()
FROM audit_events
GROUP BY tenant_id;

-- Also initialize chain heads for all tenants that don't have audit events yet
INSERT INTO audit_chain_heads (tenant_id, head_sequence, head_hash, updated_at)
SELECT id, 0, NULL, NOW()
FROM tenants
WHERE id NOT IN (SELECT tenant_id FROM audit_chain_heads);
