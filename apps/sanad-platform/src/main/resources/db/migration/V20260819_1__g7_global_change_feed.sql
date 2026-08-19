-- G7 correctness hardening: global monotonic change feed for lossless delta sync
-- Separates optimistic-concurrency entity versions from delta-sync cursor sequencing.

CREATE TABLE IF NOT EXISTS mobile_change_log (
    change_id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE')),
    entity_version BIGINT NOT NULL,
    payload JSONB,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mobile_change_log_tenant_entity_cursor
    ON mobile_change_log(tenant_id, entity_type, change_id);
CREATE INDEX IF NOT EXISTS idx_mobile_change_log_entity
    ON mobile_change_log(tenant_id, entity_type, entity_id, change_id DESC);

ALTER TABLE mobile_change_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE mobile_change_log FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS mobile_change_log_tenant_isolation ON mobile_change_log;
CREATE POLICY mobile_change_log_tenant_isolation ON mobile_change_log
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- Capture the post-trigger row image. Existing BEFORE UPDATE sync_version triggers
-- run before this AFTER trigger, so entity_version reflects the committed version.
CREATE OR REPLACE FUNCTION fn_mobile_capture_change()
RETURNS TRIGGER AS $$
DECLARE
    row_json JSONB;
    op VARCHAR(10);
    tenant UUID;
    entity UUID;
    version BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        row_json := to_jsonb(OLD);
        tenant := OLD.tenant_id;
        entity := OLD.id;
        version := COALESCE(OLD.sync_version, 0) + 1;
        op := 'DELETE';
    ELSE
        row_json := to_jsonb(NEW);
        tenant := NEW.tenant_id;
        entity := NEW.id;
        version := COALESCE(NEW.sync_version, 0);

        IF TG_OP = 'INSERT' THEN
            op := 'CREATE';
        ELSIF COALESCE(to_jsonb(OLD)->>'deleted_at', '') = ''
              AND COALESCE(to_jsonb(NEW)->>'deleted_at', '') <> '' THEN
            op := 'DELETE';
        ELSE
            op := 'UPDATE';
        END IF;
    END IF;

    INSERT INTO mobile_change_log
        (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
    VALUES
        (tenant, TG_ARGV[0], entity, op, version, row_json, NOW());

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Bootstrap pre-existing records so a first cursor=0 pull cannot omit legacy data.
INSERT INTO mobile_change_log (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
SELECT tenant_id, 'account', id, 'CREATE', sync_version, to_jsonb(t), COALESCE(updated_at, created_at, NOW())
FROM crm_accounts t;
INSERT INTO mobile_change_log (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
SELECT tenant_id, 'contact', id, 'CREATE', sync_version, to_jsonb(t), COALESCE(updated_at, created_at, NOW())
FROM crm_contacts t;
INSERT INTO mobile_change_log (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
SELECT tenant_id, 'lead', id, 'CREATE', sync_version, to_jsonb(t), COALESCE(updated_at, created_at, NOW())
FROM crm_leads t;
INSERT INTO mobile_change_log (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
SELECT tenant_id, 'opportunity', id, 'CREATE', sync_version, to_jsonb(t), COALESCE(updated_at, created_at, NOW())
FROM crm_opportunities t;
INSERT INTO mobile_change_log (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
SELECT tenant_id, 'task', id, 'CREATE', sync_version, to_jsonb(t), COALESCE(updated_at, created_at, NOW())
FROM crm_tasks t;
INSERT INTO mobile_change_log (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
SELECT tenant_id, 'note', id, 'CREATE', sync_version, to_jsonb(t), COALESCE(updated_at, created_at, NOW())
FROM crm_notes t;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'crm_activities') THEN
        EXECUTE $sql$
            INSERT INTO mobile_change_log (tenant_id, entity_type, entity_id, operation, entity_version, payload, changed_at)
            SELECT tenant_id, 'activity', id, 'CREATE', sync_version, to_jsonb(t), COALESCE(updated_at, created_at, NOW())
            FROM crm_activities t
        $sql$;
    END IF;
END $$;

DROP TRIGGER IF EXISTS trg_accounts_mobile_change ON crm_accounts;
CREATE TRIGGER trg_accounts_mobile_change
    AFTER INSERT OR UPDATE OR DELETE ON crm_accounts
    FOR EACH ROW EXECUTE FUNCTION fn_mobile_capture_change('account');

DROP TRIGGER IF EXISTS trg_contacts_mobile_change ON crm_contacts;
CREATE TRIGGER trg_contacts_mobile_change
    AFTER INSERT OR UPDATE OR DELETE ON crm_contacts
    FOR EACH ROW EXECUTE FUNCTION fn_mobile_capture_change('contact');

DROP TRIGGER IF EXISTS trg_leads_mobile_change ON crm_leads;
CREATE TRIGGER trg_leads_mobile_change
    AFTER INSERT OR UPDATE OR DELETE ON crm_leads
    FOR EACH ROW EXECUTE FUNCTION fn_mobile_capture_change('lead');

DROP TRIGGER IF EXISTS trg_opportunities_mobile_change ON crm_opportunities;
CREATE TRIGGER trg_opportunities_mobile_change
    AFTER INSERT OR UPDATE OR DELETE ON crm_opportunities
    FOR EACH ROW EXECUTE FUNCTION fn_mobile_capture_change('opportunity');

DROP TRIGGER IF EXISTS trg_tasks_mobile_change ON crm_tasks;
CREATE TRIGGER trg_tasks_mobile_change
    AFTER INSERT OR UPDATE OR DELETE ON crm_tasks
    FOR EACH ROW EXECUTE FUNCTION fn_mobile_capture_change('task');

DROP TRIGGER IF EXISTS trg_notes_mobile_change ON crm_notes;
CREATE TRIGGER trg_notes_mobile_change
    AFTER INSERT OR UPDATE OR DELETE ON crm_notes
    FOR EACH ROW EXECUTE FUNCTION fn_mobile_capture_change('note');

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'crm_activities') THEN
        EXECUTE 'DROP TRIGGER IF EXISTS trg_activities_mobile_change ON crm_activities';
        EXECUTE 'CREATE TRIGGER trg_activities_mobile_change AFTER INSERT OR UPDATE OR DELETE ON crm_activities FOR EACH ROW EXECUTE FUNCTION fn_mobile_capture_change(''activity'')';
    END IF;
END $$;
