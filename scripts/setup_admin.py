#!/usr/bin/env python3
"""
Generate a BCrypt hash and write a SQL file for the deterministic SNAD
control-plane owner. Uses psql to execute the SQL (avoids psycopg2 connection
issues with hosted PostgreSQL poolers).
"""
import bcrypt
import os
import sys

OWNER_ID = '00000000-0000-0000-0000-000000000010'
CANONICAL_OWNER_EMAIL = 'snad.ai.app@gmail.com'


def main():
    new_password = os.environ.get('NEW_PASSWORD', '')
    admin_email = os.environ.get('ADMIN_EMAIL', CANONICAL_OWNER_EMAIL).strip().lower()
    tenant_id = os.environ.get('CONTROL_PLANE_TENANT_ID', '')

    if not new_password:
        print("ERROR: NEW_PASSWORD is not set", file=sys.stderr)
        sys.exit(1)
    if admin_email != CANONICAL_OWNER_EMAIL:
        print(f"ERROR: ADMIN_EMAIL must be {CANONICAL_OWNER_EMAIL}", file=sys.stderr)
        sys.exit(1)
    if not tenant_id:
        print("ERROR: CONTROL_PLANE_TENANT_ID is not set", file=sys.stderr)
        sys.exit(1)

    password_bytes = new_password.encode('utf-8')
    hash_val = bcrypt.hashpw(password_bytes, bcrypt.gensalt(rounds=10)).decode('utf-8')
    print(f"Generated BCrypt hash (length: {len(hash_val)})", file=sys.stderr)

    sql = f"""SET search_path TO public;

-- The owner identity is deterministic. Never create a second admin account.
UPDATE public.users
SET email = '{CANONICAL_OWNER_EMAIL}',
    password_hash = $BODY${hash_val}$BODY$,
    status = 'ACTIVE',
    platform_admin = true,
    must_change_password = false,
    password_set_at = CURRENT_TIMESTAMP,
    password_set_by = 'controlled-owner-setup',
    session_version = session_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '{OWNER_ID}'::uuid
  AND tenant_id = '{tenant_id}'::uuid;

DO $BODY$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.users
        WHERE id = '{OWNER_ID}'::uuid
          AND tenant_id = '{tenant_id}'::uuid
          AND email = '{CANONICAL_OWNER_EMAIL}'
    ) THEN
        RAISE EXCEPTION 'Deterministic control-plane owner not found';
    END IF;
END
$BODY$;

UPDATE public.refresh_tokens
SET status = 'REVOKED'
WHERE tenant_id = '{tenant_id}'::uuid
  AND user_id = '{OWNER_ID}'::uuid
  AND status = 'ACTIVE';

UPDATE public.password_reset_tokens
SET status = 'REVOKED'
WHERE tenant_id = '{tenant_id}'::uuid
  AND user_id = '{OWNER_ID}'::uuid
  AND status = 'ACTIVE';

SELECT id, email, status, platform_admin
FROM public.users
WHERE id = '{OWNER_ID}'::uuid;
"""
    with open('/tmp/update.sql', 'w', encoding='utf-8') as file:
        file.write(sql)
    print("SQL file written to /tmp/update.sql", file=sys.stderr)


if __name__ == '__main__':
    main()
