-- ============================================================
-- V20260816_3: Websites Platform — Core Tables
--
-- Creates: websites, website_pages, website_navigation,
-- website_navigation_items, website_theme_settings,
-- website_domains, website_publications
--
-- Design:
--   * Tenant-scoped (tenant_id NOT NULL)
--   * RLS enabled (PG-only, separate migration V20260816_5)
--   * UUID PKs
--   * TIMESTAMPTZ
--   * Optimistic concurrency (version column)
--   * Unique constraints on (tenant_id, slug)
--   * Global hostname uniqueness on website_domains
-- ============================================================

CREATE TABLE IF NOT EXISTS websites (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    slug            VARCHAR(100)   NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    default_locale  VARCHAR(10)     NOT NULL DEFAULT 'ar',
    is_primary      BOOLEAN         NOT NULL DEFAULT FALSE,
    theme_config    JSONB,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_websites PRIMARY KEY (id),
    CONSTRAINT uk_websites_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT uk_websites_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_websites_status CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_websites_tenant_status ON websites(tenant_id, status);

CREATE TABLE IF NOT EXISTS website_pages (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    website_id          UUID            NOT NULL,
    title               VARCHAR(300)   NOT NULL,
    slug                VARCHAR(200)   NOT NULL,
    page_type           VARCHAR(30)     NOT NULL DEFAULT 'STANDARD',
    content_layout      JSONB,
    seo_title           VARCHAR(300),
    seo_description     VARCHAR(500),
    canonical_url       VARCHAR(500),
    og_title            VARCHAR(300),
    og_description      VARCHAR(500),
    robots_index        BOOLEAN         NOT NULL DEFAULT TRUE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    published_at        TIMESTAMP WITH TIME ZONE,
    published_by        UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_website_pages PRIMARY KEY (id),
    CONSTRAINT uk_website_pages_tenant_website_slug UNIQUE (tenant_id, website_id, slug),
    CONSTRAINT uk_website_pages_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_website_pages_website FOREIGN KEY (tenant_id, website_id)
        REFERENCES websites(tenant_id, id),
    CONSTRAINT ck_website_pages_type CHECK (page_type IN ('STANDARD','HOME','ABOUT','CONTACT','BLOG','LANDING','CUSTOM')),
    CONSTRAINT ck_website_pages_status CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_website_pages_tenant_website ON website_pages(tenant_id, website_id);
CREATE INDEX IF NOT EXISTS idx_website_pages_tenant_status ON website_pages(tenant_id, status);

CREATE TABLE IF NOT EXISTS website_navigation (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    website_id      UUID            NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    nav_type        VARCHAR(20)     NOT NULL DEFAULT 'MAIN',
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_website_navigation PRIMARY KEY (id),
    CONSTRAINT uk_website_nav_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_website_nav_tenant_website_name UNIQUE (tenant_id, website_id, name),
    CONSTRAINT fk_website_nav_website FOREIGN KEY (tenant_id, website_id)
        REFERENCES websites(tenant_id, id),
    CONSTRAINT ck_website_nav_type CHECK (nav_type IN ('MAIN','FOOTER','MOBILE','CUSTOM'))
);

CREATE TABLE IF NOT EXISTS website_navigation_items (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    navigation_id  UUID            NOT NULL,
    parent_id       UUID,
    label           VARCHAR(200)   NOT NULL,
    target_type     VARCHAR(20)     NOT NULL DEFAULT 'PAGE',
    target_page_id  UUID,
    target_url      VARCHAR(500),
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_website_nav_items PRIMARY KEY (id),
    CONSTRAINT uk_website_nav_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_website_nav_items_nav FOREIGN KEY (tenant_id, navigation_id)
        REFERENCES website_navigation(tenant_id, id),
    CONSTRAINT ck_website_nav_item_target CHECK (target_type IN ('PAGE','URL','EXTERNAL'))
);

CREATE INDEX IF NOT EXISTS idx_website_nav_items_nav ON website_navigation_items(navigation_id, sort_order);

CREATE TABLE IF NOT EXISTS website_theme_settings (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    website_id      UUID            NOT NULL,
    primary_color   VARCHAR(20),
    secondary_color VARCHAR(20),
    font_family     VARCHAR(100),
    layout          VARCHAR(30)     NOT NULL DEFAULT 'STANDARD',
    custom_css      TEXT,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_website_theme PRIMARY KEY (id),
    CONSTRAINT uk_website_theme_tenant_website UNIQUE (tenant_id, website_id),
    CONSTRAINT uk_website_theme_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_website_theme_website FOREIGN KEY (tenant_id, website_id)
        REFERENCES websites(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS website_domains (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    website_id          UUID            NOT NULL,
    hostname            VARCHAR(253)    NOT NULL,
    domain_type         VARCHAR(20)     NOT NULL DEFAULT 'CUSTOM',
    verification_status VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    activation_status  VARCHAR(20)     NOT NULL DEFAULT 'INACTIVE',
    is_primary          BOOLEAN         NOT NULL DEFAULT FALSE,
    verification_token  VARCHAR(128),
    verification_method VARCHAR(20),
    verified_at         TIMESTAMP WITH TIME ZONE,
    verified_by         UUID,
    ssl_cert_arn        VARCHAR(255),
    failure_reason      VARCHAR(500),
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_website_domains PRIMARY KEY (id),
    CONSTRAINT uk_website_domains_hostname UNIQUE (hostname),
    CONSTRAINT uk_website_domains_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_website_domains_tenant_website UNIQUE (tenant_id, website_id, hostname),
    CONSTRAINT fk_website_domains_website FOREIGN KEY (tenant_id, website_id)
        REFERENCES websites(tenant_id, id),
    CONSTRAINT ck_website_domains_type CHECK (domain_type IN ('CUSTOM','DEFAULT_GENERATED')),
    CONSTRAINT ck_website_domains_vstatus CHECK (verification_status IN ('PENDING','VERIFYING','VERIFIED','FAILED')),
    CONSTRAINT ck_website_domains_astatus CHECK (activation_status IN ('INACTIVE','ACTIVE','DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_website_domains_tenant ON website_domains(tenant_id);
CREATE INDEX IF NOT EXISTS idx_website_domains_hostname ON website_domains(hostname);
CREATE INDEX IF NOT EXISTS idx_website_domains_tenant_website ON website_domains(tenant_id, website_id);

CREATE TABLE IF NOT EXISTS website_publications (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    website_id      UUID            NOT NULL,
    page_id         UUID,
    publication_type VARCHAR(20)    NOT NULL DEFAULT 'PAGE',
    published_version BIGINT        NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PUBLISHED',
    published_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_by    UUID,
    unpublished_at  TIMESTAMP WITH TIME ZONE,
    unpublished_by  UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_website_publications PRIMARY KEY (id),
    CONSTRAINT uk_website_publications_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_website_pub_website FOREIGN KEY (tenant_id, website_id)
        REFERENCES websites(tenant_id, id),
    CONSTRAINT ck_website_pub_type CHECK (publication_type IN ('PAGE','SITE','PARTIAL')),
    CONSTRAINT ck_website_pub_status CHECK (status IN ('PUBLISHED','UNPUBLISHED'))
);

CREATE INDEX IF NOT EXISTS idx_website_publications_tenant_website ON website_publications(tenant_id, website_id);
CREATE INDEX IF NOT EXISTS idx_website_publications_page ON website_publications(page_id, status);
