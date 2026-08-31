-- ============================================================
-- V20260829_1: SCP-G1 — Application catalog (Subscription Control Plane)
--
-- Creates the `applications` catalog — the data-backed source of truth for
-- applications rendered by the executive console. The UI must never hardcode
-- the application list; adding a new application is a catalog row, not a
-- code change.
--
-- Seed: existing `modules` registry rows (V20260814_1) become applications so
-- the catalog starts populated from day one.
--
-- Platform-scoped catalog (like saas_plans / modules) — intentionally NOT
-- tenant-scoped, therefore no RLS.
-- Forward-only, additive, idempotent (IF NOT EXISTS / WHERE NOT EXISTS).
-- ============================================================

CREATE TABLE IF NOT EXISTS applications (
    id                  UUID            NOT NULL,
    code                VARCHAR(50)     NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    localized_name      VARCHAR(200),
    description         VARCHAR(1000),
    category            VARCHAR(50)     NOT NULL DEFAULT 'MODULE',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version             VARCHAR(20),
    display_order       INTEGER         NOT NULL DEFAULT 0,
    icon_key            VARCHAR(50),
    provisioning_mode   VARCHAR(20)     NOT NULL DEFAULT 'IMMEDIATE',
    supported_countries JSONB           NOT NULL DEFAULT '["GLOBAL"]'::jsonb,
    dependencies        JSONB           NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_applications PRIMARY KEY (id),
    CONSTRAINT uk_applications_code UNIQUE (code),
    CONSTRAINT ck_applications_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DEPRECATED')),
    CONSTRAINT ck_applications_provisioning CHECK (provisioning_mode IN ('IMMEDIATE', 'MANUAL', 'ASYNC'))
);

CREATE INDEX IF NOT EXISTS idx_applications_status ON applications (status);
CREATE INDEX IF NOT EXISTS idx_applications_display_order ON applications (display_order);

-- Seed the catalog from the existing module registry (idempotent).
INSERT INTO applications (
    id, code, name, localized_name, description, category, status,
    version, display_order, icon_key, provisioning_mode,
    supported_countries, dependencies, created_at, updated_at
)
SELECT gen_random_uuid(), m.code, m.name, NULL, m.description, 'MODULE', m.status,
       m.version, m.display_order, lower(m.code), 'IMMEDIATE',
       '["GLOBAL"]'::jsonb, '[]'::jsonb, NOW(), NOW()
FROM modules m
WHERE NOT EXISTS (SELECT 1 FROM applications a WHERE a.code = m.code);
