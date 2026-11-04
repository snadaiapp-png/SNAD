-- G1 F05: READ-ONLY bidirectional reconciliation of the pre-auth lookup index.
-- Never prints identifier or tenant data. Does not repair/remove rows.
\set ON_ERROR_STOP on
WITH expected AS (
  SELECT tenant_id, 'email'::text AS kind, email::text AS identifier
  FROM public.users WHERE email IS NOT NULL
  UNION
  SELECT tenant_id, 'username'::text, username::text
  FROM public.users WHERE username IS NOT NULL AND username <> email
), actual AS (
  SELECT tenant_id, kind, identifier
  FROM public.login_tenant_index
), missing AS (
  SELECT * FROM expected EXCEPT SELECT * FROM actual
), stale AS (
  SELECT * FROM actual EXCEPT SELECT * FROM expected
), duplicate_index AS (
  SELECT tenant_id, kind, identifier FROM public.login_tenant_index
  GROUP BY tenant_id, kind, identifier HAVING count(*) > 1
)
SELECT 'G1_F05_RECONCILE' AS audit,
  (SELECT count(*) FROM expected) AS expected_mappings,
  (SELECT count(*) FROM actual) AS indexed_mappings,
  (SELECT count(*) FROM missing) AS missing_mappings,
  (SELECT count(*) FROM stale) AS stale_mappings,
  (SELECT count(*) FROM duplicate_index) AS duplicate_mappings;
DO $audit$
DECLARE missing_count bigint; stale_count bigint; dup_count bigint;
BEGIN
 WITH expected AS (
   SELECT tenant_id, 'email'::text AS kind, email::text AS identifier
   FROM public.users WHERE email IS NOT NULL
   UNION
   SELECT tenant_id, 'username'::text, username::text
   FROM public.users WHERE username IS NOT NULL AND username <> email
 ), actual AS (
   SELECT tenant_id,kind,identifier FROM public.login_tenant_index
 )
 SELECT (SELECT count(*) FROM (SELECT * FROM expected EXCEPT SELECT * FROM actual) x),
        (SELECT count(*) FROM (SELECT * FROM actual EXCEPT SELECT * FROM expected) x),
        (SELECT count(*) FROM (
          SELECT tenant_id,kind,identifier FROM public.login_tenant_index
          GROUP BY 1,2,3 HAVING count(*)>1
        ) x)
 INTO missing_count,stale_count,dup_count;
 IF missing_count<>0 OR stale_count<>0 OR dup_count<>0 THEN
   RAISE EXCEPTION 'G1_F05_NOT_RECONCILED missing=% stale=% duplicates=%',
      missing_count,stale_count,dup_count;
 END IF;
 RAISE NOTICE 'G1_F05_RECONCILED=PASS';
END $audit$;
