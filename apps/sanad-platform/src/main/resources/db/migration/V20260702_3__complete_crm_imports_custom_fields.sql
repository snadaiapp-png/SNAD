-- Completes the unified CRM import and custom-field persistence model.
-- This is additive and runs after the platform RBAC reconciliation migration.

ALTER TABLE crm_import_jobs ADD COLUMN original_filename VARCHAR(255);
ALTER TABLE crm_import_jobs ADD COLUMN content_type VARCHAR(120);
ALTER TABLE crm_import_jobs ADD COLUMN file_size_bytes BIGINT;
ALTER TABLE crm_import_jobs ADD COLUMN file_sha256 VARCHAR(64);
ALTER TABLE crm_import_jobs ADD COLUMN mapping_json TEXT;
ALTER TABLE crm_import_jobs ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_import_jobs ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_import_jobs ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE crm_import_jobs ADD COLUMN worker_id VARCHAR(120);
ALTER TABLE crm_import_jobs ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_import_jobs ADD CONSTRAINT uk_crm_import_jobs_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE crm_import_jobs ADD CONSTRAINT ck_crm_import_jobs_file_size CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0);
ALTER TABLE crm_import_jobs ADD CONSTRAINT ck_crm_import_jobs_attempt_count CHECK (attempt_count >= 0);
ALTER TABLE crm_import_jobs ADD CONSTRAINT ck_crm_import_jobs_progress CHECK (
    processed_rows = succeeded_rows + failed_rows
    AND processed_rows <= total_rows
);

CREATE INDEX idx_crm_import_jobs_worker
    ON crm_import_jobs (status, lease_expires_at, created_at, tenant_id);

CREATE TABLE crm_import_files (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    import_job_id UUID NOT NULL,
    content_base64 TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_import_files PRIMARY KEY (id),
    CONSTRAINT uk_crm_import_files_job UNIQUE (tenant_id, import_job_id),
    CONSTRAINT fk_crm_import_files_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_import_files_job_same_tenant
        FOREIGN KEY (tenant_id, import_job_id) REFERENCES crm_import_jobs (tenant_id, id)
);

CREATE TABLE crm_import_errors (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    import_job_id UUID NOT NULL,
    row_number BIGINT NOT NULL,
    field_name VARCHAR(160),
    error_code VARCHAR(80) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    raw_row TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_import_errors PRIMARY KEY (id),
    CONSTRAINT fk_crm_import_errors_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_import_errors_job_same_tenant
        FOREIGN KEY (tenant_id, import_job_id) REFERENCES crm_import_jobs (tenant_id, id),
    CONSTRAINT ck_crm_import_errors_row_number CHECK (row_number >= 1)
);

CREATE INDEX idx_crm_import_errors_job
    ON crm_import_errors (tenant_id, import_job_id, row_number, id);

ALTER TABLE crm_custom_field_definitions
    ADD CONSTRAINT uk_crm_custom_field_definitions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE crm_custom_field_definitions
    ADD CONSTRAINT ck_crm_custom_field_data_type
        CHECK (data_type IN ('TEXT','NUMBER','BOOLEAN','DATE','DATETIME','EMAIL','URL'));
ALTER TABLE crm_custom_field_definitions
    ADD CONSTRAINT ck_crm_custom_field_sensitive_searchable
        CHECK (NOT (sensitive = TRUE AND searchable = TRUE));

CREATE TABLE crm_custom_field_values (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    value_text TEXT,
    value_number NUMERIC(38,12),
    value_boolean BOOLEAN,
    value_date DATE,
    value_timestamp TIMESTAMP WITH TIME ZONE,
    searchable_value VARCHAR(512),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_custom_field_values PRIMARY KEY (id),
    CONSTRAINT uk_crm_custom_field_value UNIQUE (tenant_id, definition_id, entity_id),
    CONSTRAINT fk_crm_custom_field_values_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_custom_field_values_definition_same_tenant
        FOREIGN KEY (tenant_id, definition_id)
        REFERENCES crm_custom_field_definitions (tenant_id, id),
    CONSTRAINT ck_crm_custom_field_value_entity
        CHECK (entity_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY')),
    CONSTRAINT ck_crm_custom_field_value_exactly_one CHECK (
        (CASE WHEN value_text IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_number IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_boolean IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_date IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN value_timestamp IS NULL THEN 0 ELSE 1 END) = 1
    )
);

CREATE INDEX idx_crm_custom_field_values_entity
    ON crm_custom_field_values (tenant_id, entity_type, entity_id, definition_id);
CREATE INDEX idx_crm_custom_field_values_search
    ON crm_custom_field_values (tenant_id, definition_id, searchable_value, entity_id);

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    VALUES
        ('CRM.CUSTOM_FIELD.READ', 'Read CRM Custom Fields', 'Read tenant CRM custom-field definitions and values'),
        ('CRM.CUSTOM_FIELD.WRITE', 'Write CRM Custom Fields', 'Create definitions and update tenant CRM custom-field values'),
        ('CRM.IMPORT.READ', 'Read CRM Imports', 'Read tenant CRM import jobs and error reports'),
        ('CRM.IMPORT.WRITE', 'Run CRM Imports', 'Upload, validate, execute, retry, and cancel tenant CRM imports')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN ('CRM.CUSTOM_FIELD.READ','CRM.CUSTOM_FIELD.WRITE','CRM.IMPORT.READ','CRM.IMPORT.WRITE')
 AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
