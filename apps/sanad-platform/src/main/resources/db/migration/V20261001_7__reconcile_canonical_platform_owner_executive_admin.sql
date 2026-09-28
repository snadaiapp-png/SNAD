DO $$
DECLARE
    control_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    owner_id CONSTANT UUID := '00000000-0000-0000-0000-000000000010'::uuid;
    owner_email CONSTANT TEXT := 'snad.ai.app@gmail.com';
    platform_owner_role_id UUID;
    admin_role_id UUID;
    owner_count INTEGER;
    membership_count INTEGER;
    assignment_count INTEGER;
    missing_capability_count INTEGER;
    missing_scope_count INTEGER;
BEGIN
    PERFORM set_config('app.tenant_id', control_tenant::text, TRUE);

    SELECT COUNT(*) INTO owner_count
    FROM users
    WHERE tenant_id = control_tenant
      AND id = owner_id
      AND lower(email) = lower(owner_email);
    IF owner_count <> 1 THEN
        RAISE EXCEPTION 'Canonical owner identity prerequisite missing';
    END IF;

    SELECT id INTO platform_owner_role_id
    FROM roles
    WHERE tenant_id = control_tenant AND code = 'PLATFORM_OWNER' AND status = 'ACTIVE';
    IF platform_owner_role_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE PLATFORM_OWNER prerequisite missing';
    END IF;

    PERFORM 1
    FROM platform_role_metadata
    WHERE control_tenant_id = control_tenant
      AND role_id = platform_owner_role_id
      AND role_type = 'SYSTEM'
      AND protected = TRUE
      AND owner_role = TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'PLATFORM_OWNER metadata prerequisite invalid';
    END IF;

    SELECT id INTO admin_role_id
    FROM roles
    WHERE tenant_id = control_tenant AND code = 'ADMIN' AND status = 'ACTIVE';
    IF admin_role_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE ADMIN prerequisite missing';
    END IF;

    UPDATE users
    SET status = 'ACTIVE', platform_admin = TRUE, updated_at = NOW()
    WHERE tenant_id = control_tenant
      AND id = owner_id
      AND lower(email) = lower(owner_email)
      AND (status <> 'ACTIVE' OR platform_admin IS DISTINCT FROM TRUE);

    UPDATE platform_memberships
    SET status = 'ACTIVE',
        activated_at = COALESCE(activated_at, NOW()),
        suspended_at = NULL,
        locked_at = NULL,
        disabled_at = NULL,
        updated_by = owner_id,
        status_reason = 'Canonical Platform Owner reconciliation',
        updated_at = NOW()
    WHERE control_tenant_id = control_tenant
      AND user_id = owner_id
      AND status <> 'ACTIVE';

    INSERT INTO platform_memberships (
        control_tenant_id, user_id, status, activated_at,
        created_by, updated_by, status_reason, created_at, updated_at
    )
    SELECT control_tenant, owner_id, 'ACTIVE', NOW(),
           owner_id, owner_id, 'Canonical Platform Owner reconciliation', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM platform_memberships pm
        WHERE pm.control_tenant_id = control_tenant AND pm.user_id = owner_id
    );

    UPDATE user_role_assignments
    SET status = 'ACTIVE', updated_at = NOW()
    WHERE tenant_id = control_tenant
      AND user_id = owner_id
      AND role_id IN (platform_owner_role_id, admin_role_id)
      AND organization_id IS NULL
      AND status <> 'ACTIVE';

    INSERT INTO user_role_assignments (
        id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at
    )
    SELECT gen_random_uuid(), control_tenant, owner_id, platform_owner_role_id,
           NULL, 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM user_role_assignments ura
        WHERE ura.tenant_id = control_tenant
          AND ura.user_id = owner_id
          AND ura.role_id = platform_owner_role_id
          AND ura.organization_id IS NULL
          AND ura.status = 'ACTIVE'
    );

    INSERT INTO user_role_assignments (
        id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at
    )
    SELECT gen_random_uuid(), control_tenant, owner_id, admin_role_id,
           NULL, 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM user_role_assignments ura
        WHERE ura.tenant_id = control_tenant
          AND ura.user_id = owner_id
          AND ura.role_id = admin_role_id
          AND ura.organization_id IS NULL
          AND ura.status = 'ACTIVE'
    );

    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
    SELECT gen_random_uuid(), control_tenant, platform_owner_role_id, ac.id, NOW()
    FROM access_capabilities ac
    WHERE ac.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1 FROM role_capabilities rc
          WHERE rc.tenant_id = control_tenant
            AND rc.role_id = platform_owner_role_id
            AND rc.capability_id = ac.id
      );

    INSERT INTO access_scope_grants (
        id, tenant_id, role_id, user_id, capability_id, scope_type,
        organization_id, org_unit_id, legal_entity_id,
        is_direct_exception, reason, granted_by,
        effective_from, effective_to, status, created_at
    )
    SELECT gen_random_uuid(), control_tenant, platform_owner_role_id, NULL, ac.id, 'TENANT',
           NULL, NULL, NULL, FALSE, 'Canonical Platform Owner tenant scope', owner_id,
           NOW(), NULL, 'ACTIVE', NOW()
    FROM access_capabilities ac
    WHERE ac.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1 FROM access_scope_grants asg
          WHERE asg.tenant_id = control_tenant
            AND asg.role_id = platform_owner_role_id
            AND asg.user_id IS NULL
            AND asg.capability_id = ac.id
            AND asg.scope_type = 'TENANT'
            AND asg.status = 'ACTIVE'
      );

    PERFORM 1 FROM users
    WHERE tenant_id = control_tenant AND id = owner_id
      AND lower(email) = lower(owner_email)
      AND status = 'ACTIVE' AND platform_admin = TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Canonical owner state verification failed';
    END IF;

    SELECT COUNT(*) INTO membership_count
    FROM platform_memberships
    WHERE control_tenant_id = control_tenant AND user_id = owner_id AND status = 'ACTIVE';
    IF membership_count <> 1 THEN
        RAISE EXCEPTION 'Canonical owner membership verification failed';
    END IF;

    SELECT COUNT(*) INTO assignment_count
    FROM user_role_assignments
    WHERE tenant_id = control_tenant AND user_id = owner_id
      AND role_id = platform_owner_role_id AND organization_id IS NULL AND status = 'ACTIVE';
    IF assignment_count < 1 THEN
        RAISE EXCEPTION 'PLATFORM_OWNER assignment verification failed';
    END IF;

    SELECT COUNT(*) INTO assignment_count
    FROM user_role_assignments
    WHERE tenant_id = control_tenant AND user_id = owner_id
      AND role_id = admin_role_id AND organization_id IS NULL AND status = 'ACTIVE';
    IF assignment_count < 1 THEN
        RAISE EXCEPTION 'ADMIN assignment verification failed';
    END IF;

    SELECT COUNT(*) INTO missing_capability_count
    FROM access_capabilities ac
    WHERE ac.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1 FROM role_capabilities rc
          WHERE rc.tenant_id = control_tenant
            AND rc.role_id = platform_owner_role_id
            AND rc.capability_id = ac.id
      );
    IF missing_capability_count <> 0 THEN
        RAISE EXCEPTION 'PLATFORM_OWNER capability reconciliation failed';
    END IF;

    SELECT COUNT(*) INTO missing_scope_count
    FROM access_capabilities ac
    WHERE ac.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1 FROM access_scope_grants asg
          WHERE asg.tenant_id = control_tenant
            AND asg.role_id = platform_owner_role_id
            AND asg.user_id IS NULL
            AND asg.capability_id = ac.id
            AND asg.scope_type = 'TENANT'
            AND asg.status = 'ACTIVE'
      );
    IF missing_scope_count <> 0 THEN
        RAISE EXCEPTION 'PLATFORM_OWNER scope reconciliation failed';
    END IF;

    PERFORM 1
    FROM user_role_assignments ura
    JOIN roles r ON r.id = ura.role_id AND r.tenant_id = ura.tenant_id
    WHERE r.code = 'PLATFORM_OWNER' AND ura.tenant_id <> control_tenant;
    IF FOUND THEN
        RAISE EXCEPTION 'PLATFORM_OWNER assignment exists outside control tenant';
    END IF;

    PERFORM 1 FROM roles r
    WHERE r.code = 'PLATFORM_OWNER' AND r.tenant_id <> control_tenant;
    IF FOUND THEN
        RAISE EXCEPTION 'PLATFORM_OWNER role exists outside control tenant';
    END IF;
END $$;
