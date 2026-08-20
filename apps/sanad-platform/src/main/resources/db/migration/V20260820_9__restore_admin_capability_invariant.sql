-- ============================================================
-- V20260820_9 — Restore ADMIN capability invariant
-- ============================================================
--
-- Platform invariant (enforced since Mission 01 / V20260813.1):
--   every ACTIVE capability is granted to EVERY ADMIN role.
--
-- V20260819_1 (HR employees) seeded 8 HR.* capabilities but bound them
-- only to the hardcoded seed ADMIN role
-- (tenant 00000000-0000-0000-0000-000000000001 / role
-- 00000000-0000-0000-0000-000000000100). Tenants whose ADMIN roles
-- predate that migration — and the FlywayV15 production-upgrade
-- simulation, which creates a fresh tenant + ADMIN before migrating
-- forward — end up with ACTIVE capabilities the ADMIN role never
-- received (identified in CI:
-- adminAssignments=145 vs activeCapabilities=153, delta=8 = HR.*).
--
-- This migration restores the invariant generically and idempotently
-- for ALL tenants/ADMIN roles, without touching applied migrations
-- (editing V20260819_1 would break its Flyway checksum on deployed
-- databases).
--
-- Safety:
--   * NOT EXISTS guard => idempotent; safe to run on any database.
--   * SELECT ... FROM tenants/roles/access_capabilities => only binds
--     capabilities that exist and are ACTIVE, to roles that exist.
--   * No deletes, no grants to non-ADMIN roles, no cross-tenant writes.
-- ============================================================

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
