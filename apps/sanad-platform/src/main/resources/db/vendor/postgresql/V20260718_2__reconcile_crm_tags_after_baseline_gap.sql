-- ============================================================
-- SNAD Platform — CRM-Tags forward-only production reconciliation
-- Version: 20260718.2
-- ------------------------------------------------------------
-- Repairs the production-only Flyway baseline gap where:
--   * V20260716.3 (crm_tags + crm_tag_assignments) was skipped
--     by baseline 20260717.6;
--   * V20260718.1 reconciled crm_tasks, crm_notes, and six
--     G1 extension tables but not crm_tags;
--   * V20260807.3 depends on crm_tags for a unique index;
--   * Fresh database provisioning fails at V20260807.3.
--
-- This migration is safe for normal clean installations:
-- when crm_tags and crm_tag_assignments already exist,
-- CREATE ... IF NOT EXISTS and idempotent capability inserts
-- are no-ops.
--
-- It never modifies or deletes flyway_schema_history.
-- ============================================================

-- ============================================================
-- Precondition: verify baseline gap or tables already complete
-- ============================================================

DO $precondition$
DECLARE
    tag_table_count INTEGER;
    baseline_gap_present BOOLEAN;
BEGIN
    SELECT COUNT(*)
      INTO tag_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_tags',
           'crm_tag_assignments'
       );

    IF tag_table_count NOT IN (0, 2) THEN
        RAISE EXCEPTION
            'CRM-Tags reconciliation refuses a partial table state: found % of 2 tables',
            tag_table_count;
    END IF;

    IF tag_table_count = 0 THEN
        SELECT EXISTS (
            SELECT 1
              FROM flyway_schema_history
             WHERE version = '20260717.6'
               AND type = 'BASELINE'
               AND success = TRUE
        ) INTO baseline_gap_present;

        IF NOT baseline_gap_present THEN
            RAISE EXCEPTION
                'CRM-Tags tables are absent, but the verified 20260717.6 BASELINE gap is not present';
        END IF;
    END IF;
END
$precondition$;

-- ============================================================
-- CRM Tags — original contract from V20260716.3
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_tags (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    name VARCHAR(80) NOT NULL,
    color VARCHAR(20),

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_tags PRIMARY KEY (id),
    CONSTRAINT uk_crm_tags_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_tags_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_crm_tags_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_tags_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_crm_tags_tenant_name
    ON crm_tags (tenant_id, name);

-- ============================================================
-- CRM Tag Assignments — original contract from V20260716.3
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_tag_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,

    tag_id UUID NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,

    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_tag_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_tag_assignments UNIQUE (tenant_id, tag_id, subject_type, subject_id),
    CONSTRAINT fk_crm_tag_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_tag_assignments_tag FOREIGN KEY (tenant_id, tag_id) REFERENCES crm_tags (tenant_id, id),
    CONSTRAINT ck_crm_tag_assignments_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK','NOTE')
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_tag_assignments_subject
    ON crm_tag_assignments (tenant_id, subject_type, subject_id);

CREATE INDEX IF NOT EXISTS idx_crm_tag_assignments_tag
    ON crm_tag_assignments (tenant_id, tag_id);

-- ============================================================
-- Seed CRM.TAG.READ and CRM.TAG.WRITE capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.TAG.READ',  'Read CRM Tags',  'View tenant CRM tags and assignments'),
    ('CRM.TAG.WRITE', 'Write CRM Tags', 'Create tags and assign/unassign them')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1
      FROM access_capabilities existing
     WHERE existing.code = capability.code
);

-- Grant CRM.TAG.* to ADMIN role in every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
  FROM roles role
  JOIN access_capabilities capability
    ON capability.code IN ('CRM.TAG.READ', 'CRM.TAG.WRITE')
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
    tag_index_count INTEGER;
    assignment_index_count INTEGER;
    same_tenant_fk_count INTEGER;
    capability_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_tags',
           'crm_tag_assignments'
       );

    SELECT COUNT(*)
      INTO tenant_column_count
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND column_name = 'tenant_id'
       AND table_name IN (
           'crm_tags',
           'crm_tag_assignments'
       );

    SELECT COUNT(*)
      INTO tenant_fk_count
      FROM pg_constraint constraint_row
      JOIN pg_class table_row
        ON table_row.oid = constraint_row.conrelid
     WHERE constraint_row.contype = 'f'
       AND constraint_row.confrelid = 'tenants'::regclass
       AND table_row.relname IN (
           'crm_tags',
           'crm_tag_assignments'
       );

    SELECT COUNT(*)
      INTO tag_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_tags'
       AND indexname LIKE 'idx_crm_tags_%';

    SELECT COUNT(*)
      INTO assignment_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_tag_assignments'
       AND indexname LIKE 'idx_crm_tag_assignments_%';

    SELECT COUNT(*)
      INTO same_tenant_fk_count
      FROM pg_constraint
     WHERE contype = 'f'
       AND conname = 'fk_crm_tag_assignments_tag';

    SELECT COUNT(*)
      INTO capability_count
      FROM access_capabilities
     WHERE code IN (
         'CRM.TAG.READ',
         'CRM.TAG.WRITE'
     )
       AND status = 'ACTIVE';

    IF table_count <> 2 THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation expected 2 tables, found %', table_count;
    END IF;

    IF tenant_column_count <> 2 THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation expected tenant_id on 2 tables, found %', tenant_column_count;
    END IF;

    IF tenant_fk_count <> 2 THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation expected 2 tenant foreign keys, found %', tenant_fk_count;
    END IF;

    IF tag_index_count <> 1 THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation expected 1 crm_tags index, found %', tag_index_count;
    END IF;

    IF assignment_index_count <> 2 THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation expected 2 crm_tag_assignments indexes, found %', assignment_index_count;
    END IF;

    IF same_tenant_fk_count <> 1 THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation expected 1 same-tenant tag FK, found %', same_tenant_fk_count;
    END IF;

    IF capability_count <> 2 THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation expected 2 active tag capabilities, found %', capability_count;
    END IF;
END
$postcondition$;
