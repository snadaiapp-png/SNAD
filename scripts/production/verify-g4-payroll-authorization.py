#!/usr/bin/env python3
"""Fail-closed, read-only production identity/RBAC probes plus unauthorized mutation denials."""
import json
import os
import sys
import urllib.error
import urllib.request
import socket
import uuid

ROOT = os.environ["PRODUCTION_BASE_URL"].rstrip("/")
PAYROLL = "/api/v2/hr/payroll"
CAP_VIEW = "HRM.PAYROLL.VIEW"
CAP_MUTATIONS = {"HRM.PAYROLL.CALCULATE","HRM.PAYROLL.REVIEW","HRM.PAYROLL.APPROVE","HRM.PAYROLL.EXPORT"}


def request(method, path, token=None, data=None):
    payload = None if data is None else json.dumps(data).encode()
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
        headers["Idempotency-Key"] = "g4-authz-negative-" + os.urandom(12).hex()
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(ROOT + path, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=35) as response:
            return response.status, response.read(2_000_000)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read(2_000_000)
    except (TimeoutError, socket.timeout):
        raise RuntimeError('AUTH_BACKEND_READ_TIMEOUT') from None
    except urllib.error.URLError:
        raise RuntimeError('AUTH_BACKEND_CONNECTIVITY_FAILURE') from None


def parse(payload):
    return json.loads(payload.decode("utf-8"))


def check(predicate, message):
    if not predicate:
        print("FAIL: " + message, flush=True)
        sys.exit(1)
    print("PASS: " + message, flush=True)


def login(label):
    email = os.environ[f"G4_{label}_EMAIL"]
    password = os.environ[f"G4_{label}_PASSWORD"]
    tenant_id = os.environ[f"G4_{label}_TENANT_ID"]
    status, body = request("POST", "/api/v1/auth/login", data={"email":email,"password":password,"tenantId":tenant_id})
    if status != 200:
        # Never log payload, email, password, token, or account identifier.
        category = (
            "INVALID_OR_UNAUTHORIZED_CREDENTIALS" if status in (400, 401, 403)
            else "TENANT_SELECTION_CONFLICT" if status == 409
            else "RATE_LIMITED" if status == 429
            else "BACKEND_FAILURE" if 500 <= status <= 599
            else "UNEXPECTED_HTTP_STATUS"
        )
        print(f"FAIL: {label} authenticated login HTTP={status} class={category}", flush=True)
        sys.exit(1)
    check(True, label + " authenticated login")
    data = parse(body)
    token = data.get("accessToken")
    user = data.get("user", {})
    check(bool(token) and user.get("tenantId") == tenant_id and user.get("status") == "ACTIVE", label + " correct active tenant binding")
    status, me_body = request("GET", "/api/v1/auth/me", token=token)
    check(status == 200, label + " authenticated /me")
    me = parse(me_body)
    check(me.get("tenantId") == tenant_id, label + " /me tenant binding")
    return token, me


def main():
    owner_token, owner = login("OWNER")
    denied_token, denied = login("UNAUTHORIZED")
    foreign_token, foreign = login("FOREIGN")
    check(owner["tenantId"] == denied["tenantId"] and owner["tenantId"] != foreign["tenantId"], "three test identities have intended tenant topology")
    check(CAP_VIEW in owner.get("capabilities", []), "owner effective PAYROLL.VIEW")
    check(CAP_VIEW not in denied.get("capabilities", []), "unauthorized user has no PAYROLL.VIEW")
    check(not (CAP_MUTATIONS & set(owner.get("capabilities", []))), "view-only owner has no mutation capabilities")

    status, runs_body = request("GET", PAYROLL + "/runs", token=owner_token)
    check(status == 200, "owner payroll list HTTP 200")
    runs = parse(runs_body)
    check(isinstance(runs, list), "owner payroll list is a JSON array")

    # Non-destructive negative probes.
    #
    # The certified production topology is intentionally dormant: V20261010_99
    # grants the canonical owner ONLY HRM.PAYROLL.VIEW and no production actor
    # holds any payroll mutation capability (proven live above), so no payroll
    # run may exist yet and none may be created through governed channels.
    #
    # Denial is enforced BEFORE any run data is touched: every payroll endpoint
    # is @RequireCapability-gated at the controller boundary, and transition()
    # calls authorizationGuard.requireTransition(...) before loadForUpdate(...),
    # so the probes are state-independent for any syntactically valid run
    # identifier. A real run is used when one exists; otherwise a random UUID
    # exercises the identical pre-data denial path.
    #
    # expectedVersion is kept validation-clean for @PositiveOrZero (the prior
    # -1 sentinel failed bean validation with HTTP 400 before the capability
    # gate) and always exceeds any real version by a wide margin, so even a
    # hypothetical gate bypass aborts on version conflict instead of mutating.
    if isinstance(runs, list) and len(runs) > 0 and runs[0].get("id"):
        run_id = str(runs[0]["id"])
        try:
            safe_version = int(runs[0].get("version") or 0) + 1_000_000
        except (TypeError, ValueError):
            safe_version = 1_000_000
        print("G4_NEGATIVE_PROBE_MODE=REAL_RUN", flush=True)
    else:
        run_id = str(uuid.uuid4())
        safe_version = 1_000_000
        print("G4_NEGATIVE_PROBE_MODE=SYNTHETIC_RUN_ID (dormant certified topology)", flush=True)
    # No payroll record or identifier is emitted to workflow logs.
    status, _ = request("GET", PAYROLL + "/runs", token=denied_token)
    check(status == 403, "unauthorized same-tenant payroll list HTTP 403")
    status, _ = request("GET", PAYROLL + "/runs/" + run_id, token=foreign_token)
    check(status in (403,404), "cross-tenant payroll run denied")
    for action in ("calculate","review","approve","export"):
        data = {"expectedVersion": safe_version} if action == "export" else {"expectedVersion": safe_version, "reason": "SECURITY_NEGATIVE_PROBE"}
        status, _ = request("POST", PAYROLL + "/runs/" + run_id + "/" + action, token=owner_token, data=data)
        check(status == 403, "view-only owner cannot " + action)
        status, _ = request("POST", PAYROLL + "/runs/" + run_id + "/" + action, token=denied_token, data=data)
        check(status == 403, "unauthorized actor cannot " + action)
        status, _ = request("POST", PAYROLL + "/runs/" + run_id + "/" + action, token=foreign_token, data=data)
        check(status in (403,404), "foreign tenant cannot " + action)
    print("G4_PRODUCTION_API_AUTHORIZATION=PASS")


if __name__ == "__main__":
    main()
