#!/usr/bin/env python3
"""Phase 9b: auth/me + logout using token from the cold-start login."""
import json
import time
import urllib.request
import urllib.error
import ssl
from datetime import datetime, timezone

LOGIN_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/login"
AUTH_ME_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/me"
LOGOUT_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/logout"

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
            return resp.status, dict(resp.headers), resp.read().decode("utf-8", errors="replace"), time.monotonic() - start_ts
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers) if e.headers else {}, e.read().decode("utf-8", errors="replace") if e.fp else "", time.monotonic() - start_ts
    except Exception as ex:
        return None, {}, "", time.monotonic() - start_ts

# Step 1: Fresh login (warm)
print("=== Fresh login (warm) ===")
status, hdrs, body, elapsed = do_request("POST", LOGIN_URL,
    data={"email": "admin@snad.ai", "password": "Senen@001985"}, timeout=30)
print(f"LOGIN_HTTP={status}  elapsed={elapsed:.3f}s")

if status != 200:
    print("ABORT")
    exit(1)

token = json.loads(body).get("accessToken") or json.loads(body).get("token")
auth_header = {"Authorization": f"Bearer {token}"}

# Step 2: auth/me
print("\n=== auth/me ===")
status, hdrs, body, elapsed = do_request("GET", AUTH_ME_URL, headers=auth_header, timeout=30)
print(f"AUTH_ME_HTTP={status}  elapsed={elapsed:.3f}s")
if status == 200:
    me = json.loads(body)
    print(f"  status={me.get('status')}")
    print(f"  email={me.get('email')}")
    print(f"  tenantId={me.get('tenantId')}")

# Step 3: logout
print("\n=== logout ===")
status, hdrs, body, elapsed = do_request("POST", LOGOUT_URL, headers=auth_header, timeout=30)
print(f"LOGOUT_HTTP={status}  elapsed={elapsed:.3f}s")
