#!/usr/bin/env python3
"""G1 closure — stage the pre-auth tenant index (V20261011_4 DDL + backfill).

Applies the ALREADY-REVIEWED migration V20261011_4 (pre-auth tenant resolver:
login_tenant_index + sync trigger + g1_resolve_login_tenants) to the production
database BEFORE the RLS-enabling deploy, then backfills the index from
public.users. Staging the index first means the deployed backend's login and
password-reset flows work from the first request after RLS is enabled by
Flyway — no authentication outage window.

Admin-channel selection (fail-closed, in priority order):
  1. SUPABASE_PAT against the Supabase Management API database/query endpoint
     (platform-sanctioned admin channel; no DB password handling).
  2. The (PRODUCTION_DATABASE_USERNAME, PRODUCTION_DATABASE_PASSWORD) pairing
     against the as-configured PRODUCTION_DATABASE_URL host. The pairing is
     only accepted when it authenticates as the OWNER of public.users
     (current_user = relowner), with rolsuper=false and rolbypassrls=false.

Hard safety rules:
  - Secret values are never printed, logged, or written to evidence.
  - The DDL executed is byte-identical to the reviewed migration file
    (sha256 pinned) read from the checked-out repository.
  - Evidence contains counts, channel names, verdicts, and username PREFIXES
    (role part only) — never identifiers or credentials.
  - If no admin channel authenticates, the script exits non-zero and stages
    nothing (fail closed).
"""
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from urllib.parse import urlparse

import psycopg

PROJECT_REF = "tkbrvupemreqabwzdpyq"
MIGRATION_PATH = ("apps/sanad-platform/src/main/resources/db/migration/"
                  "V20261011_4__pre_auth_tenant_resolver.sql")
MIGRATION_SHA256 = "441a6e60c05a6e699dfed0bad6c4c15b433b13db64de723cf935667a177160f6"

BACKFILL_SQL = """
INSERT INTO public.login_tenant_index (kind, identifier, tenant_id)
SELECT 'email', u.email, u.tenant_id
  FROM public.users u
 WHERE u.email IS NOT NULL
ON CONFLICT (kind, identifier, tenant_id) DO NOTHING;

INSERT INTO public.login_tenant_index (kind, identifier, tenant_id)
SELECT 'username', u.username, u.tenant_id
  FROM public.users u
 WHERE u.username IS NOT NULL AND u.username <> u.email
ON CONFLICT (kind, identifier, tenant_id) DO NOTHING;
"""

results = {
    "schema": "sanad.g1.closure.stage-index.v1",
    "time": datetime.now(timezone.utc).isoformat(),
    "project_ref": PROJECT_REF,
    "channel_selected": None,
    "migration_sha256_verified": False,
    "applied": False,
    "backfilled": False,
    "counts": {},
    "checks": {},
}


def check(name, ok):
    results["checks"][name] = "PASS" if ok else "FAIL"
    if not ok:
        raise AssertionError(name)


def load_migration_sql():
    here = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(os.path.dirname(here))
    path = os.path.join(repo_root, MIGRATION_PATH)
    with open(path, "rb") as handle:
        raw = handle.read()
    digest = hashlib.sha256(raw).hexdigest()
    check("migration_sha256_pinned", digest == MIGRATION_SHA256)
    results["migration_sha256_verified"] = True
    return raw.decode("utf-8")


def role_prefix(username):
    """Supavisor login names are role.project_ref — evidence shows role part only."""
    return str(username).split(".")[0]


def mgmt_api_channel(migration_sql, backfill_sql):
    token = os.environ.get("SUPABASE_PAT")
    if not token:
        return False
    base = f"https://api.supabase.com/v1/projects/{PROJECT_REF}"

    def call(sql):
        body = json.dumps({"query": sql}).encode("utf-8")
        request = urllib.request.Request(
            base + "/database/query", data=body, method="POST",
            headers={"Authorization": "Bearer " + token,
                     "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                response.read()
                return response.status
        except urllib.error.HTTPError as exc:
            exc.read()
            return exc.code

    status = call("SELECT 1")
    results["checks"]["supabase_pat_probe"] = status
    if not 200 <= status < 300:
        return False
    status = call(migration_sql)
    check("mgmt_api_apply_status_2xx", 200 <= status < 300)
    status = call(backfill_sql)
    check("mgmt_api_backfill_status_2xx", 200 <= status < 300)
    results["channel_selected"] = "supabase_management_api"
    return True


def owner_pair_channel(migration_sql, backfill_sql):
    raw = os.environ.get("PRODUCTION_DATABASE_URL", "")
    user = os.environ.get("PRODUCTION_DATABASE_USERNAME", "")
    password = os.environ.get("PRODUCTION_DATABASE_PASSWORD", "")
    if not raw or not user or not password:
        return False
    parsed = urlparse(raw.removeprefix("jdbc:"))
    if not parsed.hostname or not parsed.path.lstrip("/"):
        results["checks"]["owner_pair_url_parseable"] = "FAIL"
        return False
    opts = urllib.parse.parse_qs(parsed.query)
    sslmode = opts.get("sslmode", ["require"])[0]
    connect = dict(host=parsed.hostname, port=parsed.port or 5432,
                   dbname=parsed.path.lstrip("/"), user=user, password=password,
                   sslmode=sslmode, connect_timeout=12)
    try:
        with psycopg.connect(**connect) as conn:
            with conn.transaction():
                conn.execute("SET TRANSACTION READ ONLY")
                current_user, superuser, bypass = conn.execute(
                    "SELECT current_user, "
                    "(SELECT rolsuper FROM pg_roles WHERE rolname=current_user), "
                    "(SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user)"
                ).fetchone()
            results["checks"]["owner_pair_role_prefix"] = role_prefix(user)
            results["checks"]["owner_pair_rolsuper"] = bool(superuser)
            results["checks"]["owner_pair_rolbypassrls"] = bool(bypass)
            owner = conn.execute(
                "SELECT pg_get_userbyid(c.relowner) FROM pg_class c "
                "JOIN pg_namespace n ON n.oid=c.relnamespace "
                "WHERE n.nspname='public' AND c.relname='users'"
            ).fetchone()[0]
            results["checks"]["users_owner_role"] = owner
            check("pair_is_table_owner", current_user == owner)
            check("pair_not_superuser", not superuser)
            check("pair_not_bypassrls", not bypass)
            if current_user != "postgres" or superuser or bypass:
                return False
            with conn.transaction():
                conn.execute(migration_sql)
            results["applied"] = True
            with conn.transaction():
                conn.execute(backfill_sql)
            results["backfilled"] = True
            results["channel_selected"] = "production_owner_pair"
            return True
    except psycopg.OperationalError as exc:
        text = str(exc).split("\n")[0].lower()
        for needle in ("password authentication failed", "tenant/user",
                       "role does not exist", "ssl", "timeout", "unreachable",
                       "resolved", "refused", "network"):
            if needle in text:
                results["checks"]["owner_pair_auth"] = needle.replace(" ", "_")
                return False
        results["checks"]["owner_pair_auth"] = "op_error_redacted"
        return False


def verify_counts(conn):
    users_total = conn.execute("SELECT count(*) FROM public.users").fetchone()[0]
    users_email = conn.execute(
        "SELECT count(*) FROM public.users WHERE email IS NOT NULL").fetchone()[0]
    users_username = conn.execute(
        "SELECT count(*) FROM public.users WHERE username IS NOT NULL "
        "AND username <> email").fetchone()[0]
    idx_email = conn.execute(
        "SELECT count(*) FROM public.login_tenant_index WHERE kind='email'").fetchone()[0]
    idx_username = conn.execute(
        "SELECT count(*) FROM public.login_tenant_index WHERE kind='username'").fetchone()[0]
    results["counts"] = {
        "users_total": int(users_total),
        "users_with_email": int(users_email),
        "users_with_username": int(users_username),
        "index_email_rows": int(idx_email),
        "index_username_rows": int(idx_username),
    }
    check("index_email_coverage", idx_email == users_email)
    check("index_username_coverage", idx_username == users_username)
    function_ok = conn.execute(
        "SELECT to_regprocedure('public.g1_resolve_login_tenants(text,text)')"
        " IS NOT NULL").fetchone()[0]
    check("resolver_function_present", bool(function_ok))
    execute_grants = conn.execute(
        "SELECT count(*) FROM information_schema.role_function_grants "
        "WHERE specific_schema='public' AND routine_name='g1_resolve_login_tenants' "
        "AND grantee='sanad' AND privilege_type='EXECUTE'").fetchone()[0]
    check("resolver_execute_granted_to_sanad", execute_grants >= 1)


def main():
    migration_sql = load_migration_sql()
    backfill_sql = BACKFILL_SQL
    if mgmt_api_channel(migration_sql, backfill_sql):
        pass
    else:
        if not owner_pair_channel(migration_sql, backfill_sql):
            raise AssertionError("NO_ADMIN_CHANNEL: neither SUPABASE_PAT nor the "
                                 "production owner pairing authenticated")

    raw = os.environ.get("PRODUCTION_DATABASE_URL", "")
    if results["channel_selected"] == "production_owner_pair":
        parsed = urlparse(raw.removeprefix("jdbc:"))
        with psycopg.connect(host=parsed.hostname, port=parsed.port or 5432,
                             dbname=parsed.path.lstrip("/"),
                             user=os.environ["PRODUCTION_DATABASE_USERNAME"],
                             password=os.environ["PRODUCTION_DATABASE_PASSWORD"],
                             sslmode="require", connect_timeout=12) as conn:
            with conn.transaction():
                conn.execute("SET TRANSACTION READ ONLY")
                verify_counts(conn)
    else:
        # Verification through the least-privilege G1 identity (read-only).
        with psycopg.connect(host="aws-0-eu-central-1.pooler.supabase.com",
                             port=5432, dbname="postgres",
                             user="sanad." + PROJECT_REF,
                             password=os.environ["G1_DATASOURCE_PASSWORD"],
                             sslmode="require", connect_timeout=12) as conn:
            with conn.transaction():
                conn.execute("SET TRANSACTION READ ONLY")
                verify_counts(conn)

    results["result"] = "PASS"


if __name__ == "__main__":
    exit_code = 0
    try:
        main()
    except Exception as exc:
        results["result"] = "FAIL"
        results["error"] = str(exc).split("\n")[0][:280]
        print("::error::G1 stage-index FAIL: " + results["error"], file=sys.stderr)
        exit_code = 1
    finally:
        with open("g1-stage-index-evidence.json", "w", encoding="utf-8") as handle:
            json.dump(results, handle, indent=2, sort_keys=True)
        print("G1_STAGE_INDEX=" + results["result"])
    sys.exit(exit_code)
