-- ============================================================================
-- V20261001_8 — Wave 1 / Task 2: effective permission projection and
-- per-user authorization version.
--
-- Revision D semantic authority:
--   * The projection contains candidate/effective ALLOW rows only.
--   * Direct DENY remains authoritative in user_permission_overrides and is
--     evaluated before any candidate ALLOW source; DENY is never projected.
--   * Projection sources in Wave 1 are ROLE, OVERRIDE, BREAK_GLASS only.
--   * Tenant isolation is ENABLE+FORCE RLS and fails closed when app.tenant_id
--     is absent.
--
-- Execution overlay:
--   The original Revision D Task 2 stamp V20260924_2 cannot be used on the
--   current mainline. V20260924_* is occupied by HRM-G2 and V20261001_2..7 is
--   occupied by Platform IAM. V20261001_8 is the next free forward-only stamp.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN authorization_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE effective_permission_projection (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID        NOT NULL,
    user_id               UUID        NOT NULL,
    capability_id         UUID        NOT NULL,
    effect                TEXT        NOT NULL CHECK (effect IN ('ALLOW')),
    scope_type            TEXT        NOT NULL DEFAULT 'TENANT_ALL',
    scope_reference       UUID        NULL,
    source                TEXT        NOT NULL CHECK (source IN ('ROLE', 'OVERRIDE', 'BREAK_GLASS')),
    matched_role_id       UUID        NULL,
    authorization_version BIGINT      NOT NULL,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_effective_permission_projection PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_epp
    ON effective_permission_projection (
        tenant_id,
        user_id,
        capability_id,
        scope_type,
        scope_reference
    ) NULLS NOT DISTINCT;

CREATE INDEX idx_epp_subject_version
    ON effective_permission_projection (tenant_id, user_id, authorization_version);

ALTER TABLE effective_permission_projection ENABLE ROW LEVEL SECURITY;
ALTER TABLE effective_permission_projection FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON effective_permission_projection;
CREATE POLICY tenant_isolation
ON effective_permission_projection
FOR ALL
USING (
    tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    tenant_id::text = current_setting('app.tenant_id', true)
);
