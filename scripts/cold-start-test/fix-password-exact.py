#!/usr/bin/env python3
"""
Fix: Set password to exactly 'Senen@001985' (what the user knows).
The screenshot showed the user typing 'nen@001985' (missing 'S', lowercase).
We'll set the password to 'Senen@001985' and verify login.
Also verify the email is sanad.ai.app@gmail.com.
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
# The password the user expects (from the screenshot, they typed 'nen@001985'
# but the correct password is 'Senen@001985' with capital S)
CORRECT_PASSWORD = "Senen@001985"

LOGIN_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/login"
AUTH_ME_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/me"
LOGOUT_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/logout"
FORGOT_PASSWORD_URL = "https://snad-app.vercel.app/api/platform/api/v1/auth/forgot-password"

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
print("FIX: SET PASSWORD TO EXACTLY 'Senen@001985'")
print("=" * 70)
print(f"Timestamp: {utc_now()}")
print(f"Email: sanad.ai.app@gmail.com")
print(f"Password to set: Senen@001985 (capital S, then 'enen@001985')")
print("")

# Step 1: Generate BCrypt hash
print("=== Step 1: Generate BCrypt hash ===")
password_bytes = CORRECT_PASSWORD.encode("utf-8")
salt = bcrypt.gensalt(rounds=10)
password_hash = bcrypt.hashpw(password_bytes, salt).decode("utf-8")
print(f"  Hash: {password_hash[:20]}... (len={len(password_hash)})")

# Verify
if bcrypt.checkpw(password_bytes, password_hash.encode("utf-8")):
    print("  Hash verification: PASS")
else:
    print("  Hash verification: FAIL")
    exit(1)

# Step 2: Update password in DB
print("")
print("=== Step 2: Update password in DB ===")
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

# Update password hash
cur.execute("""
    UPDATE users
    SET password_hash = %s,
        password_set_at = NOW(),
        password_set_by = 'final-auth-fix',
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

cur.close()
conn.close()
print("  DB update: SUCCESS")

# Wait for connection pool
print("")
print("=== Wait 3s for DB connection pool ===")
time.sleep(3)

# Step 3: Test login with EXACT password
print("")
print("=== Step 3: Test login with 'Senen@001985' ===")
login = do_request("POST", LOGIN_URL,
    data={"email": "sanad.ai.app@gmail.com", "password": CORRECT_PASSWORD}, timeout=30)
hdrs = {k.lower(): v for k, v in login["headers"].items()}
print(f"LOGIN_HTTP={login['status']}")
print(f"LOGIN_ELAPSED={login['elapsed']:.3f}s")
print(f"X_REQUEST_ID={hdrs.get('x-request-id', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ATTEMPTS={hdrs.get('x-sanad-bff-attempts', 'NOT_PRESENT')}")
print(f"X_SANAD_BFF_ERROR={hdrs.get('x-sanad-bff-error', 'NOT_PRESENT')}")

token = None
if login["status"] == 200:
    body = json.loads(login["body"])
    token = body.get("accessToken") or body.get("token")
    user = body.get("user", {})
    print(f"  user.email={user.get('email')}")
    print(f"  user.status={user.get('status')}")
    print(f"  LOGIN=PASS")
else:
    print(f"  body: {login['body'][:300]}")
    print(f"  LOGIN=FAIL")

# Step 4: auth/me
if token:
    print("")
    print("=== Step 4: auth/me ===")
    auth_header = {"Authorization": f"Bearer {token}"}
    me = do_request("GET", AUTH_ME_URL, headers=auth_header, timeout=30)
    print(f"AUTH_ME_HTTP={me['status']}")
    if me["status"] == 200:
        me_body = json.loads(me["body"])
        print(f"  status={me_body.get('status')}")
        print(f"  email={me_body.get('email')}")
        print(f"  AUTH_ME=PASS")

    # Step 5: logout
    print("")
    print("=== Step 5: logout ===")
    logout = do_request("POST", LOGOUT_URL, headers=auth_header, timeout=30)
    print(f"LOGOUT_HTTP={logout['status']}")
    if logout["status"] == 204:
        print(f"  LOGOUT=PASS")

# Step 6: Test login with WRONG password (the one from screenshot)
print("")
print("=== Step 6: Verify WRONG password fails (nen@001985) ===")
wrong_login = do_request("POST", LOGIN_URL,
    data={"email": "sanad.ai.app@gmail.com", "password": "nen@001985"}, timeout=30)
print(f"WRONG_LOGIN_HTTP={wrong_login['status']} (should be 401)")
if wrong_login["status"] == 401:
    print(f"  WRONG_PASSWORD_REJECTED=PASS")

# Step 7: Test forgot-password (HTTP only)
print("")
print("=== Step 7: forgot-password ===")
forgot = do_request("POST", FORGOT_PASSWORD_URL,
    data={"email": "sanad.ai.app@gmail.com"}, timeout=30)
print(f"FORGOT_HTTP={forgot['status']}")
print(f"  FORGOT_PASSWORD_HTTP={'PASS' if forgot['status'] == 200 else 'FAIL'}")

# Summary
print("")
print("=" * 70)
print("SUMMARY")
print("=" * 70)
print(f"Email: sanad.ai.app@gmail.com")
print(f"Password: Senen@001985 (capital S)")
print(f"LOGIN={'PASS' if login['status'] == 200 else 'FAIL'}")
if token:
    print(f"AUTH_ME={'PASS' if me['status'] == 200 else 'FAIL'}")
    print(f"LOGOUT={'PASS' if logout['status'] == 204 else 'FAIL'}")
print(f"FORGOT_PASSWORD_HTTP={'PASS' if forgot['status'] == 200 else 'FAIL'}")
print("")
print("IMPORTANT: The password is 'Senen@001985' with a CAPITAL 'S'.")
print("The user was typing 'nen@001985' (lowercase, missing 'S').")
