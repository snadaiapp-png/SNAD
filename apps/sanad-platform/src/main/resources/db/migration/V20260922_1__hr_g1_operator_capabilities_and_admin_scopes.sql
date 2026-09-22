-- ============================================================================
-- V20260922_1 — HRM-G1 canonical capability catalog + ADMIN tenant scopes
--
-- Closes the production gap where G1 controller/UI capability constants existed
-- but the operator capability families were not fully present in
-- access_capabilities. Candidate self-service capabilities from V20260918_8 are
-- preserved idempotently. HR_MANAGER is intentionally not widened.
-- ============================================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), c.code, c.name, c.description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('HRM.RECRUITMENT.OPENING.VIEW', 'Recruitment opening view', 'View recruitment job openings'),
    ('HRM.RECRUITMENT.OPENING.MANAGE', 'Recruitment opening manage', 'Create and manage recruitment job openings'),
    ('HRM.RECRUITMENT.OPENING.PUBLISH', 'Recruitment opening publish', 'Approve and publish recruitment job openings'),
    ('HRM.RECRUITMENT.CANDIDATE.VIEW', 'Recruitment candidate view', 'View minimized recruitment candidate records'),
    ('HRM.RECRUITMENT.CANDIDATE.MANAGE', 'Recruitment candidate manage', 'Create, update, and archive recruitment candidates'),
    ('HRM.RECRUITMENT.APPLICATION.MANAGE', 'Recruitment application manage', 'Manage recruitment applications'),
    ('HRM.RECRUITMENT.APPLICATION.SUBMIT', 'Recruitment application submit', 'Submit an application for the IAM-bound candidate identity'),
    ('HRM.RECRUITMENT.APPLICATION.WITHDRAW', 'Recruitment application withdraw', 'Withdraw an application owned by the IAM-bound candidate identity'),
    ('HRM.RECRUITMENT.APPLICATION.ADVANCE', 'Recruitment application advance', 'Advance recruitment applications through governed stages'),
    ('HRM.RECRUITMENT.APPLICATION.REJECT', 'Recruitment application reject', 'Reject recruitment applications with governed reason codes'),
    ('HRM.RECRUITMENT.INTERVIEW.MANAGE', 'Recruitment interview manage', 'Manage recruitment interviews and feedback'),
    ('HRM.RECRUITMENT.INTERVIEW.SCHEDULE', 'Recruitment interview schedule', 'Schedule recruitment interviews'),
    ('HRM.RECRUITMENT.INTERVIEW.RECORD_OUTCOME', 'Recruitment interview outcome', 'Record governed recruitment interview outcomes'),
    ('HRM.RECRUITMENT.OFFER.MANAGE', 'Recruitment offer manage', 'Create and manage recruitment offers'),
    ('HRM.RECRUITMENT.OFFER.ACCEPT', 'Recruitment offer accept', 'Accept an offer owned by the IAM-bound candidate identity'),
    ('HRM.RECRUITMENT.OFFER.DECLINE', 'Recruitment offer decline', 'Decline an offer owned by the IAM-bound candidate identity'),
    ('HRM.RECRUITMENT.OFFER.EXTEND', 'Recruitment offer extend', 'Extend an approved recruitment offer'),
    ('HRM.RECRUITMENT.OFFER.APPROVE', 'Recruitment offer approve', 'Approve recruitment offers under separation of duties'),
    ('HRM.RECRUITMENT.HIRE.CONVERT', 'Recruitment hire convert', 'Convert an accepted candidate offer into the canonical hire transaction'),
    ('HRM.ONBOARDING.PLAN.MANAGE', 'Onboarding plan manage', 'Create, view, and cancel onboarding plans'),
    ('HRM.ONBOARDING.TASK.COMPLETE', 'Onboarding task complete', 'Complete assigned onboarding tasks'),
    ('HRM.ONBOARDING.TASK.WAIVE', 'Onboarding task waive', 'Waive onboarding tasks with a governed reason')
) AS c(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = c.code
);

DO $$
DECLARE
    t RECORD;
    r RECORD;
    cap RECORD;
    missing_role_capabilities INTEGER;
    missing_scope_grants INTEGER;
BEGIN
    FOR t IN SELECT id FROM tenants WHERE status = 'ACTIVE' LOOP
        PERFORM set_config('app.tenant_id', t.id::text, false);

        FOR r IN
            SELECT id, tenant_id
              FROM roles
             WHERE tenant_id = t.id
               AND code = 'ADMIN'
               AND status = 'ACTIVE'
        LOOP
            FOR cap IN
                SELECT id, code
                  FROM access_capabilities
                 WHERE status = 'ACTIVE'
                   AND (code LIKE 'HRM.RECRUITMENT.%' OR code LIKE 'HRM.ONBOARDING.%')
            LOOP
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), r.tenant_id, r.id, cap.id, NOW()
                WHERE NOT EXISTS (
                    SELECT 1
                      FROM role_capabilities rc
                     WHERE rc.tenant_id = r.tenant_id
                       AND rc.role_id = r.id
                       AND rc.capability_id = cap.id
                );

                INSERT INTO access_scope_grants (
                    id, tenant_id, role_id, capability_id, scope_type,
                    is_direct_exception, reason, status, created_at
                )
                SELECT gen_random_uuid(), r.tenant_id, r.id, cap.id, 'TENANT',
                       FALSE, 'HRM-G1 canonical ADMIN tenant scope', 'ACTIVE', NOW()
                WHERE NOT EXISTS (
                    SELECT 1
                      FROM access_scope_grants g
                     WHERE g.tenant_id = r.tenant_id
                       AND g.role_id = r.id
                       AND g.capability_id = cap.id
                       AND g.scope_type = 'TENANT'
                       AND g.status = 'ACTIVE'
                );
            END LOOP;

            SELECT COUNT(*)
              INTO missing_role_capabilities
              FROM access_capabilities c
             WHERE c.status = 'ACTIVE'
               AND (c.code LIKE 'HRM.RECRUITMENT.%' OR c.code LIKE 'HRM.ONBOARDING.%')
               AND NOT EXISTS (
                   SELECT 1
                     FROM role_capabilities rc
                    WHERE rc.tenant_id = r.tenant_id
                      AND rc.role_id = r.id
                      AND rc.capability_id = c.id
               );

            IF missing_role_capabilities <> 0 THEN
                RAISE EXCEPTION 'ADMIN role % is missing % HRM-G1 capabilities',
                    r.id, missing_role_capabilities;
            END IF;

            SELECT COUNT(*)
              INTO missing_scope_grants
              FROM access_capabilities c
             WHERE c.status = 'ACTIVE'
               AND (c.code LIKE 'HRM.RECRUITMENT.%' OR c.code LIKE 'HRM.ONBOARDING.%')
               AND NOT EXISTS (
                   SELECT 1
                     FROM access_scope_grants g
                    WHERE g.tenant_id = r.tenant_id
                      AND g.role_id = r.id
                      AND g.capability_id = c.id
                      AND g.scope_type = 'TENANT'
                      AND g.status = 'ACTIVE'
               );

            IF missing_scope_grants <> 0 THEN
                RAISE EXCEPTION 'ADMIN role % is missing % HRM-G1 tenant scope grants',
                    r.id, missing_scope_grants;
            END IF;
        END LOOP;
    END LOOP;

    PERFORM set_config('app.tenant_id', '', false);
END $$;
