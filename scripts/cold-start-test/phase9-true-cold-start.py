#!/usr/bin/env python3
"""
Phase 9: True Cold-Start Auth Acceptance
Triggers a Render restart (same image) and immediately issues ONE login.
Captures the full timeline: Render start → Spring ready → Login → Auth/me.
"""
import json
import os
import ssl
import time
import urllib.request
import urllib.error
from datetime import datetime, timezone

with open("/tmp/my-project/.secrets") as f:
    for line in f:
        line = line.strip()
        if line.startswith("export "):
            line = line[7:]
        if "=" in line and not line.startswith("#"):
            k, _, v = line.partition("=")
            v = v.strip()
            if (v.startswith("'") and v.endswith("'")) or (v.startswith('"') and v.endswith('"')):
                v = v[1:-1]
            os.environ[k.strip()] = v

RENDER_API_KEY = os.environ["RENDER_API_KEY"]
RENDER_SVC_ID = "srv-d8ragqkm0tmc73bviqq0"
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

def do_request(method, url, headers=None, data=None, timeout=200):
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

def trigger_render_deploy():
    """Trigger a new deploy (restart) of the Render service."""
    url = f"https://api.render.com/v1/services/{RENDER_SVC_ID}/deploys"
    req = urllib.request.Request(
        url,
        data=json.dumps({"clearCache": False}).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {RENDER_API_KEY}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=20, context=ctx) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return {"error": str(e)}

def get_deploy_status(deploy_id):
    url = f"https://api.render.com/v1/services/{RENDER_SVC_ID}/deploys/{deploy_id}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {RENDER_API_KEY}",
            "Accept": "application/json",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return {"error": str(e)}

def get_render_events(limit=10):
    url = f"https://api.render.com/v1/services/{RENDER_SVC_ID}/events?limit={limit}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {RENDER_API_KEY}",
            "Accept": "application/json",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return []

print("=" * 70)
print("PHASE 9: TRUE COLD-START AUTH ACCEPTANCE")
print("=" * 70)
print(f"Start: {utc_now()}")
print("")

# Step 1: Trigger Render restart (deploy same image)
print("--- Step 1: Trigger Render restart (deploy same image) ---")
RENDER_START_TS = utc_now()
deploy = trigger_render_deploy()
deploy_id = deploy.get("id")
print(f"RENDER_START_TS={RENDER_START_TS}")
print(f"DEPLOY_ID={deploy_id}")
print(f"DEPLOY_IMAGE={deploy.get('image',{}).get('ref','')[:80]}")
print("")

# Step 2: Wait for deploy to go live
print("--- Step 2: Wait for deploy to go live (poll every 20s, max 8 min) ---")
SPRING_STARTED_TS = None
RENDER_READY_TS = None
for i in range(1, 25):
    time.sleep(20)
    d = get_deploy_status(deploy_id)
    status = d.get("status", "?")
    print(f"  poll {i} ({i*20}s): status={status}")
    if status == "live":
        RENDER_READY_TS = utc_now()
        SPRING_STARTED_TS = d.get("finishedAt", RENDER_READY_TS)
        print(f"  DEPLOY LIVE!")
        print(f"  startedAt={d.get('startedAt')}")
        print(f"  finishedAt={d.get('finishedAt')}")
        break
    elif status in ("update_failed", "canceled", "build_failed"):
        print(f"  DEPLOY FAILED: {status}")
        print(f"  Failure details: {json.dumps(d, indent=2)[:500]}")
        break

print("")
if RENDER_READY_TS is None:
    print("COLD_START_FAILED: Deploy did not go live")
    exit(1)

# Step 3: Immediate login (first request after restart)
print("--- Step 3: Immediate login (FIRST request after restart) ---")
LOGIN_START_TS = utc_now()
print(f"LOGIN_START_TS={LOGIN_START_TS}")
login = do_request("POST", LOGIN_URL,
                   data={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
                   timeout=200)
LOGIN_END_TS = utc_now()
LOGIN_ELAPSED = login["elapsed"]

print(f"LOGIN_END_TS={LOGIN_END_TS}")
print(f"LOGIN_ELAPSED={LOGIN_ELAPSED:.3f}s")
print(f"LOGIN_HTTP={login['status']}")
hdrs = {k.lower(): v for k, v in login["headers"].items()}
print(f"X_REQUEST_ID={hdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ATTEMPTS={hdrs.get('x-sanad-bff-attempts', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={hdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")

# Step 4: auth/me + logout (if login succeeded)
AUTH_ME_HTTP = None
if login["status"] == 200:
    try:
        body = json.loads(login["body"])
        token = body.get("accessToken") or body.get("token") or body.get("access_token")
    except:
        token = None

    if token:
        auth_header = {"Authorization": f"Bearer {token}"}
        print("")
        print("--- Step 4: auth/me ---")
        me = do_request("GET", AUTH_ME_URL, headers=auth_header, timeout=30)
        AUTH_ME_HTTP = me["status"]
        print(f"AUTH_ME_HTTP={AUTH_ME_HTTP}")
        print(f"AUTH_ME_ELAPSED={me['elapsed']:.3f}s")

        print("")
        print("--- Step 5: logout ---")
        logout = do_request("POST", LOGOUT_URL, headers=auth_header, timeout=30)
        print(f"LOGOUT_HTTP={logout['status']}")
        print(f"LOGOUT_ELAPSED={logout['elapsed']:.3f}s")

# Save results
results = {
    "render_start_ts": RENDER_START_TS,
    "deploy_id": deploy_id,
    "spring_started_ts": SPRING_STARTED_TS,
    "render_ready_ts": RENDER_READY_TS,
    "login_start_ts": LOGIN_START_TS,
    "login_end_ts": LOGIN_END_TS,
    "login_elapsed": LOGIN_ELAPSED,
    "login_http": login["status"],
    "x_request_id": hdrs.get("x-request-id"),
    "x_sanad_bff_attempts": hdrs.get("x-sanad-bff-attempts"),
    "x_sanad_bff_error": hdrs.get("x-sanad-bff-error"),
    "auth_me_http": AUTH_ME_HTTP,
}
with open("/home/z/my-project/scripts/cold-start-test/phase9-cold-start-results.json", "w") as f:
    json.dump(results, f, indent=2, default=str)

print("")
print("=" * 70)
print("PHASE 9 SUMMARY")
print("=" * 70)
print(json.dumps(results, indent=2, default=str))
