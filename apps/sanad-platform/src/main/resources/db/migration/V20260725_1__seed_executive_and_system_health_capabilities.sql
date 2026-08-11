-- ============================================================
-- SNAD Platform — V20260725.1
-- Seed Executive Management and System Health capabilities
-- and grant them to the platform ADMIN role.
-- ------------------------------------------------------------
-- Background:
--   The Control Plane (الإدارة العليا) and System Health (صحة النظام)
--   surfaces previously reused ROLE.READ / ROLE.WRITE as their
--   capability gate. The platform now exposes dedicated capability
--   codes so that executive operators can be granted least-privilege
--   scopes that match their actual responsibilities:
--
--     EXECUTIVE_VIEW        — View the executive dashboard, tenant
--                             directory, plans, subscriptions, and
--                             organizations/memberships.
--     EXECUTIVE_MANAGE      — Mutate tenants, plans, subscriptions,
--                             organizations and memberships.
--     EXECUTIVE_BILLING     — Read invoices and mark them as paid.
--     SYSTEM_HEALTH_VIEW    — Read the platform health snapshot.
--     SYSTEM_HEALTH_MONITOR — Execute controlled self-healing and
--                             diagnostic actions on the platform.
--     SYSTEM_HEALTH_ALERTS  — Receive and acknowledge system health
--                             alerts (reserved for future alerting
--                             endpoints; granted now so the role is
--                             ready when those endpoints ship).
--
-- Strategy:
--   * INSERT only; no UPDATE or DELETE — preserves existing data.
--   * Idempotent via WHERE NOT EXISTS on capability code.
--   * Uses gen_random_uuid() — supported by both H2 (PostgreSQL
--     compatibility mode) and PostgreSQL.
--   * Grants the new capabilities to every ACTIVE ADMIN role using
--     the same CROSS JOIN pattern established by V20260702_2 and
--     V20260717_5 — deny-by-default for every non-admin role.
--
-- Compatible with PostgreSQL and H2 (PostgreSQL mode). No PL/pgSQL,
-- no JSONB, no partial indexes — runs identically on both engines.
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    VALUES
        ('EXECUTIVE_VIEW',        'View Executive Dashboard',     'View the executive management dashboard, tenant directory, plans, subscriptions, organizations and memberships'),
        ('EXECUTIVE_MANAGE',      'Manage Platform Resources',    'Create and update tenants, plans, subscriptions, organizations and memberships'),
        ('EXECUTIVE_BILLING',     'Manage Billing Invoices',       'Read billing invoices and mark them as paid'),
        ('SYSTEM_HEALTH_VIEW',    'View System Health',           'Read the platform health snapshot, service status, tenant pressure and risk forecast'),
        ('SYSTEM_HEALTH_MONITOR', 'Monitor and Self-Heal System',  'Execute controlled diagnostics, self-healing and maintenance actions on platform services'),
        ('SYSTEM_HEALTH_ALERTS',  'Manage System Health Alerts',  'Receive, acknowledge and configure system health alerts')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

-- Grant the new capabilities to every ACTIVE ADMIN role (deny-by-default
-- for all other roles). Mirrors the V20260717_5 grant pattern: the ADMIN
-- role is the only role that automatically receives new platform-wide
-- capabilities. Tenant admins can grant narrower subsets to other roles
-- via the standard ROLE.WRITE capability.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'EXECUTIVE_VIEW',
      'EXECUTIVE_MANAGE',
      'EXECUTIVE_BILLING',
      'SYSTEM_HEALTH_VIEW',
      'SYSTEM_HEALTH_MONITOR',
      'SYSTEM_HEALTH_ALERTS'
  )
 AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
