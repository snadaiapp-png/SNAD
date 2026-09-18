-- ============================================================
-- V20260908_2 — HRM-G1: RLS ENABLE + FORCE + tenant_isolation policies
--               on every §5.2 table (design §8.1, G0 policy shape)
-- ============================================================
-- Policy shape (identical to G0 V20260905_16):
--   USING  (tenant_id::text = current_setting('app.tenant_id', true))
--   WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true))
-- Missing context ('app.tenant_id' unset → NULL) yields an empty result
-- set / denied write — fail closed by construction. FORCE makes the table
-- owner (sanad) subject to RLS as well — no bypass.
-- ============================================================

ALTER TABLE hr_job_openings ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_job_openings FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_job_openings;
CREATE POLICY tenant_isolation ON hr_job_openings FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_job_opening_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_job_opening_periods FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_job_opening_periods;
CREATE POLICY tenant_isolation ON hr_job_opening_periods FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_candidates ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_candidates FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_candidates;
CREATE POLICY tenant_isolation ON hr_candidates FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_applications FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_applications;
CREATE POLICY tenant_isolation ON hr_applications FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_application_stage_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_application_stage_periods FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_application_stage_periods;
CREATE POLICY tenant_isolation ON hr_application_stage_periods FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_interviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_interviews FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_interviews;
CREATE POLICY tenant_isolation ON hr_interviews FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_interview_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_interview_participants FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_interview_participants;
CREATE POLICY tenant_isolation ON hr_interview_participants FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_interview_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_interview_feedback FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_interview_feedback;
CREATE POLICY tenant_isolation ON hr_interview_feedback FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_offers ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_offers FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_offers;
CREATE POLICY tenant_isolation ON hr_offers FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_offer_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_offer_versions FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_offer_versions;
CREATE POLICY tenant_isolation ON hr_offer_versions FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_hire_conversions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_hire_conversions FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_hire_conversions;
CREATE POLICY tenant_isolation ON hr_hire_conversions FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_onboarding_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_onboarding_plans FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_onboarding_plans;
CREATE POLICY tenant_isolation ON hr_onboarding_plans FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_onboarding_checklist_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_onboarding_checklist_templates FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_onboarding_checklist_templates;
CREATE POLICY tenant_isolation ON hr_onboarding_checklist_templates FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_onboarding_checklists ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_onboarding_checklists FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_onboarding_checklists;
CREATE POLICY tenant_isolation ON hr_onboarding_checklists FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_onboarding_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_onboarding_tasks FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_onboarding_tasks;
CREATE POLICY tenant_isolation ON hr_onboarding_tasks FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
