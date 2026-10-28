-- ============================================================
-- V20261022_1: Zid e-commerce platform integration schema
--
-- Forward-only migration following the fail-closed RLS convention
-- established by V20261021_1 (crm_post_baseline_fail_closed_rls).
--
-- Design notes:
--   * Every tenant-scoped table carries tenant_id NOT NULL and gets
--     ENABLE + FORCE ROW LEVEL SECURITY with a fail-closed
--     tenant_isolation policy (absent/empty app.tenant_id => zero rows,
--     rejected writes).
--   * Zid OAuth token material is stored ONLY in ciphertext columns
--     produced by JcePlatformCryptographyService (AES-GCM, AAD bound to
--     tenant|purpose|keyVersion). No plaintext token columns exist.
--   * Uniqueness constraints are the last line of defense for tenant
--     binding: a Zid store can be bound to exactly one tenant
--     (uk_zid_connections_store), and one tenant cannot bind the same
--     store twice (uk_zid_connections_tenant_store).
--   * Capabilities are seeded ACTIVE but NOT auto-granted to any role;
--     tenant administrators must attach them through the existing
--     access management UI (deny-by-default RBAC).
-- ============================================================

-- ------------------------------------------------------------
-- 1. zid_store_connections — trusted binding between a Zid store
--    and a SANAD tenant, plus encrypted OAuth token material.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_store_connections (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   uuid NOT NULL REFERENCES tenants (id),
    zid_store_id                text NOT NULL,
    zid_store_name              text,
    status                      text NOT NULL DEFAULT 'CONNECTED'
                                CHECK (status IN ('CONNECTED', 'REAUTH_REQUIRED', 'PAUSED', 'FAILED')),
    authorization_token_enc     text,
    manager_token_enc           text,
    refresh_token_enc           text,
    token_key_version           text,
    authorization_expires_at    timestamptz,
    refresh_expires_at          timestamptz,
    last_refresh_at             timestamptz,
    last_sync_at                timestamptz,
    last_sync_error             text,
    authorized_by               uuid,
    created_at                  timestamptz NOT NULL DEFAULT NOW(),
    updated_at                  timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_zid_connections_store UNIQUE (zid_store_id),
    CONSTRAINT uk_zid_connections_tenant_store UNIQUE (tenant_id, zid_store_id)
);

CREATE INDEX IF NOT EXISTS idx_zid_connections_tenant
    ON zid_store_connections (tenant_id);

-- ------------------------------------------------------------
-- 2. zid_link_states — one-time OAuth state values (CSRF / tenant
--    binding). The raw state never lands in the database; only its
--    SHA-256 hash is stored. Expired/used states are rejected.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_link_states (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id),
    user_id         uuid NOT NULL,
    state_hash      text NOT NULL,
    status          text NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    redirect_target text,
    created_at      timestamptz NOT NULL DEFAULT NOW(),
    expires_at      timestamptz NOT NULL,
    CONSTRAINT uk_zid_link_states_state_hash UNIQUE (state_hash)
);

CREATE INDEX IF NOT EXISTS idx_zid_link_states_tenant
    ON zid_link_states (tenant_id, created_at);

-- ------------------------------------------------------------
-- 3. zid_products — mirrored product catalog (pull-only from Zid).
--    raw_payload keeps the last verified API document for audit and
--    reconciliation; sync writes are idempotent via ON CONFLICT.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_products (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id),
    connection_id   uuid NOT NULL REFERENCES zid_store_connections (id) ON DELETE CASCADE,
    zid_product_id  text NOT NULL,
    sku             text,
    name_ar         text,
    name_en         text,
    price           numeric(18, 4),
    sale_price      numeric(18, 4),
    currency        text,
    is_draft        boolean,
    is_published    boolean,
    is_infinite     boolean,
    quantity        integer,
    zid_updated_at  timestamptz,
    raw_payload     jsonb,
    created_at      timestamptz NOT NULL DEFAULT NOW(),
    updated_at      timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_zid_products UNIQUE (tenant_id, connection_id, zid_product_id)
);

CREATE INDEX IF NOT EXISTS idx_zid_products_connection
    ON zid_products (connection_id, updated_at);

-- ------------------------------------------------------------
-- 4. zid_product_stocks — stock mirrors derived from the verified
--    products payload (stocks[] array). PULL-ONLY: no write-back to
--    Zid is implemented in this integration.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_product_stocks (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id),
    connection_id       uuid NOT NULL REFERENCES zid_store_connections (id) ON DELETE CASCADE,
    zid_product_id      text NOT NULL,
    zid_stock_id        text NOT NULL,
    location_name_ar    text,
    location_name_en    text,
    available_quantity  integer,
    is_infinite         boolean,
    synced_at           timestamptz NOT NULL DEFAULT NOW(),
    created_at          timestamptz NOT NULL DEFAULT NOW(),
    updated_at          timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_zid_product_stocks UNIQUE (tenant_id, connection_id, zid_product_id, zid_stock_id)
);

-- ------------------------------------------------------------
-- 5. zid_orders — order mirror (pull + webhook re-pull). Status and
--    payment fields mirror the verified orders payload. No financial
--    operations (refund/capture/cancel) are ever triggered from here.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_orders (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id),
    connection_id       uuid NOT NULL REFERENCES zid_store_connections (id) ON DELETE CASCADE,
    zid_order_id        text NOT NULL,
    code                text,
    status_code         text,
    status_name         text,
    payment_status      text,
    order_total         numeric(18, 4),
    currency            text,
    customer_name       text,
    is_guest_customer   boolean,
    requires_shipping   boolean,
    zid_created_at      timestamptz,
    zid_updated_at      timestamptz,
    synced_at           timestamptz NOT NULL DEFAULT NOW(),
    raw_payload         jsonb,
    created_at          timestamptz NOT NULL DEFAULT NOW(),
    updated_at          timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_zid_orders UNIQUE (tenant_id, connection_id, zid_order_id)
);

CREATE INDEX IF NOT EXISTS idx_zid_orders_connection
    ON zid_orders (connection_id, zid_updated_at);

-- ------------------------------------------------------------
-- 6. zid_customers — MINIMAL customer data. PII (email/mobile) is
--    NOT stored in plaintext: only blind-index HMAC values are kept,
--    preserving cross-tenant non-disclosure and making deletion
--    trivial (row deletion removes all personal data).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_customers (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id),
    connection_id       uuid NOT NULL REFERENCES zid_store_connections (id) ON DELETE CASCADE,
    zid_customer_id     text NOT NULL,
    display_name        text,
    email_blind_index   text,
    mobile_blind_index  text,
    is_guest            boolean,
    zid_created_at      timestamptz,
    synced_at           timestamptz NOT NULL DEFAULT NOW(),
    created_at          timestamptz NOT NULL DEFAULT NOW(),
    updated_at          timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_zid_customers UNIQUE (tenant_id, connection_id, zid_customer_id)
);

-- ------------------------------------------------------------
-- 7. zid_webhook_events — idempotent webhook inbox. Rows are only
--    inserted AFTER signature verification and trusted-binding
--    resolution (fail-closed: unbound stores are rejected with 422,
--    invalid signatures with 401 — nothing is persisted for them).
--    UNIQUE (zid_store_id, payload_sha256) makes delivery retries
--    idempotent; a differing payload under the same event id raises
--    a replay mismatch (409).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_webhook_events (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id),
    connection_id       uuid NOT NULL REFERENCES zid_store_connections (id),
    zid_store_id        text NOT NULL,
    event_type          text,
    event_id            text,
    payload_sha256      text NOT NULL,
    raw_payload         jsonb NOT NULL,
    signature_verified  boolean NOT NULL DEFAULT TRUE,
    status              text NOT NULL DEFAULT 'RECEIVED'
                        CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED', 'FAILED_MANUAL')),
    attempts            integer NOT NULL DEFAULT 0,
    next_attempt_at     timestamptz,
    last_error          text,
    received_at         timestamptz NOT NULL DEFAULT NOW(),
    processed_at        timestamptz,
    CONSTRAINT uk_zid_webhook_events UNIQUE (zid_store_id, payload_sha256)
);

CREATE INDEX IF NOT EXISTS idx_zid_webhook_events_pending
    ON zid_webhook_events (status, next_attempt_at)
    WHERE status IN ('RECEIVED', 'FAILED');

-- ------------------------------------------------------------
-- 8. zid_sync_audit — operational audit trail (sync runs, webhook
--    outcomes, conflicts). NEVER contains token material or customer
--    PII; free-text fields are written by ZidSyncAuditService which
--    redacts token-shaped values.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_sync_audit (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id),
    connection_id   uuid,
    sync_type       text NOT NULL,
    entity          text,
    direction       text NOT NULL DEFAULT 'PULL'
                    CHECK (direction IN ('PULL', 'WEBHOOK_INGEST', 'RECONCILIATION', 'BINDING')),
    status          text NOT NULL
                    CHECK (status IN ('SUCCESS', 'FAILED', 'PARTIAL', 'CONFLICT')),
    items_processed integer NOT NULL DEFAULT 0,
    items_failed    integer NOT NULL DEFAULT 0,
    error_summary   text,
    correlation_id  text,
    started_at      timestamptz NOT NULL DEFAULT NOW(),
    finished_at     timestamptz
);

CREATE INDEX IF NOT EXISTS idx_zid_sync_audit_tenant
    ON zid_sync_audit (tenant_id, started_at DESC);

-- ------------------------------------------------------------
-- 9. zid_reconciliation_reports — output of the periodic drift
--    detection between Zid (source of truth for remote fields) and
--    the SANAD mirror.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zid_reconciliation_reports (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id),
    connection_id   uuid NOT NULL REFERENCES zid_store_connections (id) ON DELETE CASCADE,
    entity          text NOT NULL,
    drift_count     integer NOT NULL DEFAULT 0,
    checked_count   integer NOT NULL DEFAULT 0,
    summary         jsonb,
    status          text NOT NULL DEFAULT 'COMPLETED'
                    CHECK (status IN ('COMPLETED', 'FAILED')),
    generated_at    timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_zid_reconciliation_tenant
    ON zid_reconciliation_reports (tenant_id, generated_at DESC);

-- ------------------------------------------------------------
-- 10. Fail-closed RLS on every tenant-scoped table (V20261021_1
--     convention). Precondition checks abort the migration loudly
--     if a table or its tenant_id column is missing.
-- ------------------------------------------------------------
DO $snad$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'zid_store_connections',
        'zid_link_states',
        'zid_products',
        'zid_product_stocks',
        'zid_orders',
        'zid_customers',
        'zid_webhook_events',
        'zid_sync_audit',
        'zid_reconciliation_reports'
    ]
    LOOP
        IF to_regclass(format('public.%I', tbl)) IS NULL THEN
            RAISE EXCEPTION 'ZID_MIGRATION_PRECONDITION: required table % is missing', tbl;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = tbl
              AND column_name = 'tenant_id'
        ) THEN
            RAISE EXCEPTION 'ZID_MIGRATION_PRECONDITION: tenant_id column missing from %', tbl;
        END IF;

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON public.%I', tbl);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON public.%I FOR ALL
             USING (
                 NULLIF(current_setting(''app.tenant_id'', true), '''') IS NOT NULL
                 AND tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')
             )
             WITH CHECK (
                 NULLIF(current_setting(''app.tenant_id'', true), '''') IS NOT NULL
                 AND tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')
             )',
            tbl
        );
    END LOOP;
END
$snad$;

-- ------------------------------------------------------------
-- 11. Capability seeds — ACTIVE but NOT granted to any role.
--     Tenant administrators attach them via the access UI; the
--     backend aspect is deny-by-default until then.
-- ------------------------------------------------------------
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'ZID_INTEGRATION_VIEW', 'Zid Integration View',
       'View Zid connection status, sync summaries, and audit trail', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'ZID_INTEGRATION_VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'ZID_INTEGRATION_MANAGE', 'Zid Integration Manage',
       'Link/unlink Zid stores and trigger manual synchronizations', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'ZID_INTEGRATION_MANAGE');
