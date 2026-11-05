-- G1: Correct tenant-transfer mapping cleanup in pre-auth login index.
-- Never edit V20261011_4 after Flyway has applied it.
-- This changes the function body only; existing ownership, EXECUTE ACLs,
-- trigger registration, and pre-auth lookup behavior are preserved.
-- Pre-auth index is intentionally globally searchable by the backend;
-- do not blindly add tenant RLS to this table.
CREATE OR REPLACE FUNCTION public.sync_login_tenant_index()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO pg_catalog, public, pg_temp
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM public.login_tenant_index
         WHERE tenant_id = OLD.tenant_id
           AND ((kind = 'email' AND identifier = OLD.email)
             OR (kind = 'username' AND identifier = OLD.username));
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        -- OLD.tenant_id is essential when moving a user to another tenant.
        DELETE FROM public.login_tenant_index
         WHERE tenant_id = OLD.tenant_id
           AND ((kind = 'email' AND identifier = OLD.email)
             OR (kind = 'username' AND identifier = OLD.username));
    END IF;

    INSERT INTO public.login_tenant_index(kind, identifier, tenant_id)
    VALUES ('email', NEW.email, NEW.tenant_id)
    ON CONFLICT (kind, identifier, tenant_id) DO NOTHING;

    IF NEW.username IS NOT NULL AND NEW.username <> NEW.email THEN
        INSERT INTO public.login_tenant_index(kind, identifier, tenant_id)
        VALUES ('username', NEW.username, NEW.tenant_id)
        ON CONFLICT (kind, identifier, tenant_id) DO NOTHING;
    END IF;

    RETURN NEW;
END;
$function$;
