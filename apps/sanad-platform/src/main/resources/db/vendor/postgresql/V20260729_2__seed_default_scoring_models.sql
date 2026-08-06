-- ============================================================
-- SNAD Platform — CRM-010 — V20260729.2
-- Default Scoring Models Seed
--
-- Seeds default scoring model weights for each score type.
-- These are tenant-agnostic defaults; tenants can override.
-- ============================================================

-- Note: Scoring models are seeded as global defaults.
-- In production, each tenant will get their own models via
-- the ScoringModelUseCases application layer.

-- For now, we create a single "default" tenant record pattern.
-- The application layer will clone these per tenant on first access.
INSERT INTO crm_scoring_models (id, tenant_id, score_type, version, weights, active, activated_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000000',
       'HEALTH', '1.0',
       '{"engagement": 0.30, "pipeline": 0.25, "response": 0.20, "support": 0.15, "nps": 0.10}'::jsonb,
       FALSE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM crm_scoring_models
    WHERE score_type = 'HEALTH' AND version = '1.0'
);

INSERT INTO crm_scoring_models (id, tenant_id, score_type, version, weights, active, activated_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000000',
       'ENGAGEMENT', '1.0',
       '{"meeting_freq": 0.35, "email_open": 0.20, "response_time": 0.25, "activity_count": 0.20}'::jsonb,
       FALSE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM crm_scoring_models
    WHERE score_type = 'ENGAGEMENT' AND version = '1.0'
);

INSERT INTO crm_scoring_models (id, tenant_id, score_type, version, weights, active, activated_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000000',
       'RISK', '1.0',
       '{"churn_signals": 0.40, "engagement_decline": 0.30, "tenure": 0.15, "support_issues": 0.15}'::jsonb,
       FALSE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM crm_scoring_models
    WHERE score_type = 'RISK' AND version = '1.0'
);

INSERT INTO crm_scoring_models (id, tenant_id, score_type, version, weights, active, activated_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000000',
       'LOYALTY', '1.0',
       '{"tenure": 0.30, "repeat_business": 0.30, "advocacy": 0.20, "engagement": 0.20}'::jsonb,
       FALSE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM crm_scoring_models
    WHERE score_type = 'LOYALTY' AND version = '1.0'
);
