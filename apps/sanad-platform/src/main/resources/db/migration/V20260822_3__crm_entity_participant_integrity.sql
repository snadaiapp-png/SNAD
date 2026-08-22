CREATE OR REPLACE FUNCTION crm_validate_entity_participant_reference() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE entity_exists boolean;
BEGIN
    CASE NEW.entity_type
        WHEN 'CONTACT' THEN PERFORM 1 FROM public.crm_contacts WHERE tenant_id = NEW.tenant_id AND id = NEW.entity_id FOR KEY SHARE; IF NOT FOUND THEN RAISE EXCEPTION 'CRM_PARTICIPANT_ENTITY_NOT_FOUND: entity_type=%, entity_id=%, tenant_id=%', NEW.entity_type, NEW.entity_id, NEW.tenant_id USING ERRCODE = 'foreign_key_violation'; END IF;
        WHEN 'TASK' THEN PERFORM 1 FROM public.crm_tasks WHERE tenant_id = NEW.tenant_id AND id = NEW.entity_id FOR KEY SHARE; IF NOT FOUND THEN RAISE EXCEPTION 'CRM_PARTICIPANT_ENTITY_NOT_FOUND: entity_type=%, entity_id=%, tenant_id=%', NEW.entity_type, NEW.entity_id, NEW.tenant_id USING ERRCODE = 'foreign_key_violation'; END IF;
        WHEN 'CASE' THEN PERFORM 1 FROM public.crm_cases WHERE tenant_id = NEW.tenant_id AND id = NEW.entity_id FOR KEY SHARE; IF NOT FOUND THEN RAISE EXCEPTION 'CRM_PARTICIPANT_ENTITY_NOT_FOUND: entity_type=%, entity_id=%, tenant_id=%', NEW.entity_type, NEW.entity_id, NEW.tenant_id USING ERRCODE = 'foreign_key_violation'; END IF;
        ELSE RAISE EXCEPTION 'CRM_PARTICIPANT_INVALID_ENTITY_TYPE: %', NEW.entity_type USING ERRCODE = 'check_violation';
    END CASE;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_crm_entity_participant_integrity BEFORE INSERT OR UPDATE OF tenant_id, entity_type, entity_id ON crm_entity_participants FOR EACH ROW EXECUTE FUNCTION crm_validate_entity_participant_reference();
CREATE OR REPLACE FUNCTION crm_guard_contact_delete_with_participants() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE guc_value text; has_participants boolean;
BEGIN guc_value := current_setting('app.tenant_id', true); IF guc_value IS NULL OR guc_value::uuid != OLD.tenant_id THEN RAISE EXCEPTION 'CRM_DELETE_GUARD_TENANT_CONTEXT_REQUIRED: crm_contacts id=% tenant_id=%', OLD.id, OLD.tenant_id USING ERRCODE = 'check_violation'; END IF;
SELECT EXISTS (SELECT 1 FROM crm_entity_participants WHERE tenant_id = OLD.tenant_id AND entity_type = 'CONTACT' AND entity_id = OLD.id) INTO has_participants;
IF has_participants THEN RAISE EXCEPTION 'CRM_DELETE_GUARD_PARTICIPANT_HISTORY_EXISTS: crm_contacts id=% tenant_id=%', OLD.id, OLD.tenant_id USING ERRCODE = 'foreign_key_violation'; END IF;
RETURN OLD; END; $$;
CREATE TRIGGER trg_crm_contacts_delete_guard BEFORE DELETE ON crm_contacts FOR EACH ROW EXECUTE FUNCTION crm_guard_contact_delete_with_participants();
CREATE OR REPLACE FUNCTION crm_guard_task_delete_with_participants() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE guc_value text; has_participants boolean;
BEGIN guc_value := current_setting('app.tenant_id', true); IF guc_value IS NULL OR guc_value::uuid != OLD.tenant_id THEN RAISE EXCEPTION 'CRM_DELETE_GUARD_TENANT_CONTEXT_REQUIRED: crm_tasks id=% tenant_id=%', OLD.id, OLD.tenant_id USING ERRCODE = 'check_violation'; END IF;
SELECT EXISTS (SELECT 1 FROM crm_entity_participants WHERE tenant_id = OLD.tenant_id AND entity_type = 'TASK' AND entity_id = OLD.id) INTO has_participants;
IF has_participants THEN RAISE EXCEPTION 'CRM_DELETE_GUARD_PARTICIPANT_HISTORY_EXISTS: crm_tasks id=% tenant_id=%', OLD.id, OLD.tenant_id USING ERRCODE = 'foreign_key_violation'; END IF;
RETURN OLD; END; $$;
CREATE TRIGGER trg_crm_tasks_delete_guard BEFORE DELETE ON crm_tasks FOR EACH ROW EXECUTE FUNCTION crm_guard_task_delete_with_participants();
CREATE OR REPLACE FUNCTION crm_guard_case_delete_with_participants() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE guc_value text; has_participants boolean;
BEGIN guc_value := current_setting('app.tenant_id', true); IF guc_value IS NULL OR guc_value::uuid != OLD.tenant_id THEN RAISE EXCEPTION 'CRM_DELETE_GUARD_TENANT_CONTEXT_REQUIRED: crm_cases id=% tenant_id=%', OLD.id, OLD.tenant_id USING ERRCODE = 'check_violation'; END IF;
SELECT EXISTS (SELECT 1 FROM crm_entity_participants WHERE tenant_id = OLD.tenant_id AND entity_type = 'CASE' AND entity_id = OLD.id) INTO has_participants;
IF has_participants THEN RAISE EXCEPTION 'CRM_DELETE_GUARD_PARTICIPANT_HISTORY_EXISTS: crm_cases id=% tenant_id=%', OLD.id, OLD.tenant_id USING ERRCODE = 'foreign_key_violation'; END IF;
RETURN OLD; END; $$;
CREATE TRIGGER trg_crm_cases_delete_guard BEFORE DELETE ON crm_cases FOR EACH ROW EXECUTE FUNCTION crm_guard_case_delete_with_participants();
