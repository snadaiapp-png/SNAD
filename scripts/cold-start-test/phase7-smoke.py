#!/usr/bin/env python3
"""Phase 7: Production smoke test — login, auth/me, logout."""
import json
import time
import urllib.request
import urllib.error
import ssl
from datetime import datetime, timezone

LOGIN_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/login"
AUTH_ME_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/me"
LOGOUT_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/logout"
ADMIN_EMAIL = "admin@snad.ai"
ADMIN_PASSWORD = "Senen@001985"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def utc_now():
    n = datetime.now(timezone.utc)
    return n.strftime("%Y-%m-%dT%H:%M:%S.") + f"{n.microsecond // 1000:03d}Z"

def do_request(method, url, headers=None, data=None, timeout=30):
    req_headers = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)
    body = None
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        req_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    start_ts = time.monotonic()
    start_wall = utc_now()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            status = resp.status
            resp_headers = dict(resp.headers)
            resp_body = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        status = e.code
        resp_headers = dict(e.headers) if e.headers else {}
        resp_body = e.read().decode("utf-8", errors="replace") if e.fp else ""
    except Exception as ex:
        elapsed = time.monotonic() - start_ts
        return {"status": None, "elapsed": elapsed, "error": str(ex),
                "headers": {}, "body": "", "start_ts": start_wall}
    elapsed = time.monotonic() - start_ts
    return {"status": status, "elapsed": elapsed, "error": None,
            "headers": resp_headers, "body": resp_body, "start_ts": start_wall}

print("=" * 70)
print("PHASE 7 — PRODUCTION SMOKE TEST")
print("=" * 70)
print(f"Start: {utc_now()}")
print("")

# Step 1: Login
print("--- Step 1: Login ---")
login = do_request("POST", LOGIN_URL,
                   data={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
                   timeout=30)
print(f"LOGIN_START={login['start_ts']}")
print(f"LOGIN_HTTP={login['status']}")
print(f"LOGIN_ELAPSED={login['elapsed']:.3f}s")
hdrs = {k.lower(): v for k, v in login["headers"].items()}
print(f"X_REQUEST_ID={hdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ATTEMPTS={hdrs.get('x-sanad-bff-attempts', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={hdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")

token = None
if login["status"] == 200:
    try:
        body = json.loads(login["body"])
        for k in ("accessToken", "token", "access_token", "authToken"):
            if k in body:
                token = body[k]
                break
        print(f"LOGIN_USER_EMAIL={body.get('user',{}).get('email')}")
        print(f"LOGIN_USER_STATUS={body.get('user',{}).get('status')}")
        print(f"TOKEN_EXTRACTED={'YES' if token else 'NO'}")
    except Exception as e:
        print(f"LOGIN_BODY_PARSE_ERROR={e}")
else:
    print(f"LOGIN_BODY={login['body'][:300]}")
    print("ABORT: Login failed")
    exit(1)

print("")

# Step 2: auth/me
if token:
    print("--- Step 2: GET /api/v1/auth/me ---")
    auth_header = {"Authorization": f"Bearer {token}"}
    me = do_request("GET", AUTH_ME_URL, headers=auth_header, timeout=30)
    print(f"AUTH_ME_HTTP={me['status']}")
    print(f"AUTH_ME_ELAPSED={me['elapsed']:.3f}s")
    me_hdrs = {k.lower(): v for k, v in me["headers"].items()}
    print(f"AUTH_ME_X_REQUEST_ID={me_hdrs.get('x-request-id', 'NOT_PRESENT')}")
    if me["status"] == 200:
        try:
            me_body = json.loads(me["body"])
            def find_key(d, keys):
                if isinstance(d, dict):
                    for k in keys:
                        if k in d:
                            return d[k]
                    for v in d.values():
                        r = find_key(v, keys)
                        if r is not None:
                            return r
                return None
            print(f"AUTH_ME_STATUS={find_key(me_body, ['status', 'accountStatus'])}")
            print(f"AUTH_ME_EMAIL={find_key(me_body, ['email'])}")
            print(f"AUTH_ME_TENANT={find_key(me_body, ['tenantId', 'tenant_id'])}")
        except Exception as e:
            print(f"AUTH_ME_PARSE_ERROR={e}")
    else:
        print(f"AUTH_ME_BODY={me['body'][:300]}")

    print("")

    # Step 3: Logout
    print("--- Step 3: POST /api/v1/auth/logout ---")
    logout = do_request("POST", LOGOUT_URL, headers=auth_header, timeout=30)
    print(f"LOGOUT_HTTP={logout['status']}")
    print(f"LOGOUT_ELAPSED={logout['elapsed']:.3f}s")

# Save results
results = {
    "login": {"http": login["status"], "elapsed": login["elapsed"],
              "x_request_id": hdrs.get("x-request-id"),
              "x_bff_attempts": hdrs.get("x-sanad-bff-attempts"),
              "x_bff_error": hdrs.get("x-sanad-bff-error")},
    "auth_me": {"http": me["status"] if token else None,
                "elapsed": me["elapsed"] if token else None} if token else None,
    "logout": {"http": logout["status"] if token else None,
               "elapsed": logout["elapsed"] if token else None} if token else None,
}
with open("/home/z/my-project/scripts/cold-start-test/phase7-smoke-results.json", "w") as f:
    json.dump(results, f, indent=2, default=str)
print("")
print("Results saved to phase7-smoke-results.json")
