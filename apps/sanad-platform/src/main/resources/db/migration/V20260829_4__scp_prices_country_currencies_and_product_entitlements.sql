-- ============================================================
-- V20260829_4: SCP-G2 — Pricing engine foundation, country/currency model,
--               product-scoped entitlements
--
-- 1. `prices` — version-aware prices for plan versions and products.
--    Supports (architecturally, via price_model + tiers JSONB):
--    FLAT, PER_USER, PER_EMPLOYEE, PER_BRANCH, PER_TRANSACTION,
--    PER_API_REQUEST, PER_AI_TOKEN, TIERED, VOLUME, USAGE_BASED,
--    HYBRID, CUSTOM_CONTRACT.
--    Prices are country- and currency-customizable (e.g. SA->SAR, GLOBAL->USD).
--    Money is BIGINT minor units (repo standard; no floating point).
-- 2. `country_currencies` — country -> default currency mapping (catalog data).
-- 3. `product_entitlements` — module capability overrides carried by ADD_ON /
--    METERED products; merged additively into effective entitlements by the
--    item-aware resolver (the plan-derived engine stays untouched).
--
-- Forward-only, additive, idempotent.
-- ============================================================

-- ============================================================
-- STEP 1: prices
-- ============================================================
CREATE TABLE IF NOT EXISTS prices (
    id                UUID            NOT NULL,
    plan_version_id   UUID,
    product_id        UUID,
    price_model       VARCHAR(30)     NOT NULL,
    country_code      VARCHAR(10)     NOT NULL DEFAULT 'GLOBAL',
    currency_code     VARCHAR(3)      NOT NULL,
    billing_interval  VARCHAR(16)     NOT NULL DEFAULT 'MONTHLY',
    base_amount_minor BIGINT          NOT NULL DEFAULT 0,
    unit_amount_minor BIGINT,
    tiers             JSONB,
    min_amount_minor  BIGINT,
    max_amount_minor  BIGINT,
    effective_from    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    effective_to      TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_prices PRIMARY KEY (id),
    CONSTRAINT fk_prices_plan_version FOREIGN KEY (plan_version_id) REFERENCES plan_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_prices_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_prices_owner CHECK (plan_version_id IS NOT NULL OR product_id IS NOT NULL),
    CONSTRAINT ck_prices_model CHECK (price_model IN (
        'FLAT', 'PER_USER', 'PER_EMPLOYEE', 'PER_BRANCH', 'PER_TRANSACTION',
        'PER_API_REQUEST', 'PER_AI_TOKEN', 'TIERED', 'VOLUME',
        'USAGE_BASED', 'HYBRID', 'CUSTOM_CONTRACT'
    )),
    CONSTRAINT ck_prices_interval CHECK (billing_interval IN ('MONTHLY', 'ANNUAL', 'ONE_TIME')),
    CONSTRAINT ck_prices_country CHECK (country_code ~ '^[A-Z]{2}$' OR country_code = 'GLOBAL'),
    CONSTRAINT ck_prices_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_prices_amounts CHECK (
        base_amount_minor >= 0
        AND (unit_amount_minor IS NULL OR unit_amount_minor >= 0)
        AND (min_amount_minor IS NULL OR min_amount_minor >= 0)
        AND (max_amount_minor IS NULL OR max_amount_minor >= 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_prices_lookup
    ON prices (plan_version_id, product_id, country_code, billing_interval, effective_from DESC);
CREATE INDEX IF NOT EXISTS idx_prices_product ON prices (product_id);

-- ============================================================
-- STEP 2: country_currencies (default currency per country — catalog data)
-- ============================================================
CREATE TABLE IF NOT EXISTS country_currencies (
    country_code   VARCHAR(10) NOT NULL,
    currency_code  VARCHAR(3)  NOT NULL,
    is_default     BOOLEAN     NOT NULL DEFAULT false,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_country_currencies PRIMARY KEY (country_code),
    CONSTRAINT ck_cc_country CHECK (country_code ~ '^[A-Z]{2}$' OR country_code = 'GLOBAL'),
    CONSTRAINT ck_cc_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);

INSERT INTO country_currencies (country_code, currency_code, is_default, created_at, updated_at)
SELECT v.country_code, v.currency_code, v.is_default, NOW(), NOW()
FROM (VALUES
    ('SA', 'SAR', false),
    ('AE', 'AED', false),
    ('KW', 'KWD', false),
    ('GLOBAL', 'USD', true)
) AS v(country_code, currency_code, is_default)
WHERE NOT EXISTS (
    SELECT 1 FROM country_currencies c WHERE c.country_code = v.country_code
);

-- ============================================================
-- STEP 3: product_entitlements (item-derived entitlements)
-- ============================================================
CREATE TABLE IF NOT EXISTS product_entitlements (
    id              UUID            NOT NULL,
    product_id      UUID            NOT NULL,
    module_id       UUID            NOT NULL,
    module_enabled  BOOLEAN         NOT NULL DEFAULT false,
    capability_code VARCHAR(150),
    boolean_value   BOOLEAN,
    limit_value     BIGINT,
    quota_value     BIGINT,
    quota_period    VARCHAR(20),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_product_entitlements PRIMARY KEY (id),
    CONSTRAINT fk_pe_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_pe_module FOREIGN KEY (module_id) REFERENCES modules (id),
    CONSTRAINT uk_pe_product_module_cap UNIQUE (product_id, module_id, capability_code),
    CONSTRAINT ck_pe_quota_period CHECK (quota_period IS NULL OR quota_period IN ('DAILY', 'MONTHLY', 'YEARLY', 'TOTAL'))
);

CREATE INDEX IF NOT EXISTS idx_pe_product ON product_entitlements (product_id);
CREATE INDEX IF NOT EXISTS idx_pe_module ON product_entitlements (module_id);
