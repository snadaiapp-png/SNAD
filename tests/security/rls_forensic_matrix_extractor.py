#!/usr/bin/env python3
"""
SNAD RLS Forensic Matrix — extracts the live catalog state from an isolated
PostgreSQL fixture (read-only against the fixture, zero production access).
Outputs: JSON evidence + human-readable summary.
"""
import json, sys, os
import psycopg

OUT = os.environ.get("SNAD_MATRIX_OUT", "target/rls-evidence")
os.makedirs(OUT, exist_ok=True)
DB = sys.argv[1] if len(sys.argv) > 1 else "sanad"
conn = psycopg.connect(f"host={os.environ.get('SNAD_TEST_HOST','127.0.0.1')} port={os.environ.get('SNAD_TEST_PORT','5432')} dbname={DB} user={os.environ.get('SNAD_TEST_APP_USER','sanad')} password={os.environ['SNAD_TEST_APP_PASSWORD']}")
cur = conn.cursor()

result = {"database": DB, "captured_at": None}

# 1) Tables inventory with RLS/FORCE/owner
cur.execute("""
SELECT c.relname, pg_get_userbyid(c.relowner),
       c.relrowsecurity, c.relforcerowsecurity, c.relkind
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r' ORDER BY c.relname""")
tables = [{"name": r[0], "owner": r[1], "rls": r[2], "force_rls": r[3]} for r in cur.fetchall()]
result["tables"] = tables
result["counts"] = {
    "tables_total": len(tables),
    "tables_without_rls": sum(1 for t in tables if not t["rls"]),
    "tables_rls_not_forced": sum(1 for t in tables if t["rls"] and not t["force_rls"]),
    "tables_rls_and_forced": sum(1 for t in tables if t["rls"] and t["force_rls"]),
}

# 2) Grants on tables for roles of interest
cur.execute("""
SELECT grantee, COUNT(DISTINCT c.relname), string_agg(DISTINCT privilege_type, ',' ORDER BY privilege_type)
FROM information_schema.role_table_grants g
JOIN pg_class c ON c.relname=g.table_name
JOIN pg_namespace n ON n.oid=c.relnamespace AND n.nspname=g.table_schema
WHERE g.table_schema='public' AND c.relkind='r'
  AND grantee IN ('PUBLIC','anon','authenticated','service_role','sanad')
GROUP BY grantee ORDER BY grantee""")
result["grants_by_role"] = [{"grantee": r[0], "tables": r[1], "privileges": r[2]} for r in cur.fetchall()]

# per-table grants for external roles on RLS-less tables specifically
no_rls = [t["name"] for t in tables if not t["rls"]]
cur.execute("""
SELECT DISTINCT g.table_name, g.grantee, string_agg(DISTINCT g.privilege_type, ',' ORDER BY g.privilege_type)
FROM information_schema.role_table_grants g
WHERE g.table_schema='public' AND g.table_name = ANY(%s)
  AND g.grantee IN ('PUBLIC','anon','authenticated','service_role')
GROUP BY g.table_name, g.grantee ORDER BY 1,2""", (no_rls,))
result["no_rls_tables_external_grants"] = [{"table": r[0], "grantee": r[1], "privileges": r[2]} for r in cur.fetchall()]

# 3) Policies: full detail
cur.execute("""
SELECT n.nspname, c.relname, pol.polname, pol.polpermissive,
       array_to_string(array(SELECT rolname FROM pg_roles WHERE oid = ANY(pol.polroles)), ','),
       pol.polcmd,
       pg_get_expr(pol.polqual, pol.polrelid), pg_get_expr(pol.polwithcheck, pol.polrelid)
FROM pg_policy pol
JOIN pg_class c ON c.oid = pol.polrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname='public' ORDER BY c.relname, pol.polname""")
policies = [{"table": r[1], "name": r[2], "permissive": r[3], "roles": [x.strip() for x in r[4].split(',')],
             "cmd": r[5], "using": r[6], "with_check": r[7]} for r in cur.fetchall()]
result["policies"] = policies
result["counts"]["policies_total"] = len(policies)

# 4) Fail-open pattern detection: NULL-allow tenant context
failopen = []
for p in policies:
    expr = ((p["using"] or "") + " || " + (p["with_check"] or ""))
    if "app.tenant_id" in expr and ("IS NULL" in expr or "ISNULL" in expr):
        failopen.append(p)
result["fail_open_candidates"] = [
    {"table": p["table"], "name": p["name"], "cmd": p["cmd"], "roles": p["roles"],
     "using": p["using"], "with_check": p["with_check"]} for p in failopen]
result["counts"]["fail_open_candidates"] = len(failopen)

# policies referencing app.tenant_id at all (total tenant-aware)
result["counts"]["policies_with_tenant_guc"] = sum(1 for p in policies if "app.tenant_id" in ((p["using"] or "")+(p["with_check"] or "")))

# 5) Functions: security definer, search_path, EXECUTE grants
cur.execute("""
SELECT p.proname, pg_get_userbyid(p.proowner), p.prosecdef,
       p.proconfig, p.prokind
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prokind='f' ORDER BY p.proname""")
funcs = []
for r in cur.fetchall():
    sp = None
    if r[3]:
        for kv in r[3]:
            if kv.startswith("search_path="):
                sp = kv.split("=",1)[1]
    funcs.append({"name": r[0], "owner": r[1], "security_definer": r[2], "search_path": sp})
result["functions_total"] = len(funcs)
result["functions_security_definer"] = [f for f in funcs if f["security_definer"]]
result["functions_unpinned_search_path"] = [
    {"name": f["name"], "owner": f["owner"], "security_definer": f["security_definer"]}
    for f in funcs if not f["search_path"]]

# EXECUTE grants to PUBLIC/anon/authenticated on functions
cur.execute("""
SELECT p.proname, g.grantee
FROM information_schema.routine_privileges g
JOIN pg_proc p ON p.proname=g.routine_name
JOIN pg_namespace n ON n.oid=p.pronamespace AND n.nspname=g.specific_schema
WHERE g.specific_schema='public' AND g.grantee IN ('PUBLIC','anon','authenticated')
ORDER BY p.proname, g.grantee""")
result["function_execute_public_anon_auth"] = [{"function": r[0], "grantee": r[1]} for r in cur.fetchall()]

# 6) Roles with BYPASSRLS or SUPERUSER (non-system)
cur.execute("SELECT rolname, rolsuper, rolbypassrls, rolcanlogin FROM pg_roles WHERE rolname NOT LIKE 'pg_%' ORDER BY 1")
result["roles"] = [{"name": r[0], "superuser": r[1], "bypassrls": r[2], "canlogin": r[3]} for r in cur.fetchall()]

# 7) Schema-level privileges on public (direct ACL inspection)
cur.execute("""
SELECT COALESCE(r.rolname,'PUBLIC') AS grantee, a.privilege_type
FROM pg_namespace n, aclexplode(COALESCE(n.nspacl, acldefault('n', n.nspowner))) a
LEFT JOIN pg_roles r ON r.oid = a.grantee
WHERE n.nspname='public' ORDER BY 1,2""")
schema_privs = [{"grantee": r[0], "privilege": r[1]} for r in cur.fetchall()]
result["schema_public_privileges"] = schema_privs

# 8) Views (indirect access paths)
cur.execute("""
SELECT c.relname, pg_get_userbyid(c.relowner)
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('v','m') ORDER BY c.relname""")
result["views"] = [{"name": r[0], "owner": r[1]} for r in cur.fetchall()]

# 9) Flyway history summary
cur.execute("SELECT COUNT(*), SUM(CASE WHEN success THEN 1 ELSE 0 END) FROM flyway_schema_history")
r = cur.fetchone()
result["flyway"] = {"migrations_recorded": r[0], "successful": r[1]}

cur.execute("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5")
result["flyway_last_applied"] = [{"version": r[0], "description": r[1], "success": r[2]} for r in cur.fetchall()]

# 10) sync_login_tenant_index specific state
cur.execute("""
SELECT p.proname, p.prosecdef,
       EXISTS (SELECT 1 FROM information_schema.routine_privileges g WHERE g.routine_name='sync_login_tenant_index' AND g.specific_schema='public' AND g.grantee='anon') AS anon_exec,
       EXISTS (SELECT 1 FROM information_schema.routine_privileges g WHERE g.routine_name='sync_login_tenant_index' AND g.specific_schema='public' AND g.grantee='authenticated') AS auth_exec,
       EXISTS (SELECT 1 FROM information_schema.routine_privileges g WHERE g.routine_name='sync_login_tenant_index' AND g.specific_schema='public' AND g.grantee='service_role') AS svc_exec
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname='sync_login_tenant_index'""")
r = cur.fetchone()
result["sync_login_tenant_index"] = {"security_definer": r[1], "anon_execute": bool(r[2]), "authenticated_execute": bool(r[3]), "service_role_execute": bool(r[4])} if r else None

# 11) Views reading tenant tables (indirect paths) — definition text scan
cur.execute("""
SELECT c.relname, pg_get_viewdef(c.oid, true)
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='v' ORDER BY 1""")
result["view_definitions"] = [{"name": r[0], "definition": r[1]} for r in cur.fetchall()]

cur.execute("SELECT now()")
result["captured_at"] = cur.fetchone()[0].isoformat()

conn.rollback(); conn.close()

with open(f"{OUT}/forensic-matrix-{DB}.json", "w") as f:
    json.dump(result, f, indent=1, default=str)

# Summary print
c = result["counts"]
print(f"=== FORENSIC MATRIX — DB {DB} @ {result['captured_at']} ===")
print(f"tables={c['tables_total']} | no-RLS={c['tables_without_rls']} | RLS-not-forced={c['tables_rls_not_forced']} | RLS+FORCE={c['tables_rls_and_forced']}")
print(f"policies={c['policies_total']} | fail-open candidates={c['fail_open_candidates']} | tenant-GUC policies={c['policies_with_tenant_guc']}")
print(f"functions={result['functions_total']} | security-definer={len(result['functions_security_definer'])} | unpinned-search_path={len(result['functions_unpinned_search_path'])}")
print(f"execute-grants to PUBLIC/anon/auth: {len(result['function_execute_public_anon_auth'])}")
print(f"no-RLS tables with external grants: {len(result['no_rls_tables_external_grants'])}")
print(f"flyway: {result['flyway']}")
sync = result.get("sync_login_tenant_index")
print(f"sync_login_tenant_index: {sync}")
byp = [r for r in result["roles"] if r["bypassrls"] or r["superuser"]]
print(f"roles with superuser/bypassrls: {[r['name'] for r in byp]}")
print(f"schema public grants: {result['schema_public_privileges']}")
print(f"views: {len(result['views'])}")
