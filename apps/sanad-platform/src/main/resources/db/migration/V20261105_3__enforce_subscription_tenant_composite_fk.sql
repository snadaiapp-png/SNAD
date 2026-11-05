-- G1 cross-tenant referential integrity closure.
-- Forward-only migration. Do not edit already-applied Flyway versions.
-- Fails closed if data contains mismatched subscription/tenant references.
-- Requires unique (tenant_id,id) on tenant_subscriptions.
-- Existing single-column FKs and ON DELETE CASCADE semantics are retained.
-- Flyway must execute transactionally as the authorized schema owner/migrator.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $pre$
BEGIN
  -- PostgreSQL's ADD FOREIGN KEY checks the referenced key's uniqueness.
  -- Do not treat the presence of a particular index name as proof.
  IF to_regclass('public.tenant_subscriptions') IS NULL THEN
    RAISE EXCEPTION 'G1_COMPOSITE_FK_MISSING_REFERENCED_TABLE';
  END IF;
  IF EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE (conrelid='public.provisioning_jobs'::regclass AND conname='fk_prov_jobs_tenant_subscription')
       OR (conrelid='public.subscription_commands'::regclass AND conname='fk_sub_commands_tenant_subscription')
  ) THEN
    RAISE EXCEPTION 'G1_COMPOSITE_FK_ALREADY_EXISTS_CHECK_MIGRATION_HISTORY';
  END IF;
END
$pre$;

ALTER TABLE public.provisioning_jobs
  ADD CONSTRAINT fk_prov_jobs_tenant_subscription
  FOREIGN KEY (tenant_id, subscription_id)
  REFERENCES public.tenant_subscriptions (tenant_id, id)
  ON DELETE CASCADE NOT VALID;

ALTER TABLE public.subscription_commands
  ADD CONSTRAINT fk_sub_commands_tenant_subscription
  FOREIGN KEY (tenant_id, subscription_id)
  REFERENCES public.tenant_subscriptions (tenant_id, id)
  ON DELETE CASCADE NOT VALID;

-- VALIDATE is mandatory. Existing mismatches must abort migration.
ALTER TABLE public.provisioning_jobs
  VALIDATE CONSTRAINT fk_prov_jobs_tenant_subscription;
ALTER TABLE public.subscription_commands
  VALIDATE CONSTRAINT fk_sub_commands_tenant_subscription;

DO $post$
BEGIN
  IF (SELECT count(*) FROM pg_constraint
      WHERE conname IN ('fk_prov_jobs_tenant_subscription','fk_sub_commands_tenant_subscription')
        AND conrelid IN ('public.provisioning_jobs'::regclass,'public.subscription_commands'::regclass)
        AND contype='f' AND convalidated) <> 2 THEN
    RAISE EXCEPTION 'G1_COMPOSITE_FK_VALIDATION_INCOMPLETE';
  END IF;
END
$post$;
