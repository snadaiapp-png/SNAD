-- ============================================================================
-- V20260912_1__r0c13_subscription_billing_foundation.sql
-- R0C13 / R13-G02 — additive persistence foundation.
-- Forward-only; no destructive DDL, data rewrite, provider activation,
-- Finance writer replacement, or subscription lifecycle writer replacement.
-- ============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_subscriptions_tenant_id
    ON tenant_subscriptions (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_invoices_tenant_id
    ON billing_invoices (tenant_id, id);

CREATE TABLE IF NOT EXISTS subscription_billing_provider_customers (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_customer_ref VARCHAR(200) NOT NULL,
    sanitized_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_subscription_billing_provider_customers PRIMARY KEY (id),
    CONSTRAINT uq_subscription_billing_provider_customers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_subscription_billing_provider_customers_tenant_provider UNIQUE (tenant_id, provider),
    CONSTRAINT fk_subscription_billing_provider_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_subscription_billing_provider_customers_provider CHECK (provider ~ '^[A-Z0-9_]{2,40}$'),
    CONSTRAINT ck_subscription_billing_provider_customers_metadata CHECK (
        NOT (sanitized_metadata ?| ARRAY['card_number','pan','cvc','cvv','track_data','pin',
            'password','secret','api_key','apikey','token','authorization'])
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_billing_provider_customer_ref
    ON subscription_billing_provider_customers (provider, provider_customer_ref);

CREATE TABLE IF NOT EXISTS subscription_billing_payment_attempts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    billing_invoice_id UUID NOT NULL,
    provider_customer_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_payment_ref VARCHAR(200),
    idempotency_key VARCHAR(200) NOT NULL,
    state VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    amount_minor BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    failure_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_subscription_billing_payment_attempts PRIMARY KEY (id),
    CONSTRAINT uq_subscription_billing_payment_attempts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_subscription_billing_payment_attempts_idempotency UNIQUE (tenant_id, provider, idempotency_key),
    CONSTRAINT fk_subscription_billing_payment_attempts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_subscription_billing_payment_attempts_subscription
        FOREIGN KEY (tenant_id, subscription_id) REFERENCES tenant_subscriptions(tenant_id, id),
    CONSTRAINT fk_subscription_billing_payment_attempts_invoice
        FOREIGN KEY (tenant_id, billing_invoice_id) REFERENCES billing_invoices(tenant_id, id),
    CONSTRAINT fk_subscription_billing_payment_attempts_customer
        FOREIGN KEY (tenant_id, provider_customer_id) REFERENCES subscription_billing_provider_customers(tenant_id, id),
    CONSTRAINT ck_subscription_billing_payment_attempts_state
        CHECK (state IN ('CREATED','PENDING','SUCCEEDED','FAILED','CANCELLED','REFUNDED')),
    CONSTRAINT ck_subscription_billing_payment_attempts_amount CHECK (amount_minor >= 0),
    CONSTRAINT ck_subscription_billing_payment_attempts_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_billing_provider_payment_ref
    ON subscription_billing_payment_attempts (provider, provider_payment_ref)
    WHERE provider_payment_ref IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_subscription_billing_payment_attempts_invoice
    ON subscription_billing_payment_attempts (tenant_id, billing_invoice_id, created_at DESC);

CREATE TABLE IF NOT EXISTS subscription_billing_provider_events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subscription_id UUID,
    billing_invoice_id UUID,
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(240) NOT NULL,
    provider_payment_ref VARCHAR(200),
    event_type VARCHAR(160) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    signature_verified_at TIMESTAMPTZ,
    processing_state VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_subscription_billing_provider_events PRIMARY KEY (id),
    CONSTRAINT uq_subscription_billing_provider_events_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_subscription_billing_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT fk_subscription_billing_provider_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_subscription_billing_provider_events_subscription
        FOREIGN KEY (tenant_id, subscription_id) REFERENCES tenant_subscriptions(tenant_id, id),
    CONSTRAINT fk_subscription_billing_provider_events_invoice
        FOREIGN KEY (tenant_id, billing_invoice_id) REFERENCES billing_invoices(tenant_id, id),
    CONSTRAINT ck_subscription_billing_provider_events_hash CHECK (payload_sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_subscription_billing_provider_events_state
        CHECK (processing_state IN ('RECEIVED','PROCESSING','PROCESSED','FAILED')),
    CONSTRAINT ck_subscription_billing_provider_events_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_subscription_billing_provider_events_signature_time
        CHECK (signature_verified = FALSE OR signature_verified_at IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_subscription_billing_provider_events_retry
    ON subscription_billing_provider_events (processing_state, next_attempt_at)
    WHERE processing_state IN ('RECEIVED','FAILED');

CREATE TABLE IF NOT EXISTS subscription_billing_finance_links (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    billing_invoice_id UUID NOT NULL,
    finance_invoice_id UUID NOT NULL,
    external_reference VARCHAR(240) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_subscription_billing_finance_links PRIMARY KEY (id),
    CONSTRAINT uq_subscription_billing_finance_links_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_subscription_billing_finance_links_billing_invoice UNIQUE (tenant_id, billing_invoice_id),
    CONSTRAINT uq_subscription_billing_finance_links_finance_invoice UNIQUE (tenant_id, finance_invoice_id),
    CONSTRAINT uq_subscription_billing_finance_links_external_reference UNIQUE (tenant_id, external_reference),
    CONSTRAINT fk_subscription_billing_finance_links_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_subscription_billing_finance_links_subscription
        FOREIGN KEY (tenant_id, subscription_id) REFERENCES tenant_subscriptions(tenant_id, id),
    CONSTRAINT fk_subscription_billing_finance_links_billing_invoice
        FOREIGN KEY (tenant_id, billing_invoice_id) REFERENCES billing_invoices(tenant_id, id),
    CONSTRAINT fk_subscription_billing_finance_links_finance_invoice
        FOREIGN KEY (tenant_id, finance_invoice_id) REFERENCES finance_invoices(tenant_id, id),
    CONSTRAINT ck_subscription_billing_finance_links_external_reference
        CHECK (external_reference LIKE 'SCP_INVOICE:%')
);

CREATE TABLE IF NOT EXISTS subscription_billing_outbox (
    event_id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID,
    idempotency_key VARCHAR(200) NOT NULL,
    payload_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'READY',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 8,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claim_token UUID,
    claimed_by VARCHAR(200),
    claim_expires_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_subscription_billing_outbox PRIMARY KEY (event_id),
    CONSTRAINT uq_subscription_billing_outbox_tenant_id UNIQUE (tenant_id, event_id),
    CONSTRAINT uq_subscription_billing_outbox_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_subscription_billing_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_subscription_billing_outbox_event_type CHECK (event_type LIKE 'BILLING.%'),
    CONSTRAINT ck_subscription_billing_outbox_status CHECK (status IN ('READY','CLAIMED','DELIVERED','DEAD_LETTER')),
    CONSTRAINT ck_subscription_billing_outbox_attempts CHECK (attempt_count >= 0 AND max_attempts > 0),
    CONSTRAINT ck_subscription_billing_outbox_payload CHECK (
        NOT (payload_metadata ?| ARRAY['card_number','pan','cvc','cvv','track_data','pin',
            'password','secret','api_key','apikey','token','authorization'])
    )
);
CREATE INDEX IF NOT EXISTS idx_subscription_billing_outbox_claim
    ON subscription_billing_outbox (status, available_at) WHERE status = 'READY';

CREATE TABLE IF NOT EXISTS subscription_billing_reconciliation_runs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    mode VARCHAR(20) NOT NULL DEFAULT 'READ_ONLY',
    state VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    requested_by UUID,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    summary_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_subscription_billing_reconciliation_runs PRIMARY KEY (id),
    CONSTRAINT uq_subscription_billing_reconciliation_runs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_subscription_billing_reconciliation_runs_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_subscription_billing_reconciliation_runs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_subscription_billing_reconciliation_runs_mode CHECK (mode IN ('READ_ONLY','REPAIR')),
    CONSTRAINT ck_subscription_billing_reconciliation_runs_state CHECK (state IN ('RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_subscription_billing_reconciliation_runs_completion CHECK (
        (state = 'RUNNING' AND completed_at IS NULL)
        OR (state IN ('COMPLETED','FAILED') AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_subscription_billing_reconciliation_runs_summary CHECK (
        NOT (summary_metadata ?| ARRAY['card_number','pan','cvc','cvv','track_data','pin',
            'password','secret','api_key','apikey','token','authorization'])
    )
);

CREATE TABLE IF NOT EXISTS subscription_billing_reconciliation_items (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    reconciliation_run_id UUID NOT NULL,
    billing_invoice_id UUID,
    finance_link_id UUID,
    payment_attempt_id UUID,
    provider_event_id UUID,
    classification VARCHAR(60) NOT NULL,
    expected_amount_minor BIGINT,
    observed_amount_minor BIGINT,
    expected_currency VARCHAR(3),
    observed_currency VARCHAR(3),
    details_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    state VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT pk_subscription_billing_reconciliation_items PRIMARY KEY (id),
    CONSTRAINT uq_subscription_billing_reconciliation_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_subscription_billing_reconciliation_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_subscription_billing_reconciliation_items_run
        FOREIGN KEY (tenant_id, reconciliation_run_id)
        REFERENCES subscription_billing_reconciliation_runs(tenant_id, id),
    CONSTRAINT fk_subscription_billing_reconciliation_items_billing_invoice
        FOREIGN KEY (tenant_id, billing_invoice_id) REFERENCES billing_invoices(tenant_id, id),
    CONSTRAINT fk_subscription_billing_reconciliation_items_finance_link
        FOREIGN KEY (tenant_id, finance_link_id) REFERENCES subscription_billing_finance_links(tenant_id, id),
    CONSTRAINT fk_subscription_billing_reconciliation_items_payment_attempt
        FOREIGN KEY (tenant_id, payment_attempt_id) REFERENCES subscription_billing_payment_attempts(tenant_id, id),
    CONSTRAINT fk_subscription_billing_reconciliation_items_provider_event
        FOREIGN KEY (tenant_id, provider_event_id) REFERENCES subscription_billing_provider_events(tenant_id, id),
    CONSTRAINT ck_subscription_billing_reconciliation_items_classification CHECK (classification IN (
        'MATCHED','MISSING_FINANCE_INVOICE','MISSING_PROVIDER_REFERENCE','AMOUNT_MISMATCH',
        'CURRENCY_MISMATCH','PROVIDER_PAID_FINANCE_PENDING','FINANCE_PAID_PROVIDER_UNCONFIRMED',
        'DUPLICATE_PROVIDER_EVENT','TENANT_BINDING_MISMATCH','SIGNATURE_REJECTED'
    )),
    CONSTRAINT ck_subscription_billing_reconciliation_items_amounts CHECK (
        (expected_amount_minor IS NULL OR expected_amount_minor >= 0)
        AND (observed_amount_minor IS NULL OR observed_amount_minor >= 0)
    ),
    CONSTRAINT ck_subscription_billing_reconciliation_items_currency CHECK (
        (expected_currency IS NULL OR expected_currency ~ '^[A-Z]{3}$')
        AND (observed_currency IS NULL OR observed_currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_subscription_billing_reconciliation_items_state CHECK (state IN ('OPEN','RESOLVED','IGNORED')),
    CONSTRAINT ck_subscription_billing_reconciliation_items_details CHECK (
        NOT (details_metadata ?| ARRAY['card_number','pan','cvc','cvv','track_data','pin',
            'password','secret','api_key','apikey','token','authorization'])
    )
);
CREATE INDEX IF NOT EXISTS idx_subscription_billing_reconciliation_items_run
    ON subscription_billing_reconciliation_items (tenant_id, reconciliation_run_id, classification);

-- Fail-closed FORCE RLS on every R0C13 tenant-owned table.
ALTER TABLE subscription_billing_provider_customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_billing_provider_customers FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription_billing_provider_customers;
CREATE POLICY tenant_isolation ON subscription_billing_provider_customers FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE subscription_billing_payment_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_billing_payment_attempts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription_billing_payment_attempts;
CREATE POLICY tenant_isolation ON subscription_billing_payment_attempts FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE subscription_billing_provider_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_billing_provider_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription_billing_provider_events;
CREATE POLICY tenant_isolation ON subscription_billing_provider_events FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE subscription_billing_finance_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_billing_finance_links FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription_billing_finance_links;
CREATE POLICY tenant_isolation ON subscription_billing_finance_links FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE subscription_billing_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_billing_outbox FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription_billing_outbox;
CREATE POLICY tenant_isolation ON subscription_billing_outbox FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE subscription_billing_reconciliation_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_billing_reconciliation_runs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription_billing_reconciliation_runs;
CREATE POLICY tenant_isolation ON subscription_billing_reconciliation_runs FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE subscription_billing_reconciliation_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_billing_reconciliation_items FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription_billing_reconciliation_items;
CREATE POLICY tenant_isolation ON subscription_billing_reconciliation_items FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
