-- ============================================================================
-- V20261005_2__r0c13_g07_operator_capabilities.sql
-- R0C13 / R13-G07 — granular billing operator capabilities.
--
-- Forward-only and additive:
--   * seeds the canonical G07 capability family;
--   * preserves legacy EXECUTIVE_BILLING callers by mirroring existing
--     EXECUTIVE_BILLING role grants onto the new granular capabilities;
--   * does not alter billing/Finance writer authority or provider mode.
-- ============================================================================

INSERT INTO access_capabilities
    (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), seed.code, seed.name, seed.description,
       'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('BILLING.READ', 'Billing Read',
     'Read governed subscription billing, Finance linkage, and reconciliation evidence'),
    ('BILLING.MANAGE', 'Billing Manage',
     'Manage governed subscription billing operations'),
    ('BILLING.RECONCILE', 'Billing Reconcile',
     'Run governed billing reconciliation and explicit repair operations'),
    ('BILLING.REFUND', 'Billing Refund',
     'Request governed subscription billing refunds independently of generic billing management'),
    ('BILLING.PROVIDER_ADMIN', 'Billing Provider Administration',
     'Read payment-provider readiness diagnostics and administer provider configuration surfaces')
) AS seed(code, name, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM access_capabilities existing
    WHERE existing.code = seed.code
);

-- Compatibility bridge: a role that already held the legacy broad
-- EXECUTIVE_BILLING authority keeps equivalent access after G07 introduces
-- granular codes. No role without EXECUTIVE_BILLING is broadened here.
INSERT INTO role_capabilities
    (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), legacy.tenant_id, legacy.role_id, granular.id, NOW()
FROM role_capabilities legacy
JOIN access_capabilities legacy_capability
  ON legacy_capability.id = legacy.capability_id
 AND legacy_capability.code = 'EXECUTIVE_BILLING'
 AND legacy_capability.status = 'ACTIVE'
CROSS JOIN access_capabilities granular
WHERE granular.code IN (
    'BILLING.READ',
    'BILLING.MANAGE',
    'BILLING.RECONCILE',
    'BILLING.REFUND',
    'BILLING.PROVIDER_ADMIN'
)
  AND granular.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM role_capabilities existing
      WHERE existing.tenant_id = legacy.tenant_id
        AND existing.role_id = legacy.role_id
        AND existing.capability_id = granular.id
  );

-- Scope compatibility bridge: capability membership alone is not sufficient in
-- SANAD's scoped authorization model. Mirror every ACTIVE EXECUTIVE_BILLING
-- scope grant onto the granular G07 capability family while preserving the
-- principal, scope dimensions, validity window, and direct-exception metadata.
-- This is additive/idempotent and keeps existing legacy authority effective
-- without broadening callers that never held EXECUTIVE_BILLING scope.
INSERT INTO access_scope_grants (
    id, tenant_id, role_id, user_id, capability_id, scope_type,
    organization_id, org_unit_id, legal_entity_id,
    is_direct_exception, reason, granted_by,
    effective_from, effective_to, status, created_at
)
SELECT
    gen_random_uuid(),
    legacy_scope.tenant_id,
    legacy_scope.role_id,
    legacy_scope.user_id,
    granular.id,
    legacy_scope.scope_type,
    legacy_scope.organization_id,
    legacy_scope.org_unit_id,
    legacy_scope.legal_entity_id,
    legacy_scope.is_direct_exception,
    legacy_scope.reason,
    legacy_scope.granted_by,
    legacy_scope.effective_from,
    legacy_scope.effective_to,
    'ACTIVE',
    NOW()
FROM access_scope_grants legacy_scope
JOIN access_capabilities legacy_capability
  ON legacy_capability.id = legacy_scope.capability_id
 AND legacy_capability.code = 'EXECUTIVE_BILLING'
 AND legacy_capability.status = 'ACTIVE'
CROSS JOIN access_capabilities granular
WHERE legacy_scope.status = 'ACTIVE'
  AND granular.code IN (
      'BILLING.READ',
      'BILLING.MANAGE',
      'BILLING.RECONCILE',
      'BILLING.REFUND',
      'BILLING.PROVIDER_ADMIN'
  )
  AND granular.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM access_scope_grants existing
      WHERE existing.tenant_id = legacy_scope.tenant_id
        AND existing.capability_id = granular.id
        AND existing.role_id IS NOT DISTINCT FROM legacy_scope.role_id
        AND existing.user_id IS NOT DISTINCT FROM legacy_scope.user_id
        AND existing.scope_type = legacy_scope.scope_type
        AND existing.organization_id IS NOT DISTINCT FROM legacy_scope.organization_id
        AND existing.org_unit_id IS NOT DISTINCT FROM legacy_scope.org_unit_id
        AND existing.legal_entity_id IS NOT DISTINCT FROM legacy_scope.legal_entity_id
        AND existing.status = 'ACTIVE'
  );

-- Canonical owner reconciliation: PLATFORM_OWNER is an invariant-bearing role.
-- It must retain tenant scope for every ACTIVE capability even when no legacy
-- EXECUTIVE_BILLING access_scope_grant exists to mirror.
DO $g07_owner$
DECLARE
    control_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    owner_user CONSTANT UUID := '00000000-0000-0000-0000-000000000010'::uuid;
    platform_owner_role_id UUID;
    missing_role_capability_count INTEGER;
    missing_scope_count INTEGER;
BEGIN
    PERFORM set_config('app.tenant_id', control_tenant::text, TRUE);

    SELECT id
      INTO platform_owner_role_id
      FROM roles
     WHERE tenant_id = control_tenant
       AND code = 'PLATFORM_OWNER'
       AND status = 'ACTIVE';

    IF platform_owner_role_id IS NULL THEN
        RAISE EXCEPTION 'R0C13 G07 canonical PLATFORM_OWNER prerequisite missing';
    END IF;

    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
    SELECT gen_random_uuid(), control_tenant, platform_owner_role_id, capability.id, NOW()
      FROM access_capabilities capability
     WHERE capability.status = 'ACTIVE'
       AND capability.code IN (
           'BILLING.READ',
           'BILLING.MANAGE',
           'BILLING.RECONCILE',
           'BILLING.REFUND',
           'BILLING.PROVIDER_ADMIN'
       )
       AND NOT EXISTS (
           SELECT 1
             FROM role_capabilities existing
            WHERE existing.tenant_id = control_tenant
              AND existing.role_id = platform_owner_role_id
              AND existing.capability_id = capability.id
       );

    INSERT INTO access_scope_grants (
        id, tenant_id, role_id, user_id, capability_id, scope_type,
        organization_id, org_unit_id, legal_entity_id,
        is_direct_exception, reason, granted_by,
        effective_from, effective_to, status, created_at
    )
    SELECT gen_random_uuid(), control_tenant, platform_owner_role_id, NULL,
           capability.id, 'TENANT',
           NULL, NULL, NULL,
           FALSE, 'R0C13 G07 canonical PLATFORM_OWNER tenant scope', owner_user,
           NOW(), NULL, 'ACTIVE', NOW()
      FROM access_capabilities capability
     WHERE capability.status = 'ACTIVE'
       AND capability.code IN (
           'BILLING.READ',
           'BILLING.MANAGE',
           'BILLING.RECONCILE',
           'BILLING.REFUND',
           'BILLING.PROVIDER_ADMIN'
       )
       AND NOT EXISTS (
           SELECT 1
             FROM access_scope_grants existing
            WHERE existing.tenant_id = control_tenant
              AND existing.role_id = platform_owner_role_id
              AND existing.user_id IS NULL
              AND existing.capability_id = capability.id
              AND existing.scope_type = 'TENANT'
              AND existing.status = 'ACTIVE'
       );

    SELECT COUNT(*)
      INTO missing_role_capability_count
      FROM access_capabilities capability
     WHERE capability.status = 'ACTIVE'
       AND capability.code IN (
           'BILLING.READ',
           'BILLING.MANAGE',
           'BILLING.RECONCILE',
           'BILLING.REFUND',
           'BILLING.PROVIDER_ADMIN'
       )
       AND NOT EXISTS (
           SELECT 1
             FROM role_capabilities existing
            WHERE existing.tenant_id = control_tenant
              AND existing.role_id = platform_owner_role_id
              AND existing.capability_id = capability.id
       );

    IF missing_role_capability_count <> 0 THEN
        RAISE EXCEPTION
            'R0C13 G07 PLATFORM_OWNER capability reconciliation failed: % missing',
            missing_role_capability_count;
    END IF;

    SELECT COUNT(*)
      INTO missing_scope_count
      FROM access_capabilities capability
     WHERE capability.status = 'ACTIVE'
       AND capability.code IN (
           'BILLING.READ',
           'BILLING.MANAGE',
           'BILLING.RECONCILE',
           'BILLING.REFUND',
           'BILLING.PROVIDER_ADMIN'
       )
       AND NOT EXISTS (
           SELECT 1
             FROM access_scope_grants existing
            WHERE existing.tenant_id = control_tenant
              AND existing.role_id = platform_owner_role_id
              AND existing.user_id IS NULL
              AND existing.capability_id = capability.id
              AND existing.scope_type = 'TENANT'
              AND existing.status = 'ACTIVE'
       );

    IF missing_scope_count <> 0 THEN
        RAISE EXCEPTION
            'R0C13 G07 PLATFORM_OWNER tenant-scope reconciliation failed: % missing',
            missing_scope_count;
    END IF;
END
$g07_owner$;

DO $g07_seed$
DECLARE
    missing_count integer;
BEGIN
    SELECT COUNT(*)
      INTO missing_count
      FROM (VALUES
          ('BILLING.READ'),
          ('BILLING.MANAGE'),
          ('BILLING.RECONCILE'),
          ('BILLING.REFUND'),
          ('BILLING.PROVIDER_ADMIN')
      ) required(code)
      LEFT JOIN access_capabilities capability
        ON capability.code = required.code
       AND capability.status = 'ACTIVE'
     WHERE capability.id IS NULL;

    IF missing_count <> 0 THEN
        RAISE EXCEPTION 'R0C13 G07 capability seed incomplete: % required capabilities missing',
            missing_count;
    END IF;
END
$g07_seed$;
