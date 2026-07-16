-- EXEC-PROMPT-CRM-005: Enterprise Account and Customer Master
-- Extends the existing CRM Account aggregate; no parallel customer table is introduced.

ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS legal_name VARCHAR(240);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS trading_name VARCHAR(240);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS registration_number VARCHAR(120);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS tax_number VARCHAR(120);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS industry_code VARCHAR(80);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS customer_segment VARCHAR(80);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS customer_tier VARCHAR(40);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS website VARCHAR(500);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS primary_email VARCHAR(255);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS primary_phone VARCHAR(64);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS country_code VARCHAR(2);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS risk_rating VARCHAR(24);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(18,2);
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS payment_terms_days INTEGER;
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS data_quality_score INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS merged_into_account_id UUID;

UPDATE crm_accounts
SET legal_name = COALESCE(legal_name, display_name),
    data_quality_score = CASE
        WHEN display_name IS NOT NULL AND primary_currency_code IS NOT NULL THEN 25
        ELSE 0
    END
WHERE legal_name IS NULL OR data_quality_score = 0;

CREATE TABLE IF NOT EXISTS crm_account_addresses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    address_type VARCHAR(24) NOT NULL,
    label VARCHAR(120),
    line1 VARCHAR(240) NOT NULL,
    line2 VARCHAR(240),
    city VARCHAR(120) NOT NULL,
    state_region VARCHAR(120),
    postal_code VARCHAR(32),
    country_code VARCHAR(2) NOT NULL,
    primary_address BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_crm_account_address_account FOREIGN KEY (account_id) REFERENCES crm_accounts(id) ON DELETE CASCADE,
    CONSTRAINT chk_crm_account_address_type CHECK (address_type IN ('REGISTERED','BILLING','SHIPPING','OFFICE','OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_crm_account_addresses_tenant_account
    ON crm_account_addresses (tenant_id, account_id, active, updated_at);

CREATE TABLE IF NOT EXISTS crm_account_identifiers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    identifier_type VARCHAR(40) NOT NULL,
    identifier_value VARCHAR(180) NOT NULL,
    normalized_value VARCHAR(180) NOT NULL,
    issuer_country_code VARCHAR(2),
    primary_identifier BOOLEAN NOT NULL DEFAULT FALSE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_crm_account_identifier_account FOREIGN KEY (account_id) REFERENCES crm_accounts(id) ON DELETE CASCADE,
    CONSTRAINT chk_crm_account_identifier_type CHECK (identifier_type IN ('COMMERCIAL_REGISTRATION','TAX','VAT','NATIONAL_ID','DUNS','EXTERNAL','OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_crm_account_identifiers_lookup
    ON crm_account_identifiers (tenant_id, identifier_type, normalized_value, active);
CREATE INDEX IF NOT EXISTS idx_crm_account_identifiers_account
    ON crm_account_identifiers (tenant_id, account_id, active);

CREATE TABLE IF NOT EXISTS crm_account_relationships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_account_id UUID NOT NULL,
    target_account_id UUID NOT NULL,
    relationship_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE,
    effective_to DATE,
    notes VARCHAR(1000),
    created_by UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_crm_account_relationship_source FOREIGN KEY (source_account_id) REFERENCES crm_accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_crm_account_relationship_target FOREIGN KEY (target_account_id) REFERENCES crm_accounts(id) ON DELETE CASCADE,
    CONSTRAINT chk_crm_account_relationship_self CHECK (source_account_id <> target_account_id),
    CONSTRAINT chk_crm_account_relationship_status CHECK (status IN ('ACTIVE','INACTIVE','EXPIRED')),
    CONSTRAINT chk_crm_account_relationship_type CHECK (relationship_type IN ('PARENT','SUBSIDIARY','PARTNER','SUPPLIER','CUSTOMER','AFFILIATE','OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_crm_account_relationships_source
    ON crm_account_relationships (tenant_id, source_account_id, status);
CREATE INDEX IF NOT EXISTS idx_crm_account_relationships_target
    ON crm_account_relationships (tenant_id, target_account_id, status);

CREATE TABLE IF NOT EXISTS crm_account_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    account_id UUID NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    changed_by UUID,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_crm_account_status_history_account FOREIGN KEY (account_id) REFERENCES crm_accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_crm_account_status_history_account
    ON crm_account_status_history (tenant_id, account_id, changed_at);

CREATE TABLE IF NOT EXISTS crm_account_merge_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_account_id UUID NOT NULL,
    target_account_id UUID NOT NULL,
    source_version BIGINT NOT NULL,
    target_version BIGINT NOT NULL,
    contacts_moved INTEGER NOT NULL DEFAULT 0,
    opportunities_moved INTEGER NOT NULL DEFAULT 0,
    activities_moved INTEGER NOT NULL DEFAULT 0,
    addresses_moved INTEGER NOT NULL DEFAULT 0,
    identifiers_moved INTEGER NOT NULL DEFAULT 0,
    relationships_moved INTEGER NOT NULL DEFAULT 0,
    reason VARCHAR(500),
    merged_by UUID,
    merged_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_crm_account_merge_source FOREIGN KEY (source_account_id) REFERENCES crm_accounts(id),
    CONSTRAINT fk_crm_account_merge_target FOREIGN KEY (target_account_id) REFERENCES crm_accounts(id),
    CONSTRAINT chk_crm_account_merge_distinct CHECK (source_account_id <> target_account_id)
);

CREATE INDEX IF NOT EXISTS idx_crm_account_merge_history_tenant
    ON crm_account_merge_history (tenant_id, merged_at);

CREATE INDEX IF NOT EXISTS idx_crm_accounts_enterprise_identity
    ON crm_accounts (tenant_id, registration_number, tax_number, primary_email);
CREATE INDEX IF NOT EXISTS idx_crm_accounts_customer_classification
    ON crm_accounts (tenant_id, customer_segment, customer_tier, risk_rating);
