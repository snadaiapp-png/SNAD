#!/usr/bin/env python3
"""G1_DATASOURCE_* provisioning — re-verify then write, one in-memory pass.

Safety properties (fail-closed):
- The chosen candidate must already exist in the sanctioned secret store
  (injected by the workflow as CANDIDATE_* environment variables).
- The candidate value is resolved and re-verified against the chosen
  endpoint BEFORE any write: only a credential that authenticates as
  role `sanad` with rolsuper=false and rolbypassrls=false is eligible.
- Secret values live exclusively in local Python variables and are piped
  to `gh secret set` via stdin subprocess pipes. They are never printed,
  logged, echoed, or written to disk.
- Only the three G1_DATASOURCE_* environment secrets are written. Shared
  backend secrets, role passwords, and Render credentials are untouched.
"""
import json
import os
import subprocess
import sys
from urllib.parse import urlparse

import psycopg

PROJECT_REF = "tkbrvupemreqabwzdpyq"
POOLER_HOST = "aws-0-eu-central-1.pooler.supabase.com"
REPO = "snadaiapp-png/SNAD"
ENVIRONMENT = "production"
ENDPOINTS = {
    "pooler-session-5432": (POOLER_HOST, 5432),
    "pooler-txn-6543": (POOLER_HOST, 6543),
}
CANDIDATES = {
    "SPRING_DATASOURCE_PASSWORD",
    "PRODUCTION_DATABASE_PASSWORD",
    "PRODUCTION_DATABASE_URL",
    "PRODUCTION_DATABASE_JDBC_URL",
}


def gh(*args, stdin=None):
    """Run gh CLI in-process; stdout returned, never echoed."""
    result = subprocess.run(
        ["gh", *args], input=stdin, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        raise RuntimeError("gh " + args[0] + " failed: " + result.stderr.strip()[:200])
    return result.stdout


def resolve_password(candidate):
    raw = os.environ.get("CANDIDATE_" + candidate, "")
    if not raw:
        raise SystemExit("CANDIDATE_SECRET_EMPTY_OR_MISSING: " + candidate)
    parsed = urlparse(raw.removeprefix("jdbc:"))
    if parsed.username and parsed.password:
        return parsed.password
    return raw


def reverify(host, port, password):
    """Prove the credential is the least-privilege sanad role. Fail-closed."""
    with psycopg.connect(host=host, port=port, dbname="postgres",
                         user="sanad." + PROJECT_REF, password=password,
                         sslmode="require", connect_timeout=10) as conn:
        with conn.transaction():
            conn.execute("SET TRANSACTION READ ONLY")
            current_user, superuser, bypass = conn.execute(
                "SELECT current_user, "
                "(SELECT rolsuper FROM pg_roles WHERE rolname = current_user), "
                "(SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user)"
            ).fetchone()
    eligible = (current_user == "sanad" and not superuser and not bypass)
    print("REVERIFY current_user=" + str(current_user)
          + " rolsuper=" + str(bool(superuser))
          + " rolbypassrls=" + str(bool(bypass))
          + " VERDICT=" + ("ROLE_OK" if eligible else "ROLE_REJECTED"))
    if not eligible:
        raise SystemExit("CANDIDATE_NOT_ELIGIBLE_FOR_G1")


def main():
    candidate = os.environ.get("CHOSEN_CANDIDATE", "")
    endpoint = os.environ.get("CHOSEN_ENDPOINT", "")
    if candidate not in CANDIDATES:
        raise SystemExit("UNKNOWN_CANDIDATE")
    if endpoint not in ENDPOINTS:
        raise SystemExit("UNKNOWN_ENDPOINT")

    # Token authority check (fail-closed) before touching anything.
    perms = json.loads(gh("api", "repos/" + REPO, "--jq", ".permissions"))
    if not (perms.get("admin") or perms.get("maintain") or perms.get("push")):
        raise SystemExit("PROVISIONING_TOKEN_LACKS_SECRET_AUTHORITY")
    print("PROVISIONING_TOKEN_AUTHORITY=OK")

    host, port = ENDPOINTS[endpoint]
    password = resolve_password(candidate)
    reverify(host, port, password)

    jdbc_url = ("jdbc:postgresql://" + host + ":" + str(port)
                + "/postgres?sslmode=require")
    username = "sanad." + PROJECT_REF

    # Values flow to gh via stdin pipes only; never argv, never stdout.
    gh("secret", "set", "G1_DATASOURCE_URL", "--repo", REPO,
       "--env", ENVIRONMENT, stdin=jdbc_url)
    gh("secret", "set", "G1_DATASOURCE_USERNAME", "--repo", REPO,
       "--env", ENVIRONMENT, stdin=username)
    gh("secret", "set", "G1_DATASOURCE_PASSWORD", "--repo", REPO,
       "--env", ENVIRONMENT, stdin=password)
    print("G1_DATASOURCE_URL/USERNAME/PASSWORD written to environment "
          + ENVIRONMENT + " (values never emitted)")

    listing = gh("secret", "list", "--repo", REPO, "--env", ENVIRONMENT)
    written = [line.split("\t")[0] for line in listing.splitlines()
               if line.startswith("G1_DATASOURCE_")]
    print("VERIFIED_PRESENT=" + json.dumps(sorted(written)))
    if sorted(written) != ["G1_DATASOURCE_PASSWORD", "G1_DATASOURCE_URL",
                           "G1_DATASOURCE_USERNAME"]:
        raise SystemExit("POST_WRITE_VERIFICATION_FAILED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
