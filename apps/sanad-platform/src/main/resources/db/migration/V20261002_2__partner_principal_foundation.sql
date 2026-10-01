-- ============================================================================
-- V20261002_2 — Wave 2 / Task 1: Partner principal foundation (CORRECTED).
--
-- Creates the canonical `partners` table as an independent commercial principal.
-- A partner is NOT a tenant — it is a platform-level principal.
--
-- This is the CORRECTED version of the old V20261002_1 (which collided with
-- V20261002_1__hr_g3_performance_reviews_rls.sql on main). The old local
-- commit e45b828e is preserved as forensic reference only and is NOT
-- cherry-picked — its migration version is invalid on latest main, and its
-- design included Commercial Identity fields that belong to a later W2 slice.
--
-- Design authority:
--   docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md
--   Section 13: "A partner is a separate principal, not a normal tenant."
--   Section 13: "Commercial administration does not imply access to CRM, HRM,
--    payroll, accounting, or other tenant business data."
--
-- Column scope (per W2-T1 directive §7 classification):
--   A. Required for W2-T1 principal/security foundation: INCLUDED
--   B. Commercial Identity — DEFERRED to later W2 slice: EXCLUDED
--   C. Unsupported/unclear invariant — REMOVE: EXCLUDED
--
-- Excluded (Category B — Commercial Identity, deferred):
--   legal_name, trade_name, commercial_registration_number,
--   tax_registration_number, country, city, national_address,
--   billing_address, business_email, finance_email, phone_number,
--   website, primary_logo_url, invoice_logo_url, verification_status
--
-- Excluded (Category C — no canonical domain rule):
--   UNIQUE(partner_type, legal_name) — REMOVED
--   (identity is UUID-based, not human-readable name uniqueness)
--
-- RLS decision (per Phase 9 threat model):
--   NOT APPLIED. Partners is a platform-level catalog table (mirrors
--   access_capabilities and platform_role_metadata). Application-layer
--   @RequireCapability("PARTNER.*") is the authoritative access control.
--   Partner-scoped filtering (Partner A cannot read Partner B) is enforced
--   at the SERVICE layer via PartnerAuthorizationContextResolver.
--   Tests must prove: tenant user cannot enumerate partners through API.
--
-- Audit: reuses authorization_change_events (W1 V20261001_1) with
--        target_type='PARTNER'.
--
-- Next collision-free version: 20261002.3 (latest was 20261002.2).
-- ============================================================================

CREATE TABLE partners (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    partner_type        TEXT        NOT NULL CHECK (
        partner_type IN ('PARTNER','AGENT','RESELLER','DISTRIBUTOR','SELLER')
    ),
    status              TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (
        status IN ('ACTIVE','INACTIVE','SUSPENDED','TERMINATED')
    ),
    suspended_at        TIMESTAMPTZ NULL,
    suspended_reason    TEXT        NULL,
    terminated_at       TIMESTAMPTZ NULL,
    terminated_reason   TEXT        NULL,
    updated_by          UUID        NULL,
    created_by          UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_partners PRIMARY KEY (id),
    CONSTRAINT ck_partners_suspended_when_suspended_status
        CHECK ((status = 'SUSPENDED') = (suspended_at IS NOT NULL)),
    CONSTRAINT ck_partners_terminated_when_terminated_status
        CHECK ((status = 'TERMINATED') = (terminated_at IS NOT NULL)),
    CONSTRAINT ck_partners_suspended_reason_when_suspended
        CHECK (suspended_at IS NULL OR suspended_reason IS NOT NULL),
    CONSTRAINT ck_partners_terminated_reason_when_terminated
        CHECK (terminated_at IS NULL OR terminated_reason IS NOT NULL),
    CONSTRAINT ck_partners_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_partners_status ON partners (status);
CREATE INDEX idx_partners_type_status ON partners (partner_type, status);

-- updated_at trigger (mirrors platform convention)
CREATE OR REPLACE FUNCTION trg_partners_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partners_updated_at
    BEFORE UPDATE ON partners
    FOR EACH ROW
    EXECUTE FUNCTION trg_partners_updated_at();

-- authorization_change_event trigger (reuses W1 audit infra)
CREATE OR REPLACE FUNCTION trg_partners_authorization_change_event()
RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.status IS DISTINCT FROM OLD.status)
       OR (NEW.version IS DISTINCT FROM OLD.version) THEN
        INSERT INTO authorization_change_events (
            id, tenant_id, event_type, actor_user_id,
            target_type, target_id, payload, created_at
        )
        VALUES (
            gen_random_uuid(),
            NULL,
            CASE
                WHEN NEW.status = 'SUSPENDED' AND OLD.status <> 'SUSPENDED' THEN 'PARTNER_SUSPENDED'
                WHEN NEW.status = 'TERMINATED' AND OLD.status <> 'TERMINATED' THEN 'PARTNER_TERMINATED'
                WHEN NEW.status = 'ACTIVE' AND OLD.status <> 'ACTIVE' THEN 'PARTNER_ACTIVATED'
                WHEN NEW.status = 'INACTIVE' AND OLD.status <> 'INACTIVE' THEN 'PARTNER_DEACTIVATED'
                ELSE 'PARTNER_AUTHORITY_VERSION_INCREMENTED'
            END,
            NEW.updated_by,
            'PARTNER',
            NEW.id,
            jsonb_build_object(
                'partner_id', NEW.id,
                'partner_type', NEW.partner_type,
                'previous_status', OLD.status,
                'new_status', NEW.status,
                'previous_version', OLD.version,
                'new_version', NEW.version,
                'reason', COALESCE(NEW.suspended_reason, NEW.terminated_reason)
            ),
            NOW()
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER partners_authorization_change_event
    AFTER UPDATE ON partners
    FOR EACH ROW
    EXECUTE FUNCTION trg_partners_authorization_change_event();

COMMENT ON TABLE partners IS $comment$
W2-T1 partner principal foundation (V20261002_2). Independent commercial
principal — NOT a tenant. Minimal schema: partner_type, status, version,
lifecycle timestamps, audit provenance. Commercial identity fields are
deferred to a later W2 slice. No UNIQUE on human-readable names — identity
is UUID-based. RLS intentionally NOT applied — platform-level catalog table
(mirrors access_capabilities / platform_role_metadata). Application-layer
@RequireCapability("PARTNER.*") is authoritative access control.
$comment$;
