#!/usr/bin/env python3
"""
Clean up ALL users with admin@snad.ai from ALL tenants,
then let Spring Boot's CredentialBootstrapConfig create a single
clean user with the correct BCrypt hash.
"""
import os
import psycopg2
import sys

def main():
    jdbc_url = os.environ.get('PROD_JDBC_URL', '')
    db_user = os.environ.get('PROD_DB_USER', '')
    db_pass = os.environ.get('PROD_DB_PASSWORD', '')
    admin_email = 'admin@snad.ai'

    conn_str = jdbc_url.replace('jdbc:postgresql://', '')
    host_port = conn_str.split('/')[0]
    host = host_port.split(':')[0]
    port = host_port.split(':')[1] if ':' in host_port else '5432'
    db_name = conn_str.split('/')[1].split('?')[0] if '/' in conn_str else 'postgres'

    print(f"Host: {host}:{port}", file=sys.stderr)
    print(f"DB: {db_name}", file=sys.stderr)

    conn = psycopg2.connect(
        host=host, port=port, dbname=db_name,
        user=db_user, password=db_pass, sslmode='require'
    )
    conn.autocommit = False
    cur = conn.cursor()

    # 1. Show ALL users with admin@snad.ai
    cur.execute("SELECT id, tenant_id, email, status, length(password_hash) AS hash_len FROM public.users WHERE email = %s", (admin_email,))
    users = cur.fetchall()
    print(f"\nFound {len(users)} user(s) with email {admin_email}:", file=sys.stderr)
    for u in users:
        print(f"  id={u[0]} tenant_id={u[1]} status={u[3]} hash_len={u[4]}", file=sys.stderr)

    # 2. Delete ALL user_role_assignments for these users
    for u in users:
        cur.execute("DELETE FROM public.user_role_assignments WHERE user_id = %s", (u[0],))
        print(f"  Deleted {cur.rowcount} role assignments for user {u[0]}", file=sys.stderr)

    # 3. Delete ALL refresh_tokens for these users
    for u in users:
        cur.execute("DELETE FROM public.refresh_tokens WHERE user_id = %s", (u[0],))
        print(f"  Deleted {cur.rowcount} refresh tokens for user {u[0]}", file=sys.stderr)

    # 4. Delete ALL users with this email
    cur.execute("DELETE FROM public.users WHERE email = %s", (admin_email,))
    print(f"\nDeleted {cur.rowcount} user(s) with email {admin_email}", file=sys.stderr)

    # 5. Verify
    cur.execute("SELECT count(*) FROM public.users WHERE email = %s", (admin_email,))
    count = cur.fetchone()[0]
    print(f"\nRemaining users with {admin_email}: {count}", file=sys.stderr)

    # 6. Show remaining users in DB
    cur.execute("SELECT id, tenant_id, email FROM public.users LIMIT 10")
    remaining = cur.fetchall()
    print(f"\nRemaining users in DB: {len(remaining)}", file=sys.stderr)
    for u in remaining:
        print(f"  {u}", file=sys.stderr)

    conn.commit()
    cur.close()
    conn.close()
    print("\n✓ Cleanup complete!", file=sys.stderr)

if __name__ == '__main__':
    main()
