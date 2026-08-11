#!/usr/bin/env python3
"""
Setup admin user with password.
Uses psycopg2 to avoid shell variable expansion issues with BCrypt $ characters.
"""
import bcrypt
import os
import psycopg2
import uuid
import sys

def main():
    # Get env vars
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

    # Parse JDBC URL: jdbc:postgresql://host:port/db?params
    conn_str = jdbc_url.replace('jdbc:postgresql://', '')
    host_port = conn_str.split('/')[0]
    host = host_port.split(':')[0]
    port = host_port.split(':')[1] if ':' in host_port else '5432'
    db_name = conn_str.split('/')[1].split('?')[0] if '/' in conn_str else 'postgres'

    print(f"Host: {host}:{port}", file=sys.stderr)
    print(f"DB: {db_name}", file=sys.stderr)

    # Generate BCrypt hash
    password_bytes = new_password.encode('utf-8')
    hash_val = bcrypt.hashpw(password_bytes, bcrypt.gensalt(rounds=10)).decode('utf-8')
    print(f"Generated BCrypt hash (length: {len(hash_val)})", file=sys.stderr)

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

    # Set search_path
    cur.execute("SET search_path TO public")

    # Check existing users
    cur.execute("SELECT id, email, status FROM users")
    users = cur.fetchall()
    print(f"Existing users: {len(users)}", file=sys.stderr)
    for u in users:
        print(f"  {u}", file=sys.stderr)

    # Check if admin user exists
    cur.execute("SELECT id FROM users WHERE email = %s", (admin_email,))
    existing = cur.fetchone()

    if existing:
        # Update password
        cur.execute(
            "UPDATE users SET password_hash = %s, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP WHERE email = %s",
            (hash_val, admin_email)
        )
        print(f"Updated password for existing user: {admin_email}", file=sys.stderr)
    else:
        # Create new user
        user_id = str(uuid.uuid4())
        cur.execute(
            """INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
               VALUES (%s, %s::uuid, %s, 'SNAD Administrator', 'ACTIVE', %s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            (user_id, tenant_id, admin_email, hash_val)
        )
        print(f"Created new user: {admin_email}", file=sys.stderr)

        # Assign ADMIN role
        cur.execute(
            """INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
               SELECT gen_random_uuid(), u.tenant_id, u.id, r.id, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
               FROM users u
               JOIN roles r ON r.tenant_id = u.tenant_id AND r.code = 'ADMIN'
               WHERE u.email = %s
               AND NOT EXISTS (
                   SELECT 1 FROM user_role_assignments ura
                   WHERE ura.tenant_id = u.tenant_id AND ura.user_id = u.id AND ura.role_id = r.id
               )""",
            (admin_email,)
        )
        print(f"Assigned ADMIN role", file=sys.stderr)

    # Verify
    cur.execute("SELECT email, status, (password_hash IS NOT NULL) AS has_password FROM users")
    for row in cur.fetchall():
        print(f"  User: {row}", file=sys.stderr)

    conn.commit()
    cur.close()
    conn.close()
    print("Done!", file=sys.stderr)

if __name__ == '__main__':
    main()
