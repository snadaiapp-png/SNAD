-- ============================================================
-- Canonicalize access capability codes to the UPPERCASE convention.
-- ============================================================
-- ROOT CAUSE (production incident, correlationId edfce834-25db-4fff-a023-a268b2ed6ede):
-- V20260830_2 seeded the granular control-plane capability codes in
-- LOWERCASE ('audit.read', 'usage.read', 'subscription.read', ...), but the
-- platform capability-code convention is UPPERCASE — every legacy dotted code
-- is uppercase (e.g. CRM.ACCOUNT.READ, BUSINESS_PROCESS.READ) and
-- AccessCapabilityService.requireCode() normalizes every lookup to
-- UPPERCASE before the exact-match query:
--
--     loadByCode('audit.read')  ->  findByCode('AUDIT.READ')  ->  NOT FOUND
--
-- The result was a false CAPABILITY_NOT_FOUND deny for every endpoint
-- annotated @RequireCapability("audit.read") / @RequireCapability("usage.read")
-- (GovernanceController /audit/v2 and UsageController /usage), even though
-- the capabilities existed, were ACTIVE, and were granted to every
-- EXECUTIVE_VIEW-holding role by the backward-compatibility grant in
-- V20260830_2.
--
-- FIX: canonicalize the stored codes to UPPERCASE so the exact-match lookup
-- after requireCode() normalization resolves. This aligns the data with the
-- documented code convention instead of weakening the normalizer (which
-- also guards create()/update() conflict detection).
--
-- SAFETY:
--   * role_capabilities references capabilities by UUID id, never by code,
--     so renaming codes cannot orphan any grant;
--   * the RLS / tenant-isolation grants and capability statuses are
--     untouched;
--   * idempotent — a second run is a no-op (WHERE code <> UPPER(code));
--   * forward-only, additive to the audit trail (no DELETE markers).
-- ============================================================

UPDATE access_capabilities
SET code = UPPER(code),
    updated_at = NOW()
WHERE code <> UPPER(code);

-- Postcondition: every capability code is now convention-conformant.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM access_capabilities WHERE code <> UPPER(code)) THEN
        RAISE EXCEPTION 'capability code canonicalization incomplete';
    END IF;
END
$$;
