-- ============================================================================
-- V20261004_1 — Wave 2 / Task 9: Partner recovery safety.
--
-- Adds a partner-scoped membership-capability primitive and protects the final
-- AUTHORIZATION.RECOVER path independently from LAST_PARTNER_ADMIN.
--
-- Security invariants:
--   * AUTHORIZATION.RECOVER remains the canonical capability registry entry.
--   * Recovery authority belongs to an ACTIVE, in-window partner membership.
--   * Partner context may read only its own recovery grants; platform control
--     plane is the only writer.
--   * Physical DELETE is forbidden; lifecycle is retained/audited.
--   * The final currently-valid recovery path cannot be deactivated, revoked,
--     expired by mutation, or invalidated by membership lifecycle mutation.
--   * Destructive recovery mutations serialize through the canonical partner
--     row to preserve the invariant under concurrency.
--   * LAST_PARTNER_ADMIN is intentionally untouched and remains an independent
--     safety invariant.
-- ============================================================================

-- The executive/control-plane needs read-only visibility of memberships to
-- validate recovery assignments. Existing partner write isolation remains
-- unchanged; this is SELECT-only and does not alter LAST_PARTNER_ADMIN.
CREATE POLICY partner_memberships_platform_select
ON partner_memberships
FOR SELECT
USING (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

CREATE TABLE partner_membership_capabilities (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    partner_id        UUID        NOT NULL,
    membership_id     UUID        NOT NULL,
    capability_id     UUID        NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'REVOKED')
    ),
    valid_from        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until       TIMESTAMPTZ NULL,
    revoked_at        TIMESTAMPTZ NULL,
    revoked_reason    TEXT        NULL,
    created_by        UUID        NOT NULL,
    updated_by        UUID        NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_partner_membership_capabilities PRIMARY KEY (id),
    CONSTRAINT fk_pmc_partner
        FOREIGN KEY (partner_id) REFERENCES partners(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pmc_membership
        FOREIGN KEY (membership_id) REFERENCES partner_memberships(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pmc_capability
        FOREIGN KEY (capability_id) REFERENCES access_capabilities(id) ON DELETE RESTRICT,
    CONSTRAINT ck_pmc_validity_window
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_pmc_revoked_alignment
        CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
    CONSTRAINT ck_pmc_revoked_reason_required
        CHECK (revoked_at IS NULL OR revoked_reason IS NOT NULL),
    CONSTRAINT ck_pmc_version_non_negative
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_pmc_active_membership_capability
    ON partner_membership_capabilities (partner_id, membership_id, capability_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_pmc_partner_status
    ON partner_membership_capabilities (partner_id, status);

CREATE INDEX idx_pmc_membership_status
    ON partner_membership_capabilities (membership_id, status);

-- ---------------------------------------------------------------------------
-- RLS: platform control plane writes; partners can only read their own rows.
-- ---------------------------------------------------------------------------
ALTER TABLE partner_membership_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE partner_membership_capabilities FORCE ROW LEVEL SECURITY;

CREATE POLICY pmc_platform_select
ON partner_membership_capabilities
FOR SELECT
USING (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

CREATE POLICY pmc_platform_insert
ON partner_membership_capabilities
FOR INSERT
WITH CHECK (
    current_setting('app.tenant_id', true)
        = '00000000-0000-0000-0000-000000000001'
    AND NULLIF(current_setting('app.partner_id', true), '') IS NULL
);

CREATE POLICY pmc_platform_update
ON partner_membership_capabilities
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

CREATE POLICY pmc_partner_select
ON partner_membership_capabilities
FOR SELECT
USING (
    NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
    AND partner_id::text = current_setting('app.partner_id', true)
);

-- ---------------------------------------------------------------------------
-- Lifecycle validation. The generic shape is intentionally allowlisted to the
-- one capability approved for T9; future widening requires a governed migration.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_partner_membership_capability_lifecycle()
RETURNS TRIGGER AS $$
DECLARE
    membership_partner UUID;
    membership_status TEXT;
    membership_valid_from TIMESTAMPTZ;
    membership_valid_until TIMESTAMPTZ;
    capability_code TEXT;
    capability_status TEXT;
    capability_protected BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'PARTNER_MEMBERSHIP_CAPABILITY_DELETE_FORBIDDEN: revoke the grant instead'
            USING ERRCODE = '23001';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.partner_id IS DISTINCT FROM OLD.partner_id
           OR NEW.membership_id IS DISTINCT FROM OLD.membership_id
           OR NEW.capability_id IS DISTINCT FROM OLD.capability_id THEN
            RAISE EXCEPTION 'PARTNER_MEMBERSHIP_CAPABILITY_IDENTITY_IMMUTABLE'
                USING ERRCODE = '23001';
        END IF;

        IF OLD.status = 'REVOKED' AND NEW.status <> 'REVOKED' THEN
            RAISE EXCEPTION 'PARTNER_MEMBERSHIP_CAPABILITY_TERMINAL'
                USING ERRCODE = '23001';
        END IF;

        NEW.updated_at = NOW();
        NEW.version = OLD.version + 1;
    END IF;

    SELECT partner_id, status, valid_from, valid_until
      INTO membership_partner, membership_status,
           membership_valid_from, membership_valid_until
      FROM partner_memberships
     WHERE id = NEW.membership_id;

    IF membership_partner IS NULL THEN
        RETURN NEW; -- canonical FK reports 23503
    END IF;

    IF membership_partner <> NEW.partner_id THEN
        RAISE EXCEPTION 'PARTNER_RECOVERY_MEMBERSHIP_MISMATCH'
            USING ERRCODE = '23001';
    END IF;

    SELECT code, status, system_protected
      INTO capability_code, capability_status, capability_protected
      FROM access_capabilities
     WHERE id = NEW.capability_id;

    IF capability_code IS NULL THEN
        RETURN NEW; -- canonical FK reports 23503
    END IF;

    IF capability_code <> 'AUTHORIZATION.RECOVER'
       OR capability_status <> 'ACTIVE'
       OR capability_protected IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'PARTNER_RECOVERY_CAPABILITY_INVALID'
            USING ERRCODE = '23001';
    END IF;

    IF NEW.status = 'ACTIVE' THEN
        IF membership_status <> 'ACTIVE'
           OR membership_valid_from > NOW()
           OR (membership_valid_until IS NOT NULL AND membership_valid_until <= NOW()) THEN
            RAISE EXCEPTION 'PARTNER_RECOVERY_MEMBERSHIP_INACTIVE'
                USING ERRCODE = '23001';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pmc_lifecycle
    BEFORE INSERT OR UPDATE OR DELETE ON partner_membership_capabilities
    FOR EACH ROW
    EXECUTE FUNCTION enforce_partner_membership_capability_lifecycle();

-- ---------------------------------------------------------------------------
-- Final recovery path protection on grant lifecycle.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_final_partner_recovery_capability()
RETURNS TRIGGER AS $$
DECLARE
    remaining_recovery_count INTEGER;
    new_still_current BOOLEAN;
BEGIN
    IF TG_OP <> 'UPDATE' THEN
        RETURN NEW;
    END IF;

    -- Only destructive mutations of a currently-valid recovery path need the
    -- survival simulation. Non-destructive metadata updates are allowed.
    IF OLD.status <> 'ACTIVE'
       OR OLD.valid_from > NOW()
       OR (OLD.valid_until IS NOT NULL AND OLD.valid_until <= NOW()) THEN
        RETURN NEW;
    END IF;

    new_still_current :=
        NEW.status = 'ACTIVE'
        AND NEW.valid_from <= NOW()
        AND (NEW.valid_until IS NULL OR NEW.valid_until > NOW());

    IF new_still_current THEN
        RETURN NEW;
    END IF;

    PERFORM 1 FROM partners WHERE id = OLD.partner_id FOR UPDATE;

    SELECT COUNT(DISTINCT pmc.membership_id)
      INTO remaining_recovery_count
      FROM partner_membership_capabilities pmc
      JOIN partner_memberships pm
        ON pm.id = pmc.membership_id
       AND pm.partner_id = pmc.partner_id
      JOIN access_capabilities ac
        ON ac.id = pmc.capability_id
     WHERE pmc.partner_id = OLD.partner_id
       AND pmc.id <> OLD.id
       AND pmc.status = 'ACTIVE'
       AND pmc.valid_from <= NOW()
       AND (pmc.valid_until IS NULL OR pmc.valid_until > NOW())
       AND pm.status = 'ACTIVE'
       AND pm.valid_from <= NOW()
       AND (pm.valid_until IS NULL OR pm.valid_until > NOW())
       AND ac.code = 'AUTHORIZATION.RECOVER'
       AND ac.status = 'ACTIVE'
       AND ac.system_protected = TRUE;

    IF remaining_recovery_count = 0 THEN
        RAISE EXCEPTION
            'FINAL_PARTNER_RECOVERY_CAPABILITY_REQUIRED: partner % must retain at least one active AUTHORIZATION.RECOVER path',
            OLD.partner_id
            USING ERRCODE = '23001';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pmc_final_recovery_guard
    BEFORE UPDATE ON partner_membership_capabilities
    FOR EACH ROW
    EXECUTE FUNCTION enforce_final_partner_recovery_capability();

-- ---------------------------------------------------------------------------
-- Membership lifecycle can invalidate a recovery path without changing the
-- grant row, so guard that path independently from LAST_PARTNER_ADMIN.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_partner_membership_recovery_survival()
RETURNS TRIGGER AS $$
DECLARE
    current_recovery_count INTEGER;
    remaining_recovery_count INTEGER;
    old_membership_current BOOLEAN;
    new_membership_current BOOLEAN;
BEGIN
    IF TG_OP <> 'UPDATE' THEN
        RETURN NEW;
    END IF;

    old_membership_current :=
        OLD.status = 'ACTIVE'
        AND OLD.valid_from <= NOW()
        AND (OLD.valid_until IS NULL OR OLD.valid_until > NOW());

    new_membership_current :=
        NEW.status = 'ACTIVE'
        AND NEW.valid_from <= NOW()
        AND (NEW.valid_until IS NULL OR NEW.valid_until > NOW());

    IF NOT old_membership_current OR new_membership_current THEN
        RETURN NEW;
    END IF;

    SELECT COUNT(*)
      INTO current_recovery_count
      FROM partner_membership_capabilities pmc
      JOIN access_capabilities ac ON ac.id = pmc.capability_id
     WHERE pmc.partner_id = OLD.partner_id
       AND pmc.membership_id = OLD.id
       AND pmc.status = 'ACTIVE'
       AND pmc.valid_from <= NOW()
       AND (pmc.valid_until IS NULL OR pmc.valid_until > NOW())
       AND ac.code = 'AUTHORIZATION.RECOVER'
       AND ac.status = 'ACTIVE'
       AND ac.system_protected = TRUE;

    IF current_recovery_count = 0 THEN
        RETURN NEW;
    END IF;

    PERFORM 1 FROM partners WHERE id = OLD.partner_id FOR UPDATE;

    SELECT COUNT(DISTINCT pmc.membership_id)
      INTO remaining_recovery_count
      FROM partner_membership_capabilities pmc
      JOIN partner_memberships pm
        ON pm.id = pmc.membership_id
       AND pm.partner_id = pmc.partner_id
      JOIN access_capabilities ac
        ON ac.id = pmc.capability_id
     WHERE pmc.partner_id = OLD.partner_id
       AND pm.id <> OLD.id
       AND pmc.status = 'ACTIVE'
       AND pmc.valid_from <= NOW()
       AND (pmc.valid_until IS NULL OR pmc.valid_until > NOW())
       AND pm.status = 'ACTIVE'
       AND pm.valid_from <= NOW()
       AND (pm.valid_until IS NULL OR pm.valid_until > NOW())
       AND ac.code = 'AUTHORIZATION.RECOVER'
       AND ac.status = 'ACTIVE'
       AND ac.system_protected = TRUE;

    IF remaining_recovery_count = 0 THEN
        RAISE EXCEPTION
            'FINAL_PARTNER_RECOVERY_CAPABILITY_REQUIRED: partner % must retain at least one active AUTHORIZATION.RECOVER path',
            OLD.partner_id
            USING ERRCODE = '23001';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partner_memberships_recovery_guard
    BEFORE UPDATE ON partner_memberships
    FOR EACH ROW
    EXECUTE FUNCTION enforce_partner_membership_recovery_survival();

-- ---------------------------------------------------------------------------
-- Audit. Rejected mutations never reach this AFTER trigger.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_partner_membership_capabilities_audit()
RETURNS TRIGGER AS $$
DECLARE
    event_type_val TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        event_type_val := 'PARTNER_RECOVERY_GRANTED';
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'ACTIVE' AND NEW.status = 'REVOKED' THEN
            event_type_val := 'PARTNER_RECOVERY_REVOKED';
        ELSIF OLD.status = 'ACTIVE' AND NEW.status = 'INACTIVE' THEN
            event_type_val := 'PARTNER_RECOVERY_DEACTIVATED';
        ELSIF OLD.status <> 'ACTIVE' AND NEW.status = 'ACTIVE' THEN
            event_type_val := 'PARTNER_RECOVERY_ACTIVATED';
        ELSE
            event_type_val := 'PARTNER_RECOVERY_UPDATED';
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
        'PARTNER_RECOVERY_GRANT',
        NEW.id,
        jsonb_build_object(
            'partner_id', NEW.partner_id,
            'membership_id', NEW.membership_id,
            'capability_id', NEW.capability_id,
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

CREATE TRIGGER pmc_audit
    AFTER INSERT OR UPDATE ON partner_membership_capabilities
    FOR EACH ROW
    EXECUTE FUNCTION trg_partner_membership_capabilities_audit();

COMMENT ON TABLE partner_membership_capabilities IS $comment$
W2-T9 partner-scoped capability assignment primitive. Initial allowlist is
AUTHORIZATION.RECOVER only. Final recovery-path survival is independent from
LAST_PARTNER_ADMIN and serialized through the canonical partners row.
$comment$;
