-- F06: the pre-auth workflow token pointer is read by the runtime,
-- but maintained by an owner SECURITY DEFINER trigger on the authoritative
-- FORCE-RLS workflow_external_actions table. Deny direct runtime mutations.
-- This must run only after separated ownership is established.
DO $g1_workflow_pointer_acl$
DECLARE
  pointer_owner text;
  trigger_owner text;
  trigger_definer boolean;
BEGIN
  IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
    SELECT pg_get_userbyid(relowner) INTO pointer_owner
      FROM pg_class WHERE oid='public.workflow_external_action_token_index'::regclass;
    SELECT pg_get_userbyid(proowner), prosecdef INTO trigger_owner, trigger_definer
      FROM pg_proc WHERE oid='public.sync_workflow_external_action_token_index()'::regprocedure;
    IF pointer_owner IS DISTINCT FROM current_user
       OR trigger_owner IS DISTINCT FROM current_user
       OR trigger_definer IS DISTINCT FROM true THEN
      RAISE EXCEPTION 'G1_F06_WORKFLOW_POINTER_OWNER_DEFINER_CONTRACT';
    END IF;
    REVOKE INSERT, UPDATE, DELETE ON public.workflow_external_action_token_index FROM sanad;
    IF NOT has_table_privilege('sanad','public.workflow_external_action_token_index','SELECT')
       OR has_table_privilege('sanad','public.workflow_external_action_token_index','INSERT')
       OR has_table_privilege('sanad','public.workflow_external_action_token_index','UPDATE')
       OR has_table_privilege('sanad','public.workflow_external_action_token_index','DELETE') THEN
      RAISE EXCEPTION 'G1_F06_WORKFLOW_POINTER_RUNTIME_ACL_FAILED';
    END IF;
  END IF;
END $g1_workflow_pointer_acl$;
