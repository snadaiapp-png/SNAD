#!/usr/bin/env python3
"""
SANAD Platform - Secure Password Generator & bcrypt Hasher
Generates strong new passwords for Admin and Identity B users,
creates bcrypt hashes compatible with Spring Security BCryptPasswordEncoder.
"""

import secrets
import string
import json
import bcrypt
from datetime import datetime, timezone
from pathlib import Path

# ---------- Configuration ----------
# bcrypt cost factor — must match Spring Security default (10)
BCRYPT_COST = 10

# Password policy: 20 chars, mixed case + digits + symbols
PASSWORD_LENGTH = 20
ALPHABET = string.ascii_letters + string.digits + "!@#$%^&*-_=+"

# ---------- Password Generation ----------
def generate_strong_password(length: int = PASSWORD_LENGTH) -> str:
    """Generate a cryptographically secure password with guaranteed complexity."""
    while True:
        # Use secrets module for cryptographic security
        pwd = ''.join(secrets.choice(ALPHABET) for _ in range(length))
        # Guarantee complexity: at least one of each category
        has_lower = any(c in string.ascii_lowercase for c in pwd)
        has_upper = any(c in string.ascii_uppercase for c in pwd)
        has_digit = any(c in string.digits for c in pwd)
        has_symbol = any(c in "!@#$%^&*-_=+" for c in pwd)
        if has_lower and has_upper and has_digit and has_symbol:
            return pwd

# ---------- bcrypt Hashing ----------
def hash_password_bcrypt(password: str) -> str:
    """
    Produce a Spring Security-compatible bcrypt hash.
    Spring's BCryptPasswordEncoder uses cost 10 by default and outputs
    the standard $2a$ format. We replicate that here.
    """
    salt = bcrypt.gensalt(rounds=BCRYPT_COST, prefix=b"2a")
    hashed = bcrypt.hashpw(password.encode('utf-8'), salt)
    return hashed.decode('utf-8')

# ---------- Verification ----------
def verify_hash(password: str, hash_str: str) -> bool:
    """Sanity check: verify the hash matches the password."""
    return bcrypt.checkpw(password.encode('utf-8'), hash_str.encode('utf-8'))

# ---------- Main ----------
def main():
    print("=" * 70)
    print("SANAD Platform — Secure Password Rotation")
    print(f"Generated at: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 70)

    # Generate passwords
    admin_password = generate_strong_password()
    identity_b_password = generate_strong_password()

    # Hash passwords
    admin_hash = hash_password_bcrypt(admin_password)
    identity_b_hash = hash_password_bcrypt(identity_b_password)

    # Verify hashes (sanity check)
    assert verify_hash(admin_password, admin_hash), "Admin hash verification failed!"
    assert verify_hash(identity_b_password, identity_b_hash), "Identity B hash verification failed!"

    # ---------- Display Results ----------
    print("\n[1] ADMIN CREDENTIALS")
    print("-" * 50)
    print(f"Password:   {admin_password}")
    print(f"bcrypt:     {admin_hash}")
    print(f"Verified:   YES")

    print("\n[2] IDENTITY B CREDENTIALS")
    print("-" * 50)
    print(f"Password:   {identity_b_password}")
    print(f"bcrypt:     {identity_b_hash}")
    print(f"Verified:   YES")

    # ---------- Save JSON manifest (for secure storage) ----------
    manifest = {
        "platform": "SANAD",
        "rotation_date": datetime.now(timezone.utc).isoformat(),
        "bcrypt_cost": BCRYPT_COST,
        "credentials": {
            "admin": {
                "username": "admin",
                "password": admin_password,
                "bcrypt_hash": admin_hash,
                "verification": "PASSED"
            },
            "identity_b": {
                "username": "identity-b",
                "password": identity_b_password,
                "bcrypt_hash": identity_b_hash,
                "verification": "PASSED"
            }
        },
        "security_notes": [
            "Store this file in a secure secrets manager (NOT in git).",
            "Delete this file after recording credentials in vault.",
            "Previous Identity B password (PilotB2026!Secure) is COMPROMISED — revoke all sessions."
        ]
    }

    manifest_path = Path("/home/z/my-project/download/sanad_credentials_rotation.json")
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding='utf-8')
    print(f"\n[3] Credentials manifest saved to: {manifest_path}")
    print("    (Treat this file as TOP SECRET — delete after recording to vault)")

    # ---------- Generate SQL update script ----------
    sql_script = f"""-- =====================================================================
-- SANAD Platform — Password Rotation SQL Script
-- Generated: {datetime.now(timezone.utc).isoformat()}
-- =====================================================================
-- IMPORTANT:
--   1. Run this in the SANAD PostgreSQL database (Render).
--   2. Replace table/column names if your schema differs.
--   3. Commit immediately after verification.
--   4. Revoke all active sessions for both users.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------
-- Step 1: Update Admin password
-- ---------------------------------------------------------------
-- Adjust table name (users / user / account) as per your schema.
UPDATE users
SET password_hash = '{admin_hash}',
    updated_at = NOW()
WHERE username = 'admin';

-- Verify row was updated (expect: UPDATE 1)
-- If 0 rows, check the username column value.

-- ---------------------------------------------------------------
-- Step 2: Update Identity B password
-- ---------------------------------------------------------------
UPDATE users
SET password_hash = '{identity_b_hash}',
    updated_at = NOW()
WHERE username = 'identity-b';

-- Verify row was updated (expect: UPDATE 1)

-- ---------------------------------------------------------------
-- Step 3: Revoke all active sessions (optional but recommended)
-- ---------------------------------------------------------------
DELETE FROM user_sessions
WHERE username IN ('admin', 'identity-b');

-- Or if you use a refresh_token table:
-- DELETE FROM refresh_tokens
-- WHERE user_id IN (SELECT id FROM users WHERE username IN ('admin', 'identity-b'));

COMMIT;

-- =====================================================================
-- Verification Queries (run AFTER COMMIT)
-- =====================================================================
-- SELECT username, LEFT(password_hash, 7) AS hash_prefix, updated_at
-- FROM users
-- WHERE username IN ('admin', 'identity-b');
--
-- Expected:
--   username   | hash_prefix | updated_at
--   -----------+-------------+---------------------------
--   admin      | $2a$10$...  | <current timestamp>
--   identity-b | $2a$10$...  | <current timestamp>
-- =====================================================================
"""

    sql_path = Path("/home/z/my-project/download/sanad_password_rotation.sql")
    sql_path.write_text(sql_script, encoding='utf-8')
    print(f"\n[4] SQL rotation script saved to: {sql_path}")

    # ---------- Summary ----------
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print(f"Admin password length:      {len(admin_password)} chars")
    print(f"Identity B password length: {len(identity_b_password)} chars")
    print(f"bcrypt cost factor:         {BCRYPT_COST}")
    print(f"Hash format:                $2a$ (Spring Security compatible)")
    print(f"Hashes verified:            YES (both)")
    print()
    print("NEXT STEPS:")
    print("  1. Open Render Dashboard → PostgreSQL → Query")
    print("  2. Paste contents of sanad_password_rotation.sql")
    print("  3. Execute the script")
    print("  4. Verify with the verification queries")
    print("  5. Test login with new credentials via the API")
    print("  6. Store credentials in a secure vault (1Password, etc.)")
    print("  7. DELETE sanad_credentials_rotation.json after recording")
    print("=" * 70)

if __name__ == "__main__":
    main()
