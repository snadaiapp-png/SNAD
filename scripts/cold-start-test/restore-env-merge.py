#!/usr/bin/env python3
"""
Phase 3: Restore missing Render env vars using MERGE semantics (per-key PUT).

CRITICAL: Uses PUT /v1/services/{id}/env-vars/{key} (per-key) NOT
PUT /v1/services/{id}/env-vars (bulk REPLACE).

The bulk REPLACE endpoint DELETES all env vars not in the payload.
The per-key PUT endpoint MERGES — only the specified key is affected.
"""
import json
import os
import ssl
import sys
import urllib.request
import urllib.error
from datetime import datetime, timezone

# Load secrets
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

# Generate a fresh SANAD_SERVICE_AUTH_JWT_SECRET (32+ bytes hex)
import secrets as pysecrets
NEW_SERVICE_AUTH_SECRET = pysecrets.token_hex(32)  # 64 hex chars = 32 bytes

# Env vars to restore (from render.yaml + guard requirements)
# Values sourced from render.yaml (authorized source) — non-secret values only
# For SANAD_SERVICE_AUTH_JWT_SECRET: regenerated (original not recoverable)
ENVS_TO_RESTORE = {
    # Missing from render.yaml Blueprint — must be present for ProductionSecurityGuard
    "SANAD_CORS_ALLOWED_ORIGINS": {
        "value": "https://snad-app.vercel.app",
        "source": "render.yaml line: SANAD_CORS_ALLOWED_ORIGINS value: https://snad-app.vercel.app",
    },
    # Missing from render.yaml — required by ProductionWorkflowStubGuard (>=32 chars)
    "SANAD_SERVICE_AUTH_JWT_SECRET": {
        "value": NEW_SERVICE_AUTH_SECRET,
        "source": "REGENERATED (original was set via dashboard, not recoverable; 32-byte hex)",
    },
    # Missing from render.yaml Blueprint
    "SPRING_PROFILES_ACTIVE": {
        "value": "prod",
        "source": "render.yaml line: SPRING_PROFILES_ACTIVE value: prod",
    },
    "SERVER_PORT": {
        "value": "8080",
        "source": "render.yaml line: SERVER_PORT value: 8080",
    },
    "DATABASE_DRIVER": {
        "value": "org.postgresql.Driver",
        "source": "render.yaml line: DATABASE_DRIVER value: org.postgresql.Driver",
    },
    "BOOTSTRAP_ENABLED": {
        "value": "false",
        "source": "render.yaml line: BOOTSTRAP_ENABLED value: false",
    },
    "LOG_LEVEL_ROOT": {
        "value": "WARN",
        "source": "render.yaml line: LOG_LEVEL_ROOT value: WARN",
    },
    "LOG_LEVEL_SANAD": {
        "value": "INFO",
        "source": "render.yaml line: LOG_LEVEL_SANAD value: INFO",
    },
    "LAZY_INIT": {
        "value": "true",
        "source": "render.yaml line: LAZY_INIT value: true",
    },
    "MANAGEMENT_ENDPOINTS": {
        "value": "health",
        "source": "render.yaml line: MANAGEMENT_ENDPOINTS value: health",
    },
    "SHUTDOWN_TIMEOUT": {
        "value": "30s",
        "source": "render.yaml line: SHUTDOWN_TIMEOUT value: 30s",
    },
    "DATABASE_POOL_MAX": {
        "value": "3",
        "source": "render.yaml line: DATABASE_POOL_MAX value: 3",
    },
    "DATABASE_POOL_MIN": {
        "value": "1",
        "source": "render.yaml line: DATABASE_POOL_MIN value: 1",
    },
    "DATABASE_POOL_TIMEOUT": {
        "value": "30000",
        "source": "render.yaml line: DATABASE_POOL_TIMEOUT value: 30000",
    },
    "SECURITY_NOTIFICATION_ENDPOINT": {
        "value": "https://snad-app.vercel.app/api/email-proxy",
        "source": "render.yaml line: SECURITY_NOTIFICATION_ENDPOINT value: https://snad-app.vercel.app/api/email-proxy",
    },
    # Also restore SECURITY_NOTIFICATION_FROM (was present but let me verify)
    "SECURITY_NOTIFICATION_FROM": {
        "value": "SNAD <onboarding@resend.dev>",
        "source": "pre-existing value observed in earlier GET (not from render.yaml)",
    },
}

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def utc_now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def put_env_var(key, value):
    """PUT a single env var using MERGE semantics (per-key PUT)."""
    url = f"https://api.render.com/v1/services/{RENDER_SVC_ID}/env-vars/{key}"
    payload = json.dumps({"value": value}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Authorization": f"Bearer {RENDER_API_KEY}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
        method="PUT",
    )
    try:
        with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
            status = resp.status
            body = resp.read().decode("utf-8", errors="replace")
            return status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return None, str(e)

def verify_env_var(key):
    """Verify an env var is present (GET)."""
    url = f"https://api.render.com/v1/services/{RENDER_SVC_ID}/env-vars/{key}"
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
            body = json.loads(resp.read().decode("utf-8"))
            ev = body.get("envVar", body)
            val = ev.get("value", "")
            return bool(val), len(str(val))
    except Exception as e:
        return False, 0

print("=" * 70)
print("PHASE 3: RESTORE ENVIRONMENT (MERGE semantics — per-key PUT)")
print("=" * 70)
print(f"Timestamp: {utc_now()}")
print(f"Service: {RENDER_SVC_ID}")
print(f"Envs to restore: {len(ENVS_TO_RESTORE)}")
print(f"API: PUT /v1/services/{{id}}/env-vars/{{key}} (per-key, MERGE)")
print(f"CRITICAL: NOT using bulk PUT (which REPLACEs the entire env set)")
print("")

results = []
for key, info in ENVS_TO_RESTORE.items():
    value = info["value"]
    source = info["source"]
    # Mask the value for display
    if key == "SANAD_SERVICE_AUTH_JWT_SECRET":
        display_value = f"<REDACTED len={len(value)}>"
    else:
        display_value = value

    print(f"--- Restoring {key} ---")
    print(f"  source: {source}")
    print(f"  value: {display_value}")

    status, body = put_env_var(key, value)
    print(f"  PUT status: HTTP {status}")

    # Verify
    present, vlen = verify_env_var(key)
    print(f"  verify: present={present}, value_len={vlen}")
    print(f"  KEY={key}  ACTION={'RESTORED' if present else 'FAILED'}  VALUE={'REDACTED' if 'SECRET' in key or 'PASSWORD' in key else display_value}")
    print("")

    results.append({
        "key": key,
        "put_status": status,
        "verified_present": present,
        "value_len": vlen,
        "source": source,
    })

print("=" * 70)
print("PHASE 3 SUMMARY")
print("=" * 70)
restored = sum(1 for r in results if r["verified_present"])
failed = sum(1 for r in results if not r["verified_present"])
print(f"Total restored: {restored}/{len(results)}")
print(f"Failed: {failed}")
print("")

# Save results
with open("/home/z/my-project/scripts/cold-start-test/env-restore-results.json", "w") as f:
    json.dump(results, f, indent=2, default=str)

print("Results saved to: /home/z/my-project/scripts/cold-start-test/env-restore-results.json")
print("")

# Final verification of critical keys
print("=== FINAL VERIFICATION OF CRITICAL KEYS ===")
critical_keys = ["SANAD_CORS_ALLOWED_ORIGINS", "SANAD_SERVICE_AUTH_JWT_SECRET",
                 "SPRING_PROFILES_ACTIVE", "JWT_SECRET", "DATABASE_URL"]
for key in critical_keys:
    present, vlen = verify_env_var(key)
    status = "PRESENT" if present else "MISSING"
    print(f"  {key}: {status} (len={vlen})")
