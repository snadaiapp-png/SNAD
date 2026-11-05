-- G1: prevent cross-tenant subscription references at the database boundary.
-- Forward-only; do not edit previously applied Flyway migrations.
-- Run via the approved migrator workflow, first on an isolated database.
-- No explicit BEGIN/COMMIT: Flyway must execute this as one PostgreSQL transaction.
SET lock_timeout = '5s';
SET statement_timeout = '90s';

DO $preflight$
DECLARE
  v_table text;
BEGIN
  -- Production topology requires the dedicated migrator. Historical migration
  -- fixture mode is supported for clean-chain tests, not G1 certification.
  IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
    IF current_user <> 'sanad_migrator' THEN
      RAISE EXCEPTION 'G1_COMPOSITE_FK_REQUIRES_SEPARATED_MIGRATOR';
    END IF;
  ELSIF current_setting('sanad.deployment_topology', true) IS NULL
     OR current_setting('sanad.deployment_topology', true) = 'migration-fixture' THEN
    RAISE WARNING 'G1_COMPOSITE_FK_FIXTURE_MODE_NOT_CERTIFIED';
  ELSE
    RAISE EXCEPTION 'G1_COMPOSITE_FK_UNKNOWN_TOPOLOGY';
  END IF;

  FOREACH v_table IN ARRAY ARRAY['provisioning_jobs', 'subscription_commands'] LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_class c
      WHERE c.oid = format('public.%I', v_table)::regclass
        AND c.relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
        AND c.relrowsecurity AND c.relforcerowsecurity
    ) THEN
      RAISE EXCEPTION 'G1_COMPOSITE_FK_OWNER_OR_RLS_CONTRACT: %', v_table;
    END IF;
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = v_table
          AND column_name IN ('tenant_id', 'subscription_id')
          AND is_nullable = 'NO') <> 2 THEN
      RAISE EXCEPTION 'G1_COMPOSITE_FK_NULLABLE_COLUMN: %', v_table;
    END IF;
  END LOOP;

  IF EXISTS (
    SELECT 1 FROM public.provisioning_jobs j
    LEFT JOIN public.tenant_subscriptions s
      ON s.id = j.subscription_id AND s.tenant_id = j.tenant_id
    WHERE s.id IS NULL
  ) THEN
    RAISE EXCEPTION 'G1_PROVISIONING_JOBS_MISMATCH_OR_ORPHAN';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.subscription_commands c
    LEFT JOIN public.tenant_subscriptions s
      ON s.id = c.subscription_id AND s.tenant_id = c.tenant_id
    WHERE s.id IS NULL
  ) THEN
    RAISE EXCEPTION 'G1_SUBSCRIPTION_COMMANDS_MISMATCH_OR_ORPHAN';
  END IF;

  IF EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE (conrelid = 'public.provisioning_jobs'::regclass
           AND conname = 'fk_prov_jobs_tenant_subscription')
       OR (conrelid = 'public.subscription_commands'::regclass
           AND conname = 'fk_sub_commands_tenant_subscription')
  ) THEN
    RAISE EXCEPTION 'G1_COMPOSITE_FK_ALREADY_EXISTS_REVIEW_REQUIRED';
  END IF;
END
$preflight$;

-- NO ACTION avoids introducing new cascading deletion of subscription
-- command history. Review existing single-column FK deletion behavior separately.
ALTER TABLE public.provisioning_jobs
  ADD CONSTRAINT fk_prov_jobs_tenant_subscription
  FOREIGN KEY (tenant_id, subscription_id)
  REFERENCES public.tenant_subscriptions (tenant_id, id)
  ON DELETE NO ACTION;

ALTER TABLE public.subscription_commands
  ADD CONSTRAINT fk_sub_commands_tenant_subscription
  FOREIGN KEY (tenant_id, subscription_id)
  REFERENCES public.tenant_subscriptions (tenant_id, id)
  ON DELETE NO ACTION;
