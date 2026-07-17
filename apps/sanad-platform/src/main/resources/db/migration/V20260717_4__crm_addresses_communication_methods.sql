-- EXEC-PROMPT-CRM-007: canonical addresses and communication methods.
-- Portable PostgreSQL/H2 PostgreSQL-mode SQL.

CREATE TABLE crm_party_addresses (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    owner_type VARCHAR(16) NOT NULL,
    owner_id UUID NOT NULL,
    account_id UUID,
    contact_id UUID,
    address_type VARCHAR(24) NOT NULL,
    label VARCHAR(120),
    raw_formatted_address VARCHAR(1200),
    line1 VARCHAR(240) NOT NULL,
    line2 VARCHAR(240),
    line3 VARCHAR(240),
    district VARCHAR(160),
    city VARCHAR(160) NOT NULL,
    state_region VARCHAR(160),
    postal_code VARCHAR(40),
    country_code VARCHAR(2) NOT NULL,
    country_extension_json TEXT,
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    primary_address BOOLEAN NOT NULL DEFAULT FALSE,
    primary_slot SMALLINT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_source VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    valid_from DATE,
    valid_to DATE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_crm_party_addresses PRIMARY KEY (id),
    CONSTRAINT uk_crm_party_addresses_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_party_addresses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_party_addresses_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_party_addresses_contact FOREIGN KEY (tenant_id, contact_id)
        REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT ck_crm_party_addresses_owner CHECK (
        (owner_type='ACCOUNT' AND account_id IS NOT NULL AND contact_id IS NULL AND owner_id=account_id)
        OR
        (owner_type='PERSON' AND contact_id IS NOT NULL AND account_id IS NULL AND owner_id=contact_id)
    ),
    CONSTRAINT ck_crm_party_addresses_type CHECK (
        address_type IN ('REGISTERED','BILLING','SHIPPING','OFFICE','HOME','OTHER')
    ),
    CONSTRAINT ck_crm_party_addresses_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    CONSTRAINT ck_crm_party_addresses_country CHECK (CHAR_LENGTH(country_code)=2),
    CONSTRAINT ck_crm_party_addresses_primary CHECK (
        (primary_address=TRUE AND primary_slot=1) OR (primary_address=FALSE AND primary_slot IS NULL)
    ),
    CONSTRAINT ck_crm_party_addresses_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to>=valid_from),
    CONSTRAINT ck_crm_party_addresses_coordinates CHECK (
        (latitude IS NULL OR latitude BETWEEN -90 AND 90)
        AND (longitude IS NULL OR longitude BETWEEN -180 AND 180)
    )
);

CREATE UNIQUE INDEX uq_crm_party_addresses_primary
    ON crm_party_addresses (tenant_id, owner_type, owner_id, address_type, primary_slot);
CREATE INDEX idx_crm_party_addresses_owner
    ON crm_party_addresses (tenant_id, owner_type, owner_id, status, updated_at DESC, id);
CREATE INDEX idx_crm_party_addresses_search
    ON crm_party_addresses (tenant_id, country_code, city, postal_code, status);

CREATE TABLE crm_party_address_history (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    address_id UUID NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    owner_id UUID NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    previous_version BIGINT,
    new_version BIGINT NOT NULL,
    snapshot TEXT NOT NULL,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_party_address_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_party_address_history_address FOREIGN KEY (tenant_id, address_id)
        REFERENCES crm_party_addresses (tenant_id, id)
);
CREATE INDEX idx_crm_party_address_history
    ON crm_party_address_history (tenant_id, address_id, changed_at DESC, id);

CREATE TABLE crm_communication_policies (
    tenant_id UUID NOT NULL,
    email_unique_within_owner BOOLEAN NOT NULL DEFAULT TRUE,
    phone_unique_within_owner BOOLEAN NOT NULL DEFAULT TRUE,
    single_preferred_per_type BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_communication_policies PRIMARY KEY (tenant_id),
    CONSTRAINT fk_crm_communication_policies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

INSERT INTO crm_communication_policies (
    tenant_id,email_unique_within_owner,phone_unique_within_owner,single_preferred_per_type,
    created_at,updated_at
)
SELECT tenant.id,TRUE,TRUE,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM tenants tenant
WHERE NOT EXISTS (
    SELECT 1 FROM crm_communication_policies policy WHERE policy.tenant_id=tenant.id
);

CREATE TABLE crm_communication_methods (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    owner_type VARCHAR(16) NOT NULL,
    owner_id UUID NOT NULL,
    account_id UUID,
    contact_id UUID,
    method_type VARCHAR(32) NOT NULL,
    raw_value VARCHAR(1000) NOT NULL,
    normalized_value VARCHAR(1000) NOT NULL,
    display_value VARCHAR(1000) NOT NULL,
    label VARCHAR(120),
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_slot SMALLINT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED',
    verified_at TIMESTAMP WITH TIME ZONE,
    privacy_classification VARCHAR(24) NOT NULL DEFAULT 'INTERNAL',
    consent_state_reference VARCHAR(120),
    usage_purpose VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    valid_from DATE,
    valid_to DATE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_crm_communication_methods PRIMARY KEY (id),
    CONSTRAINT uk_crm_communication_methods_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_communication_methods_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_communication_methods_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_communication_methods_contact FOREIGN KEY (tenant_id, contact_id)
        REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT ck_crm_communication_methods_owner CHECK (
        (owner_type='ACCOUNT' AND account_id IS NOT NULL AND contact_id IS NULL AND owner_id=account_id)
        OR
        (owner_type='PERSON' AND contact_id IS NOT NULL AND account_id IS NULL AND owner_id=contact_id)
    ),
    CONSTRAINT ck_crm_communication_methods_type CHECK (
        method_type IN ('EMAIL','PHONE','MOBILE','FAX','WHATSAPP','SMS','MESSAGING_HANDLE','WEBSITE','OTHER')
    ),
    CONSTRAINT ck_crm_communication_methods_preferred CHECK (
        (preferred=TRUE AND preferred_slot=1) OR (preferred=FALSE AND preferred_slot IS NULL)
    ),
    CONSTRAINT ck_crm_communication_methods_verification CHECK (
        verification_status IN ('UNVERIFIED','PENDING','VERIFIED','FAILED','REVOKED')
    ),
    CONSTRAINT ck_crm_communication_methods_privacy CHECK (
        privacy_classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')
    ),
    CONSTRAINT ck_crm_communication_methods_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    CONSTRAINT ck_crm_communication_methods_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to>=valid_from),
    CONSTRAINT ck_crm_communication_methods_verified CHECK (
        (verified=TRUE AND verification_status='VERIFIED') OR verified=FALSE
    )
);

CREATE UNIQUE INDEX uq_crm_communication_methods_preferred
    ON crm_communication_methods (tenant_id, owner_type, owner_id, method_type, preferred_slot);
CREATE INDEX idx_crm_communication_methods_owner
    ON crm_communication_methods (tenant_id, owner_type, owner_id, status, updated_at DESC, id);
CREATE INDEX idx_crm_communication_methods_lookup
    ON crm_communication_methods (tenant_id, method_type, normalized_value, status);
CREATE INDEX idx_crm_communication_methods_privacy
    ON crm_communication_methods (tenant_id, privacy_classification, status);

CREATE TABLE crm_communication_method_history (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    communication_method_id UUID NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    owner_id UUID NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    previous_version BIGINT,
    new_version BIGINT NOT NULL,
    snapshot TEXT NOT NULL,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_communication_method_history PRIMARY KEY (id),
    CONSTRAINT fk_crm_communication_history_method FOREIGN KEY (tenant_id, communication_method_id)
        REFERENCES crm_communication_methods (tenant_id, id)
);
CREATE INDEX idx_crm_communication_method_history
    ON crm_communication_method_history (tenant_id, communication_method_id, changed_at DESC, id);

-- Preserve all CRM-005 account-address rows. The legacy table remains a compatibility projection.
INSERT INTO crm_party_addresses (
    id,tenant_id,version,owner_type,owner_id,account_id,contact_id,address_type,label,
    raw_formatted_address,line1,line2,line3,district,city,state_region,postal_code,country_code,
    country_extension_json,latitude,longitude,primary_address,primary_slot,verified,
    verification_source,status,valid_from,valid_to,created_by,updated_by,created_at,updated_at,archived_at
)
SELECT legacy.id,legacy.tenant_id,legacy.version,'ACCOUNT',legacy.account_id,legacy.account_id,NULL,
       legacy.address_type,legacy.label,
       TRIM(legacy.line1 || CASE WHEN legacy.line2 IS NULL THEN '' ELSE ', ' || legacy.line2 END || ', ' || legacy.city),
       legacy.line1,legacy.line2,NULL,NULL,legacy.city,legacy.state_region,legacy.postal_code,legacy.country_code,
       NULL,NULL,NULL,legacy.primary_address,CASE WHEN legacy.primary_address THEN 1 ELSE NULL END,FALSE,
       'LEGACY_CRM005',CASE WHEN legacy.active THEN 'ACTIVE' ELSE 'ARCHIVED' END,NULL,NULL,
       COALESCE(legacy.created_by,legacy.updated_by),COALESCE(legacy.updated_by,legacy.created_by),
       legacy.created_at,legacy.updated_at,CASE WHEN legacy.active THEN NULL ELSE legacy.updated_at END
FROM crm_account_addresses legacy
WHERE NOT EXISTS (
    SELECT 1 FROM crm_party_addresses target
    WHERE target.tenant_id=legacy.tenant_id AND target.id=legacy.id
);

-- Legacy primary projections are copied without deleting or rewriting their source fields.
INSERT INTO crm_communication_methods (
    id,tenant_id,owner_type,owner_id,account_id,contact_id,method_type,raw_value,normalized_value,
    display_value,label,preferred,preferred_slot,verified,verification_status,privacy_classification,
    consent_state_reference,usage_purpose,status,created_by,updated_by,created_at,updated_at
)
SELECT gen_random_uuid(),account.tenant_id,'ACCOUNT',account.id,account.id,NULL,'EMAIL',account.primary_email,
       LOWER(TRIM(account.primary_email)),TRIM(account.primary_email),'Primary email',TRUE,1,FALSE,'UNVERIFIED',
       'INTERNAL',NULL,'GENERAL','ACTIVE',account.created_by,account.updated_by,account.created_at,account.updated_at
FROM crm_accounts account
WHERE account.primary_email IS NOT NULL AND TRIM(account.primary_email)<>''
  AND NOT EXISTS (
      SELECT 1 FROM crm_communication_methods method
      WHERE method.tenant_id=account.tenant_id AND method.owner_type='ACCOUNT' AND method.owner_id=account.id
        AND method.method_type='EMAIL' AND method.normalized_value=LOWER(TRIM(account.primary_email))
  );

INSERT INTO crm_communication_methods (
    id,tenant_id,owner_type,owner_id,account_id,contact_id,method_type,raw_value,normalized_value,
    display_value,label,preferred,preferred_slot,verified,verification_status,privacy_classification,
    consent_state_reference,usage_purpose,status,created_by,updated_by,created_at,updated_at
)
SELECT gen_random_uuid(),account.tenant_id,'ACCOUNT',account.id,account.id,NULL,'PHONE',account.primary_phone,
       REPLACE(REPLACE(REPLACE(REPLACE(TRIM(account.primary_phone),' ',''),'-',''),'(',''),')',''),
       TRIM(account.primary_phone),'Primary phone',TRUE,1,FALSE,'UNVERIFIED','INTERNAL',NULL,'GENERAL','ACTIVE',
       account.created_by,account.updated_by,account.created_at,account.updated_at
FROM crm_accounts account
WHERE account.primary_phone IS NOT NULL AND TRIM(account.primary_phone)<>''
  AND NOT EXISTS (
      SELECT 1 FROM crm_communication_methods method
      WHERE method.tenant_id=account.tenant_id AND method.owner_type='ACCOUNT' AND method.owner_id=account.id
        AND method.method_type='PHONE'
        AND method.normalized_value=REPLACE(REPLACE(REPLACE(REPLACE(TRIM(account.primary_phone),' ',''),'-',''),'(',''),')','')
  );

INSERT INTO crm_communication_methods (
    id,tenant_id,owner_type,owner_id,account_id,contact_id,method_type,raw_value,normalized_value,
    display_value,label,preferred,preferred_slot,verified,verification_status,privacy_classification,
    consent_state_reference,usage_purpose,status,created_by,updated_by,created_at,updated_at
)
SELECT gen_random_uuid(),contact.tenant_id,'PERSON',contact.id,NULL,contact.id,'EMAIL',contact.primary_email,
       LOWER(TRIM(contact.primary_email)),TRIM(contact.primary_email),'Primary email',TRUE,1,FALSE,'UNVERIFIED',
       'CONFIDENTIAL',contact.consent_summary,'GENERAL','ACTIVE',contact.created_by,contact.updated_by,
       contact.created_at,contact.updated_at
FROM crm_contacts contact
WHERE contact.primary_email IS NOT NULL AND TRIM(contact.primary_email)<>''
  AND NOT EXISTS (
      SELECT 1 FROM crm_communication_methods method
      WHERE method.tenant_id=contact.tenant_id AND method.owner_type='PERSON' AND method.owner_id=contact.id
        AND method.method_type='EMAIL' AND method.normalized_value=LOWER(TRIM(contact.primary_email))
  );

INSERT INTO crm_communication_methods (
    id,tenant_id,owner_type,owner_id,account_id,contact_id,method_type,raw_value,normalized_value,
    display_value,label,preferred,preferred_slot,verified,verification_status,privacy_classification,
    consent_state_reference,usage_purpose,status,created_by,updated_by,created_at,updated_at
)
SELECT gen_random_uuid(),contact.tenant_id,'PERSON',contact.id,NULL,contact.id,'PHONE',contact.primary_phone,
       REPLACE(REPLACE(REPLACE(REPLACE(TRIM(contact.primary_phone),' ',''),'-',''),'(',''),')',''),
       TRIM(contact.primary_phone),'Primary phone',TRUE,1,FALSE,'UNVERIFIED','CONFIDENTIAL',
       contact.consent_summary,'GENERAL','ACTIVE',contact.created_by,contact.updated_by,contact.created_at,contact.updated_at
FROM crm_contacts contact
WHERE contact.primary_phone IS NOT NULL AND TRIM(contact.primary_phone)<>''
  AND NOT EXISTS (
      SELECT 1 FROM crm_communication_methods method
      WHERE method.tenant_id=contact.tenant_id AND method.owner_type='PERSON' AND method.owner_id=contact.id
        AND method.method_type='PHONE'
        AND method.normalized_value=REPLACE(REPLACE(REPLACE(REPLACE(TRIM(contact.primary_phone),' ',''),'-',''),'(',''),')','')
  );
