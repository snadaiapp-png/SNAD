-- ============================================================================
-- V20261001_1 — Wave 1 / Task 1: unified authorization override,
-- relationship, and authorization-change-event persistence foundation.
--
-- Revision D semantic authority:
--   * Direct DENY is capability-wide in v1 (scoped DENY is forbidden).
--   * New tenant-owned tables are FORCE-RLS and fail closed.
--   * authorization_change_events has an explicit tenant/platform split;
--     missing GUC context grants no visibility.
--
-- Owner-approved execution overlay:
--   The original Revision D stamp V20260924_1 collided with HRM-G2 on the
--   current execution baseline and was forward-renumbered to V20261001_1.
--
-- NOTE: partner_id intentionally has no foreign key in Wave 1 because the
-- partners table does not exist at this execution baseline. Wave 2's reserved
-- V20261002_* range owns the partner FK/orphan-scan follow-up once partners
-- exists. No partner principal semantics are activated by this migration.
-- ============================================================================

-- ============================================================================
-- 1. Direct user permission overrides
-- ============================================================================
CREATE TABLE user_permission_overrides (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    partner_id      UUID        NULL,
    user_id         UUID        NOT NULL,
    capability_id   UUID        NOT NULL REFERENCES access_capabilities(id),
    effect          TEXT        NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    scope_type      TEXT        NULL CHECK (
        scope_type IN (
            'SELF',
            'OWN',
            'DIRECT_REPORTS',
            'REPORTING_TREE',
            'TEAM',
            'DEPARTMENT',
            'ORG_UNIT',
            'ORGANIZATION',
            'BRANCH',
            'BUSINESS_UNIT',
            'LEGAL_ENTITY',
            'PROJECT',
            'TENANT_ALL'
        )
    ),
    scope_reference UUID        NULL,
    reason          TEXT        NOT NULL,
    valid_from      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until     TIMESTAMPTZ NULL,
    created_by      UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_user_permission_overrides PRIMARY KEY (id),
    CONSTRAINT ck_upo_validity_window
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_upo_deny_is_capability_wide
        CHECK (
            effect <> 'DENY'
            OR (scope_type IS NULL AND scope_reference IS NULL)
        ),
    CONSTRAINT uq_user_permission_overrides_identity
        UNIQUE (
            tenant_id,
            user_id,
            capability_id,
            effect,
            scope_type,
            scope_reference,
            valid_from
        )
);

-- The active predicate must remain immutable. In particular, do not place
-- NOW() in a partial-index predicate: PostgreSQL rejects non-IMMUTABLE index
-- predicates and they would age incorrectly even if accepted.
CREATE INDEX idx_upo_active
    ON user_permission_overrides (tenant_id, user_id)
    WHERE valid_until IS NULL;

CREATE INDEX idx_upo_expiry
    ON user_permission_overrides (tenant_id, user_id, valid_until);

ALTER TABLE user_permission_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_permission_overrides FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON user_permission_overrides;
CREATE POLICY tenant_isolation
ON user_permission_overrides
FOR ALL
USING (
    tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    tenant_id::text = current_setting('app.tenant_id', true)
);

-- ============================================================================
-- 2. ReBAC subject relationships
--
-- object_id is intentionally polymorphic across object_type targets and has
-- no database FK. Object-type-specific integrity is enforced by service-layer
-- validation together with the closed object_type CHECK below.
-- ============================================================================
CREATE TABLE subject_relationships (
    id                UUID        NOT NULL,
    tenant_id         UUID        NOT NULL REFERENCES tenants(id),
    subject_user_id   UUID        NOT NULL,
    relationship_type TEXT        NOT NULL CHECK (
        relationship_type IN (
            'MANAGES',
            'MEMBER_OF',
            'BELONGS_TO',
            'ADMIN_OF',
            'PARTNER_MANAGES'
        )
    ),
    object_type       TEXT        NOT NULL CHECK (
        object_type IN (
            'EMPLOYEE',
            'TEAM',
            'DEPARTMENT',
            'BRANCH',
            'ORG_UNIT',
            'TENANT',
            'PARTNER'
        )
    ),
    object_id         UUID        NOT NULL,
    valid_from        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until       TIMESTAMPTZ NULL,
    source            TEXT        NOT NULL DEFAULT 'EXPLICIT',
    created_by        UUID        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_subject_relationships PRIMARY KEY (id),
    CONSTRAINT uq_subject_relationships_identity
        UNIQUE (
            tenant_id,
            subject_user_id,
            relationship_type,
            object_type,
            object_id
        )
);

ALTER TABLE subject_relationships ENABLE ROW LEVEL SECURITY;
ALTER TABLE subject_relationships FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON subject_relationships;
CREATE POLICY tenant_isolation
ON subject_relationships
FOR ALL
USING (
    tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    tenant_id::text = current_setting('app.tenant_id', true)
);

-- ============================================================================
-- 3. Authorization change events
--
-- Tenant rows require their own tenant context. Platform rows use the
-- canonical control-plane tenant as the carrier context and additionally
-- require no partner context. Missing settings make both branches false.
-- ============================================================================
CREATE TABLE authorization_change_events (
    id             UUID        NOT NULL,
    tenant_id      UUID        NULL,
    event_type     TEXT        NOT NULL,
    actor_user_id  UUID        NULL,
    target_type    TEXT        NULL,
    target_id      UUID        NULL,
    payload        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    correlation_id UUID        NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_authorization_change_events PRIMARY KEY (id)
);

CREATE INDEX idx_authorization_change_events_tenant_type_created
    ON authorization_change_events (tenant_id, event_type, created_at DESC);

ALTER TABLE authorization_change_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE authorization_change_events FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON authorization_change_events;
DROP POLICY IF EXISTS authorization_change_events_isolation ON authorization_change_events;

CREATE POLICY authorization_change_events_isolation
ON authorization_change_events
FOR ALL
USING (
    (
        tenant_id IS NOT NULL
        AND tenant_id::text = current_setting('app.tenant_id', true)
    )
    OR
    (
        tenant_id IS NULL
        AND current_setting('app.tenant_id', true)
            = '00000000-0000-0000-0000-000000000001'
        AND current_setting('app.partner_id', true) IS NULL
    )
)
WITH CHECK (
    (
        tenant_id IS NOT NULL
        AND tenant_id::text = current_setting('app.tenant_id', true)
    )
    OR
    (
        tenant_id IS NULL
        AND current_setting('app.tenant_id', true)
            = '00000000-0000-0000-0000-000000000001'
        AND current_setting('app.partner_id', true) IS NULL
    )
);
