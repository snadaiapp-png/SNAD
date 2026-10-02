-- ============================================================================
-- V20261003_4 — Wave 2 / Task 3: Partner membership & admin boundary.
--
-- Canonical partner-scoped membership foundation.
--
-- Invariants:
--   * Partner is NOT a tenant.
--   * Membership is explicit and references canonical partners/users.
--   * FORCE RLS fails closed without app.partner_id.
--   * Partner A cannot read/write Partner B memberships.
--   * The last ACTIVE PARTNER_ADMIN cannot be removed, suspended, deactivated
--     or demoted.
--   * Partner membership audit remains partner-scoped inside
--     authorization_change_events.
--
-- Explicitly out of scope:
--   partner_tenant_bindings, partner_delegation_grants, commercial identity,
--   subscription/billing/settlement, tenant business-data access.
-- ============================================================================

CREATE TABLE partner_memberships (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    partner_id        UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    membership_role   TEXT        NOT NULL CHECK (
        membership_role IN ('PARTNER_ADMIN', 'PARTNER_USER')
    ),
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

    CONSTRAINT pk_partner_memberships PRIMARY KEY (id),
    CONSTRAINT fk_partner_memberships_partner
        FOREIGN KEY (partner_id) REFERENCES partners(id) ON DELETE RESTRICT,
    CONSTRAINT fk_partner_memberships_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_pm_valid_until_after_valid_from
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_pm_suspended_alignment
        CHECK ((status = 'SUSPENDED') = (suspended_at IS NOT NULL)),
    CONSTRAINT ck_pm_revoked_alignment
        CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
    CONSTRAINT ck_pm_suspended_reason_required
        CHECK (suspended_at IS NULL OR suspended_reason IS NOT NULL),
    CONSTRAINT ck_pm_revoked_reason_required
        CHECK (revoked_at IS NULL OR revoked_reason IS NOT NULL),
    CONSTRAINT ck_pm_version_non_negative
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_partner_memberships_active
    ON partner_memberships (partner_id, user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_partner_memberships_partner
    ON partner_memberships (partner_id);

CREATE INDEX idx_partner_memberships_user
    ON partner_memberships (user_id);

CREATE INDEX idx_partner_memberships_partner_role_status
    ON partner_memberships (partner_id, membership_role, status);

CREATE OR REPLACE FUNCTION trg_partner_memberships_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_memberships_updated_at
    BEFORE UPDATE ON partner_memberships
    FOR EACH ROW
    EXECUTE FUNCTION trg_partner_memberships_updated_at();

ALTER TABLE partner_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_memberships FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS partner_isolation ON partner_memberships;
CREATE POLICY partner_isolation
ON partner_memberships
FOR ALL
USING (
    NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
    AND partner_id::text = current_setting('app.partner_id', true)
)
WITH CHECK (
    NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
    AND partner_id::text = current_setting('app.partner_id', true)
);

-- Serialize all destructive admin mutations through the canonical partner row.
CREATE OR REPLACE FUNCTION enforce_last_partner_admin()
RETURNS TRIGGER AS $$
DECLARE
    active_admin_count INTEGER;
BEGIN
    IF TG_OP = 'DELETE'
       OR (
            TG_OP = 'UPDATE'
            AND OLD.membership_role = 'PARTNER_ADMIN'
            AND OLD.status = 'ACTIVE'
            AND (
                NEW.membership_role <> 'PARTNER_ADMIN'
                OR NEW.status <> 'ACTIVE'
            )
       ) THEN

        PERFORM 1
          FROM partners
         WHERE id = OLD.partner_id
         FOR UPDATE;

        SELECT COUNT(*)
          INTO active_admin_count
          FROM partner_memberships
         WHERE partner_id = OLD.partner_id
           AND membership_role = 'PARTNER_ADMIN'
           AND status = 'ACTIVE'
           AND id <> OLD.id;

        IF active_admin_count = 0 THEN
            RAISE EXCEPTION
                'LAST_PARTNER_ADMIN: partner % must retain at least one ACTIVE PARTNER_ADMIN',
                OLD.partner_id
                USING ERRCODE = '23001';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_memberships_last_admin_guard
    BEFORE DELETE OR UPDATE ON partner_memberships
    FOR EACH ROW
    EXECUTE FUNCTION enforce_last_partner_admin();

-- Extend W1 authorization_change_events RLS with an explicit partner branch.
-- Tenant and platform branches remain unchanged.
DROP POLICY IF EXISTS authorization_change_events_isolation
    ON authorization_change_events;

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
    OR
    (
        tenant_id IS NULL
        AND NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
        AND payload ->> 'partner_id' = current_setting('app.partner_id', true)
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
    OR
    (
        tenant_id IS NULL
        AND NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
        AND payload ->> 'partner_id' = current_setting('app.partner_id', true)
    )
);

CREATE OR REPLACE FUNCTION trg_partner_memberships_audit()
RETURNS TRIGGER AS $$
DECLARE
    event_type_val TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        event_type_val := CASE
            WHEN NEW.membership_role = 'PARTNER_ADMIN'
                THEN 'PARTNER_ADMIN_GRANTED'
            ELSE 'PARTNER_MEMBERSHIP_CREATED'
        END;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.membership_role <> 'PARTNER_ADMIN'
           AND NEW.membership_role = 'PARTNER_ADMIN'
           AND NEW.status = 'ACTIVE' THEN
            event_type_val := 'PARTNER_ADMIN_GRANTED';
        ELSIF OLD.membership_role = 'PARTNER_ADMIN'
           AND NEW.membership_role <> 'PARTNER_ADMIN' THEN
            event_type_val := 'PARTNER_ADMIN_REVOKED';
        ELSIF OLD.status = 'ACTIVE'
           AND NEW.status = 'SUSPENDED' THEN
            event_type_val := 'PARTNER_MEMBERSHIP_SUSPENDED';
        ELSIF OLD.status = 'ACTIVE'
           AND NEW.status = 'INACTIVE' THEN
            event_type_val := 'PARTNER_MEMBERSHIP_DEACTIVATED';
        ELSIF OLD.status <> 'ACTIVE'
           AND NEW.status = 'ACTIVE' THEN
            event_type_val := 'PARTNER_MEMBERSHIP_ACTIVATED';
        ELSIF OLD.status <> 'REVOKED'
           AND NEW.status = 'REVOKED' THEN
            event_type_val := 'PARTNER_MEMBERSHIP_REVOKED';
        ELSE
            event_type_val := 'PARTNER_MEMBERSHIP_UPDATED';
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
        'PARTNER_MEMBERSHIP',
        NEW.id,
        jsonb_build_object(
            'partner_id', NEW.partner_id,
            'user_id', NEW.user_id,
            'membership_id', NEW.id,
            'membership_role', NEW.membership_role,
            'status', NEW.status,
            'version', NEW.version
        ),
        NOW()
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_memberships_audit
    AFTER INSERT OR UPDATE ON partner_memberships
    FOR EACH ROW
    EXECUTE FUNCTION trg_partner_memberships_audit();

COMMENT ON TABLE partner_memberships IS $comment$
W2-T3 canonical partner membership boundary. Partner-scoped, FORCE-RLS,
explicit FK to partners(id) and canonical users(id), last-admin protection,
and partner-scoped authorization audit. No tenant binding/delegation/billing
semantics are introduced by this table.
$comment$;
