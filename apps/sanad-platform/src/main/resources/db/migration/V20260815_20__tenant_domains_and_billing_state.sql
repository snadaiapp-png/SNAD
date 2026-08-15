-- ============================================================
-- V20260815_20: Tenant Domain Management + Subscription Billing State
--
-- Implements two forward-only schema additions:
--   1. tenant_domains — tenant-scoped routing hostnames for the
--      application/store/website domains (Phase 4 of the master plan).
--      Designed for future Vercel domain routing; no permanent hard-coded
--      public domain value is introduced by this migration.
--   2. tenant_subscriptions.billing_state — derived lifecycle column
--      driven by billing events (invoices past due, suspended, etc.)
--      to enable the future dunning scheduler to transition
--      ACTIVE → PAST_DUE → SUSPENDED without round-tripping through
--      the invoice tables.
--
-- Design principles (mirrors all prior V20260815_* migrations):
--   * Tenant-scoped: every row carries tenant_id.
--   * State-machine CHECK constraints.
--   * TIMESTAMPTZ for temporal fields.
--   * UUID primary keys.
--   * Idempotent (IF NOT EXISTS).
--   * No flyway_schema_history manipulation.
--
-- H2 compatibility: this migration uses only standard DDL that runs
-- unchanged on both PostgreSQL (production) and H2 (test profile).
-- PostgreSQL-specific RLS policies are added in V20260815_22.
-- ============================================================

-- ============================================================
-- STEP 1: tenant_domains — tenant-scoped routing hostnames
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant_domains (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    hostname            VARCHAR(253)    NOT NULL,        -- RFC 1035 max 253 chars
    domain_type         VARCHAR(20)     NOT NULL,        -- APPLICATION | STORE | WEBSITE
    origin              VARCHAR(20)     NOT NULL DEFAULT 'CUSTOM',  -- CUSTOM | DEFAULT_GENERATED
    status              VARCHAR(20)     NOT NULL DEFAULT 'UNVERIFIED',  -- UNVERIFIED | VERIFIED | ACTIVE | INACTIVE
    verification_token  VARCHAR(128),                    -- random challenge token
    verification_method VARCHAR(20),                    -- DNS_TXT | DNS_CNAME | HTTP
    verified_at         TIMESTAMP WITH TIME ZONE,
    verified_by         UUID,                            -- user id of the actor who confirmed verification
    ssl_cert_arn        VARCHAR(255),                    -- future: ACM cert ARN or Vercel domain config id
    is_primary          BOOLEAN         NOT NULL DEFAULT FALSE,
    failure_reason     VARCHAR(500),
    last_verified_at    TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          UUID,
    CONSTRAINT pk_tenant_domains PRIMARY KEY (id),
    CONSTRAINT uk_tenant_domains_tenant_hostname UNIQUE (tenant_id, hostname),
    CONSTRAINT uk_tenant_domains_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_tenant_domains_domain_type CHECK (domain_type IN ('APPLICATION','STORE','WEBSITE')),
    CONSTRAINT ck_tenant_domains_origin CHECK (origin IN ('CUSTOM','DEFAULT_GENERATED')),
    CONSTRAINT ck_tenant_domains_status CHECK (status IN ('UNVERIFIED','VERIFIED','ACTIVE','INACTIVE')),
    CONSTRAINT ck_tenant_domains_method CHECK (verification_method IS NULL OR verification_method IN ('DNS_TXT','DNS_CNAME','HTTP'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_domains_tenant_type ON tenant_domains(tenant_id, domain_type);
CREATE INDEX IF NOT EXISTS idx_tenant_domains_tenant_status ON tenant_domains(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_tenant_domains_hostname ON tenant_domains(hostname);

-- ============================================================
-- STEP 2: tenant_subscriptions.billing_state — derived billing column
-- ============================================================
-- The lifecycle is driven by the dunning scheduler (added as a
-- @Scheduled job in BillingStateService) which examines overdue
-- billing_invoices and transitions:
--   ACTIVE → PAST_DUE   when an invoice is past due_at + grace period
--   PAST_DUE → SUSPENDED  after a configurable secondary grace period
--   SUSPENDED → ACTIVE   on successful payment (markInvoicePaid)
-- TRIALING is preserved until trial_ends_at elapses.
ALTER TABLE tenant_subscriptions
    ADD COLUMN IF NOT EXISTS billing_state VARCHAR(20) NOT NULL DEFAULT 'CURRENT';

-- Backfill existing subscriptions from their existing status.
UPDATE tenant_subscriptions
   SET billing_state = CASE
       WHEN status = 'TRIALING'  THEN 'TRIALING'
       WHEN status = 'ACTIVE'    THEN 'CURRENT'
       WHEN status = 'PAST_DUE'  THEN 'PAST_DUE'
       WHEN status = 'SUSPENDED' THEN 'SUSPENDED'
       WHEN status = 'CANCELLED' THEN 'CANCELLED'
       ELSE 'CURRENT'
   END;

ALTER TABLE tenant_subscriptions
    DROP CONSTRAINT IF EXISTS ck_tenant_subscriptions_billing_state;
ALTER TABLE tenant_subscriptions
    ADD CONSTRAINT ck_tenant_subscriptions_billing_state
        CHECK (billing_state IN ('TRIALING','CURRENT','PAST_DUE','SUSPENDED','CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_billing_state
    ON tenant_subscriptions(tenant_id, billing_state);
