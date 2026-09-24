-- B-08a: Add missing `result` column to crm_activities.
-- JdbcActivityRepository.complete() writes to this column but it was never
-- created in the original DDL.  The legacy paths stash result in `body` as a
-- workaround; the domain repository expects a dedicated column.
ALTER TABLE crm_activities ADD COLUMN IF NOT EXISTS result TEXT;

-- B-08b: Constrain crm_activities.related_type to the known entity types.
-- Prevents orphan references from arbitrary string values.
ALTER TABLE crm_activities
    ADD CONSTRAINT chk_crm_activities_related_type
    CHECK (related_type IS NULL OR related_type IN
        ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','OTHER'));
