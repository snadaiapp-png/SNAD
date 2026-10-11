#!/usr/bin/env python3
"""G1 sanad credential rotation — DBA-authorized, fail-closed, value-free evidence.

Authorization: SANAD Conditional DBA Authorization (APPROVED WITH CONDITIONS),
issued 2026-10-11 by the database owner, for role `sanad` in Supabase project
tkbrvupemreqabwzdpyq ONLY.

Hard safety rules (enforced by construction):
- Confirms the role exists with LOGIN, and WITHOUT rolsuper / rolbypassrls,
  BEFORE any change; aborts otherwise.
- Live-session impact probe over three samples; aborts if role `sanad` is in
  active use (no shared production dependency may be silently broken).
- Proves ALTER privilege with a dry-run ALTER inside an explicitly ROLLED
  BACK transaction before the real change; no privilege bypass is attempted.
- Rotates ONLY role `sanad` to the pre-staged G1_DATASOURCE_PASSWORD secret
  value; no other role, grant, RLS policy, or credential is touched.
- The new password is never printed, logged, or written to evidence: it is
  read from the GitHub secret store at runtime and masked immediately.
- Verifies the final end-to-end state by authenticating through the staged
  G1_DATASOURCE_URL as G1_DATASOURCE_USERNAME and asserting
  current_user='sanad', rolsuper=false, rolbypassrls=false.
"""
import json
import os
import sys
import time
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlparse

import psycopg
from psycopg import sql

PROJECT_REF = "tkbrvupemreqabwzdpyq"
DBNAME = "postgres"
ADMIN_USER = "postgres." + PROJECT_REF
SANAD_POOLER_USER = "sanad." + PROJECT_REF
POOLER_PORT = 5432
REGION_HOSTS = ["aws-0-eu-central-1.pooler.supabase.com",
                "aws-1-eu-central-1.pooler.supabase.com"]
CONNECT_TIMEOUT = 10
VERIFY_ATTEMPTS = 3
VERIFY_SLEEP_S = 10
IMPACT_SAMPLES = 3
IMPACT_SLEEP_S = 10

results = {
    "schema": "sanad.g1.sanad-rotation.v1",
    "time": datetime.now(timezone.utc).isoformat(),
    "project_ref": PROJECT_REF,
    "result": "FAIL",
    "checks": {},
}


def check(name, ok, detail=None):
    results["checks"][name] = "PASS" if ok else ("FAIL" if detail is None else "FAIL: " + detail)
    if not ok:
        raise RuntimeError(name + ("" if detail is None else ": " + detail))


class _DryRunRollback(Exception):
    """Control-flow signal: force the dry-run transaction to ROLL BACK."""


def redact(message: str) -> str:
    text = str(message).split("\n")[0].lower()
    if "password authentication failed" in text:
        return "password_authentication_failed"
    if "role does not exist" in text or "does not exist" in text and "role" in text:
        return "role_or_object_missing"
    for needle in ("timeout", "unreachable", "resolved", "connection refused",
                   "network", "ssl"):
        if needle in text:
            return "network_or_tls_unreachable"
    return "op_error_redacted"


def embedded_password(raw):
    """Extract a password from a (jdbc) URL value, or return the raw value."""
    if not raw:
        return None
    parsed = urlparse(raw.removeprefix("jdbc:"))
    if parsed.password:
        return parsed.password
    return raw


def admin_candidates():
    """Ordered {name: password} from the sanctioned store (values stay in RAM)."""
    found = {}
    direct = [("PRODUCTION_DATABASE_PASSWORD", os.environ.get("PRODUCTION_DATABASE_PASSWORD")),
              ("SPRING_DATASOURCE_PASSWORD", os.environ.get("SPRING_DATASOURCE_PASSWORD"))]
    for name, value in direct:
        if value:
            found[name] = value
    for name in ("PRODUCTION_DATABASE_URL", "PRODUCTION_DATABASE_JDBC_URL",
                 "SPRING_DATASOURCE_URL"):
        password = embedded_password(os.environ.get(name, ""))
        if password:
            found[name + "(embedded)"] = password
    return found


def configured_pooler_hosts():
    hosts = list(REGION_HOSTS)
    raw = os.environ.get("SPRING_DATASOURCE_URL", "")
    if raw:
        parsed = urlparse(raw.removeprefix("jdbc:"))
        if parsed.hostname and parsed.hostname.lower().endswith(".pooler.supabase.com") \
                and parsed.hostname.lower() not in [h.lower() for h in hosts]:
            hosts.insert(0, parsed.hostname)
    return hosts


def try_connect(host, user, password):
    return psycopg.connect(host=host, port=POOLER_PORT, dbname=DBNAME, user=user,
                           password=password, sslmode="require",
                           connect_timeout=CONNECT_TIMEOUT)


def discover_admin(candidates, hosts):
    """Find a sanctioned credential that authenticates the admin role. Name-only evidence."""
    attempts = []
    for name, password in candidates.items():
        for host in hosts:
            try:
                with try_connect(host, ADMIN_USER, password) as conn:
                    conn.autocommit = True
                    row = conn.execute(
                        "SELECT current_user, "
                        "(SELECT rolsuper FROM pg_roles WHERE rolname=current_user), "
                        "(SELECT rolcreaterole FROM pg_roles WHERE rolname=current_user), "
                        "(SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user)"
                    ).fetchone()
                attempts.append({"candidate": name, "host": host, "auth": "AUTH_OK"})
                return name, host, {"current_user": row[0], "rolsuper": bool(row[1]),
                                    "rolcreaterole": bool(row[2]), "rolbypassrls": bool(row[3])}, attempts
            except psycopg.OperationalError as exc:
                attempts.append({"candidate": name, "host": host,
                                 "auth": "AUTH_FAIL", "reason": redact(exc)})
            except Exception as exc:  # noqa: BLE001 - evidence stays value-free
                attempts.append({"candidate": name, "host": host,
                                 "auth": "ERROR", "reason": redact(exc)})
            time.sleep(1)
    return None, None, None, attempts


def role_attributes(conn, rolname):
    return conn.execute(
        "SELECT rolname, rolcanlogin, rolsuper, rolbypassrls, rolvaliduntil "
        "FROM pg_roles WHERE rolname=%s", (rolname,)).fetchone()


def impact_probe(conn):
    """Three-sample live-session probe plus ownership inventory (informational)."""
    samples = []
    for _ in range(IMPACT_SAMPLES):
        count = conn.execute(
            "SELECT count(*) FROM pg_stat_activity WHERE usename='sanad'").fetchone()[0]
        samples.append(int(count))
        if count:
            break
        if IMPACT_SLEEP_S:
            time.sleep(IMPACT_SLEEP_S)
    owned_tables = conn.execute(
        "SELECT count(*) FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner "
        "WHERE r.rolname='sanad' AND c.relkind IN ('r','p')").fetchone()[0]
    secdef_funcs = conn.execute(
        "SELECT count(*) FROM pg_proc p JOIN pg_roles r ON r.oid=p.proowner "
        "WHERE r.rolname='sanad' AND p.prosecdef").fetchone()[0]
    memberships = conn.execute(
        "SELECT count(*) FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member "
        "WHERE r.rolname='sanad'").fetchone()[0]
    cron_jobs = 0
    try:
        if conn.execute("SELECT to_regnamespace('cron') IS NOT NULL").fetchone()[0]:
            cron_jobs = conn.execute(
                "SELECT count(*) FROM cron.job WHERE username='sanad'").fetchone()[0]
    except Exception:  # noqa: BLE001 - cron extension may be absent; informational only
        cron_jobs = None
    return {"live_sanad_session_samples": samples, "owned_tables": int(owned_tables),
            "owned_security_definer_functions": int(secdef_funcs),
            "role_memberships": int(memberships), "cron_jobs": cron_jobs}


def alter_sanad_password(conn, new_password):
    """Utility statements cannot take bind parameters; compose a safely quoted
    literal client-side. The value is generated base64url (no quotes/backslashes)."""
    conn.execute(sql.SQL("ALTER ROLE sanad WITH PASSWORD {}")
                 .format(sql.Literal(new_password)))


def verify_via_staged_config(new_password):
    """End-to-end: authenticate through the staged G1_DATASOURCE_URL/USERNAME."""
    raw = os.environ.get("G1_DATASOURCE_URL", "")
    user = os.environ.get("G1_DATASOURCE_USERNAME", "")
    if not raw or not user:
        raise RuntimeError("STAGED_G1_CONFIG_MISSING")
    parsed = urlparse(raw.removeprefix("jdbc:"))
    if parsed.scheme != "postgresql" or not parsed.hostname:
        raise RuntimeError("STAGED_G1_URL_INVALID")
    if not parsed.hostname.lower().endswith(".pooler.supabase.com"):
        raise RuntimeError("STAGED_G1_URL_NOT_POOLER")
    if user != SANAD_POOLER_USER:
        raise RuntimeError("STAGED_G1_USERNAME_MISMATCH")
    sslmode = parse_qs(parsed.query).get("sslmode", ["require"])[0]
    if sslmode not in ("require", "verify-ca", "verify-full"):
        raise RuntimeError("STAGED_G1_URL_SSL_WEAK")
    last_error = None
    for attempt in range(1, VERIFY_ATTEMPTS + 1):
        try:
            with psycopg.connect(host=parsed.hostname, port=parsed.port or 5432,
                                 dbname=parsed.path.lstrip("/") or DBNAME, user=user,
                                 password=new_password, sslmode=sslmode,
                                 connect_timeout=CONNECT_TIMEOUT) as conn:
                conn.autocommit = True
                with conn.transaction():
                    conn.execute("SET TRANSACTION READ ONLY")
                    row = conn.execute(
                        "SELECT current_user, "
                        "(SELECT rolsuper FROM pg_roles WHERE rolname=current_user), "
                        "(SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user), "
                        "(SELECT rolvaliduntil FROM pg_roles WHERE rolname=current_user)"
                    ).fetchone()
            return {"attempt": attempt, "host": parsed.hostname,
                    "current_user": row[0], "rolsuper": bool(row[1]),
                    "rolbypassrls": bool(row[2]),
                    "rolvaliduntil": str(row[3]) if row[3] else None}
        except psycopg.OperationalError as exc:
            last_error = redact(exc)
            time.sleep(VERIFY_SLEEP_S)
    raise RuntimeError("POST_ROTATION_VERIFY_FAILED: " + str(last_error))


def main():
    confirmation = os.environ.get("G1_CONFIRMATION", "")
    check("confirmation_gate", confirmation == "ROTATE_SANAD_ROLE_ACCESS")

    new_password = os.environ.get("G1_DATASOURCE_PASSWORD", "")
    check("new_password_present", bool(new_password))

    candidates = admin_candidates()
    check("admin_candidates_present", bool(candidates))

    # The rotation target must not reuse any stored credential value.
    results["checks"]["new_password_unique_vs_candidates"] = (
        "PASS" if new_password not in candidates.values() else "FAIL")
    if new_password in candidates.values():
        raise RuntimeError("new_password_unique_vs_candidates")

    # --- 1. Admin discovery (read-only) ---
    hosts = configured_pooler_hosts()
    results["admin_discovery_attempts"] = []
    admin_name, admin_host, admin_attrs, attempts = discover_admin(candidates, hosts)
    results["admin_discovery_attempts"] = attempts
    check("admin_credential_found", admin_name is not None,
          "no sanctioned candidate authenticates " + ADMIN_USER)
    results["admin"] = {"candidate": admin_name, "host": admin_host,
                        "attributes": admin_attrs}
    print("ADMIN_CANDIDATE=" + admin_name + " HOST=" + admin_host
          + " ROLSUPER=" + str(admin_attrs["rolsuper"])
          + " ROLCREATEROLE=" + str(admin_attrs["rolcreaterole"])
          + " ROLBYPASSRLS=" + str(admin_attrs["rolbypassrls"]))

    # --- 2. Project identity + role verification (precondition 1) ---
    with try_connect(admin_host, ADMIN_USER, candidates[admin_name]) as conn:
        conn.autocommit = True
        known_table = conn.execute(
            "SELECT to_regclass('public.tenants') IS NOT NULL").fetchone()[0]
        check("sanad_project_identity", bool(known_table))
        role = role_attributes(conn, "sanad")
        check("sanad_role_exists", role is not None, "role sanad not found")
        results["sanad_role_before"] = {
            "rolcanlogin": bool(role[1]), "rolsuper": bool(role[2]),
            "rolbypassrls": bool(role[3]),
            "rolvaliduntil": str(role[4]) if role[4] else None}
        print("SANAD_ROLE_BEFORE login=" + str(bool(role[1]))
              + " rolsuper=" + str(bool(role[2]))
              + " rolbypassrls=" + str(bool(role[3]))
              + " rolvaliduntil=" + (str(role[4]) if role[4] else "none"))
        check("sanad_role_has_login", bool(role[1]), "rolcanlogin=false")
        check("sanad_role_not_superuser", not bool(role[2]), "rolsuper=true")
        check("sanad_role_not_bypassrls", not bool(role[3]), "rolbypassrls=true")
        if role[4] is not None and role[4] < datetime.now(timezone.utc):
            raise RuntimeError("sanad_role_login_expired: rolvaliduntil in the past")

        # --- 3. Impact probe (precondition 2) ---
        impact = impact_probe(conn)
        results["impact"] = impact
        print("IMPACT live_sanad_sessions=" + json.dumps(impact["live_sanad_session_samples"])
              + " owned_tables=" + str(impact["owned_tables"])
              + " secdef_funcs=" + str(impact["owned_security_definer_functions"])
              + " memberships=" + str(impact["role_memberships"])
              + " cron_jobs=" + str(impact["cron_jobs"]))
        check("no_live_sanad_sessions",
              all(s == 0 for s in impact["live_sanad_session_samples"]),
              "role sanad is in active use; rotation would break a live consumer")

        # --- 4. Privilege dry-run (precondition 3), explicitly rolled back ---
        dryrun_error = None
        try:
            with conn.transaction():
                alter_sanad_password(conn, new_password)
                raise _DryRunRollback()
        except _DryRunRollback:
            pass
        except psycopg.Error as exc:
            dryrun_error = redact(exc)
        check("admin_alter_privilege_dry_run", dryrun_error is None, dryrun_error)

        # --- 5. Real rotation (precondition 4 passed: dedicated secret already
        # staged; only role sanad is altered) ---
        try:
            alter_sanad_password(conn, new_password)
        except psycopg.Error as exc:
            raise RuntimeError("REAL_ALTER_FAILED: " + redact(exc))
        results["checks"]["rotation_applied"] = "PASS"
        print("ROTATION_APPLIED for role sanad only")

    # --- 6. End-to-end verification through the staged G1 config (condition 7) ---
    verification = verify_via_staged_config(new_password)
    results["verification"] = verification
    print("VERIFY attempt=" + str(verification["attempt"])
          + " host=" + verification["host"]
          + " current_user=" + verification["current_user"]
          + " rolsuper=" + str(verification["rolsuper"])
          + " rolbypassrls=" + str(verification["rolbypassrls"]))
    check("verified_current_user_sanad", verification["current_user"] == "sanad")
    check("verified_no_superuser", verification["rolsuper"] is False)
    check("verified_no_bypassrls", verification["rolbypassrls"] is False)

    results["result"] = "PASS"
    print("ROTATION_RESULT=PASS")
    return 0


if __name__ == "__main__":
    try:
        code = main()
    except Exception as exc:  # noqa: BLE001
        first = str(exc).split("\n")[0][:280]
        print("::error::G1 ROTATION BLOCKED/FAILED: " + first, file=sys.stderr)
        results["error"] = first
        code = 1
    finally:
        with open("g1-sanad-rotation-evidence.json", "w", encoding="utf-8") as out:
            json.dump(results, out, indent=2, sort_keys=True, default=str)
        print("EVIDENCE_WRITED g1-sanad-rotation-evidence.json")
    sys.exit(code)
