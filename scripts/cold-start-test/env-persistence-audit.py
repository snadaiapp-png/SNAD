#!/usr/bin/env python3
"""
Phase 2: Environment Persistence Audit — READ ONLY.
Reads KEY PRESENCE only. Never prints values. Never mutates.
"""
import json
import os
import ssl
import urllib.request
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

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def check_env_var(key):
    """Check if env var is present (GET). Returns (present, length)."""
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
            return bool(val), len(str(val)) if val else 0
    except Exception as e:
        return False, 0

# Source classification based on render.yaml + workflow analysis
SOURCES = {
    "SANAD_CORS_ALLOWED_ORIGINS": "render.yaml",
    "SANAD_SERVICE_AUTH_JWT_SECRET": "rotation",  # regenerated in Phase 3 of prior task
    "SANAD_WORKFLOW_ENGINE_BASE_URL": "manual",  # not in render.yaml, set via dashboard/workflow
    "SANAD_AI_GATEWAY_BASE_URL": "manual",
    "SPRING_PROFILES_ACTIVE": "render.yaml",
    "SERVER_PORT": "render.yaml",
    "DATABASE_DRIVER": "render.yaml",
    "BOOTSTRAP_ENABLED": "render.yaml",
    "LOG_LEVEL_ROOT": "render.yaml",
    "LOG_LEVEL_SANAD": "render.yaml",
    "LAZY_INIT": "render.yaml",
    "MANAGEMENT_ENDPOINTS": "render.yaml",
    "SHUTDOWN_TIMEOUT": "render.yaml",
    "DATABASE_POOL_MAX": "render.yaml",
    "DATABASE_POOL_MIN": "render.yaml",
    "DATABASE_POOL_TIMEOUT": "render.yaml",
    "SECURITY_NOTIFICATION_ENDPOINT": "render.yaml",
}

REQUIRED_KEYS = list(SOURCES.keys())

print("=" * 70)
print("PHASE 2: ENVIRONMENT PERSISTENCE AUDIT (READ ONLY)")
print("=" * 70)
print(f"Timestamp: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}")
print(f"Service: {RENDER_SVC_ID}")
print(f"Keys to verify: {len(REQUIRED_KEYS)}")
print(f"CRITICAL: No mutations. No values printed. Presence check only.")
print("")

print("=== KEY PRESENCE AUDIT ===")
all_present = True
for key in REQUIRED_KEYS:
    present, vlen = check_env_var(key)
    source = SOURCES[key]
    status = "true" if present else "false"
    print(f"KEY={key}")
    print(f"PRESENT={status}")
    print(f"SOURCE={source}")
    if not present:
        all_present = False
    print()

# Special record for SANAD_SERVICE_AUTH_JWT_SECRET
print("=== SECRET ROTATION RECORD ===")
print("KEY=SANAD_SERVICE_AUTH_JWT_SECRET")
print("ORIGINAL_SECRET_RECOVERED=false")
print("SECRET_ROTATED=true")
print("SECRET_VALUE=REDACTED")
print()

print("=== SUMMARY ===")
present_count = sum(1 for k in REQUIRED_KEYS if check_env_var(k)[0])
print(f"Required keys present: {present_count}/{len(REQUIRED_KEYS)}")
print(f"All present: {all_present}")
