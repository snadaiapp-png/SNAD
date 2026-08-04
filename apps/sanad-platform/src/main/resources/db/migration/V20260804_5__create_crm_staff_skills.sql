-- ============================================================
-- SNAD Platform — Create crm_staff_skills
-- ------------------------------------------------------------
-- Branch: fix/rem-2-crm-008-schema-foundation
-- Epic:   REM-2 — CRM-008 Database Schema Remediation
-- Gap:    GAP-04 (REM-2-GAP-MATRIX.md)
--
-- Creates the staff skills table referenced by:
--   JdbcSkillRepository.java
--   OwnershipJdbcSupport.staffSkillMapper()
--   SkillController.java (/api/v1/crm/skills)
--
-- UNIQUE(tenant_id, staff_id, skill_name) enforces one skill
-- record per staff member per skill name.
-- ============================================================

CREATE TABLE crm_staff_skills (
    id          UUID NOT NULL,
    tenant_id   UUID NOT NULL,
    staff_id    UUID NOT NULL,
    skill_name  VARCHAR(200) NOT NULL,
    level       VARCHAR(20) NOT NULL,
    proficiency INT NOT NULL,
    created_by  UUID NOT NULL,
    updated_by  UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT pk_crm_staff_skills PRIMARY KEY (id),
    CONSTRAINT uk_crm_staff_skills_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_staff_skills_tenant_staff_name UNIQUE (tenant_id, staff_id, skill_name)
);

CREATE INDEX idx_crm_staff_skills_tenant_staff
    ON crm_staff_skills (tenant_id, staff_id);

CREATE INDEX idx_crm_staff_skills_tenant_skill_name
    ON crm_staff_skills (tenant_id, skill_name);
