#!/usr/bin/env python3
"""
SANAD Production Auth Provisioning Script
- Generates new bootstrap token and password in-memory
- Updates Render env vars (additive)
- Calls bootstrap endpoint
- Locks down bootstrap immediately
- Never prints secrets
"""
import json, sys, os, secrets, string, base64, time, urllib.request, urllib.error

RENDER_API_KEY = os.environ["RENDER_API_KEY"]
RENDER_SERVICE_ID = os.environ["RENDER_SERVICE_ID"]
TENANT_ID = "958bbb1c-eece-4839-bca8-a5bfa14e6ac1"
ADMIN_EMAIL = "cp-admin@sanad-control-plane.internal"
BACKEND_URL = "https://sanad-backend-mcrj.onrender.com"

# Generate secrets in-memory only
new_token = base64.urlsafe_b64encode(secrets.token_bytes(48)).decode().rstrip('=').replace('+', '-').replace('/', '_')
new_password = ''.join(secrets.choice(string.ascii_letters + string.digits + '!@#$%^&*') for _ in range(24))

def api_call(method, url, data=None, timeout=60):
    req = urllib.request.Request(url, method=method)
    req.add_header("Authorization", f"Bearer {RENDER_API_KEY}")
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
        req.data = json.dumps(data).encode()
    try:
        resp = urllib.request.urlopen(req, timeout=timeout)
        return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body = e.read()
        return e.code, json.loads(body) if body else {}

def wait_for_health(max_wait=180):
    print("  Waiting for backend to become healthy...", flush=True)
    for attempt in range(max_wait // 5):
        try:
            req = urllib.request.Request(f"{BACKEND_URL}/actuator/health")
            resp = urllib.request.urlopen(req, timeout=10)
            data = json.loads(resp.read())
            if data.get("status") == "UP":
                print(f"  Backend healthy after ~{(attempt+1)*5}s")
                return True
        except Exception:
            pass
        time.sleep(5)
    print(f"  WARNING: Backend not healthy after {max_wait}s")
    return False

# ---- STEP 1: Fetch current env vars ----
print("[1/7] Fetching current Render env vars...", flush=True)
status, env_data = api_call("GET", f"https://api.render.com/v1/services/{RENDER_SERVICE_ID}/env-vars")
if status != 200:
    print(f"  FAIL: HTTP {status}")
    sys.exit(1)
print(f"  Current env vars: {len(env_data)} items")

# ---- STEP 2: Build updated payload ----
print("[2/7] Building updated env var payload...", flush=True)
items = []
replace_keys = {
    'SANAD_CONTROL_PLANE_TENANT_ID', 'CONTROL_PLANE_BOOTSTRAP_ENABLED',
    'CONTROL_PLANE_BOOTSTRAP_TOKEN', 'CONTROL_PLANE_ADMIN_EMAIL',
    'CONTROL_PLANE_ADMIN_PASSWORD'
}
if isinstance(env_data, list):
    for item in env_data:
        ev = item.get('envVar', item) if isinstance(item, dict) else {}
        key = ev.get('key', '') if isinstance(ev, dict) else ''
        val = ev.get('value', '') if isinstance(ev, dict) else ''
        if not key or key in replace_keys:
            continue
        items.append({'key': key, 'value': val})

items.append({'key': 'SANAD_CONTROL_PLANE_TENANT_ID', 'value': TENANT_ID})
items.append({'key': 'CONTROL_PLANE_BOOTSTRAP_ENABLED', 'value': 'true'})
items.append({'key': 'CONTROL_PLANE_BOOTSTRAP_TOKEN', 'value': new_token})
items.append({'key': 'CONTROL_PLANE_ADMIN_EMAIL', 'value': ADMIN_EMAIL})
items.append({'key': 'CONTROL_PLANE_ADMIN_PASSWORD', 'value': new_password})
print(f"  Payload: {len(items)} env vars (preserving {len(items) - 5} existing)")

# ---- STEP 3: PUT env vars to Render ----
print("[3/7] Updating Render env vars...", flush=True)
status, resp = api_call("PUT", f"https://api.render.com/v1/services/{RENDER_SERVICE_ID}/env-vars", items)
if status not in (200, 202):
    print(f"  FAIL: HTTP {status} - {json.dumps(resp)[:200]}")
    sys.exit(1)
print(f"  Env vars updated: HTTP {status}")

# ---- STEP 4: Trigger redeploy ----
print("[4/7] Triggering redeploy...", flush=True)
status, resp = api_call("POST", f"https://api.render.com/v1/services/{RENDER_SERVICE_ID}/deploys", {"clearCache": "clear"})
if status not in (200, 201, 202):
    print(f"  FAIL: HTTP {status} - {json.dumps(resp)[:200]}")
    sys.exit(1)
print(f"  Redeploy triggered: HTTP {status}")

# ---- STEP 5: Wait for health ----
print("[5/7] Waiting for backend health...", flush=True)
print("  (Waiting 60s for Render cold start...)", flush=True)
time.sleep(60)
healthy = wait_for_health(max_wait=300)

# ---- STEP 6: Call bootstrap endpoint ----
print("[6/7] Calling bootstrap endpoint...", flush=True)
try:
    req = urllib.request.Request(
        f"{BACKEND_URL}/api/v1/internal/control-plane/bootstrap-admin",
        method="POST"
    )
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Control-Plane-Bootstrap-Token", new_token)
    req.data = b'{}'
    resp = urllib.request.urlopen(req, timeout=120)
    bootstrap_result = json.loads(resp.read())
    print(f"  status: {bootstrap_result.get('status')}")
    print(f"  bootstrap: {bootstrap_result.get('bootstrap')}")
    print(f"  tenantId: {bootstrap_result.get('tenantId')}")
    print(f"  userId: {bootstrap_result.get('userId')}")
    print(f"  created: {bootstrap_result.get('created')}")
    print(f"  membershipActivated: {bootstrap_result.get('membershipActivated')}")
    print(f"  roleGrantsActivated: {bootstrap_result.get('roleGrantsActivated')}")
    if bootstrap_result.get('status') != 'ok' or bootstrap_result.get('bootstrap') != 'complete':
        print("  FAIL: Bootstrap did not complete successfully")
        sys.exit(1)
    print("  BOOTSTRAP = PASS")
except urllib.error.HTTPError as e:
    body = json.loads(e.read())
    print(f"  FAIL: HTTP {e.code} - {json.dumps(body)[:300]}")
    sys.exit(1)
except Exception as e:
    print(f"  FAIL: {e}")
    sys.exit(1)

# ---- STEP 7: Lockdown - disable bootstrap ----
print("[7/7] Disabling bootstrap mode...", flush=True)
lockdown_items = []
if isinstance(env_data, list):
    for item in env_data:
        ev = item.get('envVar', item) if isinstance(item, dict) else {}
        key = ev.get('key', '') if isinstance(ev, dict) else ''
        val = ev.get('value', '') if isinstance(ev, dict) else ''
        if not key:
            continue
        if key == 'CONTROL_PLANE_BOOTSTRAP_ENABLED':
            lockdown_items.append({'key': key, 'value': 'false'})
        elif key in ('CONTROL_PLANE_BOOTSTRAP_TOKEN', 'CONTROL_PLANE_ADMIN_PASSWORD'):
            continue  # Remove these
        elif key in ('SANAD_CONTROL_PLANE_TENANT_ID', 'CONTROL_PLANE_ADMIN_EMAIL'):
            lockdown_items.append({'key': key, 'value': val})
        else:
            lockdown_items.append({'key': key, 'value': val})

# Ensure required keys are present
existing_keys = {i['key'] for i in lockdown_items}
if 'SANAD_CONTROL_PLANE_TENANT_ID' not in existing_keys:
    lockdown_items.append({'key': 'SANAD_CONTROL_PLANE_TENANT_ID', 'value': TENANT_ID})
if 'CONTROL_PLANE_BOOTSTRAP_ENABLED' not in existing_keys:
    lockdown_items.append({'key': 'CONTROL_PLANE_BOOTSTRAP_ENABLED', 'value': 'false'})
if 'CONTROL_PLANE_ADMIN_EMAIL' not in existing_keys:
    lockdown_items.append({'key': 'CONTROL_PLANE_ADMIN_EMAIL', 'value': ADMIN_EMAIL})

status, resp = api_call("PUT", f"https://api.render.com/v1/services/{RENDER_SERVICE_ID}/env-vars", lockdown_items)
if status not in (200, 202):
    print(f"  FAIL: HTTP {status} - {json.dumps(resp)[:200]}")
    sys.exit(1)
print(f"  Bootstrap disabled: HTTP {status}")

# Trigger lockdown redeploy
print("  Triggering lockdown redeploy...", flush=True)
status, resp = api_call("POST", f"https://api.render.com/v1/services/{RENDER_SERVICE_ID}/deploys", {"clearCache": "clear"})
if status not in (200, 201, 202):
    print(f"  FAIL: HTTP {status} - {json.dumps(resp)[:200]}")
    sys.exit(1)
print(f"  Lockdown redeploy triggered: HTTP {status}")

print("  (Waiting 60s for lockdown redeploy cold start...)", flush=True)
time.sleep(60)
wait_for_health(max_wait=300)

print("\n=== PROVISIONING PHASE COMPLETE ===")
print("PROVISIONING = PASS")
print("BOOTSTRAP = complete")
print(f"TENANT_ID = {TENANT_ID}")
print(f"ADMIN_EMAIL = {ADMIN_EMAIL}")
