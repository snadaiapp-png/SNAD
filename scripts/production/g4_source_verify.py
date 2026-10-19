#!/usr/bin/env python3
"""G4 source-secret verification and conditional in-memory provisioning.

Read-only HTTP probes against the live production backend, then (only in
verify_and_provision mode and only after every proof passes) copies the
approved source secrets into the missing G4_* environment secrets using
gh over stdin, so values never touch disk or logs.

Security properties:
  - Never prints secret values (emails, passwords, tokens, foreign tenant UUID).
  - Prints only booleans, fingerprints (sha256 first 12 hex) and pinned values.
  - Fail-closed: any failed proof => exit 2 with no provisioning.
Exit codes: 0 verified(+provisioned), 2 BLOCKED, 1 infrastructure error.
"""
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.environ["PRODUCTION_BASE_URL"].rstrip("/")
MODE = os.environ["VERIFY_MODE"]
OWNER_TENANT = os.environ["OWNER_TENANT_CANDIDATE"]
REPO = os.environ["GITHUB_REPOSITORY"]
GH_TOKEN = os.environ.get("BOOTSTRAP_GH_TOKEN", "")

CAP_VIEW = "HRM.PAYROLL.VIEW"
CAP_MUTATIONS = {
    "HRM.PAYROLL.CALCULATE",
    "HRM.PAYROLL.REVIEW",
    "HRM.PAYROLL.APPROVE",
    "HRM.PAYROLL.EXPORT",
}
CANONICAL_OWNER_EMAIL = "snad.ai.app@gmail.com"
CANONICAL_OWNER_ID = "00000000-0000-0000-0000-000000000010"

VERDICTS = {}


def emit(key, value):
    VERDICTS[key] = value
    print("G4SRC " + key + "=" + json.dumps(value), flush=True)


def fp(value):
    if value is None:
        return "EMPTY"
    return hashlib.sha256(str(value).encode("utf-8")).hexdigest()[:12]


def req(method, path, token=None, data=None, timeout=45):
    payload = None if data is None else json.dumps(data).encode("utf-8")
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    attempt = 0
    while True:
        attempt += 1
        request = urllib.request.Request(ROOT + path, data=payload, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return response.status, response.read(2_000_000)
        except urllib.error.HTTPError as exc:
            if exc.code == 429 and attempt < 5:
                time.sleep(6 * attempt)
                continue
            try:
                return exc.code, exc.read(2_000_000)
            except Exception:
                return exc.code, b"{}"
        except Exception as exc:  # network-level
            if attempt < 3:
                time.sleep(4)
                continue
            return -1, str(exc).encode("utf-8")


def login(label, email_env, password_env, tenant):
    email = os.environ[email_env]
    password = os.environ[password_env]
    status, body = req("POST", "/api/v1/auth/login", data={"email": email, "password": password, "tenantId": tenant})
    result = {
        "label": label,
        "http": status,
        "email_fp": fp(email),
        "tenant_fp": fp(tenant),
    }
    token = None
    me = None
    if status == 200:
        try:
            data = json.loads(body)
        except Exception:
            data = {}
        token = data.get("accessToken")
        user = data.get("user") or {}
        user_id = str(user.get("id") or user.get("userId") or "")
        result.update(
            {
                "token_ok": bool(token),
                "user_id_fp": fp(user_id),
                "user_tenant_match": user.get("tenantId") == tenant,
                "user_status": user.get("status"),
                "canonical_owner_email_match": email.strip().lower() == CANONICAL_OWNER_EMAIL,
                "canonical_owner_id_match": user_id == CANONICAL_OWNER_ID,
            }
        )
        if token:
            me_status, me_body = req("GET", "/api/v1/auth/me", token=token)
            result["me_http"] = me_status
            if me_status == 200:
                try:
                    me = json.loads(me_body)
                except Exception:
                    me = {}
                caps = me.get("capabilities") or []
                result["me_tenant_match"] = (me.get("tenantId") == tenant)
                result["has_payroll_view"] = CAP_VIEW in caps
                result["mutation_caps_present"] = sorted(CAP_MUTATIONS & set(caps))
                result["capability_count"] = len(caps)
    return result


def gh_env_secret_names():
    out = subprocess.run(
        ["gh", "api", "repos/" + REPO + "/environments/Production/secrets?per_page=100", "--jq", "[.secrets[].name]"],
        env={**os.environ, "GH_TOKEN": GH_TOKEN},
        capture_output=True,
    )
    if out.returncode != 0:
        return None
    return set(json.loads(out.stdout.decode("utf-8")))


def gh_secret_set_stdin(name, value):
    out = subprocess.run(
        ["gh", "secret", "set", name, "--env", "Production", "--repo", REPO],
        input=value.encode("utf-8"),
        env={**os.environ, "GH_TOKEN": GH_TOKEN},
        capture_output=True,
    )
    return out.returncode == 0, out.stderr.decode("utf-8", "replace")[:200]


def main():
    emit("mode", MODE)
    emit("owner_tenant_pinned", OWNER_TENANT)

    # --- 1. Owner (canonical) proof ---
    owner = login("OWNER", "SOURCE_OWNER_EMAIL", "SOURCE_OWNER_PASSWORD", OWNER_TENANT)
    emit("owner", owner)
    time.sleep(4)

    # --- 2. Unauthorized same-tenant proof ---
    unauth = login("UNAUTHORIZED", "SOURCE_UNAUTH_EMAIL", "SOURCE_UNAUTH_PASSWORD", OWNER_TENANT)
    emit("unauthorized", unauth)
    time.sleep(4)

    # --- 3. Foreign tenant proof ---
    foreign_tenant = os.environ["SOURCE_FOREIGN_TENANT"]
    foreign = login("FOREIGN", "SOURCE_FOREIGN_EMAIL", "SOURCE_FOREIGN_PASSWORD", foreign_tenant)
    emit("foreign", foreign)

    # --- 4. Secret-level consistency checks ---
    emit("foreign_tenant_differs_from_owner", foreign_tenant != OWNER_TENANT)
    emit("existing_owner_tenant_secret_match", os.environ.get("EXISTING_OWNER_TENANT", "") == OWNER_TENANT)

    # --- 5. Fail-closed proofs required by the execution order ---
    proofs = [
        ("owner_login", owner.get("http") == 200),
        ("owner_token", owner.get("token_ok") is True),
        ("owner_tenant_binding", owner.get("user_tenant_match") is True and owner.get("me_tenant_match") is True),
        ("owner_active", owner.get("user_status") == "ACTIVE"),
        ("owner_payroll_view", owner.get("has_payroll_view") is True),
        ("owner_no_mutation_caps", owner.get("mutation_caps_present") == []),
        ("owner_canonical_id_binding", owner.get("canonical_owner_id_match") is True),
        ("unauthorized_login", unauth.get("http") == 200),
        ("unauthorized_same_tenant", unauth.get("user_tenant_match") is True and unauth.get("me_tenant_match") is True),
        ("unauthorized_no_payroll_view", unauth.get("has_payroll_view") is False),
        ("foreign_login", foreign.get("http") == 200),
        ("foreign_tenant_binding", foreign.get("user_tenant_match") is True and foreign.get("me_tenant_match") is True),
        ("foreign_tenant_differs", foreign_tenant != OWNER_TENANT),
    ]
    failed = [name for name, ok in proofs if not ok]
    for name, ok in proofs:
        emit("proof_" + name, ok)

    if failed:
        emit("result", "BLOCKED")
        emit("failed_proofs", failed)
        print("G4 SOURCE VERIFICATION: BLOCKED — no provisioning performed", flush=True)
        return 2

    emit("result", "VERIFIED")

    if MODE != "verify_and_provision":
        print("G4 SOURCE VERIFICATION: VERIFIED (verify_only — no secrets written)", flush=True)
        return 0

    if not GH_TOKEN:
        emit("provision_error", "missing BOOTSTRAP_GH_TOKEN")
        print("G4 PROVISION: BLOCKED — no bootstrap token available", flush=True)
        return 2

    existing = gh_env_secret_names()
    if existing is None:
        emit("provision_error", "cannot list environment secrets")
        return 1

    plan = [
        ("G4_OWNER_EMAIL", os.environ["SOURCE_OWNER_EMAIL"]),
        ("G4_OWNER_PASSWORD", os.environ["SOURCE_OWNER_PASSWORD"]),
        ("G4_UNAUTHORIZED_TENANT_ID", OWNER_TENANT),
        ("G4_UNAUTHORIZED_EMAIL", os.environ["SOURCE_UNAUTH_EMAIL"]),
        ("G4_UNAUTHORIZED_PASSWORD", os.environ["SOURCE_UNAUTH_PASSWORD"]),
        ("G4_FOREIGN_TENANT_ID", foreign_tenant),
        ("G4_FOREIGN_EMAIL", os.environ["SOURCE_FOREIGN_EMAIL"]),
        ("G4_FOREIGN_PASSWORD", os.environ["SOURCE_FOREIGN_PASSWORD"]),
    ]
    created = []
    skipped = []
    for name, value in plan:
        if name in existing:
            skipped.append(name)
            continue
        ok, err = gh_secret_set_stdin(name, value)
        emit("provision_" + name, ok)
        if not ok:
            emit("provision_error", name + ": " + err)
            print("G4 PROVISION: FAILED at " + name, flush=True)
            return 1
        created.append(name)
        time.sleep(2)

    # Owner tenant secret: create or replace only with proven value.
    owner_tenant_state = "already_correct"
    if "G4_OWNER_TENANT_ID" not in existing:
        ok, err = gh_secret_set_stdin("G4_OWNER_TENANT_ID", OWNER_TENANT)
        owner_tenant_state = "created" if ok else "failed"
        if not ok:
            emit("provision_error", "G4_OWNER_TENANT_ID: " + err)
    elif os.environ.get("EXISTING_OWNER_TENANT", "") != OWNER_TENANT:
        ok, err = gh_secret_set_stdin("G4_OWNER_TENANT_ID", OWNER_TENANT)
        owner_tenant_state = "replaced_with_proven_value" if ok else "replace_failed"
        if not ok:
            emit("provision_error", "G4_OWNER_TENANT_ID: " + err)
    emit("owner_tenant_secret_state", owner_tenant_state)

    final_names = gh_env_secret_names() or set()
    required = {name for name, _ in plan} | {"G4_OWNER_TENANT_ID"}
    missing_after = sorted(required - final_names)
    emit("created", sorted(created))
    emit("skipped_existing", sorted(skipped))
    emit("missing_after", missing_after)
    if missing_after:
        print("G4 PROVISION: INCOMPLETE — missing " + ",".join(missing_after), flush=True)
        return 1
    print("G4 PROVISION: COMPLETE — all 10 G4 secrets present in Environment Production", flush=True)
    return 0


if __name__ == "__main__":
    code = main()
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        try:
            with open(summary, "a", encoding="utf-8") as handle:
                handle.write("### G4 Source Verification\n```\n")
                handle.write(json.dumps(VERDICTS, indent=2, sort_keys=True))
                handle.write("\n```\n")
        except Exception:
            pass
    sys.exit(code)
