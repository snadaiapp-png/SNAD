# G1 #1462 Criterion 4 — source-level migration / owner / SECURITY DEFINER / ACL audit

**Audit date:** 2026-11-13  
**Classification:** PARTIAL SOURCE REVIEW / NOT APPROVED  
**Repo:** `snadaiapp-png/SNAD`  
**Relevant test target:** `286f905e39fb67723bdc79f46187b8d1671edc3a`  
**Review branch:** independent of production. **No live PostgreSQL query, Flyway execution, production SQL, or new test run occurred in this audit.**

## Observed facts

1. **Separated role and owner model exists in source.** `apps/sanad-platform/src/main/resources/db/migration/V20261103_2__migrator_ownership_and_runtime_acl.sql` lines 103-125 require `sanad_migrator` control of schema `public`. Lines 145-176 attempt ownership transfer. Lines 190-207 revoke existing runtime grants and role membership; lines 260-273 assert neither role has SUPERUSER/BYPASSRLS/CREATEDB/CREATEROLE and `sanad` is not a member of `sanad_migrator`. Lines 369-388 validate non-extension routine and schema ownership. These are **source controls, not proofs that production catalog matches**.

2. **Runtime ACL policy requires detailed role and object validation.** The same migration grants per-object restricted CRUD to refresh/password reset tokens, login tenant index, provisioning and append-only subscriptions (lines 215-223), but also grants broad CRUD on *other* `public` tables (lines 225-236), `EXECUTE ON ALL FUNCTIONS` to `sanad` (line 243), and default migrator EXECUTE grants to `sanad` (lines 248-254). This broad surface is **not categorically vulnerable**, but a full independent C4 review must verify the **actual effective ACLs and SECURITY DEFINER call paths**, and explain why each runtime execution privilege is needed. Negative tests of untrusted identities and role memberships are required.

3. **SECURITY DEFINER search_path risk candidate (C4-F01 / REVIEW REQUIRED).** Repeatable Flyway file `apps/sanad-platform/src/main/resources/db/migration/R__finalize_hr_backfill_closure.sql` declares several HR `SECURITY DEFINER` helpers with `SET search_path = public` at lines 17-21, 36-40, 60-64, 271-275, 302-306 and other definitions further down. Calls include unqualified HR tables and routines. The file explicitly says it re-applies on checksum change (lines 3-5); therefore a hardening approach must consider **repeatable redefinition**, not solely a one-time ALTER of the functions. `REVOKE ALL ... FROM PUBLIC` statements appear for listed HR routines (e.g. lines 286-289, 970-971), but effective EXECUTE grants, user-controlled CREATE rights, SECURITY DEFINER owners and objects resolved at runtime still require catalog verification. `search_path=public` alone does not establish exploitability.

4. **Earlier hardening exists for another function set (C4-F02 / VALIDATION REQUIRED).** Versioned migration `V20261026_4__rls_forensic_function_search_path_and_acl_hardening.sql` alters 16 functions to `pg_catalog, public, pg_temp` (lines 132-162), revokes `EXECUTE` on `public.sync_login_tenant_index()` from PUBLIC and optional roles anon/authenticated (lines 167-178) and validates aspects of the result (lines 216-265). These controls do **not** by themselves demonstrate every later HR SECURITY DEFINER helper uses a safe search_path; effective catalog state after the complete 260-migration chain is necessary.

5. **Other privileged operations and scoped workers.** `V20261111_1__service_callback_replay_p0_rls_closure.sql` documents a dedicated NOLOGIN callback cleanup role, non-inheriting runtime membership and bounded expired-row access. The design must be checked in actual `pg_roles`, role membership, policies, GRANTs and authenticated transaction-level SET ROLE behavior. Documentation is not execution proof.

## Required next checks (read-only; run only against an authorized environment)

Use a PostgreSQL 17 environment that completed the exact target migration chain and repeatable migrations. Preserve raw `psql` output, SQLSTATEs, role identity, Flyway history and source SHA. At minimum:

```sql
-- Identity and role restrictions
SELECT rolname, rolcanlogin, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole
  FROM pg_roles
 WHERE rolname IN ('sanad','sanad_migrator','sanad_callback_replay_cleanup');
SELECT pg_has_role('sanad','sanad_migrator','MEMBER') AS runtime_is_migrator_member;

-- All public SECURITY DEFINER routines, owners, exact search_path, ACL
SELECT p.oid::regprocedure AS signature, pg_get_userbyid(p.proowner) AS owner,
       p.prosecdef, p.proconfig, p.proacl
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef
 ORDER BY 1;

-- effective schema privileges and owner
SELECT n.nspname, pg_get_userbyid(n.nspowner) AS owner,
       has_schema_privilege('sanad', n.oid, 'CREATE') AS runtime_create,
       has_schema_privilege('sanad_migrator', n.oid, 'CREATE') AS migrator_create
  FROM pg_namespace n WHERE n.nspname='public';

-- effective runtime grants (review all returned objects, not just a sample)
SELECT table_schema, table_name, privilege_type, grantee
  FROM information_schema.role_table_grants
 WHERE table_schema='public' AND grantee IN ('sanad','PUBLIC','anon','authenticated')
 ORDER BY table_name, grantee, privilege_type;

-- explicit PUBLIC function privileges, including default ACL
SELECT p.oid::regprocedure AS signature, a.privilege_type
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid=p.pronamespace
  CROSS JOIN LATERAL aclexplode(coalesce(p.proacl, acldefault('f',p.proowner))) a
 WHERE n.nspname='public' AND p.prosecdef
   AND a.grantee=0 AND a.privilege_type='EXECUTE'
 ORDER BY 1;

-- ownership cross-check (avoid suppressing extension-owned exceptions)
SELECT c.oid::regclass, c.relkind, pg_get_userbyid(c.relowner) AS owner
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S')
 ORDER BY 1;
```

### Dynamic negative probes required for full C4
- Confirm a low-privilege or `sanad` session cannot CREATE in schema `public`; test a controlled alternate-schema shadow routine only in an isolated sandbox if risk analysis justifies it; never on live production.
- Check every `SECURITY DEFINER` function's owner and effective `EXECUTE` by runtime and untrusted roles, including potentially unsafe unqualified references; verify that no path bypasses tenant control.
- Confirm migration history/owners and FORCED RLS on the protected set after repeatable migration application; validate schema/function default privileges and role membership on complete target SHA.
- Ensure no privileged helper becomes directly invokable after default `GRANT EXECUTE ON ALL FUNCTIONS`; verify ACLs for `login_tenant_index`, token resolver and worker cleanup roles.
- Decide whether to harden HR functions to an explicitly controlled search_path (e.g. `pg_catalog, public` with qualified objects and runtime negative tests) and ensure the repeatable migration itself retains that hardening.

## Disposition

`C4_SOURCE_REVIEW = PARTIAL`  
`C4_SECURITY_DEFINER_SEARCH_PATH = REVIEW_REQUIRED`  
`C4_POSTGRES_LIVE_CATALOG = UNVERIFIED`  
`C4_NEGATIVE_PROBES = NOT_EXECUTED`  
`C4_APPROVAL = HOLD`

No production SQL, G1 certificate, issue closure or payments authorization. `P0_SECURITY_HOLD=ACTIVE`, `G1=HOLD`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF`.
