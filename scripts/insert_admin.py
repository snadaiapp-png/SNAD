#!/usr/bin/env python3
"""Provision/rotate the deterministic SNAD control-plane owner credential."""
import bcrypt
import os
import psycopg2
import sys

OWNER_ID = '00000000-0000-0000-0000-000000000010'
CANONICAL_OWNER_EMAIL = 'snad.ai.app@gmail.com'


def main():
    jdbc_url = os.environ.get('PROD_JDBC_URL', '')
    db_user = os.environ.get('PROD_DB_USER', '')
    db_pass = os.environ.get('PROD_DB_PASSWORD', '')
    tenant_id = os.environ.get('CONTROL_PLANE_TENANT_ID', '')
    new_password = os.environ.get('NEW_PASSWORD', '')

    if not all((jdbc_url, db_user, db_pass, tenant_id, new_password)):
        print("ERROR: database credentials, tenant id, and NEW_PASSWORD are required", file=sys.stderr)
        sys.exit(1)

    conn_str = jdbc_url.replace('jdbc:postgresql://', '')
    host_port = conn_str.split('/')[0]
    host = host_port.split(':')[0]
    port = host_port.split(':')[1] if ':' in host_port else '5432'
    db_name = conn_str.split('/')[1].split('?')[0] if '/' in conn_str else 'postgres'

    hash_val = bcrypt.hashpw(
        new_password.encode('utf-8'), bcrypt.gensalt(rounds=10)
    ).decode('utf-8')
    print(f"Generated BCrypt hash (length: {len(hash_val)})", file=sys.stderr)

    conn = psycopg2.connect(
        host=host,
        port=port,
        dbname=db_name,
        user=db_user,
        password=db_pass,
        sslmode='require'
    )
    conn.autocommit = False
    cur = conn.cursor()

    # Never create a second owner identity. The deterministic owner must already
    # exist through Flyway; this script only canonicalizes/rotates that account.
    cur.execute("""
        SELECT id
        FROM public.users
        WHERE tenant_id = %s::uuid
          AND lower(email) = %s
          AND id <> %s::uuid
    """, (tenant_id, CANONICAL_OWNER_EMAIL, OWNER_ID))
    if cur.fetchone() is not None:
        conn.rollback()
        raise RuntimeError("Canonical owner email belongs to another control-plane user")

    cur.execute("""
        UPDATE public.users
        SET email = %s,
            password_hash = %s,
            status = 'ACTIVE',
            platform_admin = true,
            must_change_password = false,
            password_set_at = CURRENT_TIMESTAMP,
            password_set_by = 'controlled-owner-setup',
            session_version = session_version + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = %s::uuid
          AND tenant_id = %s::uuid
    """, (CANONICAL_OWNER_EMAIL, hash_val, OWNER_ID, tenant_id))
    if cur.rowcount != 1:
        conn.rollback()
        raise RuntimeError("Deterministic control-plane owner not found")

    cur.execute("""
        UPDATE public.refresh_tokens
        SET status = 'REVOKED'
        WHERE tenant_id = %s::uuid
          AND user_id = %s::uuid
          AND status = 'ACTIVE'
    """, (tenant_id, OWNER_ID))

    cur.execute("""
        UPDATE public.password_reset_tokens
        SET status = 'REVOKED'
        WHERE tenant_id = %s::uuid
          AND user_id = %s::uuid
          AND status = 'ACTIVE'
    """, (tenant_id, OWNER_ID))

    cur.execute("""
        SELECT email, status, platform_admin
        FROM public.users
        WHERE id = %s::uuid AND tenant_id = %s::uuid
    """, (OWNER_ID, tenant_id))
    row = cur.fetchone()
    print(f"Owner identity: {row}", file=sys.stderr)

    conn.commit()
    cur.close()
    conn.close()
    print("Done!", file=sys.stderr)


if __name__ == '__main__':
    main()
