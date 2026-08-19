-- G7 correctness hardening: ensure all seven syncable CRM entities advance sync_version.
-- Historical V20260812_2 omitted crm_activities from the trigger set.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_activities'
    ) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_activities_sync_version ON crm_activities(sync_version)';
        EXECUTE 'DROP TRIGGER IF EXISTS trg_activities_sync_version ON crm_activities';
        EXECUTE 'CREATE TRIGGER trg_activities_sync_version BEFORE UPDATE ON crm_activities FOR EACH ROW EXECUTE FUNCTION fn_update_sync_version()';
    END IF;
END $$;
