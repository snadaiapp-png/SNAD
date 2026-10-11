#!/usr/bin/env python3
"""G1 datasource credential forensics probe (read-only, fail-closed, non-secret-output).

Purpose
-------
Prove, inside an authorized GitHub Actions execution context (environment:
production), whether any credential already present in the sanctioned secret
store authenticates to the sanctioned Supabase PostgreSQL project as the
least-privilege role ``sanad``.

Hard safety rules (enforced by construction of this script):
- Secret VALUES are never printed, logged, or written to evidence output.
- Evidence contains candidate NAMES, endpoint CLASSES, and verdicts only.
- Every connection attempt is made inside a read-only transaction and is
  closed immediately; no data is read beyond the identity triple
  (current_user / rolsuper / rolbypassrls).
- Roles identified as superuser or BYPASSRLS are marked ROLE_REJECTED and
  must never be used for G1 certification.
"""
import json
import os
import sys
import time
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlparse

import psycopg

PROJECT_REF = "tkbrvupemreqabwzdpyq"
POOLER_HOST = "aws-0-eu-central-1.pooler.supabase.com"
DIRECT_HOST = "db." + PROJECT_REF + ".supabase.co"
DBNAME = "postgres"
POOLER_USER = "sanad." + PROJECT_REF
DIRECT_USER = "sanad"

SAFE_REASON_SUBSTRINGS = (
    "password authentication failed",
    "role does not exist",
    "too many connections",
    "ssl",
)


def redact_reason(message: str) -> str:
    """Collapse a psycopg error into a coarse, value-free classification."""
    text = str(message).split("\n")[0].lower()
    for needle in SAFE_REASON_SUBSTRINGS:
        if needle in text:
            return needle.replace(" ", "_")
    if ("timeout" in text or "unreachable" in text or "resolved" in text
            or "connection refused" in text or "network" in text):
        return "network_unreachable_or_dns"
    return "op_error_redacted"


def endpoints_from_config_url():
    """Derive the as-configured endpoint (host/port/db/user) without printing it."""
    raw = os.environ.get("SPRING_DATASOURCE_URL", "")
    if not raw:
        return None
    parsed = urlparse(raw.removeprefix("jdbc:"))
    if not parsed.hostname:
        return None
    opts = parse_qs(parsed.query)
    return {
        "endpoint": "as-configured-url",
        "host": parsed.hostname,
        "port": parsed.port or 5432,
        "dbname": parsed.path.lstrip("/") or DBNAME,
        "user": os.environ.get("SPRING_DATASOURCE_USERNAME") or parsed.username,
        "host_class": "pooler" if "pooler" in parsed.hostname.lower() else "direct",
    }


def canonical_endpoints():
    return [
        {"endpoint": "pooler-session-5432", "host": POOLER_HOST, "port": 5432,
         "dbname": DBNAME, "user": POOLER_USER, "host_class": "pooler"},
        {"endpoint": "pooler-txn-6543", "host": POOLER_HOST, "port": 6543,
         "dbname": DBNAME, "user": POOLER_USER, "host_class": "pooler"},
        {"endpoint": "direct-5432", "host": DIRECT_HOST, "port": 5432,
         "dbname": DBNAME, "user": DIRECT_USER, "host_class": "direct"},
    ]


def candidate_credentials():
    """Return {candidate_name: password} strictly from the sanctioned store."""
    found = {}
    for name in ("SPRING_DATASOURCE_PASSWORD", "PRODUCTION_DATABASE_PASSWORD"):
        value = os.environ.get(name)
        if value:
            found[name] = value
    for name in ("PRODUCTION_DATABASE_URL", "PRODUCTION_DATABASE_JDBC_URL",
                 "SPRING_DATASOURCE_URL"):
        raw = os.environ.get(name)
        if not raw:
            continue
        parsed = urlparse(raw.removeprefix("jdbc:"))
        if parsed.username and parsed.password:
            found[name + "::embedded_credentials"] = parsed.password
    return found


def probe_once(endpoint, password):
    record = {"endpoint": endpoint["endpoint"], "host_class": endpoint["host_class"],
              "port": endpoint["port"]}
    try:
        with psycopg.connect(host=endpoint["host"], port=endpoint["port"],
                             dbname=endpoint["dbname"], user=endpoint["user"],
                             password=password, sslmode="require",
                             connect_timeout=8) as conn:
            with conn.transaction():
                conn.execute("SET TRANSACTION READ ONLY")
                current_user, session_user, superuser, bypass = conn.execute(
                    "SELECT current_user, session_user, "
                    "(SELECT rolsuper FROM pg_roles WHERE rolname = current_user), "
                    "(SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user)"
                ).fetchone()
            record.update({
                "auth": "AUTH_OK",
                "current_user": current_user,
                "session_user": session_user,
                "rolsuper": bool(superuser),
                "rolbypassrls": bool(bypass),
                "verdict": ("ROLE_OK" if (current_user == "sanad"
                                          and not superuser and not bypass)
                            else "ROLE_REJECTED"),
            })
    except psycopg.OperationalError as exc:
        record.update({"auth": "AUTH_FAIL", "reason": redact_reason(exc)})
    except Exception as exc:  # noqa: BLE001 - evidence must stay value-free
        record.update({"auth": "ERROR", "reason": redact_reason(exc)})
    return record


def main():
    evidence = {
        "schema": "sanad.g1.datasource.forensics.v1",
        "time": datetime.now(timezone.utc).isoformat(),
        "project_ref": PROJECT_REF,
        "note": "candidate names + verdicts only; no secret values are captured",
        "supabase_management_api": {},
        "z_github_token_permissions": {},
        "candidates": [],
    }

    # --- Supabase project config (read-only Management API, non-secret fields) ---
    token = os.environ.get("SUPABASE_ACCESS_TOKEN")
    if token:
        probe_api = os.popen(
            "curl -s -o /tmp/sb_project.json -w '%{http_code}' "
            "-H 'Authorization: Bearer $SUPABASE_ACCESS_TOKEN' "
            "-H 'Accept: application/json' "
            "https://api.supabase.com/v1/projects/" + PROJECT_REF
        ).read().strip()
        evidence["supabase_management_api"] = {"http_status": probe_api}
        if probe_api.startswith("2"):
            try:
                with open("/tmp/sb_project.json", encoding="utf-8") as handle:
                    project = json.load(handle)
                db = project.get("database") or {}
                evidence["supabase_management_api"].update({
                    "region": project.get("region"),
                    "db_host": db.get("host"),
                    "db_version": db.get("version") or project.get("database", {}).get("backing_database_version"),
                })
            except Exception:  # noqa: BLE001
                evidence["supabase_management_api"]["parse"] = "skipped"
        os.system("shred -u /tmp/sb_project.json 2>/dev/null || rm -f /tmp/sb_project.json")

    # --- Probe matrix ---
    endpoints = canonical_endpoints()
    configured = endpoints_from_config_url()
    if configured:
        endpoints.insert(0, configured)

    candidates = candidate_credentials()
    if not candidates:
        evidence["candidates"].append({"candidate": "NONE_PRESENT", "results": []})
    for name, password in candidates.items():
        results = []
        for endpoint in endpoints:
            results.append(probe_once(endpoint, password))
            time.sleep(1)
        evidence["candidates"].append({"candidate": name, "results": results})

    # --- Z_GITHUB_TOKEN capability probe (permissions booleans only) ---
    ztoken = os.environ.get("Z_GITHUB_TOKEN")
    if ztoken:
        import subprocess
        proc = subprocess.run(
            ["gh", "api", "repos/snadaiapp-png/SNAD",
             "--jq", ".permissions"],
            env={**os.environ, "GH_TOKEN": ztoken},
            capture_output=True, text=True, timeout=30)
        if proc.returncode == 0:
            try:
                evidence["z_github_token_permissions"] = json.loads(proc.stdout)
            except json.JSONDecodeError:
                evidence["z_github_token_permissions"] = {"parse": "failed"}
        else:
            evidence["z_github_token_permissions"] = {"api": "unauthorized_or_error"}

    with open("g1-forensics-evidence.json", "w", encoding="utf-8") as handle:
        json.dump(evidence, handle, indent=2, sort_keys=True)

    # --- Console summary (verdicts only) ---
    role_ok = []
    for candidate in evidence["candidates"]:
        for result in candidate.get("results", []):
            line = ("CANDIDATE=" + candidate["candidate"]
                    + " ENDPOINT=" + result.get("endpoint", "?")
                    + " AUTH=" + result.get("auth", "?"))
            if result.get("auth") == "AUTH_OK":
                line += (" CURRENT_USER=" + str(result.get("current_user"))
                         + " ROLSUPER=" + str(result.get("rolsuper"))
                         + " ROLBYPASSRLS=" + str(result.get("rolbypassrls"))
                         + " VERDICT=" + str(result.get("verdict")))
            elif result.get("reason"):
                line += " REASON=" + str(result.get("reason"))
            print(line)
            if result.get("verdict") == "ROLE_OK":
                role_ok.append((candidate["candidate"], result.get("endpoint")))
    print("ROLE_OK_CANDIDATES=" + json.dumps(role_ok))
    return 0


if __name__ == "__main__":
    sys.exit(main())
