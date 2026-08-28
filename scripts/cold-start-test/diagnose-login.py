#!/usr/bin/env python3
"""
Phase 2: Diagnose actual login rejection + test forgot-password flow.
"""
import json
import time
import urllib.request
import urllib.error
import ssl
from datetime import datetime, timezone

LOGIN_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/login"
FORGOT_PASSWORD_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/forgot-password"
AUTH_ME_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/me"
LOGOUT_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/logout"

# Test credentials
ADMIN_EMAIL = "admin@snad.ai"
ADMIN_PASSWORD = "Senen@001985"  # last known password from prior work

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
print("PHASE 2: DIAGNOSE LOGIN + TEST FORGOT-PASSWORD")
print("=" * 70)
print(f"Timestamp: {utc_now()}")
print("")

# Test 1: Login with admin@snad.ai
print("=== Test 1: Login with admin@snad.ai ===")
login = do_request("POST", LOGIN_URL,
    data={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}, timeout=30)
print(f"LOGIN_HTTP={login['status']}")
print(f"LOGIN_ELAPSED={login['elapsed']:.3f}s")
hdrs = {k.lower(): v for k, v in login["headers"].items()}
print(f"X_REQUEST_ID={hdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={hdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")
if login["status"] == 200:
    try:
        body = json.loads(login["body"])
        print(f"  user.email={body.get('user',{}).get('email')}")
        print(f"  user.status={body.get('user',{}).get('status')}")
        print(f"  LOGIN=PASS")
    except:
        print(f"  body: {login['body'][:300]}")
else:
    print(f"  body: {login['body'][:300]}")
    print(f"  LOGIN=FAIL")

print("")

# Test 2: Forgot password with admin@snad.ai
print("=== Test 2: Forgot password with admin@snad.ai ===")
forgot = do_request("POST", FORGOT_PASSWORD_URL,
    data={"email": ADMIN_EMAIL}, timeout=30)
print(f"FORGOT_HTTP={forgot['status']}")
print(f"FORGOT_ELAPSED={forgot['elapsed']:.3f}s")
fhdrs = {k.lower(): v for k, v in forgot["headers"].items()}
print(f"X_REQUEST_ID={fhdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={fhdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")
print(f"  body: {forgot['body'][:500]}")

print("")

# Test 3: Forgot password with sanad.ai.app@gmail.com (non-existent user)
print("=== Test 3: Forgot password with sanad.ai.app@gmail.com (non-existent) ===")
forgot2 = do_request("POST", FORGOT_PASSWORD_URL,
    data={"email": "sanad.ai.app@gmail.com"}, timeout=30)
print(f"FORGOT_HTTP={forgot2['status']}")
print(f"FORGOT_ELAPSED={forgot2['elapsed']:.3f}s")
fhdrs2 = {k.lower(): v for k, v in forgot2["headers"].items()}
print(f"X_REQUEST_ID={fhdrs2.get('x-request-id', 'NOT_PRESENT')}")
print(f"  body: {forgot2['body'][:500]}")

print("")

# Save results
results = {
    "login": {"http": login["status"], "elapsed": login["elapsed"],
              "x_request_id": hdrs.get("x-request-id"),
              "x_bff_error": hdrs.get("x-sanad-bff-error")},
    "forgot_password_admin": {"http": forgot["status"], "elapsed": forgot["elapsed"],
                               "body": forgot["body"][:500]},
    "forgot_password_gmail": {"http": forgot2["status"], "elapsed": forgot2["elapsed"],
                               "body": forgot2["body"][:500]},
}
with open("/home/z/my-project/scripts/cold-start-test/auth-diagnosis-results.json", "w") as f:
    json.dump(results, f, indent=2, default=str)
print("Results saved to auth-diagnosis-results.json")
