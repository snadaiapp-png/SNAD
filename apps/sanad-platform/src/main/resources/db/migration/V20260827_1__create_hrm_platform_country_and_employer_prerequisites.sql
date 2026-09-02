-- ============================================================
-- HRM-G0 WS1 — Platform Country and Employer Prerequisites
-- ============================================================
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE platform_countries (
    country_code     CHAR(2)         NOT NULL,
    name_en          VARCHAR(120)    NOT NULL,
    name_ar          VARCHAR(120)    NOT NULL,
    default_locale   VARCHAR(20),
    default_currency CHAR(3),
    status           VARCHAR(20)     NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_platform_countries PRIMARY KEY (country_code),
    CONSTRAINT ck_platform_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_platform_country_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

INSERT INTO platform_countries (country_code, name_en, name_ar, default_locale, default_currency, status) VALUES
    ('SA', 'Saudi Arabia', 'المملكة العربية السعودية', 'ar-SA', 'SAR', 'ACTIVE'),
    ('AE', 'United Arab Emirates', 'الإمارات العربية المتحدة', 'ar-AE', 'AED', 'ACTIVE'),
    ('QA', 'Qatar', 'دولة قطر', 'ar-QA', 'QAR', 'ACTIVE'),
    ('BH', 'Bahrain', 'مملكة البحرين', 'ar-BH', 'BHD', 'ACTIVE'),
    ('KW', 'Kuwait', 'دولة الكويت', 'ar-KW', 'KWD', 'ACTIVE'),
    ('OM', 'Oman', 'سلطنة عمان', 'ar-OM', 'OMR', 'ACTIVE');

CREATE TABLE legal_entities (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID           NOT NULL,
    code                     VARCHAR(50)    NOT NULL,
    name                     VARCHAR(200)   NOT NULL,
    registered_country_code  CHAR(2)        NOT NULL,
    statutory_country_code   CHAR(2)        NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_legal_entities PRIMARY KEY (id),
    CONSTRAINT fk_legal_entities_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_legal_entities_registered_country FOREIGN KEY (registered_country_code) REFERENCES platform_countries (country_code),
    CONSTRAINT fk_legal_entities_statutory_country FOREIGN KEY (statutory_country_code) REFERENCES platform_countries (country_code),
    CONSTRAINT uq_legal_entities_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_legal_entity_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);
CREATE INDEX idx_legal_entities_tenant ON legal_entities (tenant_id);

CREATE TABLE organization_legal_entities (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    organization_id UUID           NOT NULL,
    legal_entity_id UUID           NOT NULL,
    effective_from  DATE           NOT NULL,
    effective_to    DATE,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_organization_legal_entities PRIMARY KEY (id),
    CONSTRAINT fk_org_legal_entity_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_org_legal_entity_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_org_legal_entity_legal_entity FOREIGN KEY (legal_entity_id) REFERENCES legal_entities (id),
    CONSTRAINT ck_org_legal_entity_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_org_legal_entity_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_org_legal_entity_tenant_org ON organization_legal_entities (tenant_id, organization_id);
CREATE INDEX idx_org_legal_entity_tenant_le ON organization_legal_entities (tenant_id, legal_entity_id);

ALTER TABLE organization_legal_entities
ADD CONSTRAINT ex_org_legal_entity_no_overlap
EXCLUDE USING gist (
    tenant_id WITH =,
    organization_id WITH =,
    legal_entity_id WITH =,
    daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
) WHERE (status = 'ACTIVE');

CREATE TABLE work_locations (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID           NOT NULL,
    code          VARCHAR(50)    NOT NULL,
    name          VARCHAR(200)   NOT NULL,
    country_code  CHAR(2)        NOT NULL,
    city          VARCHAR(120),
    timezone      VARCHAR(80),
    status        VARCHAR(20)    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_work_locations PRIMARY KEY (id),
    CONSTRAINT fk_work_locations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_work_locations_country FOREIGN KEY (country_code) REFERENCES platform_countries (country_code),
    CONSTRAINT uq_work_locations_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_work_location_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);
CREATE INDEX idx_work_locations_tenant ON work_locations (tenant_id);

ALTER TABLE legal_entities ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_entities FORCE ROW LEVEL SECURITY;
CREATE POLICY legal_entities_tenant_isolation ON legal_entities
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE organization_legal_entities ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_legal_entities FORCE ROW LEVEL SECURITY;
CREATE POLICY org_legal_entities_tenant_isolation ON organization_legal_entities
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE work_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE work_locations FORCE ROW LEVEL SECURITY;
CREATE POLICY work_locations_tenant_isolation ON work_locations
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
