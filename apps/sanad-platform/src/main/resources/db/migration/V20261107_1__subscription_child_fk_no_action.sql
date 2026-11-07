-- G1 approved target: preserve subscription operational history on parent deletion.
-- Forward-only; never edit the already-applied V20261105_3 migration.
-- PostgreSQL transactional Flyway migration; execute only after independent
-- retention-policy, dependent-FK, and backup/restore approvals.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $pre$
DECLARE
  v_name text;
  v_child regclass;
BEGIN
  IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
    IF current_user <> 'sanad_migrator' THEN
      RAISE EXCEPTION 'G1_NO_ACTION_REQUIRES_SEPARATED_MIGRATOR';
    END IF;
  ELSIF current_setting('sanad.deployment_topology', true) IS NULL
     OR current_setting('sanad.deployment_topology', true) = 'migration-fixture' THEN
    RAISE WARNING 'G1_NO_ACTION_FIXTURE_MODE_NOT_SECURITY_CERTIFIED';
  ELSE
    RAISE EXCEPTION 'G1_NO_ACTION_UNKNOWN_TOPOLOGY';
  END IF;
  FOR v_child, v_name IN
    SELECT 'public.provisioning_jobs'::regclass, 'fk_prov_jobs_tenant_subscription'
    UNION ALL
    SELECT 'public.subscription_commands'::regclass, 'fk_sub_commands_tenant_subscription'
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conrelid=v_child AND conname=v_name AND contype='f'
        AND convalidated AND confdeltype='c'
        AND confrelid='public.tenant_subscriptions'::regclass
    ) THEN
      RAISE EXCEPTION 'G1_NO_ACTION_UNEXPECTED_EXISTING_FK: %', v_name;
    END IF;
  END LOOP;
  IF (SELECT count(*) FROM pg_constraint
      WHERE (conrelid='public.provisioning_jobs'::regclass AND conname='fk_prov_jobs_subscription'
             OR conrelid='public.subscription_commands'::regclass AND conname='fk_sub_commands_subscription')
        AND contype='f' AND convalidated AND confdeltype='c'
        AND confrelid='public.tenant_subscriptions'::regclass) <> 2 THEN
    RAISE EXCEPTION 'G1_NO_ACTION_UNEXPECTED_LEGACY_FK';
  END IF;
END
$pre$;

-- Both legacy single-column CASCADE FKs must also be replaced;
-- otherwise a parent deletion could still cascade despite the composite NO ACTION FKs.
ALTER TABLE public.provisioning_jobs
  DROP CONSTRAINT fk_prov_jobs_subscription;
ALTER TABLE public.provisioning_jobs
  ADD CONSTRAINT fk_prov_jobs_subscription
  FOREIGN KEY (subscription_id)
  REFERENCES public.tenant_subscriptions (id)
  ON DELETE NO ACTION NOT VALID;

ALTER TABLE public.subscription_commands
  DROP CONSTRAINT fk_sub_commands_subscription;
ALTER TABLE public.subscription_commands
  ADD CONSTRAINT fk_sub_commands_subscription
  FOREIGN KEY (subscription_id)
  REFERENCES public.tenant_subscriptions (id)
  ON DELETE NO ACTION NOT VALID;

ALTER TABLE public.provisioning_jobs
  DROP CONSTRAINT fk_prov_jobs_tenant_subscription;
ALTER TABLE public.provisioning_jobs
  ADD CONSTRAINT fk_prov_jobs_tenant_subscription
  FOREIGN KEY (tenant_id, subscription_id)
  REFERENCES public.tenant_subscriptions (tenant_id, id)
  ON DELETE NO ACTION NOT VALID;

ALTER TABLE public.subscription_commands
  DROP CONSTRAINT fk_sub_commands_tenant_subscription;
ALTER TABLE public.subscription_commands
  ADD CONSTRAINT fk_sub_commands_tenant_subscription
  FOREIGN KEY (tenant_id, subscription_id)
  REFERENCES public.tenant_subscriptions (tenant_id, id)
  ON DELETE NO ACTION NOT VALID;

ALTER TABLE public.provisioning_jobs
  VALIDATE CONSTRAINT fk_prov_jobs_subscription;
ALTER TABLE public.subscription_commands
  VALIDATE CONSTRAINT fk_sub_commands_subscription;
ALTER TABLE public.provisioning_jobs
  VALIDATE CONSTRAINT fk_prov_jobs_tenant_subscription;
ALTER TABLE public.subscription_commands
  VALIDATE CONSTRAINT fk_sub_commands_tenant_subscription;

DO $post$
BEGIN
  IF (SELECT count(*) FROM pg_constraint
      WHERE conname IN ('fk_prov_jobs_tenant_subscription','fk_sub_commands_tenant_subscription',
                       'fk_prov_jobs_subscription','fk_sub_commands_subscription')
        AND conrelid IN ('public.provisioning_jobs'::regclass,'public.subscription_commands'::regclass)
        AND confrelid='public.tenant_subscriptions'::regclass
        AND contype='f' AND convalidated AND confdeltype='a') <> 4 THEN
    RAISE EXCEPTION 'G1_NO_ACTION_POSTCONDITION_FAILED';
  END IF;
END
$post$;
