-- SANAD CRM account foundation.
-- Compatible with PostgreSQL 14+ and H2 PostgreSQL mode.

CREATE TABLE crm_accounts (
    id                    UUID                     NOT NULL,
    tenant_id             UUID                     NOT NULL,
    version               BIGINT                   NOT NULL DEFAULT 0,
    display_name          VARCHAR(240)             NOT NULL,
    normalized_name       VARCHAR(240)             NOT NULL,
    account_type          VARCHAR(40)              NOT NULL,
    lifecycle_status      VARCHAR(32)              NOT NULL,
    owner_user_id         UUID,
    primary_currency_code VARCHAR(3),
    preferred_locale      VARCHAR(35),
    time_zone             VARCHAR(64),
    source                VARCHAR(64),
    created_by            UUID                     NOT NULL,
    updated_by            UUID                     NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at           TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_crm_accounts PRIMARY KEY (id),
    CONSTRAINT fk_crm_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_accounts_type CHECK (account_type IN ('BUSINESS', 'PERSON', 'PARTNER', 'PROSPECT', 'OTHER')),
    CONSTRAINT ck_crm_accounts_status CHECK (lifecycle_status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_crm_accounts_currency CHECK (primary_currency_code IS NULL OR CHAR_LENGTH(primary_currency_code) = 3)
);

CREATE INDEX idx_crm_accounts_tenant ON crm_accounts (tenant_id);
CREATE INDEX idx_crm_accounts_tenant_status ON crm_accounts (tenant_id, lifecycle_status);
CREATE INDEX idx_crm_accounts_tenant_name ON crm_accounts (tenant_id, normalized_name);
CREATE INDEX idx_crm_accounts_tenant_owner ON crm_accounts (tenant_id, owner_user_id);
