#!/usr/bin/env python3
"""
Phase 3b: Test Resend email delivery directly.
"""
import json
import os
import urllib.request
import urllib.error
import ssl
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

# Get Resend API key
req = urllib.request.Request(
    f"https://api.render.com/v1/services/{RENDER_SVC_ID}/env-vars/SECURITY_NOTIFICATION_RESEND_API_KEY",
    headers={"Authorization": f"Bearer {RENDER_API_KEY}", "Accept": "application/json"},
    method="GET",
)
with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
    body = json.loads(resp.read().decode("utf-8"))
    RESEND_API_KEY = body.get("envVar", body).get("value", "")
    print(f"RESEND_API_KEY: PRESENT (len={len(RESEND_API_KEY)})")

# Test 1: Send a test email via Resend API directly
print("")
print("=== Test: Send test email via Resend API ===")
payload = {
    "from": "SNAD <onboarding@resend.dev>",
    "to": "sanad.ai.app@gmail.com",
    "subject": "SNAD Password Reset Test (direct API)",
    "html": "<p>This is a direct Resend API test for password reset.</p>"
}

req = urllib.request.Request(
    "https://api.resend.com/emails",
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "Authorization": f"Bearer {RESEND_API_KEY}",
        "Content-Type": "application/json",
    },
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=20, context=ctx) as resp:
        status = resp.status
        body = resp.read().decode("utf-8")
        print(f"HTTP: {status}")
        print(f"Response: {body[:500]}")
except urllib.error.HTTPError as e:
    print(f"HTTP Error: {e.code}")
    print(f"  {e.read().decode('utf-8')[:500]}")
except Exception as e:
    print(f"Error: {e}")

# Test 2: Try sending to admin@snad.ai (old email)
print("")
print("=== Test 2: Send to admin@snad.ai ===")
payload2 = {
    "from": "SNAD <onboarding@resend.dev>",
    "to": "admin@snad.ai",
    "subject": "SNAD Password Reset Test (to admin@snad.ai)",
    "html": "<p>This is a direct Resend API test for password reset to admin@snad.ai.</p>"
}

req2 = urllib.request.Request(
    "https://api.resend.com/emails",
    data=json.dumps(payload2).encode("utf-8"),
    headers={
        "Authorization": f"Bearer {RESEND_API_KEY}",
        "Content-Type": "application/json",
    },
    method="POST",
)
try:
    with urllib.request.urlopen(req2, timeout=20, context=ctx) as resp:
        status = resp.status
        body = resp.read().decode("utf-8")
        print(f"HTTP: {status}")
        print(f"Response: {body[:500]}")
except urllib.error.HTTPError as e:
    print(f"HTTP Error: {e.code}")
    print(f"  {e.read().decode('utf-8')[:500]}")
except Exception as e:
    print(f"Error: {e}")
