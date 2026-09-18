-- ============================================================================
-- V20260918_8 — HRM-G1 T10 candidate self-service identity + scoped capabilities
-- Forward-only and additive. Candidate remains recruitment-scoped and is NOT
-- converted into hr_people before governed hire conversion.
-- ============================================================================

ALTER TABLE hr_candidates
    ADD COLUMN iam_user_id UUID;

ALTER TABLE hr_candidates
    ADD CONSTRAINT fk_hr_candidates_iam_user
    FOREIGN KEY (tenant_id, iam_user_id)
    REFERENCES users (tenant_id, id);

CREATE UNIQUE INDEX uq_hr_candidates_tenant_iam_user
    ON hr_candidates (tenant_id, iam_user_id)
    WHERE iam_user_id IS NOT NULL;

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('HRM.RECRUITMENT.APPLICATION.SUBMIT', 'Recruitment application submit',
     'Submit an application for the IAM-bound candidate identity'),
    ('HRM.RECRUITMENT.APPLICATION.WITHDRAW', 'Recruitment application withdraw',
     'Withdraw an application owned by the IAM-bound candidate identity'),
    ('HRM.RECRUITMENT.OFFER.ACCEPT', 'Recruitment offer accept',
     'Accept an offer owned by the IAM-bound candidate identity'),
    ('HRM.RECRUITMENT.OFFER.DECLINE', 'Recruitment offer decline',
     'Decline an offer owned by the IAM-bound candidate identity')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);
