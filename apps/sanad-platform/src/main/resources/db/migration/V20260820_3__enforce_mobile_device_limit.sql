-- G7 / ISO-006 — Maximum registered devices per user
-- Policy: at most 5 ACTIVE devices for one (tenant_id, user_id).
-- Enforcement is database-side so every registration path is covered.
-- The transaction advisory lock serializes concurrent registrations for the
-- same tenant/user and prevents two requests from both observing a free slot.

CREATE OR REPLACE FUNCTION enforce_mobile_active_device_limit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    active_device_count integer;
    identity_key text;
BEGIN
    IF NEW.status <> 'ACTIVE' THEN
        RETURN NEW;
    END IF;

    -- Re-check only when a row enters ACTIVE or changes ownership identity.
    IF TG_OP = 'UPDATE'
       AND OLD.status = 'ACTIVE'
       AND OLD.tenant_id = NEW.tenant_id
       AND OLD.user_id = NEW.user_id THEN
        RETURN NEW;
    END IF;

    identity_key := NEW.tenant_id::text || ':' || NEW.user_id::text;
    PERFORM pg_advisory_xact_lock(hashtextextended(identity_key, 0));

    SELECT count(*)
      INTO active_device_count
      FROM mobile_device_registry
     WHERE tenant_id = NEW.tenant_id
       AND user_id = NEW.user_id
       AND status = 'ACTIVE'
       AND device_id <> NEW.device_id;

    IF active_device_count >= 5 THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = 'MOBILE_DEVICE_LIMIT_EXCEEDED',
            DETAIL = 'A user may have at most 5 ACTIVE mobile devices per tenant.';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_mobile_active_device_limit ON mobile_device_registry;
CREATE TRIGGER trg_mobile_active_device_limit
BEFORE INSERT OR UPDATE OF status, tenant_id, user_id
ON mobile_device_registry
FOR EACH ROW
EXECUTE FUNCTION enforce_mobile_active_device_limit();

COMMENT ON FUNCTION enforce_mobile_active_device_limit() IS
'G7 ISO-006: enforces a maximum of five ACTIVE mobile devices per tenant/user and serializes concurrent registrations.';
