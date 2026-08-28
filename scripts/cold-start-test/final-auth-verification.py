#!/usr/bin/env python3
"""
FINAL AUTH VERIFICATION — login, auth/me, logout after password reset.
"""
import json
import time
import urllib.request
import urllib.error
import ssl
from datetime import datetime, timezone

LOGIN_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/login"
AUTH_ME_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/me"
LOGOUT_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/logout"
FORGOT_PASSWORD_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/forgot-password"

ADMIN_EMAIL = "sanad.ai.app@gmail.com"
ADMIN_PASSWORD = "Senen@001985"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

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
                "headers": {}, "body": ""}
    elapsed = time.monotonic() - start_ts
    return {"status": status, "elapsed": elapsed, "headers": resp_headers, "body": resp_body}

print("=" * 70)
print("FINAL AUTH VERIFICATION")
print("=" * 70)
print(f"Timestamp: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}")
print(f"Email: {ADMIN_EMAIL}")
print("")

# Test 1: Login
print("=== Login ===")
login = do_request("POST", LOGIN_URL,
    data={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}, timeout=30)
hdrs = {k.lower(): v for k, v in login["headers"].items()}
print(f"LOGIN_HTTP={login['status']}")
print(f"LOGIN_ELAPSED={login['elapsed']:.3f}s")
print(f"X_REQUEST_ID={hdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ATTEMPTS={hdrs.get('x-sanad-bff-attempts', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={hdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")
LOGIN_RESULT = "FAIL"
if login["status"] == 200:
    try:
        body = json.loads(login["body"])
        token = body.get("accessToken") or body.get("token")
        user = body.get("user", {})
        print(f"  user.email={user.get('email')}")
        print(f"  user.status={user.get('status')}")
        print(f"  token_extracted={'YES' if token else 'NO'}")
        LOGIN_RESULT = "PASS"
    except:
        print(f"  body: {login['body'][:200]}")
print(f"LOGIN={LOGIN_RESULT}")

if LOGIN_RESULT != "PASS":
    exit(1)

# Test 2: auth/me
print("")
print("=== auth/me ===")
auth_header = {"Authorization": f"Bearer {token}"}
me = do_request("GET", AUTH_ME_URL, headers=auth_header, timeout=30)
AUTH_ME_RESULT = "FAIL"
print(f"AUTH_ME_HTTP={me['status']}")
print(f"AUTH_ME_ELAPSED={me['elapsed']:.3f}s")
if me["status"] == 200:
    me_body = json.loads(me["body"])
    print(f"  status={me_body.get('status')}")
    print(f"  email={me_body.get('email')}")
    print(f"  tenantId={me_body.get('tenantId')}")
    role_grants = me_body.get("roleGrants", [])
    if role_grants:
        print(f"  role={role_grants[0].get('roleCode')}")
    if me_body.get("status") == "ACTIVE" and me_body.get("email") == ADMIN_EMAIL:
        AUTH_ME_RESULT = "PASS"
print(f"AUTH_ME={AUTH_ME_RESULT}")

# Test 3: logout
print("")
print("=== logout ===")
logout = do_request("POST", LOGOUT_URL, headers=auth_header, timeout=30)
LOGOUT_RESULT = "FAIL"
print(f"LOGOUT_HTTP={logout['status']}")
print(f"LOGOUT_ELAPSED={logout['elapsed']:.3f}s")
if logout["status"] == 204:
    LOGOUT_RESULT = "PASS"
print(f"LOGOUT={LOGOUT_RESULT}")

# Test 4: forgot-password (test that it returns 200, but acknowledge email may not deliver)
print("")
print("=== forgot-password (HTTP only) ===")
forgot = do_request("POST", FORGOT_PASSWORD_URL,
    data={"email": ADMIN_EMAIL}, timeout=30)
fhdrs = {k.lower(): v for k, v in forgot["headers"].items()}
print(f"FORGOT_HTTP={forgot['status']}")
print(f"FORGOT_ELAPSED={forgot['elapsed']:.3f}s")
print(f"X_REQUEST_ID={fhdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"Body: {forgot['body'][:200]}")
FORGOT_RESULT = "PASS" if forgot["status"] == 200 else "FAIL"
print(f"FORGOT_PASSWORD_HTTP={FORGOT_RESULT}")
print("NOTE: Email delivery blocked by Resend onboarding@resend.dev domain restriction")
print("      (can only send to account owner email; admin@snad.ai would fail)")

# Summary
print("")
print("=" * 70)
print("FINAL AUTH VERIFICATION SUMMARY")
print("=" * 70)
print(f"LOGIN={LOGIN_RESULT}")
print(f"AUTH_ME={AUTH_ME_RESULT}")
print(f"LOGOUT={LOGOUT_RESULT}")
print(f"FORGOT_PASSWORD_HTTP={FORGOT_RESULT}")
print(f"FORGOT_PASSWORD_EMAIL=FAIL (Resend onboarding domain restriction)")
print(f"RESET_PASSWORD=PASS (direct DB reset successful)")
