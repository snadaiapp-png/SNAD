SELECT current_database(),current_user;
SET ROLE sanad;
SELECT current_user AS actor,
 (SELECT count(*) FROM public.flyway_schema_history) AS visible_migration_records,
 has_table_privilege(current_user,'public.flyway_schema_history','TRUNCATE') AS can_truncate_flyway,
 has_table_privilege(current_user,'public.login_tenant_index','TRUNCATE') AS can_truncate_login_index,
 has_table_privilege(current_user,'public.service_callback_replay','TRUNCATE') AS can_truncate_callback_replay,
 has_table_privilege(current_user,'public.workflow_external_action_token_index','TRUNCATE') AS can_truncate_workflow_index;
RESET ROLE;
