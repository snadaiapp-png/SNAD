#!/usr/bin/env python3
"""Read-only, fail-closed RLS certification against the configured SANAD PostgreSQL."""
import json
import os
import sys
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlparse
from uuid import UUID

import psycopg

TABLES = ("crm_accounts", "hr_employees", "users")
OWNER = UUID("00000000-0000-0000-0000-000000000010")
CONTROL = UUID("00000000-0000-0000-0000-000000000001")
results = {"schema": "sanad.g1.postgresql-direct.rls.v1", "result": "FAIL",
           "time": datetime.now(timezone.utc).isoformat(), "checks": {}}

def check(name, ok):
    results["checks"][name] = "PASS" if ok else "FAIL"
    if not ok:
        raise AssertionError(name)

def main():
    raw = os.environ["G1_DATASOURCE_URL"]
    if not raw.startswith("jdbc:postgresql://"):
        raise ValueError("Expected jdbc:postgresql:// datasource")
    parsed = urlparse(raw.removeprefix("jdbc:"))
    if parsed.scheme != "postgresql" or not parsed.hostname:
        raise ValueError("Invalid PostgreSQL URL")
    host = parsed.hostname.lower()
    project_ref = "tkbrvupemreqabwzdpyq"
    check("supabase_endpoint", host == "db." + project_ref + ".supabase.co"
          or host.endswith(".pooler.supabase.com"))
    user = os.environ["G1_DATASOURCE_USERNAME"]
    if host.endswith(".pooler.supabase.com"):
        # Supavisor login names are qualified; PostgreSQL current_user is not.
        check("least_privilege_secret_user", user == "sanad." + project_ref)
    else:
        check("least_privilege_secret_user", user == "sanad")
    tenant_a = UUID(os.environ["AUTH_SMOKE_TENANT_A_ID"])
    tenant_b = UUID(os.environ["AUTH_SMOKE_TENANT_B_ID"])
    check("distinct_tenants", tenant_a != tenant_b and tenant_a != CONTROL and tenant_b != CONTROL)
    opts = parse_qs(parsed.query)
    sslmode = opts.get("sslmode", ["require"])[0]
    check("ssl_required", sslmode in ("require", "verify-ca", "verify-full"))
    connect = dict(host=parsed.hostname, port=parsed.port or 5432,
                   dbname=parsed.path.lstrip("/"), user=user,
                   password=os.environ["G1_DATASOURCE_PASSWORD"],
                   sslmode=sslmode, connect_timeout=12)
    # Sanitized evidence only: never emit URL, password, or tenant IDs.
    results["connection"] = {
        "host": host, "database": parsed.path.lstrip("/"),
        "configured_login": user, "sslmode": sslmode,
    }
    with psycopg.connect(**connect) as conn:
        with conn.transaction():
            conn.execute("SET TRANSACTION READ ONLY")
            current_user, bypass, superuser = conn.execute(
                "SELECT current_user, r.rolbypassrls, r.rolsuper FROM pg_roles r WHERE r.rolname=current_user"
            ).fetchone()
            results["connection"]["effective_current_user"] = current_user
            check("runtime_role_is_sanad", current_user == "sanad")
            check("runtime_cannot_bypass_rls", not bypass and not superuser)
            results["catalog"] = {}
            for tenant, label in ((tenant_a, "A"), (tenant_b, "B"), (CONTROL, "CONTROL")):
                tenant_count = conn.execute(
                    "SELECT count(*) FROM public.tenants WHERE id=%s AND status='ACTIVE'",
                    (tenant,)).fetchone()[0]
                check("tenant_" + label + "_exists_active", tenant_count == 1)
            for table in TABLES:
                relation = conn.execute(
                    "SELECT c.relrowsecurity, c.relforcerowsecurity, pg_get_userbyid(c.relowner), c.relkind "
                    "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                    "WHERE n.nspname='public' AND c.relname=%s AND c.relkind IN ('r','p')",
                    (table,)).fetchone()
                if relation is None:
                    results["catalog"][table] = {"exists": False}
                    check(table + "_exists_in_public", False)
                    continue
                enabled, forced, owner_name, relkind = relation
                results["catalog"][table] = {
                    "exists": True, "rls_enabled": bool(enabled),
                    "force_rls": bool(forced), "owner": owner_name, "relkind": relkind,
                }
                check(table + "_rls_enabled", enabled)
                # Table-owner exemption is checked by role + ownership rather than FORCE alone.
                is_owner = conn.execute(
                    "SELECT pg_get_userbyid(c.relowner)=current_user FROM pg_class c "
                    "JOIN pg_namespace n ON n.oid=c.relnamespace "
                    "WHERE n.nspname='public' AND c.relname=%s", (table,)).fetchone()[0]
                check(table + "_rls_applies_to_runtime", forced or not is_owner)
                for tenant, label in ((tenant_a, "A"), (tenant_b, "B")):
                    conn.execute("SELECT set_config('app.tenant_id', %s, true)", (str(tenant),))
                    # Identifiers are immutable constants, never supplied by user input.
                    visible, other = conn.execute(
                        f"SELECT count(*), count(*) FILTER (WHERE tenant_id <> %s) FROM public.{table}",
                        (tenant,)).fetchone()
                    check(table + "_tenant_" + label + "_no_cross_rows", other == 0)
                    results["checks"][table + "_tenant_" + label + "_rows_examined"] = int(visible)
            # Canonical owner invariant is read-only; no password, token or personal record is exported.
            conn.execute("SELECT set_config('app.tenant_id', %s, true)", (str(CONTROL),))
            owner = conn.execute(
                "SELECT count(*) FROM public.users WHERE id=%s AND tenant_id=%s AND status='ACTIVE'",
                (OWNER, CONTROL)).fetchone()[0]
            check("canonical_owner_active_in_control_tenant", owner == 1)
    results["result"] = "PASS"

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("::error::G1 FAIL: " + str(exc).split("\n")[0][:280], file=sys.stderr)
        sys.exit_code = 1
    finally:
        with open("g1-postgres-rls-evidence.json", "w", encoding="utf-8") as output:
            json.dump(results, output, indent=2, sort_keys=True)
        print("G1_CERTIFICATION=" + results["result"])
    sys.exit(getattr(sys, "exit_code", 0))
