-- ============================================================================
-- V20261003_5 — Wave 2 / Task 4: Partner <-> Tenant binding boundary.
--
-- Establishes the governed commercial/control-plane relationship between a
-- platform-level partner principal and a tenant. A binding is NOT a business-
-- data authorization grant and does not create CRM/HRM/ERP/Accounting access.
--
-- Security invariants:
--   * Platform control-plane context is the only writer.
--   * Partner context is read-only and can see only its own bindings.
--   * Missing context fails closed.
--   * A tenant may have at most one ACTIVE partner binding at a time.
--   * partner_id and tenant_id are immutable after creation.
--   * TERMINATED is terminal; physical DELETE is forbidden.
--   * All lifecycle mutations are audited in authorization_change_events.
--
-- Explicitly out of scope:
--   partner_delegation_grants, tenant business-data access, commercial
--   agreements, billing, settlement, branding, portal/UI.
-- ============================================================================

CREATE TABLE partner_tenant_bindings (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    partner_id         UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    status             TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'TERMINATED')
    ),
    valid_from         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until        TIMESTAMPTZ NULL,
    suspended_at       TIMESTAMPTZ NULL,
    suspended_reason   TEXT        NULL,
    terminated_at      TIMESTAMPTZ NULL,
    terminated_reason  TEXT        NULL,
    created_by         UUID        NOT NULL,
    updated_by         UUID        NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version            INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_partner_tenant_bindings PRIMARY KEY (id),
    CONSTRAINT fk_partner_tenant_bindings_partner
        FOREIGN KEY (partner_id) REFERENCES partners(id) ON DELETE RESTRICT,
    CONSTRAINT fk_partner_tenant_bindings_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_ptb_validity_window
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_ptb_suspended_alignment
        CHECK ((status = 'SUSPENDED') = (suspended_at IS NOT NULL)),
    CONSTRAINT ck_ptb_suspended_reason_required
        CHECK (suspended_at IS NULL OR suspended_reason IS NOT NULL),
    CONSTRAINT ck_ptb_terminated_alignment
        CHECK ((status = 'TERMINATED') = (terminated_at IS NOT NULL)),
    CONSTRAINT ck_ptb_terminated_reason_required
        CHECK (terminated_at IS NULL OR terminated_reason IS NOT NULL),
    CONSTRAINT ck_ptb_version_non_negative
        CHECK (version >= 0)
);

-- One tenant may be actively governed by only one partner at a time.
CREATE UNIQUE INDEX uq_partner_tenant_bindings_active_tenant
    ON partner_tenant_bindings (tenant_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_partner_tenant_bindings_partner_status
    ON partner_tenant_bindings (partner_id, status);

CREATE INDEX idx_partner_tenant_bindings_tenant_status
    ON partner_tenant_bindings (tenant_id, status);

-- Immutable identity + lifecycle safety. Transfer is modeled as terminate old
-- binding then create a new binding; never mutate ownership in place.
CREATE OR REPLACE FUNCTION enforce_partner_tenant_binding_lifecycle()
RETURNS TRIGGER AS $$
DECLARE
    partner_status TEXT;
    tenant_status TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'PARTNER_TENANT_BINDING_DELETE_FORBIDDEN: terminate the binding instead'
            USING ERRCODE = '23001';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.partner_id IS DISTINCT FROM OLD.partner_id
           OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id THEN
            RAISE EXCEPTION 'PARTNER_TENANT_BINDING_IDENTITY_IMMUTABLE'
                USING ERRCODE = '23001';
        END IF;

        IF OLD.status = 'TERMINATED' AND NEW.status <> 'TERMINATED' THEN
            RAISE EXCEPTION 'PARTNER_TENANT_BINDING_TERMINAL'
                USING ERRCODE = '23001';
        END IF;

        NEW.updated_at = NOW();
        NEW.version = OLD.version + 1;
    END IF;

    IF NEW.status = 'ACTIVE' THEN
        SELECT status INTO partner_status FROM partners WHERE id = NEW.partner_id;
        SELECT status INTO tenant_status FROM tenants WHERE id = NEW.tenant_id;

        -- Known-but-not-ACTIVE partners are rejected here (23001). Unknown ids
        -- fall through so the canonical FK constraint reports 23503 instead of
        -- this trigger masking the referential-integrity violation.
        IF partner_status IS NOT NULL AND partner_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'PARTNER_TENANT_BINDING_PARTNER_NOT_ACTIVE'
                USING ERRCODE = '23001';
        END IF;

        IF tenant_status = 'ARCHIVED' THEN
            RAISE EXCEPTION 'PARTNER_TENANT_BINDING_TENANT_ARCHIVED'
                USING ERRCODE = '23001';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_tenant_bindings_lifecycle_guard
    BEFORE INSERT OR UPDATE OR DELETE ON partner_tenant_bindings
    FOR EACH ROW
    EXECUTE FUNCTION enforce_partner_tenant_binding_lifecycle();

ALTER TABLE partner_tenant_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_tenant_bindings FORCE ROW LEVEL SECURITY;

-- Platform control plane can inspect the complete binding registry.
CREATE POLICY partner_tenant_bindings_platform_select
ON partner_tenant_bindings
FOR SELECT
USING (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

-- Partner principals can inspect only their own relationship records.
CREATE POLICY partner_tenant_bindings_partner_select
ON partner_tenant_bindings
FOR SELECT
USING (
    NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
    AND partner_id::text = current_setting('app.partner_id', true)
);

-- Only the platform control plane can create or change a binding. There is
-- intentionally no partner INSERT/UPDATE/DELETE policy.
CREATE POLICY partner_tenant_bindings_platform_insert
ON partner_tenant_bindings
FOR INSERT
WITH CHECK (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

CREATE POLICY partner_tenant_bindings_platform_update
ON partner_tenant_bindings
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

CREATE OR REPLACE FUNCTION trg_partner_tenant_bindings_audit()
RETURNS TRIGGER AS $$
DECLARE
    event_type_val TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        event_type_val := 'PARTNER_TENANT_BOUND';
    ELSIF TG_OP = 'UPDATE' THEN
        event_type_val := CASE
            WHEN OLD.status <> 'ACTIVE' AND NEW.status = 'ACTIVE'
                THEN 'PARTNER_TENANT_BINDING_ACTIVATED'
            WHEN OLD.status = 'ACTIVE' AND NEW.status = 'SUSPENDED'
                THEN 'PARTNER_TENANT_BINDING_SUSPENDED'
            WHEN OLD.status = 'ACTIVE' AND NEW.status = 'INACTIVE'
                THEN 'PARTNER_TENANT_BINDING_DEACTIVATED'
            WHEN OLD.status <> 'TERMINATED' AND NEW.status = 'TERMINATED'
                THEN 'PARTNER_TENANT_BINDING_TERMINATED'
            ELSE 'PARTNER_TENANT_BINDING_UPDATED'
        END;
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
        'PARTNER_TENANT_BINDING',
        NEW.id,
        jsonb_build_object(
            'partner_id', NEW.partner_id,
            'target_tenant_id', NEW.tenant_id,
            'binding_id', NEW.id,
            'previous_status', CASE WHEN TG_OP = 'UPDATE' THEN OLD.status ELSE NULL END,
            'status', NEW.status,
            'version', NEW.version
        ),
        NOW()
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_tenant_bindings_audit
    AFTER INSERT OR UPDATE ON partner_tenant_bindings
    FOR EACH ROW
    EXECUTE FUNCTION trg_partner_tenant_bindings_audit();

COMMENT ON TABLE partner_tenant_bindings IS $comment$
W2-T4 governed Partner<->Tenant relationship registry. Platform-control-plane
writes only; partner principals have read-only visibility into their own
bindings. A binding establishes commercial/control-plane relationship only and
does not grant tenant business-data permissions or delegated capabilities.
$comment$;
