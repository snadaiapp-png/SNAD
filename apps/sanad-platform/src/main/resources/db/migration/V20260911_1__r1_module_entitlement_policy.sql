-- ============================================================
-- R1-A — Explicit module entitlement policy (paid opt-in)
-- ============================================================
-- R1 GATE R1.2: WORKFLOW is an OPTIONAL_PAID_MODULE + EXPLICIT_OPT_IN.
-- A module entitlement policy makes "no explicit plan entitlement row"
-- a fail-closed answer for opt-in modules, without changing the
-- backward-compatible fallback semantics of any existing module.
--
-- DEFAULT_COMPATIBILITY: historical semantics — when a plan has no
--   plan_module_entitlements rows for the module, resolve enablement
--   from the module catalog flag (modules.enabled). Existing modules
--   keep this behavior unless separately migrated and proven safe.
-- EXPLICIT_OPT_IN: an active subscription alone never grants the
--   module; only an explicit plan_module_entitlements row (or the
--   control plane enabling one) grants it.
--
-- Forward-only. No historical migration is rewritten.
-- ============================================================

ALTER TABLE modules
    ADD COLUMN IF NOT EXISTS entitlement_policy VARCHAR(30)
    NOT NULL DEFAULT 'DEFAULT_COMPATIBILITY';

ALTER TABLE modules
    DROP CONSTRAINT IF EXISTS ck_modules_entitlement_policy;

ALTER TABLE modules
    ADD CONSTRAINT ck_modules_entitlement_policy
    CHECK (entitlement_policy IN ('DEFAULT_COMPATIBILITY', 'EXPLICIT_OPT_IN'));

-- WORKFLOW is the first explicit paid opt-in module (R1 GATE R1.2).
UPDATE modules
   SET entitlement_policy = 'EXPLICIT_OPT_IN'
 WHERE code = 'WORKFLOW';

COMMENT ON COLUMN modules.entitlement_policy IS
    'R1 commercial policy: DEFAULT_COMPATIBILITY keeps the historical catalog-flag fallback when a plan has no entitlement rows; EXPLICIT_OPT_IN requires an explicit plan_module_entitlements row (fail closed otherwise).';
