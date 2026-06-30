-- SANAD CRM contact foundation.
CREATE TABLE crm_contacts (
    id               UUID                     NOT NULL,
    tenant_id        UUID                     NOT NULL,
    version          BIGINT                   NOT NULL DEFAULT 0,
    account_id       UUID,
    given_name       VARCHAR(120)              NOT NULL,
    family_name      VARCHAR(120),
    display_name     VARCHAR(240)              NOT NULL,
    normalized_name  VARCHAR(240)              NOT NULL,
    primary_email    VARCHAR(255),
    primary_phone    VARCHAR(64),
    preferred_locale VARCHAR(35),
    time_zone        VARCHAR(64),
    lifecycle_status VARCHAR(32)               NOT NULL,
    owner_user_id    UUID,
    created_by       UUID                     NOT NULL,
    updated_by       UUID                     NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_crm_contacts PRIMARY KEY (id),
    CONSTRAINT fk_crm_contacts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_contacts_account FOREIGN KEY (account_id) REFERENCES crm_accounts (id),
    CONSTRAINT ck_crm_contacts_status CHECK (lifecycle_status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_crm_contacts_tenant ON crm_contacts (tenant_id);
CREATE INDEX idx_crm_contacts_account ON crm_contacts (tenant_id, account_id);
CREATE INDEX idx_crm_contacts_name ON crm_contacts (tenant_id, normalized_name);
CREATE INDEX idx_crm_contacts_email ON crm_contacts (tenant_id, primary_email);
CREATE INDEX idx_crm_contacts_owner ON crm_contacts (tenant_id, owner_user_id);
