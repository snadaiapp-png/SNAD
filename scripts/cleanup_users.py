#!/usr/bin/env python3
"""Audit the deterministic SNAD control-plane owner identity without deleting users."""
import os
import psycopg2
import sys

OWNER_ID = '00000000-0000-0000-0000-000000000010'
CONTROL_PLANE_TENANT_ID_DEFAULT = '00000000-0000-0000-0000-000000000001'
CANONICAL_OWNER_EMAIL = 'snad.ai.app@gmail.com'
LEGACY_OWNER_EMAIL = 'admin@snad.ai'


def main():
    jdbc_url = os.environ.get('PROD_JDBC_URL', '')
    db_user = os.environ.get('PROD_DB_USER', '')
    db_pass = os.environ.get('PROD_DB_PASSWORD', '')
    tenant_id = os.environ.get('CONTROL_PLANE_TENANT_ID', CONTROL_PLANE_TENANT_ID_DEFAULT)

    if not all((jdbc_url, db_user, db_pass)):
        print("ERROR: production database credentials are required", file=sys.stderr)
        sys.exit(1)

    conn_str = jdbc_url.replace('jdbc:postgresql://', '')
    host_port = conn_str.split('/')[0]
    host = host_port.split(':')[0]
    port = host_port.split(':')[1] if ':' in host_port else '5432'
    db_name = conn_str.split('/')[1].split('?')[0] if '/' in conn_str else 'postgres'

    conn = psycopg2.connect(
        host=host, port=port, dbname=db_name,
        user=db_user, password=db_pass, sslmode='require'
    )
    cur = conn.cursor()

    cur.execute("""
        SELECT id, tenant_id, email, status, platform_admin
        FROM public.users
        WHERE tenant_id = %s::uuid
          AND (id = %s::uuid OR lower(email) IN (%s, %s))
        ORDER BY id
    """, (tenant_id, OWNER_ID, CANONICAL_OWNER_EMAIL, LEGACY_OWNER_EMAIL))
    rows = cur.fetchall()

    for row in rows:
        print(
            f"owner-audit id={row[0]} tenant={row[1]} email={row[2]} "
            f"status={row[3]} platform_admin={row[4]}",
            file=sys.stderr,
        )

    canonical = [row for row in rows if str(row[0]) == OWNER_ID and row[2].lower() == CANONICAL_OWNER_EMAIL]
    legacy = [row for row in rows if row[2].lower() == LEGACY_OWNER_EMAIL]

    cur.close()
    conn.close()

    if len(canonical) != 1 or legacy:
        print(
            "ERROR: owner identity is not canonical; run Flyway migration "
            "V20260828_1__canonicalize_control_plane_owner_email.sql",
            file=sys.stderr,
        )
        sys.exit(2)

    print("Owner identity is canonical. No destructive cleanup required.", file=sys.stderr)


if __name__ == '__main__':
    main()
