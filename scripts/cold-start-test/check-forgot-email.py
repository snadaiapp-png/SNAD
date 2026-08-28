#!/usr/bin/env python3
"""
Phase 2b: Check if forgot-password email was actually sent.
1. Check password_reset_tokens table for tokens created for admin@snad.ai
2. Check Resend email provider for sent emails
"""
import psycopg2
import json
import urllib.request
import urllib.error
import ssl
import os
from datetime import datetime, timezone

DB_HOST = "aws-0-eu-central-1.pooler.supabase.com"
DB_PORT = "5432"
DB_NAME = "postgres"
DB_USER = "sanad.tkbrvupemreqabwzdpyq"
DB_PASSWORD = "c0afe3e54ff26a2b6826ad68d1879fa2f53b91188f9343db27671af06e4003e0"

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

RESEND_API_KEY = ""  # We need to find this

def utc_now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

print("=" * 70)
print("PHASE 2b: CHECK FORGOT-PASSWORD EMAIL DELIVERY")
print("=" * 70)
print(f"Timestamp: {utc_now()}")
print("")

# Step 1: Check password_reset_tokens table
print("=== Step 1: Check password_reset_tokens table ===")
try:
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
        sslmode="require", connect_timeout=15,
    )
    cur = conn.cursor()

    # Get admin user ID
    cur.execute("SELECT id, email FROM users WHERE email = 'admin@snad.ai'")
    admin = cur.fetchone()
    if admin:
        admin_id = admin[0]
        print(f"Admin user: id={admin_id}, email={admin[1]}")

        # Check password_reset_tokens for this user
        print("")
        print(f"=== password_reset_tokens for admin@snad.ai ===")
        cur.execute("""
            SELECT id, user_id, status, expires_at, created_at, used_at, ip_address
            FROM password_reset_tokens
            WHERE user_id = %s
            ORDER BY created_at DESC
            LIMIT 10
        """, (admin_id,))
        rows = cur.fetchall()
        print(f"Total tokens found: {len(rows)}")
        for row in rows:
            print(f"  id={row[0]}")
            print(f"  user_id={row[1]}")
            print(f"  status={row[2]}")
            print(f"  expires_at={row[3]}")
            print(f"  created_at={row[4]}")
            print(f"  used_at={row[5]}")
            print(f"  ip_address={row[6]}")
            print("")

    # Also check ALL recent tokens
    print("=== All recent password_reset_tokens (limit 10) ===")
    cur.execute("""
        SELECT t.id, t.user_id, u.email, t.status, t.expires_at, t.created_at, t.used_at
        FROM password_reset_tokens t
        JOIN users u ON t.user_id = u.id
        ORDER BY t.created_at DESC
        LIMIT 10
    """)
    rows = cur.fetchall()
    print(f"Total recent tokens: {len(rows)}")
    for row in rows:
        print(f"  id={row[0]} user={row[2]} status={row[3]} created={row[5]} expires={row[4]} used={row[6]}")

    cur.close()
    conn.close()
except Exception as e:
    print(f"DB ERROR: {e}")
    import traceback
    traceback.print_exc()

# Step 2: Check Resend email provider
print("")
print("=== Step 2: Check Resend email provider ===")

# Find the Resend API key from Render env
RENDER_API_KEY = os.environ.get("RENDER_API_KEY", "")
RENDER_SVC_ID = "srv-d8ragqkm0tmc73bviqq0"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# Get SECURITY_NOTIFICATION_RESEND_API_KEY from Render env
try:
    req = urllib.request.Request(
        f"https://api.render.com/v1/services/{RENDER_SVC_ID}/env-vars/SECURITY_NOTIFICATION_RESEND_API_KEY",
        headers={
            "Authorization": f"Bearer {RENDER_API_KEY}",
            "Accept": "application/json",
        },
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
        body = json.loads(resp.read().decode("utf-8"))
        ev = body.get("envVar", body)
        RESEND_API_KEY = ev.get("value", "")
        print(f"RESEND_API_KEY: {'PRESENT (len=' + str(len(RESEND_API_KEY)) + ')' if RESEND_API_KEY else 'MISSING'}")
except Exception as e:
    print(f"Error getting Resend key: {e}")

# Also check for CRM_EMAIL_RESEND_API_KEY
try:
    req = urllib.request.Request(
        f"https://api.render.com/v1/services/{RENDER_SVC_ID}/env-vars/CRM_EMAIL_RESEND_API_KEY",
        headers={
            "Authorization": f"Bearer {RENDER_API_KEY}",
            "Accept": "application/json",
        },
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
        body = json.loads(resp.read().decode("utf-8"))
        ev = body.get("envVar", body)
        crm_resend = ev.get("value", "")
        print(f"CRM_EMAIL_RESEND_API_KEY: {'PRESENT (len=' + str(len(crm_resend)) + ')' if crm_resend else 'MISSING (may use SECURITY_NOTIFICATION_RESEND_API_KEY instead)'}")
except Exception as e:
    print(f"CRM_EMAIL_RESEND_API_KEY: NOT SET (error: {e})")

# Check Resend for recent emails
if RESEND_API_KEY:
    print("")
    print("=== Querying Resend for recent emails ===")
    try:
        req = urllib.request.Request(
            "https://api.resend.com/emails?limit=10",
            headers={
                "Authorization": f"Bearer {RESEND_API_KEY}",
                "Accept": "application/json",
            },
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            emails = body.get("data", [])
            print(f"Recent emails from Resend: {len(emails)}")
            for email in emails[:10]:
                print(f"  id={email.get('id')}")
                print(f"  to={email.get('to')}")
                print(f"  subject={email.get('subject')}")
                print(f"  status={email.get('status')}")
                print(f"  created_at={email.get('created_at')}")
                print(f"  from={email.get('from')}")
                print("")
    except urllib.error.HTTPError as e:
        print(f"Resend API error: HTTP {e.code}")
        print(f"  {e.read().decode('utf-8')[:500]}")
    except Exception as e:
        print(f"Resend query error: {e}")
else:
    print("Cannot query Resend — no API key")
