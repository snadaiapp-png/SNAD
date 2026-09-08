-- ============================================================
-- V20260906_2: R0C-10 — Subscription multiplicity MODEL_B
--
-- MODEL_B frozen contract (R0C-9 closed contract, inherited):
--   Historical subscriptions:           tenant -> 0..N
--   Effective/current subscriptions:    tenant -> 0..1
--   Effective definition: status NOT IN ('CANCELLED','EXPIRED','TERMINATED')
--
-- 1. Drops the legacy full-tenant UNIQUE constraint
--    uk_tenant_subscriptions_tenant — under MODEL_B a tenant's terminal
--    history (CANCELLED / EXPIRED / TERMINATED) must be preserved as
--    multiple immutable rows, which UNIQUE(tenant_id) forbids.
-- 2. Replaces it with the MODEL_B effective-uniqueness backstop:
--    a PARTIAL unique index on (tenant_id) restricted to non-terminal
--    rows. At most ONE effective subscription per tenant, enforced by
--    PostgreSQL — the final concurrency authority behind the service
--    guard (expired-successor continuation, R0C-10 §4).
-- 3. Adds an efficient (tenant_id, status) lookup index for
--    effective/history resolution (naming follows the existing
--    idx_tenant_subscriptions_* convention).
--
-- Forward-only, additive-only:
--   - NO data is rewritten, NO row is deleted or modified
--     (no physical DELETE of subscription history).
--   - NO RLS statement is touched (tenant_subscriptions is not an
--     RLS-enforced table in this repository; module tables keep their
--     RLS untouched).
--   - ck_tenant_subscriptions_status already admits all lifecycle
--     values (widened additively by V20260830_1) — no CHECK change.
-- ============================================================

-- STEP 1: remove the legacy one-per-tenant storage invariant
ALTER TABLE tenant_subscriptions DROP CONSTRAINT IF EXISTS uk_tenant_subscriptions_tenant;

-- STEP 2: MODEL_B effective-uniqueness backstop (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_subscriptions_effective
    ON tenant_subscriptions (tenant_id)
    WHERE status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED');

-- STEP 3: efficient effective/history lookup index
CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_tenant_status
    ON tenant_subscriptions (tenant_id, status);
