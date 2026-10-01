-- HRM G3 Task 2 — performance reviews persistence + fail-closed RLS.
-- PostgreSQL Direct. No Docker/Testcontainers.
-- Scope intentionally limited to the G3 performance-review contracts;
-- capability catalog seeding belongs to the G3 API slice (Task 4).
--
-- Canonical identity invariants:
--   review.tenant_id + review.employment_id + review.person_id must resolve to
--   the same canonical hr_employees row (subject).
--   review.tenant_id + review.reviewer_employment_id + review.reviewer_person_id
--   must resolve to the same canonical hr_employees row (reviewer).
--   This prevents cross-tenant subject/reviewer references and prevents
--   attaching a review to a Person different from the Employment owner.
--
-- Lifecycle: DRAFT -> SUBMITTED -> ACKNOWLEDGED, DRAFT -> CANCELLED.
--   Structural status values are guarded here; transition legality is
--   validated by the application service on top of this table.
--
-- Source rules (structural):
--   SELF    -> reviewer is the subject;
--   MANAGER / PEER -> reviewer is a different canonical employment.

CREATE TABLE hr_performance_reviews (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,

    person_id               UUID         NOT NULL,
    employment_id           UUID         NOT NULL,

    reviewer_person_id      UUID         NOT NULL,
    reviewer_employment_id  UUID         NOT NULL,

    source                  VARCHAR(20)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    cycle                   VARCHAR(80)  NOT NULL,
    period_start            DATE         NOT NULL,
    period_end              DATE         NOT NULL,

    rating                  INTEGER,
    comments                TEXT,

    version                 INTEGER      NOT NULL DEFAULT 0,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by              UUID,

    CONSTRAINT pk_hr_performance_reviews PRIMARY KEY (id),
    CONSTRAINT fk_hr_performance_reviews_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_performance_reviews_canonical_subject
        FOREIGN KEY (tenant_id, employment_id, person_id)
        REFERENCES hr_employees (tenant_id, id, person_id),
    CONSTRAINT fk_hr_performance_reviews_canonical_reviewer
        FOREIGN KEY (tenant_id, reviewer_employment_id, reviewer_person_id)
        REFERENCES hr_employees (tenant_id, id, person_id),
    CONSTRAINT ck_hr_performance_reviews_source
        CHECK (source IN ('SELF', 'MANAGER', 'PEER')),
    CONSTRAINT ck_hr_performance_reviews_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACKNOWLEDGED', 'CANCELLED')),
    CONSTRAINT ck_hr_performance_reviews_period
        CHECK (period_end >= period_start),
    CONSTRAINT ck_hr_performance_reviews_rating_range
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_hr_performance_reviews_rating_required
        CHECK (status NOT IN ('SUBMITTED', 'ACKNOWLEDGED') OR rating IS NOT NULL),
    CONSTRAINT ck_hr_performance_reviews_self_reviewer_is_subject
        CHECK (source <> 'SELF'
               OR (reviewer_employment_id = employment_id
                   AND reviewer_person_id = person_id)),
    CONSTRAINT ck_hr_performance_reviews_nonself_reviewer_differs
        CHECK (source = 'SELF' OR reviewer_employment_id <> employment_id),
    CONSTRAINT uq_hr_performance_reviews_reviewer_source_period
        UNIQUE (tenant_id, employment_id, reviewer_employment_id, source, period_start, period_end)
);

CREATE INDEX ix_hr_performance_reviews_tenant_employment
    ON hr_performance_reviews (tenant_id, employment_id);

CREATE INDEX ix_hr_performance_reviews_tenant_reviewer
    ON hr_performance_reviews (tenant_id, reviewer_employment_id);

CREATE INDEX ix_hr_performance_reviews_tenant_status
    ON hr_performance_reviews (tenant_id, status);

ALTER TABLE hr_performance_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_performance_reviews FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hr_performance_reviews FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
