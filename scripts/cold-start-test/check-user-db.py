#!/usr/bin/env python3
"""
Phase 1: Check Production DB for user sanad.ai.app@gmail.com.
Verify: exists, ACTIVE, not locked, correct tenant.
"""
import psycopg2
from datetime import datetime, timezone

DB_HOST = "aws-0-eu-central-1.pooler.supabase.com"
DB_PORT = "5432"
DB_NAME = "postgres"
DB_USER = "sanad.tkbrvupemreqabwzdpyq"
DB_PASSWORD = "c0afe3e54ff26a2b6826ad68d1879fa2f53b91188f9343db27671af06e4003e0"

EMAILS_TO_CHECK = [
    "sanad.ai.app@gmail.com",
    "admin@snad.ai",
]

def utc_now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

print("=" * 70)
print("PHASE 1: CHECK PRODUCTION DB FOR USER")
print("=" * 70)
print(f"Timestamp: {utc_now()}")
print("")

try:
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
        sslmode="require", connect_timeout=15,
    )
    print("DB connection: SUCCESS")
    cur = conn.cursor()

    # Query users with the actual schema
    print("")
    print("=== Query users by email (exact + case-insensitive) ===")
    for email in EMAILS_TO_CHECK:
        print(f"\n--- Email: {email} ---")
        cur.execute("""
            SELECT id, email, display_name, status, tenant_id,
                   password_hash IS NOT NULL AS has_password,
                   length(password_hash) AS password_hash_len,
                   must_change_password, session_version, platform_admin,
                   last_login_at, password_set_at, password_set_by,
                   created_at, updated_at
            FROM users
            WHERE email = %s OR email ILIKE %s
        """, (email, email))
        rows = cur.fetchall()
        if rows:
            for row in rows:
                print(f"  id: {row[0]}")
                print(f"  email: {row[1]}")
                print(f"  display_name: {row[2]}")
                print(f"  status: {row[3]}")
                print(f"  tenant_id: {row[4]}")
                print(f"  has_password: {row[5]}")
                print(f"  password_hash_len: {row[6]}")
                print(f"  must_change_password: {row[7]}")
                print(f"  session_version: {row[8]}")
                print(f"  platform_admin: {row[9]}")
                print(f"  last_login_at: {row[10]}")
                print(f"  password_set_at: {row[11]}")
                print(f"  password_set_by: {row[12]}")
                print(f"  created_at: {row[13]}")
                print(f"  updated_at: {row[14]}")
        else:
            print(f"  USER NOT FOUND")

    # List ALL users to understand what exists
    print("")
    print("=== All users in DB (limit 20) ===")
    cur.execute("""
        SELECT id, email, status, tenant_id,
               password_hash IS NOT NULL AS has_pw,
               must_change_password, platform_admin,
               last_login_at, created_at
        FROM users
        ORDER BY created_at DESC
        LIMIT 20
    """)
    rows = cur.fetchall()
    print(f"Total users found: {len(rows)}")
    for row in rows:
        print(f"  id={row[0]} email={row[1]} status={row[2]} tenant={row[3]} has_pw={row[4]} must_change={row[5]} platform_admin={row[6]} last_login={row[7]}")

    # Check for password reset tokens table
    print("")
    print("=== Find password reset token table ===")
    cur.execute("""
        SELECT table_schema, table_name
        FROM information_schema.tables
        WHERE table_name LIKE '%password%' OR table_name LIKE '%reset%' OR table_name LIKE '%token%'
        AND table_schema NOT IN ('pg_catalog', 'information_schema')
        ORDER BY table_schema, table_name
    """)
    tables = cur.fetchall()
    for t in tables:
        print(f"  {t[0]}.{t[1]}")

    # Check password_reset_tokens table schema if exists
    print("")
    print("=== password_reset_tokens columns ===")
    cur.execute("""
        SELECT column_name, data_type, is_nullable
        FROM information_schema.columns
        WHERE table_name = 'password_reset_tokens'
        AND table_schema NOT IN ('pg_catalog', 'information_schema')
        ORDER BY ordinal_position
    """)
    cols = cur.fetchall()
    for c in cols:
        print(f"  {c[0]}: {c[1]} (nullable: {c[2]})")

    cur.close()
    conn.close()
    print("")
    print("DB connection closed.")

except Exception as e:
    print(f"DB ERROR: {e}")
    import traceback
    traceback.print_exc()
