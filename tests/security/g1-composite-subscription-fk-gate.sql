-- G1 regression gate: subscription child references must match tenant.
-- Read-only. Requires the forward-only NO ACTION composite FK migration to be applied.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
DO $g1$
DECLARE
  v_child regclass;
  v_name text;
  v_referenced regclass := to_regclass('public.tenant_subscriptions');
  v_child_attnums smallint[];
  v_parent_attnums smallint[];
BEGIN
  IF v_referenced IS NULL THEN
    RAISE EXCEPTION 'G1_COMPOSITE_FK_PARENT_MISSING';
  END IF;
  FOR v_child, v_name IN
    SELECT 'public.provisioning_jobs'::regclass, 'fk_prov_jobs_tenant_subscription'
    UNION ALL
    SELECT 'public.subscription_commands'::regclass, 'fk_sub_commands_tenant_subscription'
  LOOP
    SELECT array_agg(attnum ORDER BY ord) INTO v_child_attnums
    FROM unnest(ARRAY['tenant_id','subscription_id']) WITH ORDINALITY AS c(name,ord)
    JOIN pg_attribute a ON a.attrelid=v_child AND a.attname=c.name AND a.attnum>0;
    SELECT array_agg(attnum ORDER BY ord) INTO v_parent_attnums
    FROM unnest(ARRAY['tenant_id','id']) WITH ORDINALITY AS c(name,ord)
    JOIN pg_attribute a ON a.attrelid=v_referenced AND a.attname=c.name AND a.attnum>0;
    IF coalesce(array_length(v_child_attnums,1),0)<>2
       OR coalesce(array_length(v_parent_attnums,1),0)<>2
       OR NOT EXISTS (
        SELECT 1 FROM pg_constraint k
        WHERE k.conrelid=v_child AND k.confrelid=v_referenced
          AND k.conname=v_name AND k.contype='f' AND k.convalidated
          AND k.conkey=v_child_attnums AND k.confkey=v_parent_attnums
          AND k.confdeltype='a'
       ) THEN
      RAISE EXCEPTION 'G1_COMPOSITE_FK_INVALID table=% expected=%',v_child,v_name;
    END IF;
    IF NOT EXISTS (
      SELECT 1 FROM pg_constraint k
      WHERE k.conrelid=v_child AND k.confrelid=v_referenced
        AND k.conname=CASE WHEN v_name='fk_prov_jobs_tenant_subscription'
                           THEN 'fk_prov_jobs_subscription'
                           ELSE 'fk_sub_commands_subscription' END
        AND k.contype='f' AND k.convalidated AND k.confdeltype='a'
        AND k.conkey=ARRAY[v_child_attnums[2]]::smallint[]
        AND k.confkey=ARRAY[v_parent_attnums[2]]::smallint[]
    ) THEN
      RAISE EXCEPTION 'G1_LEGACY_FK_MUST_BE_NO_ACTION table=%',v_child;
    END IF;
    RAISE NOTICE 'PASS: G1_COMPOSITE_AND_LEGACY_FKS_NO_ACTION table=%',v_child;
  END LOOP;
END
$g1$;
ROLLBACK;
SELECT 'G1_COMPOSITE_FK_CATALOG_GATE_COMPLETED' AS result;
