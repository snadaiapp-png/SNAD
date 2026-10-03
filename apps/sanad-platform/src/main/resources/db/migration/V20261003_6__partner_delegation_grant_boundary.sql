-- ============================================================================
-- V20261003_6 — Wave 2 / Task 5: Partner delegation grant boundary + gate.
--
-- Delegation is EXPLICIT. A partner membership plus an ACTIVE partner-tenant
-- binding never authorizes partner action against a tenant by itself: every
-- delegated capability must be granted through partner_delegation_grants and
-- evaluated by the application-side PartnerDelegationGate.
--
-- Security invariants:
--   * Only platform control-plane context may create or change grants.
--   * Partner context is read-only and can see only its own grants.
--   * Missing context fails closed (no row is visible or writable).
--   * Tenant-plane context has NO branch on this table: a delegation grant is
--     partner-plane governance data, never tenant business data.
--   * partner_id / tenant_id / capability_code are immutable after creation.
--   * REVOKED is terminal; physical DELETE is forbidden (retention).
--   * A grant may only be ACTIVE while the partner is ACTIVE and an ACTIVE,
--     in-window binding exists for the exact (partner_id, tenant_id) pair.
--   * At most one ACTIVE grant per (partner_id, tenant_id, capability_code).
--   * All lifecycle mutations are audited in authorization_change_events with
--     a partner-scoped payload so the existing partner audit RLS branch applies.
--
-- Delegation registry:
--   * partner_delegation_capabilities is the explicit whitelist of delegatable
--     capabilities (W2-T5 directive section 6).
--   * Business-data capabilities (CRM.*, HRM.*, Payroll, Accounting, ERP.*) are
--     deliberately ABSENT: unknown capability = DENY, so commercial delegation
--     can never silently become business-data access (W2-T5 directive section 8).
--   * Unknown capability codes cannot be granted (FK to the registry).
--
-- Explicitly out of scope:
--   partner portal HTTP surface (W2-T6), executive control plane (W2-T7),
--   provisioning adapter (W2-T11), commercial identity, billing, settlement.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Delegation capability registry (explicit whitelist)
-- ----------------------------------------------------------------------------

CREATE TABLE partner_delegation_capabilities (
    code          TEXT        NOT NULL,
    description   TEXT        NULL,
    status        TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (
        status IN ('ACTIVE', 'INACTIVE')
    ),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_partner_delegation_capabilities PRIMARY KEY (code),
    CONSTRAINT ck_pdc_code_shape CHECK (code ~ '^[A-Z][A-Z_]*\.[A-Z][A-Z_.]*$')
);

INSERT INTO partner_delegation_capabilities (code, description) VALUES
    ('TENANT.CREATE',              'Delegated tenant provisioning through the canonical provisioning service'),
    ('TENANT.ACTIVATE',            'Delegated activation of a bound tenant'),
    ('TENANT.SUSPEND',             'Delegated suspension of a bound tenant'),
    ('TENANT.USER.MANAGE',         'Delegated user lifecycle management inside a bound tenant'),
    ('TENANT.AUTHORIZATION.MANAGE','Delegated tenant-scoped authorization administration'),
    ('SUBSCRIPTION.CREATE',        'Delegated subscription creation for a bound tenant'),
    ('SUBSCRIPTION.UPGRADE',       'Delegated subscription upgrade for a bound tenant'),
    ('SUBSCRIPTION.DOWNGRADE',     'Delegated subscription downgrade for a bound tenant'),
    ('SUBSCRIPTION.CANCEL',        'Delegated subscription cancellation for a bound tenant'),
    ('BILLING.READ',               'Delegated billing visibility for a bound tenant'),
    ('BILLING.MANAGE',             'Delegated billing administration for a bound tenant');

CREATE OR REPLACE FUNCTION trg_partner_delegation_capabilities_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_delegation_capabilities_updated_at
    BEFORE UPDATE ON partner_delegation_capabilities
    FOR EACH ROW
    EXECUTE FUNCTION trg_partner_delegation_capabilities_updated_at();

-- ----------------------------------------------------------------------------
-- 2. Delegation grants
-- ----------------------------------------------------------------------------

CREATE TABLE partner_delegation_grants (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    partner_id        UUID        NOT NULL,
    tenant_id         UUID        NOT NULL,
    capability_code   TEXT        NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'REVOKED')
    ),
    valid_from        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until       TIMESTAMPTZ NULL,
    suspended_at      TIMESTAMPTZ NULL,
    suspended_reason  TEXT        NULL,
    revoked_at        TIMESTAMPTZ NULL,
    revoked_reason    TEXT        NULL,
    created_by        UUID        NOT NULL,
    updated_by        UUID        NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_partner_delegation_grants PRIMARY KEY (id),
    CONSTRAINT fk_pdg_partner
        FOREIGN KEY (partner_id) REFERENCES partners(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pdg_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pdg_capability
        FOREIGN KEY (capability_code)
        REFERENCES partner_delegation_capabilities(code) ON DELETE RESTRICT,
    CONSTRAINT ck_pdg_validity_window
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_pdg_suspended_alignment
        CHECK ((status = 'SUSPENDED') = (suspended_at IS NOT NULL)),
    CONSTRAINT ck_pdg_suspended_reason_required
        CHECK (suspended_at IS NULL OR suspended_reason IS NOT NULL),
    CONSTRAINT ck_pdg_revoked_alignment
        CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
    CONSTRAINT ck_pdg_revoked_reason_required
        CHECK (revoked_at IS NULL OR revoked_reason IS NOT NULL),
    CONSTRAINT ck_pdg_version_non_negative
        CHECK (version >= 0)
);

-- One ACTIVE delegation per (partner, tenant, capability). History rows
-- (INACTIVE / SUSPENDED / REVOKED) are retained and never block a governed
-- re-grant, which is modeled as a NEW row.
CREATE UNIQUE INDEX uq_partner_delegation_grants_active
    ON partner_delegation_grants (partner_id, tenant_id, capability_code)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_pdg_partner_status
    ON partner_delegation_grants (partner_id, status);

CREATE INDEX idx_pdg_tenant_status
    ON partner_delegation_grants (tenant_id, status);

CREATE INDEX idx_pdg_partner_tenant_capability
    ON partner_delegation_grants (partner_id, tenant_id, capability_code);

-- ----------------------------------------------------------------------------
-- 3. Lifecycle trigger: identity immutability, terminal REVOKED, forbidden
--    physical DELETE, and ACTIVE-state cross-table validation.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION enforce_partner_delegation_grant_lifecycle()
RETURNS TRIGGER AS $$
DECLARE
    partner_status      TEXT;
    active_binding_cnt  INTEGER;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'PARTNER_DELEGATION_GRANT_DELETE_FORBIDDEN: revoke the grant instead'
            USING ERRCODE = '23001';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.partner_id IS DISTINCT FROM OLD.partner_id
           OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
           OR NEW.capability_code IS DISTINCT FROM OLD.capability_code THEN
            RAISE EXCEPTION 'PARTNER_DELEGATION_GRANT_IDENTITY_IMMUTABLE'
                USING ERRCODE = '23001';
        END IF;

        IF OLD.status = 'REVOKED' AND NEW.status <> 'REVOKED' THEN
            RAISE EXCEPTION 'PARTNER_DELEGATION_GRANT_TERMINAL'
                USING ERRCODE = '23001';
        END IF;

        NEW.updated_at = NOW();
        NEW.version = OLD.version + 1;
    END IF;

    IF NEW.status = 'ACTIVE' THEN
        -- Known-but-not-ACTIVE partners are rejected here (23001). Unknown ids
        -- fall through so the canonical FK constraint reports 23503 instead of
        -- this trigger masking the referential-integrity violation.
        SELECT status INTO partner_status FROM partners WHERE id = NEW.partner_id;

        IF partner_status IS NOT NULL AND partner_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'PARTNER_DELEGATION_GRANT_PARTNER_INACTIVE'
                USING ERRCODE = '23001';
        END IF;

        -- An ACTIVE grant requires an ACTIVE, in-window binding for the exact
        -- (partner_id, tenant_id) pair. Binding presence alone is NOT enough:
        -- the binding must currently be governable.
        SELECT COUNT(*) INTO active_binding_cnt
          FROM partner_tenant_bindings
         WHERE partner_id = NEW.partner_id
           AND tenant_id = NEW.tenant_id
           AND status = 'ACTIVE'
           AND valid_from <= NOW()
           AND (valid_until IS NULL OR valid_until > NOW());

        IF active_binding_cnt = 0 THEN
            RAISE EXCEPTION 'PARTNER_DELEGATION_GRANT_BINDING_INACTIVE'
                USING ERRCODE = '23001';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_delegation_grants_lifecycle
    BEFORE INSERT OR UPDATE OR DELETE ON partner_delegation_grants
    FOR EACH ROW
    EXECUTE FUNCTION enforce_partner_delegation_grant_lifecycle();

-- ----------------------------------------------------------------------------
-- 4. RLS: platform control plane is the only writer; partner context is
--    read-only over its own rows; tenant-plane and missing contexts fail
--    closed with zero visibility.
-- ----------------------------------------------------------------------------

ALTER TABLE partner_delegation_grants ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_delegation_grants FORCE ROW LEVEL SECURITY;

CREATE POLICY partner_delegation_grants_platform_select
ON partner_delegation_grants
FOR SELECT
USING (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

CREATE POLICY partner_delegation_grants_partner_select
ON partner_delegation_grants
FOR SELECT
USING (
    NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
    AND partner_id::text = current_setting('app.partner_id', true)
);

-- Intentionally no partner INSERT/UPDATE/DELETE policy: partner context is
-- read-only. There is also intentionally no tenant-plane branch: delegation
-- grants are partner-plane governance records, never tenant data.
CREATE POLICY partner_delegation_grants_platform_insert
ON partner_delegation_grants
FOR INSERT
WITH CHECK (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

CREATE POLICY partner_delegation_grants_platform_update
ON partner_delegation_grants
FOR UPDATE
USING (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
)
WITH CHECK (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

-- ----------------------------------------------------------------------------
-- 5. Registry RLS: platform control plane manages the whitelist; partner
--    context may read ACTIVE registry rows (portal listing support) but can
--    never write; missing context fails closed.
-- ----------------------------------------------------------------------------

ALTER TABLE partner_delegation_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_delegation_capabilities FORCE ROW LEVEL SECURITY;

CREATE POLICY partner_delegation_capabilities_platform_all
ON partner_delegation_capabilities
FOR ALL
USING (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
)
WITH CHECK (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

CREATE POLICY partner_delegation_capabilities_partner_select
ON partner_delegation_capabilities
FOR SELECT
USING (
    NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
    AND status = 'ACTIVE'
);

-- ----------------------------------------------------------------------------
-- 6. Audit: lifecycle mutations are written to authorization_change_events.
--    The payload carries partner_id so the existing partner-scoped audit RLS
--    branch (V20261003_4) exposes delegation audit to the right partner only.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION trg_partner_delegation_grants_audit()
RETURNS TRIGGER AS $$
DECLARE
    event_type_val TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        event_type_val := 'PARTNER_DELEGATION_CREATED';
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.status <> 'ACTIVE' AND NEW.status = 'ACTIVE' THEN
            event_type_val := 'PARTNER_DELEGATION_ACTIVATED';
        ELSIF OLD.status = 'ACTIVE' AND NEW.status = 'SUSPENDED' THEN
            event_type_val := 'PARTNER_DELEGATION_SUSPENDED';
        ELSIF OLD.status = 'ACTIVE' AND NEW.status = 'INACTIVE' THEN
            event_type_val := 'PARTNER_DELEGATION_DEACTIVATED';
        ELSIF OLD.status <> 'REVOKED' AND NEW.status = 'REVOKED' THEN
            event_type_val := 'PARTNER_DELEGATION_REVOKED';
        ELSE
            event_type_val := 'PARTNER_DELEGATION_UPDATED';
        END IF;
    ELSE
        RETURN NEW;
    END IF;

    INSERT INTO authorization_change_events (
        id, tenant_id, event_type, actor_user_id,
        target_type, target_id, payload, created_at
    )
    VALUES (
        gen_random_uuid(),
        NULL,
        event_type_val,
        COALESCE(NEW.updated_by, NEW.created_by),
        'PARTNER_DELEGATION',
        NEW.id,
        jsonb_build_object(
            'partner_id', NEW.partner_id,
            'tenant_id', NEW.tenant_id,
            'grant_id', NEW.id,
            'capability_code', NEW.capability_code,
            'status', NEW.status,
            'valid_from', NEW.valid_from,
            'valid_until', NEW.valid_until,
            'version', NEW.version
        ),
        NOW()
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_delegation_grants_audit
    AFTER INSERT OR UPDATE ON partner_delegation_grants
    FOR EACH ROW
    EXECUTE FUNCTION trg_partner_delegation_grants_audit();

-- Registry status changes are platform-plane audit events (no partner branch:
-- payload intentionally carries no partner_id).
CREATE OR REPLACE FUNCTION trg_partner_delegation_capabilities_audit()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO authorization_change_events (
            id, tenant_id, event_type, actor_user_id,
            target_type, target_id, payload, created_at
        )
        VALUES (
            gen_random_uuid(),
            NULL,
            CASE
                WHEN NEW.status = 'ACTIVE' THEN 'PARTNER_DELEGATION_CAPABILITY_ACTIVATED'
                ELSE 'PARTNER_DELEGATION_CAPABILITY_DEACTIVATED'
            END,
            NULL,
            'PARTNER_DELEGATION_CAPABILITY',
            NULL,
            jsonb_build_object(
                'capability_code', NEW.code,
                'previous_status', OLD.status,
                'new_status', NEW.status
            ),
            NOW()
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_delegation_capabilities_audit
    AFTER UPDATE ON partner_delegation_capabilities
    FOR EACH ROW
    EXECUTE FUNCTION trg_partner_delegation_capabilities_audit();
