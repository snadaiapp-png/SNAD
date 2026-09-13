-- SNAD production hardening: Supabase Data API is not an application data plane.
-- Architecture: Browser -> Vercel BFF -> Spring Boot -> PostgreSQL.
-- Therefore public-schema access must be deny-by-default for PUBLIC / anon / authenticated.
--
-- This migration is portable:
-- - Supabase roles (anon/authenticated/service_role) may not exist in local PostgreSQL.
-- - Extension-owned functions are excluded from application RPC hardening.
-- - Existing SECURITY DEFINER application functions remain owner/admin-only.

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;

DO $$
DECLARE
    api_role TEXT;
    fn RECORD;
BEGIN
    -- Current Supabase Data API roles: remove direct table/sequence access when present.
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM %I',
                api_role
            );
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM %I',
                api_role
            );
        END IF;
    END LOOP;

    -- Application-owned functions only (exclude extension members).
    FOR fn IN
        SELECT
            p.oid::regprocedure AS signature,
            p.prosecdef AS security_definer
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND NOT EXISTS (
              SELECT 1
              FROM pg_depend d
              WHERE d.classid = 'pg_proc'::regclass
                AND d.objid = p.oid
                AND d.deptype = 'e'
          )
    LOOP
        EXECUTE format(
            'REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC',
            fn.signature
        );

        FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated']
        LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
                EXECUTE format(
                    'REVOKE EXECUTE ON FUNCTION %s FROM %I',
                    fn.signature,
                    api_role
                );
            END IF;
        END LOOP;

        -- SECURITY DEFINER migration/backfill functions are never a backend RPC surface.
        IF fn.security_definer
           AND CURRENT_USER <> 'sanad'
           AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad') THEN
            EXECUTE format(
                'REVOKE EXECUTE ON FUNCTION %s FROM sanad',
                fn.signature
            );
        END IF;
    END LOOP;
END
$$;

-- Future objects created by the Flyway owner are fail-closed by default.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

DO $$
DECLARE
    api_role TEXT;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I',
                api_role
            );
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I',
                api_role
            );
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM %I',
                api_role
            );
        END IF;
    END LOOP;
END
$$;
