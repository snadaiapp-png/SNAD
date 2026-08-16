-- ============================================================
-- V20260816_5: E-Commerce capabilities + commerce tables
--
-- Adds ECOMMERCE.* capabilities and creates commerce tables:
--   commerce_stores, commerce_store_domains, commerce_products,
--   commerce_product_variants, commerce_collections,
--   commerce_collection_products, commerce_prices,
--   commerce_carts, commerce_cart_items, commerce_checkout_sessions,
--   commerce_orders, commerce_order_items, commerce_order_status_history
--
-- H2 compatibility: pure DDL, runs on PG and H2.
-- ============================================================

-- Seed ECOMMERCE.* capabilities (ECOMMERCE_CX module already registered in V20260814_1)
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('ECOMMERCE.VIEW',        'Ecommerce View',        'View stores, products, collections, orders, and commerce data'),
    ('ECOMMERCE.WRITE',       'Ecommerce Write',       'Create and update stores, products, collections, prices'),
    ('ECOMMERCE.PUBLISH',     'Ecommerce Publish',     'Publish and unpublish products and collections'),
    ('ECOMMERCE.ADMIN',       'Ecommerce Admin',       'Full administrative access including store lifecycle and domain management'),
    ('ECOMMERCE.ORDER_MANAGE','Ecommerce Order Manage','Manage orders, fulfillment, cancellations, and refunds')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants (idempotent)
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN ('ECOMMERCE.VIEW','ECOMMERCE.WRITE','ECOMMERCE.PUBLISH','ECOMMERCE.ADMIN','ECOMMERCE.ORDER_MANAGE')
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- commerce_stores
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_stores (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(200)   NOT NULL,
    code                VARCHAR(50)    NOT NULL,
    slug                VARCHAR(100)   NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    default_locale      VARCHAR(10)     NOT NULL DEFAULT 'ar',
    default_currency    VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    is_primary          BOOLEAN         NOT NULL DEFAULT FALSE,
    settings            JSONB,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_stores PRIMARY KEY (id),
    CONSTRAINT uk_commerce_stores_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT uk_commerce_stores_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_commerce_stores_status CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_commerce_stores_tenant_status ON commerce_stores(tenant_id, status);

-- ============================================================
-- commerce_store_domains
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_store_domains (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    store_id            UUID            NOT NULL,
    hostname            VARCHAR(253)    NOT NULL,
    domain_type         VARCHAR(20)     NOT NULL DEFAULT 'CUSTOM',
    verification_status VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    activation_status  VARCHAR(20)     NOT NULL DEFAULT 'INACTIVE',
    is_primary          BOOLEAN         NOT NULL DEFAULT FALSE,
    verification_token  VARCHAR(128),
    verification_method VARCHAR(20),
    verified_at         TIMESTAMP WITH TIME ZONE,
    verified_by         UUID,
    failure_reason      VARCHAR(500),
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_store_domains PRIMARY KEY (id),
    CONSTRAINT uk_commerce_store_domains_hostname UNIQUE (hostname),
    CONSTRAINT uk_commerce_store_domains_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_commerce_store_domains_tenant_store UNIQUE (tenant_id, store_id, hostname),
    CONSTRAINT fk_commerce_store_domains_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES commerce_stores(tenant_id, id),
    CONSTRAINT ck_commerce_sd_type CHECK (domain_type IN ('CUSTOM','DEFAULT_GENERATED')),
    CONSTRAINT ck_commerce_sd_vstatus CHECK (verification_status IN ('PENDING','VERIFYING','VERIFIED','FAILED')),
    CONSTRAINT ck_commerce_sd_astatus CHECK (activation_status IN ('INACTIVE','ACTIVE','DISABLED'))
);
CREATE INDEX IF NOT EXISTS idx_commerce_store_domains_hostname ON commerce_store_domains(hostname);

-- ============================================================
-- commerce_products
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_products (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    store_id            UUID            NOT NULL,
    name                VARCHAR(300)   NOT NULL,
    slug                VARCHAR(200)   NOT NULL,
    sku                 VARCHAR(100),
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    product_type        VARCHAR(30)     NOT NULL DEFAULT 'PHYSICAL',
    published_at        TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_products PRIMARY KEY (id),
    CONSTRAINT uk_commerce_products_tenant_store_slug UNIQUE (tenant_id, store_id, slug),
    CONSTRAINT uk_commerce_products_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_products_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES commerce_stores(tenant_id, id),
    CONSTRAINT ck_commerce_products_type CHECK (product_type IN ('PHYSICAL','DIGITAL','SERVICE','BUNDLE')),
    CONSTRAINT ck_commerce_products_status CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_commerce_products_tenant_store ON commerce_products(tenant_id, store_id);
CREATE INDEX IF NOT EXISTS idx_commerce_products_tenant_status ON commerce_products(tenant_id, status);

-- ============================================================
-- commerce_product_variants
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_product_variants (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    product_id      UUID            NOT NULL,
    sku             VARCHAR(100),
    name            VARCHAR(300)   NOT NULL,
    options         JSONB,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_product_variants PRIMARY KEY (id),
    CONSTRAINT uk_commerce_pv_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_pv_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES commerce_products(tenant_id, id),
    CONSTRAINT ck_commerce_pv_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_commerce_pv_product ON commerce_product_variants(product_id);

-- ============================================================
-- commerce_collections
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_collections (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    store_id        UUID            NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    slug            VARCHAR(200)   NOT NULL,
    description     TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_collections PRIMARY KEY (id),
    CONSTRAINT uk_commerce_coll_tenant_store_slug UNIQUE (tenant_id, store_id, slug),
    CONSTRAINT uk_commerce_coll_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_coll_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES commerce_stores(tenant_id, id),
    CONSTRAINT ck_commerce_coll_status CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS commerce_collection_products (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    collection_id   UUID            NOT NULL,
    product_id      UUID            NOT NULL,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_coll_products PRIMARY KEY (id),
    CONSTRAINT uk_commerce_coll_products UNIQUE (collection_id, product_id),
    CONSTRAINT uk_commerce_coll_products_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_commerce_coll_products_coll ON commerce_collection_products(collection_id);

-- ============================================================
-- commerce_prices
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_prices (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    store_id            UUID            NOT NULL,
    product_id          UUID            NOT NULL,
    variant_id          UUID,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    amount              NUMERIC(18,2)  NOT NULL,
    compare_at_amount   NUMERIC(18,2),
    valid_from          TIMESTAMP WITH TIME ZONE,
    valid_to            TIMESTAMP WITH TIME ZONE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_prices PRIMARY KEY (id),
    CONSTRAINT uk_commerce_prices_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_prices_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES commerce_products(tenant_id, id),
    CONSTRAINT ck_commerce_prices_amount CHECK (amount >= 0),
    CONSTRAINT ck_commerce_prices_status CHECK (status IN ('ACTIVE','INACTIVE','EXPIRED'))
);
CREATE INDEX IF NOT EXISTS idx_commerce_prices_product ON commerce_prices(product_id, status);

-- ============================================================
-- commerce_carts + commerce_cart_items
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_carts (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    store_id        UUID            NOT NULL,
    customer_ref    VARCHAR(500),
    currency        VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    subtotal        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    expires_at      TIMESTAMP WITH TIME ZONE,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_carts PRIMARY KEY (id),
    CONSTRAINT uk_commerce_carts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_carts_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES commerce_stores(tenant_id, id),
    CONSTRAINT ck_commerce_carts_status CHECK (status IN ('ACTIVE','CHECKED_OUT','EXPIRED','ABANDONED'))
);
CREATE INDEX IF NOT EXISTS idx_commerce_carts_tenant_store ON commerce_carts(tenant_id, store_id);

CREATE TABLE IF NOT EXISTS commerce_cart_items (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    cart_id         UUID            NOT NULL,
    product_id      UUID            NOT NULL,
    variant_id      UUID,
    quantity        INTEGER         NOT NULL DEFAULT 1,
    unit_price      NUMERIC(18,2)  NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    line_total       NUMERIC(18,2)  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_cart_items PRIMARY KEY (id),
    CONSTRAINT uk_commerce_cart_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_cart_items_cart FOREIGN KEY (tenant_id, cart_id)
        REFERENCES commerce_carts(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_commerce_cart_items_qty CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS idx_commerce_cart_items_cart ON commerce_cart_items(cart_id);

-- ============================================================
-- commerce_orders + commerce_order_items + commerce_order_status_history
-- ============================================================
CREATE TABLE IF NOT EXISTS commerce_orders (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    store_id            UUID            NOT NULL,
    order_number        VARCHAR(60)     NOT NULL,
    cart_id             UUID,
    customer_reference  VARCHAR(500),
    customer_snapshot   JSONB,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    subtotal            NUMERIC(18,2)  NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18,2)  NOT NULL DEFAULT 0,
    shipping_total      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18,2)  NOT NULL DEFAULT 0,
    payment_status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    fulfillment_status  VARCHAR(20)     NOT NULL DEFAULT 'UNFULFILLED',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    idempotency_key     VARCHAR(200),
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_orders PRIMARY KEY (id),
    CONSTRAINT uk_commerce_orders_tenant_number UNIQUE (tenant_id, order_number),
    CONSTRAINT uk_commerce_orders_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_commerce_orders_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_commerce_orders_store FOREIGN KEY (tenant_id, store_id)
        REFERENCES commerce_stores(tenant_id, id),
    CONSTRAINT ck_commerce_orders_pstatus CHECK (payment_status IN ('PENDING','AUTHORIZED','PAID','PARTIALLY_REFUNDED','REFUNDED','FAILED')),
    CONSTRAINT ck_commerce_orders_fstatus CHECK (fulfillment_status IN ('UNFULFILLED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','RETURNED')),
    CONSTRAINT ck_commerce_orders_status CHECK (status IN ('PENDING','CONFIRMED','PAID','PROCESSING','COMPLETED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_commerce_orders_tenant_store ON commerce_orders(tenant_id, store_id);
CREATE INDEX IF NOT EXISTS idx_commerce_orders_tenant_status ON commerce_orders(tenant_id, status);

CREATE TABLE IF NOT EXISTS commerce_order_items (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    order_id        UUID            NOT NULL,
    product_id      UUID,
    variant_id      UUID,
    product_name    VARCHAR(300)   NOT NULL,
    product_sku     VARCHAR(100),
    variant_options JSONB,
    quantity        INTEGER         NOT NULL DEFAULT 1,
    unit_price      NUMERIC(18,2)  NOT NULL,
    discount        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    tax             NUMERIC(18,2)  NOT NULL DEFAULT 0,
    line_total      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_order_items PRIMARY KEY (id),
    CONSTRAINT uk_commerce_oi_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_oi_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES commerce_orders(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_commerce_oi_qty CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS idx_commerce_oi_order ON commerce_order_items(order_id);

CREATE TABLE IF NOT EXISTS commerce_order_status_history (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    order_id        UUID            NOT NULL,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20)     NOT NULL,
    from_payment    VARCHAR(20),
    to_payment      VARCHAR(20),
    reason          VARCHAR(500),
    actor           UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_commerce_osh PRIMARY KEY (id),
    CONSTRAINT uk_commerce_osh_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_commerce_osh_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES commerce_orders(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_commerce_osh_order ON commerce_order_status_history(order_id);
