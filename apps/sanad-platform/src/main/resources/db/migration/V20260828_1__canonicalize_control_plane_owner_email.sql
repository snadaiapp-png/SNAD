-- Canonical project-owner identity for the SNAD control plane.
-- Forward-only migration: do not edit the historical seed migration.
-- No credential material is changed here; password recovery remains the
-- supported way to rotate the owner's credential.

DO $$
DECLARE
    owner_updated INTEGER;
BEGIN
    -- Fail closed rather than deleting an unrelated user if the approved
    -- canonical email is already attached to another control-plane identity.
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
          AND id <> '00000000-0000-0000-0000-000000000010'::uuid
          AND lower(email) = 'snad.ai.app@gmail.com'
    ) THEN
        RAISE EXCEPTION 'Canonical project-owner email is already assigned to another control-plane user';
    END IF;

    UPDATE users
    SET email = 'snad.ai.app@gmail.com',
        status = 'ACTIVE',
        platform_admin = true,
        must_change_password = false,
        session_version = session_version + 1,
        updated_at = NOW()
    WHERE id = '00000000-0000-0000-0000-000000000010'::uuid
      AND tenant_id = '00000000-0000-0000-0000-000000000001'::uuid;

    GET DIAGNOSTICS owner_updated = ROW_COUNT;
    IF owner_updated <> 1 THEN
        RAISE EXCEPTION 'Deterministic SNAD control-plane owner was not found';
    END IF;

    -- Invalidate sessions and recovery links minted under the legacy identity.
    UPDATE refresh_tokens
    SET status = 'REVOKED'
    WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
      AND user_id = '00000000-0000-0000-0000-000000000010'::uuid
      AND status = 'ACTIVE';

    UPDATE password_reset_tokens
    SET status = 'REVOKED'
    WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
      AND user_id = '00000000-0000-0000-0000-000000000010'::uuid
      AND status = 'ACTIVE';
END $$;
