#!/usr/bin/env python3
"""
Insert admin user using psycopg2 — avoids all bash/psql variable issues.
"""
import bcrypt
import os
import psycopg2
import uuid
import sys

def main():
    jdbc_url = os.environ.get('PROD_JDBC_URL', '')
    db_user = os.environ.get('PROD_DB_USER', '')
    db_pass = os.environ.get('PROD_DB_PASSWORD', '')
    tenant_id = os.environ.get('CONTROL_PLANE_TENANT_ID', '')
    admin_email = os.environ.get('ADMIN_EMAIL', 'admin@snad.ai')
    new_password = os.environ.get('NEW_PASSWORD', '')

    if not new_password:
        print("ERROR: NEW_PASSWORD is not set", file=sys.stderr)
        sys.exit(1)

    print(f"Admin email: {admin_email}", file=sys.stderr)
    print(f"Tenant ID: {tenant_id}", file=sys.stderr)

    # Parse JDBC URL
    conn_str = jdbc_url.replace('jdbc:postgresql://', '')
    host_port = conn_str.split('/')[0]
    host = host_port.split(':')[0]
    port = host_port.split(':')[1] if ':' in host_port else '5432'
    db_name = conn_str.split('/')[1].split('?')[0] if '/' in conn_str else 'postgres'

    print(f"Host: {host}:{port}", file=sys.stderr)
    print(f"DB: {db_name}", file=sys.stderr)

    # Generate BCrypt hash
    password_bytes = new_password.encode('utf-8')
    hash_val = bcrypt.hashpw(password_bytes, bcrypt.gensalt(rounds=10, prefix=b"2a")).decode('utf-8')
    print(f"Generated BCrypt hash (length: {len(hash_val)})", file=sys.stderr)
    print(f"Hash: {hash_val}", file=sys.stderr)

    # Connect to DB
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

    # 1. Create tenant if not exists
    cur.execute("""
        INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at)
        SELECT %s::uuid, 'SNAD Control Plane', 'control-plane', 'ACTIVE',
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        WHERE NOT EXISTS (SELECT 1 FROM public.tenants WHERE id = %s::uuid)
    """, (tenant_id, tenant_id))
    print(f"Tenant: {cur.rowcount} rows", file=sys.stderr)

    # 2. Create ADMIN role if not exists
    cur.execute("""
        INSERT INTO public.roles (id, tenant_id, code, name, description, status, created_at, updated_at)
        SELECT gen_random_uuid(), %s::uuid, 'ADMIN', 'Administrator',
               'Full administrative access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE tenant_id = %s::uuid AND code = 'ADMIN')
    """, (tenant_id, tenant_id))
    print(f"ADMIN role: {cur.rowcount} rows", file=sys.stderr)

    # 3. Grant ALL capabilities to ADMIN role
    cur.execute("""
        INSERT INTO public.role_capabilities (id, tenant_id, role_id, capability_id, created_at)
        SELECT gen_random_uuid(), r.tenant_id, r.id, ac.id, CURRENT_TIMESTAMP
        FROM public.roles r
        CROSS JOIN public.access_capabilities ac
        WHERE r.tenant_id = %s::uuid AND r.code = 'ADMIN' AND r.status = 'ACTIVE'
          AND ac.status = 'ACTIVE'
          AND NOT EXISTS (SELECT 1 FROM public.role_capabilities rc
            WHERE rc.tenant_id = r.tenant_id AND rc.role_id = r.id AND rc.capability_id = ac.id)
    """, (tenant_id,))
    print(f"Role capabilities: {cur.rowcount} rows", file=sys.stderr)

    # 4. Create admin user if not exists
    cur.execute("""
        INSERT INTO public.users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
        SELECT gen_random_uuid(), %s::uuid, %s, 'SNAD Administrator',
               'ACTIVE', %s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE email = %s)
    """, (tenant_id, admin_email, hash_val, admin_email))
    print(f"User insert: {cur.rowcount} rows", file=sys.stderr)

    # 5. Update password for existing user
    cur.execute("""
        UPDATE public.users SET password_hash = %s, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
        WHERE email = %s
    """, (hash_val, admin_email))
    print(f"Password update: {cur.rowcount} rows", file=sys.stderr)

    # 6. Assign ADMIN role
    cur.execute("""
        INSERT INTO public.user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
        SELECT gen_random_uuid(), u.tenant_id, u.id, r.id, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        FROM public.users u
        JOIN public.roles r ON r.tenant_id = u.tenant_id AND r.code = 'ADMIN'
        WHERE u.email = %s
        AND NOT EXISTS (
            SELECT 1 FROM public.user_role_assignments ura
            WHERE ura.tenant_id = u.tenant_id AND ura.user_id = u.id AND ura.role_id = r.id
        )
    """, (admin_email,))
    print(f"Role assignment: {cur.rowcount} rows", file=sys.stderr)

    # Verify
    cur.execute("SELECT email, status, (password_hash IS NOT NULL) AS has_password, length(password_hash) AS hash_len FROM public.users WHERE email = %s", (admin_email,))
    row = cur.fetchone()
    print(f"User: {row}", file=sys.stderr)

    cur.execute("SELECT r.code FROM public.user_role_assignments ura JOIN public.users u ON u.id = ura.user_id JOIN public.roles r ON r.id = ura.role_id WHERE u.email = %s", (admin_email,))
    for row in cur.fetchall():
        print(f"Role: {row}", file=sys.stderr)

    conn.commit()
    cur.close()
    conn.close()
    print("Done!", file=sys.stderr)

if __name__ == '__main__':
    main()
