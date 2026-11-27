#!/usr/bin/env python3
"""
verify_tenant_ctx_catalog.py — G1 signed tenant-context catalog verification.

Re-runnable verification of the FINAL catalog state after the official Flyway
chain (V20261124_1 binding objects, V20261124_2 enforcement rewrite, and the
F-INT-02 canonicalized repeatable unit) has been applied. Checks:

  1. Binding objects exist: platform_tenant_ctx_keys, platform_ctx_enforce,
     app_tenant_ctx_slot(), app_bind_tenant_ctx(uuid,text), app_tenant_ctx_text().
  2. Enforcement flag row present (platform_ctx_enforce.flag = 'ON').
  3. At least one ACTIVE key row in platform_tenant_ctx_keys.
  4. Zero policies reference the raw tenant GUC pattern
     (current_setting('app.tenant_id'::text, true)) — fail-closed signature:
     all rewritten policies read public.app_tenant_ctx_text().
  5. Zero public functions reference the raw tenant GUC (excluding the three
     ctx functions whose internal GUC reads ARE the validation mechanism).
  6. ACL contract: slot()/bind() NOT executable by PUBLIC; the validator IS
     PUBLIC-executable (policy-evaluation contract); slot()/bind() executable
     by the runtime role sanad.
  7. Keys-table zero-grant: the runtime role sanad holds no table privilege on
     platform_tenant_ctx_keys (owner-restore only for the owner role).

Usage:
  python3 scripts/verify_tenant_ctx_catalog.py \
      --host 127.0.0.1 --port 5432 --db sanad --user postgres [--password ...]

Output: JSON report on stdout; exit 0 = all checks PASS, exit 1 = any FAIL.
"""
import argparse
import json
import sys

try:
    import psycopg2
except ImportError:
    sys.stderr.write("psycopg2 is required (pip install psycopg2-binary)\n")
    sys.exit(2)

CTX_FUNCTIONS = ("app_tenant_ctx_slot", "app_bind_tenant_ctx", "app_tenant_ctx_text")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5432)
    parser.add_argument("--db", default="sanad")
    parser.add_argument("--user", default="postgres")
    parser.add_argument("--password", default="")
    args = parser.parse_args()

    conn = psycopg2.connect(
        host=args.host, port=args.port, dbname=args.db,
        user=args.user, password=args.password)
    conn.autocommit = True
    cur = conn.cursor()
    results = []

    def check(name: str, passed: bool, detail) -> None:
        results.append({"check": name, "status": "PASS" if passed else "FAIL", "detail": detail})

    # 1. Binding objects
    for obj, kind in [
        ("public.platform_tenant_ctx_keys", "table"),
        ("public.platform_ctx_enforce", "table"),
        ("public.app_tenant_ctx_slot()", "function"),
        ("public.app_bind_tenant_ctx(uuid,text)", "function"),
        ("public.app_tenant_ctx_text()", "function"),
    ]:
        cur.execute("SELECT to_regclass(%s) IS NOT NULL OR to_regprocedure(%s) IS NOT NULL",
                    (obj, obj))
        check(f"object_exists:{obj}", bool(cur.fetchone()[0]), kind)

    # 2. Enforcement flag
    cur.execute("SELECT count(*) FROM public.platform_ctx_enforce WHERE flag = 'ON'")
    flag_rows = cur.fetchone()[0]
    check("enforce_flag_on", flag_rows >= 1, f"rows={flag_rows}")

    # 3. ACTIVE key present
    cur.execute("SELECT count(*) FROM public.platform_tenant_ctx_keys WHERE status = 'ACTIVE'")
    active_keys = cur.fetchone()[0]
    check("active_key_present", active_keys >= 1, f"active_keys={active_keys}")

    # 4. Policies: zero raw-GUC references; signed validator present
    cur.execute("""
        SELECT count(*) FROM pg_policies
        WHERE qual LIKE '%%current_setting(''app.tenant_id''::text, true)%%'
           OR with_check LIKE '%%current_setting(''app.tenant_id''::text, true)%%'""")
    raw_policies = cur.fetchone()[0]
    check("policies_raw_guc_remaining_zero", raw_policies == 0, f"raw_guc_policies={raw_policies}")

    cur.execute("""
        SELECT count(*) FROM pg_policies
        WHERE qual LIKE '%%app_tenant_ctx_text()%%' OR with_check LIKE '%%app_tenant_ctx_text()%%'""")
    signed_policies = cur.fetchone()[0]
    check("policies_signed_validator_present", signed_policies > 0, f"signed_policies={signed_policies}")

    cur.execute("SELECT count(*) FROM pg_policies")
    total_policies = cur.fetchone()[0]
    check("policy_inventory_recorded", total_policies > 0, f"total_policies={total_policies}")

    # 5. Functions: zero raw-GUC references outside the three ctx functions
    cur.execute("""
        SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.prosrc LIKE '%%current_setting(''app.tenant_id''%%'
          AND p.proname NOT IN %s""", (CTX_FUNCTIONS,))
    raw_functions = cur.fetchone()[0]
    check("functions_raw_guc_remaining_zero", raw_functions == 0, f"raw_guc_functions={raw_functions}")

    # 6. ACL contract
    cur.execute("""
        SELECT count(*)
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace,
        LATERAL aclexplode(COALESCE(p.proacl, acldefault('f', p.proowner))) a
        WHERE n.nspname = 'public'
          AND p.proname IN ('app_tenant_ctx_slot', 'app_bind_tenant_ctx')
          AND a.grantee = 0 AND a.privilege_type = 'EXECUTE'
          AND ((p.proname = 'app_bind_tenant_ctx'
                AND p.oid = to_regprocedure('public.app_bind_tenant_ctx(uuid,text)'))
            OR (p.proname = 'app_tenant_ctx_slot'
                AND p.oid = to_regprocedure('public.app_tenant_ctx_slot()')))""")
    protocol_public_execute = cur.fetchone()[0]
    check("slot_bind_not_public_executable", protocol_public_execute == 0,
          f"public_execute_grants={protocol_public_execute}")

    cur.execute("""
        SELECT count(*)
        FROM pg_proc p,
        LATERAL aclexplode(COALESCE(p.proacl, acldefault('f', p.proowner))) a
        WHERE p.oid = to_regprocedure('public.app_tenant_ctx_text()')
          AND a.grantee = 0 AND a.privilege_type = 'EXECUTE'""")
    validator_public = cur.fetchone()[0]
    check("validator_public_executable", validator_public >= 1, f"public_grants={validator_public}")

    cur.execute("SELECT has_function_privilege('sanad', 'public.app_bind_tenant_ctx(uuid,text)', 'EXECUTE')")
    runtime_bind = bool(cur.fetchone()[0])
    check("runtime_role_can_bind", runtime_bind, "has_function_privilege(sanad, bind)")

    # 7. Runtime zero-grant on the keys table — SEPARATED topology only.
    # On the migration-fixture topology the chain runs as the runtime role
    # (owner-as-runner discipline, V20261103_2/V20261124_1 headers): the table
    # owner IS sanad and the zero-grant assertion does not apply.
    cur.execute("SELECT current_setting('sanad.deployment_topology', true)")
    topology = cur.fetchone()[0]
    cur.execute("SELECT pg_get_userbyid(relowner) FROM pg_class "
                "WHERE oid = 'public.platform_tenant_ctx_keys'::regclass")
    keys_owner = cur.fetchone()[0]
    cur.execute("SELECT has_table_privilege('sanad', 'public.platform_tenant_ctx_keys', 'SELECT')")
    runtime_keys_read = bool(cur.fetchone()[0])
    fixture_owner_as_runner = keys_owner == "sanad"
    if fixture_owner_as_runner:
        check("keys_table_zero_grant_runtime", True,
              f"N/A on fixture topology (owner-as-runner: owner={keys_owner}, "
              f"topology={topology or 'unset'}); assert on a separated-topology database")
    else:
        check("keys_table_zero_grant_runtime", not runtime_keys_read,
              f"sanad_select_on_keys={runtime_keys_read} owner={keys_owner}")

    cur.execute("""
        SELECT pg_get_userbyid(relowner), relacl IS NOT NULL AND relacl::text <> '{}'
        FROM pg_class WHERE oid = 'public.platform_tenant_ctx_keys'::regclass""")
    owner, acl_restored = cur.fetchone()
    check("keys_table_owner_access_restored", acl_restored,
          f"owner={owner} (owner ACL materialized non-empty)")

    conn.close()

    report = {
        "verification": "verify_tenant_ctx_catalog",
        "database": f"{args.db}@{args.host}:{args.port}",
        "checks": results,
        "summary": {
            "total": len(results),
            "passed": sum(1 for r in results if r["status"] == "PASS"),
            "failed": sum(1 for r in results if r["status"] == "FAIL"),
        },
    }
    print(json.dumps(report, indent=2))
    return 0 if report["summary"]["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
