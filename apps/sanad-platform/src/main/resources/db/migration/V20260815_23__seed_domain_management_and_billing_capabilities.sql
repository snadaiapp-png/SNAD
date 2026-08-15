-- ============================================================
-- V20260815_23: Add DOMAIN_MANAGEMENT.* + BILLING.* capabilities
--
-- Closes the senior management governance gap identified by the
-- v20260815.7 forensic audit:
--   * Item 17 (Revenue Oversight) needs BILLING.* capabilities.
--   * Item 4 (Domain Management) needs DOMAIN_MANAGEMENT.* capabilities.
--
-- These capabilities are bound to the ADMIN role for every tenant
-- so platform operators can manage their tenant domains and review
-- billing state without needing a manual capability grant.
--
-- H2 compatibility: pure idempotent INSERTs, runs unchanged on both
-- PostgreSQL and H2.
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('DOMAIN_MANAGEMENT.VIEW',   'Domain Management View',   'View tenant custom/default domains and verification status'),
    ('DOMAIN_MANAGEMENT.WRITE',  'Domain Management Write',  'Add, update, and remove tenant domains; initiate DNS verification'),
    ('DOMAIN_MANAGEMENT.ADMIN',  'Domain Management Admin',  'Set primary domain, force-verify, deactivate a domain'),
    ('DOMAIN_MANAGEMENT.VERIFY', 'Domain Management Verify',  'Confirm DNS verification challenge and activate a verified domain'),
    ('BILLING.VIEW',   'Billing View',   'View invoices, payments, billing state, and dunning history'),
    ('BILLING.WRITE',   'Billing Write',  'Mark invoices paid, issue manual adjustments, apply credits'),
    ('BILLING.ADMIN',   'Billing Admin',  'Override billing state, trigger dunning run, manage payment gateway webhooks')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants (idempotent)
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'DOMAIN_MANAGEMENT.VIEW', 'DOMAIN_MANAGEMENT.WRITE',
       'DOMAIN_MANAGEMENT.ADMIN', 'DOMAIN_MANAGEMENT.VERIFY',
       'BILLING.VIEW', 'BILLING.WRITE', 'BILLING.ADMIN'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
