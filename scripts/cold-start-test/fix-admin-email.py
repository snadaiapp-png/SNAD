#!/usr/bin/env python3
"""
Phase 3: Update admin user email to sanad.ai.app@gmail.com.
This is the Resend account owner email — the only address onboarding@resend.dev can deliver to.
Then trigger forgot-password to send a real reset email.
"""
import psycopg2
import json
import urllib.request
import urllib.error
import ssl
import time
from datetime import datetime, timezone

DB_HOST = "aws-0-eu-central-1.pooler.supabase.com"
DB_PORT = "5432"
DB_NAME = "postgres"
DB_USER = "sanad.tkbrvupemreqabwzdpyq"
DB_PASSWORD = "c0afe3e54ff26a2b6826ad68d1879fa2f53b91188f9343db27671af06e4003e0"

ADMIN_USER_ID = "00000000-0000-0000-0000-000000000010"
OLD_EMAIL = "admin@snad.ai"
NEW_EMAIL = "sanad.ai.app@gmail.com"

FORGOT_PASSWORD_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/forgot-password"
RESET_PASSWORD_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/reset-password"
LOGIN_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/login"
AUTH_ME_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/me"
LOGOUT_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/logout"

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
print("PHASE 3: UPDATE ADMIN EMAIL + TRIGGER FORGOT-PASSWORD")
print("=" * 70)
print(f"Timestamp: {utc_now()}")
print(f"Admin user ID: {ADMIN_USER_ID}")
print(f"Old email: {OLD_EMAIL}")
print(f"New email: {NEW_EMAIL}")
print("")

# Step 1: Update email in DB
print("=== Step 1: Update admin user email in DB ===")
try:
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
        sslmode="require", connect_timeout=15,
    )
    cur = conn.cursor()

    # Check current state
    cur.execute("SELECT id, email, status FROM users WHERE id = %s", (ADMIN_USER_ID,))
    row = cur.fetchone()
    print(f"  Before: id={row[0]} email={row[1]} status={row[2]}")

    # Update email
    cur.execute("UPDATE users SET email = %s, updated_at = NOW() WHERE id = %s", (NEW_EMAIL, ADMIN_USER_ID))
    conn.commit()
    print(f"  UPDATE rows affected: {cur.rowcount}")

    # Verify
    cur.execute("SELECT id, email, status FROM users WHERE id = %s", (ADMIN_USER_ID,))
    row = cur.fetchone()
    print(f"  After: id={row[0]} email={row[1]} status={row[2]}")

    # Also check no duplicate email
    cur.execute("SELECT id, email FROM users WHERE email = %s", (NEW_EMAIL,))
    rows = cur.fetchall()
    print(f"  Users with new email: {len(rows)}")
    for r in rows:
        print(f"    id={r[0]} email={r[1]}")

    cur.close()
    conn.close()
    print("  DB update: SUCCESS")
except Exception as e:
    print(f"  DB ERROR: {e}")
    import traceback
    traceback.print_exc()
    exit(1)

# Step 2: Trigger forgot-password
print("")
print("=== Step 2: Trigger forgot-password (should send real email) ===")
forgot = do_request("POST", FORGOT_PASSWORD_URL,
    data={"email": NEW_EMAIL}, timeout=30)
print(f"FORGOT_HTTP={forgot['status']}")
print(f"FORGOT_ELAPSED={forgot['elapsed']:.3f}s")
fhdrs = {k.lower(): v for k, v in forgot["headers"].items()}
print(f"X_REQUEST_ID={fhdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={fhdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")
print(f"Body: {forgot['body'][:300]}")

# Step 3: Check DB for new token status
print("")
print("=== Step 3: Check new token status in DB ===")
import time as _time
_time.sleep(2)  # Allow time for email delivery to complete

try:
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
        sslmode="require", connect_timeout=15,
    )
    cur = conn.cursor()
    cur.execute("""
        SELECT id, user_id, status, expires_at, created_at, used_at
        FROM password_reset_tokens
        WHERE user_id = %s
        ORDER BY created_at DESC
        LIMIT 3
    """, (ADMIN_USER_ID,))
    rows = cur.fetchall()
    print(f"Recent tokens: {len(rows)}")
    for row in rows:
        print(f"  id={row[0]}")
        print(f"  user_id={row[1]}")
        print(f"  status={row[2]}")
        print(f"  expires_at={row[3]}")
        print(f"  created_at={row[4]}")
        print(f"  used_at={row[5]}")
        print("")
    cur.close()
    conn.close()
except Exception as e:
    print(f"DB ERROR: {e}")
