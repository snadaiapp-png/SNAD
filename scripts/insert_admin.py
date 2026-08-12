#!/usr/bin/env python3
"""
Generate BCrypt hash and write psql script file.
Uses psql variable syntax :'hash' to avoid $ expansion issues.
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
    print(f"Hash starts with: {hash_val[:10]}...", file=sys.stderr)

    # Write psql script file
    # Use psql variable :hash to pass the hash value
    # psql's :'hash' syntax properly quotes the value as a string literal
    sql = f"""SET search_path TO public;

-- 1. Create tenant if not exists (required by fk_users_tenant)
INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at)
SELECT '{tenant_id}'::uuid, 'SNAD Control Plane', 'control-plane', 'ACTIVE',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.tenants WHERE id = '{tenant_id}'::uuid);

-- 2. Create ADMIN role if not exists
INSERT INTO public.roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), '{tenant_id}'::uuid, 'ADMIN', 'Administrator',
       'Full administrative access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE tenant_id = '{tenant_id}'::uuid AND code = 'ADMIN');

-- 3. Grant ALL capabilities to ADMIN role
INSERT INTO public.role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), r.tenant_id, r.id, ac.id, CURRENT_TIMESTAMP
FROM public.roles r
CROSS JOIN public.access_capabilities ac
WHERE r.tenant_id = '{tenant_id}'::uuid AND r.code = 'ADMIN' AND r.status = 'ACTIVE'
  AND ac.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM public.role_capabilities rc
    WHERE rc.tenant_id = r.tenant_id AND rc.role_id = r.id AND rc.capability_id = ac.id);

-- 4. Check if user exists
SELECT 'BEFORE:' AS status, email FROM public.users WHERE email = '{admin_email}';

-- 5. Insert admin user if not exists
INSERT INTO public.users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
SELECT gen_random_uuid(), '{tenant_id}'::uuid, '{admin_email}', 'SNAD Administrator',
       'ACTIVE', :'hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE email = '{admin_email}');

-- 6. Update password if user exists
UPDATE public.users SET password_hash = :'hash', status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE email = '{admin_email}';

-- 7. Assign ADMIN role
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
SELECT 'AFTER:' AS status, email, status, (password_hash IS NOT NULL) AS has_password FROM public.users WHERE email = '{admin_email}';
SELECT 'ROLES:' AS status, r.code FROM public.user_role_assignments ura
JOIN public.users u ON u.id = ura.user_id
JOIN public.roles r ON r.id = ura.role_id
WHERE u.email = '{admin_email}';
"""
    with open('/tmp/insert_admin.sql', 'w') as f:
        f.write(sql)

    # Write hash to file for psql -v
    with open('/tmp/hash_value.txt', 'w') as f:
        f.write(hash_val)

    print("SQL file written to /tmp/insert_admin.sql", file=sys.stderr)
    print("Hash file written to /tmp/hash_value.txt", file=sys.stderr)

if __name__ == '__main__':
    main()
