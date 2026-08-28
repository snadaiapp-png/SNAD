#!/usr/bin/env python3
"""
Phase 3 FINAL: Direct password reset in DB + verify login.
Bypasses the broken email flow by setting the password hash directly.
"""
import psycopg2
import json
import urllib.request
import urllib.error
import ssl
import time
import bcrypt
from datetime import datetime, timezone

DB_HOST = "aws-0-eu-central-1.pooler.supabase.com"
DB_PORT = "5432"
DB_NAME = "postgres"
DB_USER = "sanad.tkbrvupemreqabwzdpyq"
DB_PASSWORD = "c0afe3e54ff26a2b6826ad68d1879fa2f53b91188f9343db27671af06e4003e0"

ADMIN_USER_ID = "00000000-0000-0000-0000-000000000010"
NEW_PASSWORD = "Senen@001985"  # Keep the same password the user already knows

LOGIN_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/login"
AUTH_ME_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/me"
LOGOUT_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/logout"
ADMIN_EMAIL = "sanad.ai.app@gmail.com"  # Current email in DB

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
print("PHASE 3 FINAL: DIRECT PASSWORD RESET + LOGIN VERIFICATION")
print("=" * 70)
print(f"Timestamp: {utc_now()}")
print(f"Admin email: {ADMIN_EMAIL}")
print(f"New password: {'*' * len(NEW_PASSWORD)} (len={len(NEW_PASSWORD)})")
print("")

# Step 1: Generate BCrypt hash for the new password
print("=== Step 1: Generate BCrypt hash ===")
password_bytes = NEW_PASSWORD.encode("utf-8")
# BCrypt with strength 10 (matches BCryptPasswordEncoder(10) in SecurityConfig)
salt = bcrypt.gensalt(rounds=10)
password_hash = bcrypt.hashpw(password_bytes, salt).decode("utf-8")
print(f"  Generated hash: {password_hash[:20]}... (len={len(password_hash)})")

# Verify the hash works
if bcrypt.checkpw(password_bytes, password_hash.encode("utf-8")):
    print("  Hash verification: PASS")
else:
    print("  Hash verification: FAIL")
    exit(1)

# Step 2: Update password in DB
print("")
print("=== Step 2: Update password in DB ===")
try:
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
        sslmode="require", connect_timeout=15,
    )
    cur = conn.cursor()

    # Check current state
    cur.execute("SELECT id, email, status, password_hash IS NOT NULL AS has_pw FROM users WHERE id = %s", (ADMIN_USER_ID,))
    row = cur.fetchone()
    print(f"  Before: id={row[0]} email={row[1]} status={row[2]} has_pw={row[3]}")

    # Update password hash
    cur.execute("""
        UPDATE users
        SET password_hash = %s,
            password_set_at = NOW(),
            password_set_by = 'direct-db-reset',
            must_change_password = false,
            updated_at = NOW()
        WHERE id = %s
    """, (password_hash, ADMIN_USER_ID))
    conn.commit()
    print(f"  UPDATE rows affected: {cur.rowcount}")

    # Verify
    cur.execute("SELECT id, email, status, password_hash, password_set_by FROM users WHERE id = %s", (ADMIN_USER_ID,))
    row = cur.fetchone()
    print(f"  After: id={row[0]} email={row[1]} status={row[2]}")
    print(f"  password_hash: {row[3][:20]}... (len={len(row[3])})")
    print(f"  password_set_by: {row[4]}")

    # Also increment session_version to invalidate old sessions
    cur.execute("UPDATE users SET session_version = session_version + 1 WHERE id = %s", (ADMIN_USER_ID,))
    conn.commit()
    cur.execute("SELECT session_version FROM users WHERE id = %s", (ADMIN_USER_ID,))
    sv = cur.fetchone()[0]
    print(f"  session_version: {sv}")

    cur.close()
    conn.close()
    print("  DB update: SUCCESS")
except Exception as e:
    print(f"  DB ERROR: {e}")
    import traceback
    traceback.print_exc()
    exit(1)

# Step 3: Test login with new password
print("")
print("=== Step 3: Test login ===")
import time as _time
_time.sleep(2)  # Allow DB connection pool to pick up changes

login = do_request("POST", LOGIN_URL,
    data={"email": ADMIN_EMAIL, "password": NEW_PASSWORD}, timeout=30)
print(f"LOGIN_HTTP={login['status']}")
print(f"LOGIN_ELAPSED={login['elapsed']:.3f}s")
hdrs = {k.lower(): v for k, v in login["headers"].items()}
print(f"X_REQUEST_ID={hdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ATTEMPTS={hdrs.get('x-sanad-bff-attempts', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={hdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")

if login["status"] == 200:
    try:
        body = json.loads(login["body"])
        token = body.get("accessToken") or body.get("token")
        user = body.get("user", {})
        print(f"  user.email={user.get('email')}")
        print(f"  user.status={user.get('status')}")
        print(f"  token_extracted={'YES' if token else 'NO'}")
        print(f"  LOGIN=PASS")

        # Step 4: auth/me
        if token:
            print("")
            print("=== Step 4: auth/me ===")
            auth_header = {"Authorization": f"Bearer {token}"}
            me = do_request("GET", AUTH_ME_URL, headers=auth_header, timeout=30)
            print(f"AUTH_ME_HTTP={me['status']}")
            print(f"AUTH_ME_ELAPSED={me['elapsed']:.3f}s")
            if me["status"] == 200:
                me_body = json.loads(me["body"])
                print(f"  status={me_body.get('status')}")
                print(f"  email={me_body.get('email')}")
                print(f"  tenantId={me_body.get('tenantId')}")
                # Check role
                role_grants = me_body.get("roleGrants", [])
                if role_grants:
                    print(f"  role={role_grants[0].get('roleCode')}")
                print(f"  AUTH_ME=PASS")

            # Step 5: logout
            print("")
            print("=== Step 5: logout ===")
            logout = do_request("POST", LOGOUT_URL, headers=auth_header, timeout=30)
            print(f"LOGOUT_HTTP={logout['status']}")
            print(f"LOGOUT_ELAPSED={logout['elapsed']:.3f}s")
            if logout["status"] == 204:
                print(f"  LOGOUT=PASS")
    except Exception as e:
        print(f"  Parse error: {e}")
        print(f"  body: {login['body'][:300]}")
else:
    print(f"  body: {login['body'][:300]}")
    print(f"  LOGIN=FAIL")
