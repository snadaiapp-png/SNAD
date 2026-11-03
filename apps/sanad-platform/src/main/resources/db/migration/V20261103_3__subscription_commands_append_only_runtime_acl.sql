-- Issue #1462. Forward-only correction of production ACL drift.
-- Requires V20261103_1 and V20261103_2 to have been applied by Flyway.
-- No RLS policies, data, table ownership, or bypass privileges are changed.
DO $$
BEGIN
  IF to_regclass('public.subscription_commands') IS NULL
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls)
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_migrator')
  THEN RAISE EXCEPTION 'G1_1462_PRECONDITION: table/runtime/migrator missing'; END IF;
  IF (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.subscription_commands'::regclass) = 'sanad'
  THEN RAISE EXCEPTION 'G1_1462_PRECONDITION: runtime owns ledger; separation required'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid='public.subscription_commands'::regclass AND relrowsecurity AND relforcerowsecurity)
  THEN RAISE EXCEPTION 'G1_1462_PRECONDITION: ENABLE+FORCE RLS required'; END IF;
END $$;
REVOKE UPDATE, DELETE ON TABLE public.subscription_commands FROM sanad;
DO $$
BEGIN
  IF has_table_privilege('sanad','public.subscription_commands','UPDATE')
    OR has_table_privilege('sanad','public.subscription_commands','DELETE')
    OR NOT has_table_privilege('sanad','public.subscription_commands','SELECT')
    OR NOT has_table_privilege('sanad','public.subscription_commands','INSERT')
  THEN RAISE EXCEPTION 'G1_1462_POSTCONDITION: runtime ledger ACL not append-only'; END IF;
END $$;
