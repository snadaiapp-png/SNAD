-- F06 narrow privilege reduction for separated migrator/runtime topology.
-- CallbackReplayStore uses INSERT for nonce/JTI consumption and DELETE for
-- expiration cleanup; UPDATE is not required by that runtime path.
-- Forward-only migration: do not alter V20261103_2 or historical checksums.
DO $g1_callback_acl$
BEGIN
  IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
    IF pg_get_userbyid((SELECT relowner FROM pg_class
       WHERE oid='public.service_callback_replay'::regclass)) <> current_user THEN
      RAISE EXCEPTION 'G1_F06_MIGRATOR_NOT_TABLE_OWNER';
    END IF;
    REVOKE UPDATE ON TABLE public.service_callback_replay FROM sanad;
    IF has_table_privilege('sanad','public.service_callback_replay','UPDATE')
       OR NOT has_table_privilege('sanad','public.service_callback_replay','SELECT')
       OR NOT has_table_privilege('sanad','public.service_callback_replay','INSERT')
       OR NOT has_table_privilege('sanad','public.service_callback_replay','DELETE') THEN
      RAISE EXCEPTION 'G1_F06_CALLBACK_ACL_POSTCONDITION_FAILED';
    END IF;
  END IF;
END $g1_callback_acl$;
