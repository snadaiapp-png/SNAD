#!/usr/bin/env python3
"""
Generate BCrypt hash and write SQL file.
Uses psql to execute the SQL (avoids psycopg2 connection issues with Supabase pooler).
"""
import bcrypt
import os
import sys

def main():
    new_password = os.environ.get('NEW_PASSWORD', '')
    admin_email = os.environ.get('ADMIN_EMAIL', 'admin@snad.ai')
    tenant_id = os.environ.get('CONTROL_PLANE_TENANT_ID', '')

    if not new_password:
        print("ERROR: NEW_PASSWORD is not set", file=sys.stderr)
        sys.exit(1)

    # Generate BCrypt hash
    password_bytes = new_password.encode('utf-8')
    hash_val = bcrypt.hashpw(password_bytes, bcrypt.gensalt(rounds=10)).decode('utf-8')
    print(f"Generated BCrypt hash (length: {len(hash_val)})", file=sys.stderr)

    # Write SQL file using psql variable syntax to avoid shell expansion
    # The hash is passed as a psql variable :hash_val
    sql = f"""SET search_path TO public;

-- Check if users table exists
SELECT count(*) AS user_count FROM public.users;

-- Insert or update admin user
INSERT INTO public.users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
SELECT gen_random_uuid(), '{tenant_id}'::uuid, '{admin_email}', 'SNAD Administrator',
       'ACTIVE', $BODY${hash_val}$BODY$, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE email = '{admin_email}');

-- Update password for existing user
UPDATE public.users SET password_hash = $BODY${hash_val}$BODY$, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE email = '{admin_email}';

-- Assign ADMIN role
INSERT INTO public.user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
SELECT gen_random_uuid(), u.tenant_id, u.id, r.id, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM public.users u
JOIN public.roles r ON r.tenant_id = u.tenant_id AND r.code = 'ADMIN'
WHERE u.email = '{admin_email}'
AND NOT EXISTS (
    SELECT 1 FROM public.user_role_assignments ura
    WHERE ura.tenant_id = u.tenant_id AND ura.user_id = u.id AND ura.role_id = r.id
);

-- Verify
SELECT email, status, (password_hash IS NOT NULL) AS has_password FROM public.users;
"""
    with open('/tmp/update.sql', 'w') as f:
        f.write(sql)
    print("SQL file written to /tmp/update.sql", file=sys.stderr)

if __name__ == '__main__':
    main()
