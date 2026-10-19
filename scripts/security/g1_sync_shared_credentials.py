#!/usr/bin/env python3
"""G1 closure — sync shared backend credentials to the current `sanad` password.

Root cause fixed here (RC-1, evidence run 39236371817 + rotation #3 of
2026-10-11): the DBA-authorized rotation set the production role `sanad`
password to the value staged in G1_DATASOURCE_PASSWORD and intentionally left
shared backend secrets untouched; SPRING_DATASOURCE_PASSWORD and
PRODUCTION_DATABASE_PASSWORD (both repo scope and production environment
scope) therefore hold the stale pre-rotation value and fail authentication.

This script copies the CURRENT, forensically-verified `sanad` password from
G1_DATASOURCE_PASSWORD into the four shared-secret slots. No new password is
generated, no DB role is modified, and no value is ever printed, logged, or
written to evidence (names, timestamps, and HTTP status codes only).

Fail-closed rails:
  1. The source value must authenticate as role `sanad` with
     rolsuper=false and rolbypassrls=false BEFORE any write.
  2. An admin token is auto-selected from the sanctioned store by probing
     read access to the repository secret list; the selected NAME (never its
     value) is recorded. If none qualifies, the script aborts before writing.
  3. Values travel only in memory: API writes use libsodium sealed boxes
     against the scope public keys; values never reach argv or logs.
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

import psycopg
from nacl import encoding, public

REPO = "snadaiapp-png/SNAD"
API = "https://api.github.com"
PROJECT_REF = "tkbrvupemreqabwzdpyq"
POOLER_HOST = "aws-0-eu-central-1.pooler.supabase.com"
SOURCE_NAME = "G1_DATASOURCE_PASSWORD"
TARGETS = ("SPRING_DATASOURCE_PASSWORD", "PRODUCTION_DATABASE_PASSWORD")
ADMIN_CANDIDATES = ("Z_GITHUB_TOKEN", "BRANCH_PROTECTION_TOKEN", "G4_BOOTSTRAP_PAT")

results = {
    "schema": "sanad.g1.closure.credential-sync.v1",
    "time": datetime.now(timezone.utc).isoformat(),
    "source": SOURCE_NAME,
    "source_role_verified": False,
    "admin_token_selected": None,
    "writes": [],
    "post_verify": {},
}


def check(name, ok):
    results.setdefault("checks", {})[name] = "PASS" if ok else "FAIL"
    if not ok:
        raise AssertionError(name)


def api(method, path, token, payload=None):
    request = urllib.request.Request(API + path, method=method, data=(
        json.dumps(payload).encode("utf-8") if payload is not None else None),
        headers={"Authorization": "Bearer " + token,
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read()
            return response.status, (json.loads(body) if body else {})
    except urllib.error.HTTPError as exc:
        exc.read()
        return exc.code, {}


def verify_source_role(password):
    with psycopg.connect(host=POOLER_HOST, port=5432, dbname="postgres",
                         user="sanad." + PROJECT_REF, password=password,
                         sslmode="require", connect_timeout=12) as conn:
        with conn.transaction():
            conn.execute("SET TRANSACTION READ ONLY")
            current_user, superuser, bypass = conn.execute(
                "SELECT current_user, "
                "(SELECT rolsuper FROM pg_roles WHERE rolname=current_user), "
                "(SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user)"
            ).fetchone()
        check("source_is_sanad", current_user == "sanad")
        check("source_not_superuser", not superuser)
        check("source_not_bypassrls", not bypass)
        results["source_role_verified"] = True


def select_admin_token():
    for name in ADMIN_CANDIDATES:
        token = os.environ.get(name)
        if not token:
            continue
        status, _ = api("GET", f"/repos/{REPO}/actions/secrets?per_page=1", token)
        if status == 200:
            results["admin_token_selected"] = name
            return token
        results.setdefault("admin_probe", {})[name] = status
    raise AssertionError("NO_ADMIN_TOKEN: no sanctioned token can administer secrets")


def put_secret(token, scope, name, value):
    if scope == "repo":
        key_path = f"/repos/{REPO}/actions/secrets/public-key"
        put_path = f"/repos/{REPO}/actions/secrets/{name}"
    else:
        key_path = f"/repos/{REPO}/environments/production/secrets/public-key"
        put_path = f"/repos/{REPO}/environments/production/secrets/{name}"
    status, keydoc = api("GET", key_path, token)
    check(f"public_key_readable_{scope}", status == 200)
    recipient = public.PublicKey(keydoc["key"].encode("utf-8"),
                                 encoding.Base64Encoder())
    sealed = public.SealedBox(recipient).encrypt(value.encode("utf-8"))
    status, _ = api("PUT", put_path, token, {
        "encrypted_value": base64.b64encode(sealed).decode("utf-8"),
        "key_id": keydoc["key_id"],
    })
    results["writes"].append({"scope": scope, "secret": name, "http_status": status})
    check(f"write_ok_{scope}_{name}", status in (201, 204))


def post_verify(token):
    for scope, path in (("repo", f"/repos/{REPO}/actions/secrets?per_page=100"),
                        ("production", f"/repos/{REPO}/environments/production/secrets?per_page=100")):
        status, doc = api("GET", path, token)
        check(f"list_ok_{scope}", status == 200)
        entries = {s["name"]: s.get("updated_at") for s in doc.get("secrets", [])}
        for name in TARGETS:
            results["post_verify"][f"{scope}:{name}"] = {
                "present": name in entries, "updated_at": entries.get(name)}


def main():
    source = os.environ.get(SOURCE_NAME)
    check("source_present", bool(source))
    verify_source_role(source)
    token = select_admin_token()
    for scope in ("repo", "production"):
        for name in TARGETS:
            put_secret(token, scope, name, source)
    post_verify(token)
    results["result"] = "PASS"


if __name__ == "__main__":
    exit_code = 0
    try:
        main()
    except Exception as exc:
        results["result"] = "FAIL"
        results["error"] = str(exc).split("\n")[0][:280]
        print("::error::G1 credential sync FAIL: " + results["error"], file=sys.stderr)
        exit_code = 1
    finally:
        with open("g1-credential-sync-evidence.json", "w", encoding="utf-8") as handle:
            json.dump(results, handle, indent=2, sort_keys=True)
        print("G1_CREDENTIAL_SYNC=" + results["result"])
    sys.exit(exit_code)
