-- ============================================================
-- SNAD Platform — CRM-G1 forward-only production reconciliation
-- Version: 20260718.1
-- ------------------------------------------------------------
-- Repairs the production-only Flyway baseline gap where:
--   * 20260717.6 is recorded as BASELINE rather than SQL;
--   * 20260717.100 and 20260717.101 were applied successfully;
--   * all eight CRM-G1 tables, their 26 explicit indexes, and
--     CRM.TASK / CRM.NOTE capabilities are absent.
--
-- This migration is safe for normal clean installations:
-- when the eight CRM-G1 tables already exist, CREATE ... IF NOT
-- EXISTS and idempotent capability inserts are no-ops.
--
-- It never modifies or deletes flyway_schema_history.
-- ============================================================

DO $precondition$
DECLARE
    g1_table_count INTEGER;
    baseline_gap_present BOOLEAN;
    crm_007_v100_present BOOLEAN;
    crm_007_v101_present BOOLEAN;
BEGIN
    SELECT COUNT(*)
      INTO g1_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_tasks',
           'crm_assignments',
           'crm_transfers',
           'crm_notes',
           'crm_audit_logs',
           'crm_reports',
           'crm_phone_numbers',
           'crm_contact_lookup_index'
       );

    IF g1_table_count NOT IN (0, 8) THEN
        RAISE EXCEPTION
            'CRM-G1 reconciliation refuses a partial table state: found % of 8 tables',
            g1_table_count;
    END IF;

    IF g1_table_count = 0 THEN
        SELECT EXISTS (
            SELECT 1
              FROM flyway_schema_history
             WHERE version = '20260717.6'
               AND type = 'BASELINE'
               AND success = TRUE
        ) INTO baseline_gap_present;

        SELECT EXISTS (
            SELECT 1
              FROM flyway_schema_history
             WHERE version = '20260717.100'
               AND type = 'SQL'
               AND success = TRUE
        ) INTO crm_007_v100_present;

        SELECT EXISTS (
            SELECT 1
              FROM flyway_schema_history
             WHERE version = '20260717.101'
               AND type = 'SQL'
               AND success = TRUE
        ) INTO crm_007_v101_present;

        IF NOT baseline_gap_present THEN
            RAISE EXCEPTION
                'CRM-G1 tables are absent, but the verified 20260717.6 BASELINE gap is not present';
        END IF;

        IF NOT crm_007_v100_present OR NOT crm_007_v101_present THEN
            RAISE EXCEPTION
                'CRM-G1 baseline-gap repair requires successful SQL migrations 20260717.100 and 20260717.101';
        END IF;
    END IF;
END
$precondition$;

-- ============================================================
-- CRM Tasks — original contract from V20260716.1
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_tasks (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    title VARCHAR(240) NOT NULL,
    description TEXT,

    related_type VARCHAR(40),
    related_id UUID,

    assignee_user_id UUID,
    owner_user_id UUID,

    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    priority INTEGER NOT NULL DEFAULT 50,

    start_at TIMESTAMP WITH TIME ZONE,
    due_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    result TEXT,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_tasks PRIMARY KEY (id),
    CONSTRAINT uk_crm_tasks_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_tasks_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_tasks_related_type CHECK (
        related_type IS NULL OR related_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY')
    ),
    CONSTRAINT ck_crm_tasks_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_crm_tasks_priority CHECK (priority BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_crm_tasks_assignee_status
    ON crm_tasks (tenant_id, assignee_user_id, status, due_at);
CREATE INDEX IF NOT EXISTS idx_crm_tasks_related
    ON crm_tasks (tenant_id, related_type, related_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_tasks_status_due
    ON crm_tasks (tenant_id, status, due_at, updated_at DESC);

-- ============================================================
-- CRM Notes — original contract from V20260716.2
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_notes (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    body TEXT NOT NULL,
    author_user_id UUID,
    archived BOOLEAN NOT NULL DEFAULT FALSE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_notes PRIMARY KEY (id),
    CONSTRAINT uk_crm_notes_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_notes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_notes_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK')
    ),
    CONSTRAINT ck_crm_notes_body_not_empty CHECK (LENGTH(TRIM(body)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_crm_notes_subject
    ON crm_notes (tenant_id, subject_type, subject_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_notes_author
    ON crm_notes (tenant_id, author_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_notes_active
    ON crm_notes (tenant_id, archived, created_at DESC);

-- ============================================================
-- Six remaining CRM-G1 extension tables — original 20260717.6
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    assigned_user_id UUID NOT NULL,
    assignment_role VARCHAR(80) NOT NULL DEFAULT 'OWNER',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE,
    reason TEXT,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_assignments_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK')
    ),
    CONSTRAINT ck_crm_assignments_status CHECK (status IN ('ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT ck_crm_assignments_dates CHECK (ends_at IS NULL OR ends_at >= starts_at)
);

CREATE INDEX IF NOT EXISTS idx_crm_assignments_subject_active
    ON crm_assignments (tenant_id, subject_type, subject_id, status, starts_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_assignments_user_active
    ON crm_assignments (tenant_id, assigned_user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_assignments_role_status
    ON crm_assignments (tenant_id, assignment_role, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS crm_transfers (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    from_user_id UUID,
    to_user_id UUID NOT NULL,
    transfer_type VARCHAR(24) NOT NULL DEFAULT 'OWNERSHIP',
    status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    requested_by UUID NOT NULL,
    approved_by UUID,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    reason TEXT,
    decision_notes TEXT,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_transfers PRIMARY KEY (id),
    CONSTRAINT uk_crm_transfers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_transfers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_transfers_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK')
    ),
    CONSTRAINT ck_crm_transfers_type CHECK (transfer_type IN ('OWNERSHIP','ASSIGNMENT','QUEUE')),
    CONSTRAINT ck_crm_transfers_status CHECK (
        status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED','CANCELLED')
    ),
    CONSTRAINT ck_crm_transfers_distinct_users CHECK (
        from_user_id IS NULL OR from_user_id <> to_user_id
    ),
    CONSTRAINT ck_crm_transfers_decision_time CHECK (
        decided_at IS NULL OR decided_at >= requested_at
    ),
    CONSTRAINT ck_crm_transfers_completion_time CHECK (
        completed_at IS NULL OR completed_at >= requested_at
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_transfers_subject_status
    ON crm_transfers (tenant_id, subject_type, subject_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_transfers_recipient_status
    ON crm_transfers (tenant_id, to_user_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_transfers_requested_at
    ON crm_transfers (tenant_id, requested_at DESC, status);

CREATE TABLE IF NOT EXISTS crm_audit_logs (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,

    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID,
    action_code VARCHAR(80) NOT NULL,
    actor_user_id UUID,
    correlation_id VARCHAR(120),
    source_ip VARCHAR(64),
    user_agent VARCHAR(500),
    before_json TEXT,
    after_json TEXT,
    metadata_json TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_audit_logs PRIMARY KEY (id),
    CONSTRAINT uk_crm_audit_logs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_audit_logs_entity_type CHECK (
        entity_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','PIPELINE','ACTIVITY','TASK',
                        'ASSIGNMENT','TRANSFER','NOTE','REPORT','PHONE_NUMBER','IMPORT','CUSTOM_FIELD')
    ),
    CONSTRAINT ck_crm_audit_logs_action_not_empty CHECK (LENGTH(TRIM(action_code)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_entity_time
    ON crm_audit_logs (tenant_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_actor_time
    ON crm_audit_logs (tenant_id, actor_user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_correlation
    ON crm_audit_logs (tenant_id, correlation_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_logs_action_time
    ON crm_audit_logs (tenant_id, action_code, occurred_at DESC);

CREATE TABLE IF NOT EXISTS crm_reports (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    name VARCHAR(240) NOT NULL,
    description TEXT,
    report_type VARCHAR(60) NOT NULL,
    definition_json TEXT NOT NULL,
    parameters_json TEXT,
    visibility VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    owner_user_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    schedule_expression VARCHAR(160),
    last_run_at TIMESTAMP WITH TIME ZONE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_reports PRIMARY KEY (id),
    CONSTRAINT uk_crm_reports_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_reports_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_reports_visibility CHECK (visibility IN ('PRIVATE','TEAM','TENANT')),
    CONSTRAINT ck_crm_reports_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    CONSTRAINT ck_crm_reports_name_not_empty CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_crm_reports_definition_not_empty CHECK (LENGTH(TRIM(definition_json)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_crm_reports_owner_status
    ON crm_reports (tenant_id, owner_user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_reports_type_status
    ON crm_reports (tenant_id, report_type, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_reports_last_run
    ON crm_reports (tenant_id, last_run_at DESC, status);

CREATE TABLE IF NOT EXISTS crm_phone_numbers (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    phone_type VARCHAR(24) NOT NULL DEFAULT 'MOBILE',
    country_code VARCHAR(8),
    national_number VARCHAR(40),
    e164 VARCHAR(24) NOT NULL,
    extension VARCHAR(12),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_phone_numbers PRIMARY KEY (id),
    CONSTRAINT uk_crm_phone_numbers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_phone_numbers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_phone_numbers_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT ck_crm_phone_numbers_type CHECK (phone_type IN ('MOBILE','WORK','HOME','FAX','OTHER')),
    CONSTRAINT ck_crm_phone_numbers_e164 CHECK (LENGTH(TRIM(e164)) >= 4 AND e164 LIKE '+%')
);

CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_contact
    ON crm_phone_numbers (tenant_id, contact_id, archived, is_primary DESC);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_e164
    ON crm_phone_numbers (tenant_id, e164, archived);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_primary
    ON crm_phone_numbers (tenant_id, is_primary, archived, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_phone_numbers_verified
    ON crm_phone_numbers (tenant_id, verified_at DESC, archived);

CREATE TABLE IF NOT EXISTS crm_contact_lookup_index (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    normalized_phone VARCHAR(40),
    normalized_email VARCHAR(320),
    normalized_name VARCHAR(320) NOT NULL,
    searchable_text TEXT,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_contact_lookup_index PRIMARY KEY (id),
    CONSTRAINT uk_crm_contact_lookup_tenant_contact UNIQUE (tenant_id, contact_id),
    CONSTRAINT fk_crm_contact_lookup_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_contact_lookup_contact_same_tenant
        FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT ck_crm_contact_lookup_identifier CHECK (
        normalized_phone IS NOT NULL OR normalized_email IS NOT NULL OR LENGTH(TRIM(normalized_name)) > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_phone
    ON crm_contact_lookup_index (tenant_id, normalized_phone, active);
CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_email
    ON crm_contact_lookup_index (tenant_id, normalized_email, active);
CREATE INDEX IF NOT EXISTS idx_crm_contact_lookup_name
    ON crm_contact_lookup_index (tenant_id, normalized_name, active);

-- ============================================================
-- Reconcile the four capabilities skipped by the incorrect baseline
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.TASK.READ',  'Read CRM Tasks',  'View tenant CRM tasks'),
    ('CRM.TASK.WRITE', 'Write CRM Tasks', 'Create and update tenant CRM tasks'),
    ('CRM.NOTE.READ',  'Read CRM Notes',  'View tenant CRM notes'),
    ('CRM.NOTE.WRITE', 'Write CRM Notes', 'Create and archive tenant CRM notes')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1
      FROM access_capabilities existing
     WHERE existing.code = capability.code
);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
  FROM roles role
  JOIN access_capabilities capability
    ON capability.code IN (
        'CRM.TASK.READ', 'CRM.TASK.WRITE',
        'CRM.NOTE.READ', 'CRM.NOTE.WRITE'
    )
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

-- ============================================================
-- Transactional postconditions. Any failure rolls back migration.
-- ============================================================

DO $postcondition$
DECLARE
    table_count INTEGER;
    tenant_column_count INTEGER;
    tenant_fk_count INTEGER;
    explicit_index_count INTEGER;
    non_tenant_first_index_count INTEGER;
    same_tenant_fk_count INTEGER;
    capability_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_tasks',
           'crm_assignments',
           'crm_transfers',
           'crm_notes',
           'crm_audit_logs',
           'crm_reports',
           'crm_phone_numbers',
           'crm_contact_lookup_index'
       );

    SELECT COUNT(*)
      INTO tenant_column_count
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND column_name = 'tenant_id'
       AND table_name IN (
           'crm_tasks',
           'crm_assignments',
           'crm_transfers',
           'crm_notes',
           'crm_audit_logs',
           'crm_reports',
           'crm_phone_numbers',
           'crm_contact_lookup_index'
       );

    SELECT COUNT(*)
      INTO tenant_fk_count
      FROM pg_constraint constraint_row
      JOIN pg_class table_row
        ON table_row.oid = constraint_row.conrelid
     WHERE constraint_row.contype = 'f'
       AND constraint_row.confrelid = 'tenants'::regclass
       AND table_row.relname IN (
           'crm_tasks',
           'crm_assignments',
           'crm_transfers',
           'crm_notes',
           'crm_audit_logs',
           'crm_reports',
           'crm_phone_numbers',
           'crm_contact_lookup_index'
       );

    SELECT COUNT(*)
      INTO explicit_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN (
           'crm_tasks',
           'crm_assignments',
           'crm_transfers',
           'crm_notes',
           'crm_audit_logs',
           'crm_reports',
           'crm_phone_numbers',
           'crm_contact_lookup_index'
       )
       AND indexname LIKE 'idx_crm_%';

    SELECT COUNT(*)
      INTO non_tenant_first_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN (
           'crm_tasks',
           'crm_assignments',
           'crm_transfers',
           'crm_notes',
           'crm_audit_logs',
           'crm_reports',
           'crm_phone_numbers',
           'crm_contact_lookup_index'
       )
       AND indexname LIKE 'idx_crm_%'
       AND indexdef NOT LIKE '%(tenant_id,%';

    SELECT COUNT(*)
      INTO same_tenant_fk_count
      FROM pg_constraint
     WHERE contype = 'f'
       AND conname IN (
           'fk_crm_phone_numbers_contact_same_tenant',
           'fk_crm_contact_lookup_contact_same_tenant'
       );

    SELECT COUNT(*)
      INTO capability_count
      FROM access_capabilities
     WHERE code IN (
         'CRM.TASK.READ',
         'CRM.TASK.WRITE',
         'CRM.NOTE.READ',
         'CRM.NOTE.WRITE'
     )
       AND status = 'ACTIVE';

    IF table_count <> 8 THEN
        RAISE EXCEPTION 'CRM-G1 reconciliation expected 8 tables, found %', table_count;
    END IF;

    IF tenant_column_count <> 8 THEN
        RAISE EXCEPTION 'CRM-G1 reconciliation expected tenant_id on 8 tables, found %', tenant_column_count;
    END IF;

    IF tenant_fk_count <> 8 THEN
        RAISE EXCEPTION 'CRM-G1 reconciliation expected 8 tenant foreign keys, found %', tenant_fk_count;
    END IF;

    IF explicit_index_count <> 26 THEN
        RAISE EXCEPTION 'CRM-G1 reconciliation expected 26 explicit indexes, found %', explicit_index_count;
    END IF;

    IF non_tenant_first_index_count <> 0 THEN
        RAISE EXCEPTION
            'CRM-G1 reconciliation found % explicit indexes not led by tenant_id',
            non_tenant_first_index_count;
    END IF;

    IF same_tenant_fk_count <> 2 THEN
        RAISE EXCEPTION 'CRM-G1 reconciliation expected 2 same-tenant contact FKs, found %', same_tenant_fk_count;
    END IF;

    IF capability_count <> 4 THEN
        RAISE EXCEPTION 'CRM-G1 reconciliation expected 4 active task/note capabilities, found %', capability_count;
    END IF;
END
$postcondition$;
