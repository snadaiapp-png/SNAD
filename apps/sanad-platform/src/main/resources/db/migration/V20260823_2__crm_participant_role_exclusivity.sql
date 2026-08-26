-- ============================================================
-- SNAD Platform — CRM Contacts — Participant role exclusivity
--                + owner/participant DB invariant (Task C3)
-- ------------------------------------------------------------
-- W2 scope is CONTACT-ONLY:
--
--   * For CONTACT entity_type only, one user may hold at most ONE
--     active participant role per contact (COLLABORATOR OR WATCHER,
--     not both). Historical/removed participant rows are exempt.
--
--   * The CONTACT owner cannot simultaneously be an active CONTACT
--     participant on the same contact — both directions enforced
--     by triggers:
--       - participant INSERT/UPDATE → reject if user is the contact owner
--         (CRM_CONTACT_OWNER_CANNOT_PARTICIPATE)
--       - crm_contacts.owner_user_id UPDATE → reject if the new owner
--         is currently an active participant on this contact
--         (CRM_CONTACT_PARTICIPANT_CANNOT_BECOME_OWNER)
--
-- TASK/CASE semantics are UNCHANGED. The generic per-role unique
-- index `uk_crm_entity_participants_active` created by
-- V20260822_1 is PRESERVED (NOT dropped) — C3 adds a stricter
-- CONTACT-only partial unique index on top of it.
--
-- Lock order contract (CONTACT_ROW_FIRST):
--   * Participant-side trigger locks the parent crm_contacts row
--     with FOR SHARE before comparing owner_user_id. This guarantees
--     that a concurrent owner_user_id UPDATE on the same contact
--     cannot commit between our FOR SHARE read and our INSERT/UPDATE.
--     FOR SHARE is compatible with other FOR SHARE locks but blocks
--     UPDATE on the locked row.
--   * Owner-side trigger locks the crm_contacts row implicitly via
--     the UPDATE itself (row-level FOR UPDATE lock), then looks up
--     active participants. This guarantees a concurrent participant
--     INSERT cannot commit between the owner UPDATE and the active
--     participant lookup.
--   * Both directions therefore establish the same contact-first
--     lock order; there is no path that locks participant first
--     and then contact, which would risk deadlock against the
--     contact-first direction.
-- ============================================================

-- ------------------------------------------------------------
-- Section 7: CONTACT-only W2 partial unique index
-- ------------------------------------------------------------
-- Preserves the generic per-role `uk_crm_entity_participants_active`
-- index (created by V20260822_1). Adds a CONTACT-only partial unique
-- index that enforces "one active participant row per user per
-- contact" regardless of role (COLLABORATOR / WATCHER).
--
-- Predicate: removed_at IS NULL AND entity_type = 'CONTACT'
CREATE UNIQUE INDEX IF NOT EXISTS uk_crm_contact_participant_active_user
    ON crm_entity_participants (tenant_id, entity_id, user_id)
    WHERE removed_at IS NULL
      AND entity_type = 'CONTACT';

-- ------------------------------------------------------------
-- Section 8: Participant → Owner guard
-- ------------------------------------------------------------
-- BEFORE INSERT OR UPDATE OF tenant_id, entity_type, entity_id, user_id,
-- removed_at on crm_entity_participants, scoped to entity_type='CONTACT'
-- and active (removed_at IS NULL) rows.
--
-- 1. Validate transaction tenant context — current_setting('app.tenant_id')
--    must be non-null and equal NEW.tenant_id. This catches accidental
--    cross-tenant writes (defense-in-depth on top of FORCE RLS).
-- 2. Read the matching crm_contacts row FOR SHARE so a concurrent
--    owner_user_id UPDATE cannot race past our check.
-- 3. If crm_contacts.owner_user_id = NEW.user_id, raise
--    CRM_CONTACT_OWNER_CANNOT_PARTICIPATE (SQLSTATE check_violation).
-- 4. Removed/historical rows (NEW.removed_at IS NOT NULL) skip the
--    check — a former owner may legitimately have a historical
--    participant row.
CREATE OR REPLACE FUNCTION crm_check_contact_owner_not_participant()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    guc_value text;
    v_owner uuid;
BEGIN
    -- Only applies to CONTACT entity type.
    IF NEW.entity_type <> 'CONTACT' THEN
        RETURN NEW;
    END IF;
    -- Only active rows (removed_at IS NULL) participate in the invariant.
    -- Removed/historical rows may include a former owner.
    IF NEW.removed_at IS NOT NULL THEN
        RETURN NEW;
    END IF;
    -- Defense-in-depth: require tenant GUC and matching tenant_id.
    -- (FORCE RLS already enforces this, but the explicit check yields
    -- a deterministic error message that surfaces in test assertions.)
    guc_value := current_setting('app.tenant_id', true);
    IF guc_value IS NULL OR guc_value::uuid <> NEW.tenant_id THEN
        RAISE EXCEPTION 'CRM_PARTICIPANT_TENANT_CONTEXT_REQUIRED: app.tenant_id must match NEW.tenant_id (contact=%, tenant=%)',
            NEW.entity_id, NEW.tenant_id
            USING ERRCODE = 'check_violation';
    END IF;
    -- Lock the parent contact row FOR SHARE so concurrent
    -- crm_contacts.owner_user_id UPDATEs cannot commit between our
    -- read and our participant INSERT/UPDATE. Contact-first lock order.
    SELECT owner_user_id INTO v_owner
    FROM crm_contacts
    WHERE tenant_id = NEW.tenant_id AND id = NEW.entity_id
    FOR SHARE;
    IF v_owner IS NOT NULL AND v_owner = NEW.user_id THEN
        RAISE EXCEPTION 'CRM_CONTACT_OWNER_CANNOT_PARTICIPATE: owner_user_id % is already the active owner of contact %; cannot be a participant on the same contact',
            NEW.user_id, NEW.entity_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_contact_owner_not_participant ON crm_entity_participants;
CREATE TRIGGER trg_contact_owner_not_participant
    BEFORE INSERT OR UPDATE OF tenant_id, entity_type, entity_id, user_id, removed_at
    ON crm_entity_participants
    FOR EACH ROW
    WHEN (NEW.entity_type = 'CONTACT')
    EXECUTE FUNCTION crm_check_contact_owner_not_participant();

-- ------------------------------------------------------------
-- Section 9: Owner → Participant guard
-- ------------------------------------------------------------
-- BEFORE UPDATE OF owner_user_id on crm_contacts, when the owner
-- actually changes and NEW.owner_user_id IS NOT NULL.
--
-- 1. Validate tenant GUC + matching tenant_id (defense-in-depth).
-- 2. The UPDATE itself already holds a row-level FOR UPDATE lock on
--    the crm_contacts row, so concurrent participant INSERTs on this
--    contact cannot commit between the UPDATE and our active
--    participant lookup.
-- 3. If an active participant with user_id = NEW.owner_user_id exists,
--    raise CRM_CONTACT_PARTICIPANT_CANNOT_BECOME_OWNER
--    (SQLSTATE check_violation).
-- 4. Removed/historical participant rows do NOT block the owner change.
CREATE OR REPLACE FUNCTION crm_check_contact_owner_not_active_participant()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    guc_value text;
    has_active_participant boolean;
BEGIN
    -- Only enforce when owner actually changes.
    IF NEW.owner_user_id IS NOT DISTINCT FROM OLD.owner_user_id THEN
        RETURN NEW;
    END IF;
    -- No new owner → nothing to check.
    IF NEW.owner_user_id IS NULL THEN
        RETURN NEW;
    END IF;
    -- Defense-in-depth: require tenant GUC matching NEW.tenant_id.
    guc_value := current_setting('app.tenant_id', true);
    IF guc_value IS NULL OR guc_value::uuid <> NEW.tenant_id THEN
        RAISE EXCEPTION 'CRM_CONTACT_OWNER_TENANT_CONTEXT_REQUIRED: app.tenant_id must match NEW.tenant_id (contact=%, tenant=%)',
            NEW.id, NEW.tenant_id
            USING ERRCODE = 'check_violation';
    END IF;
    -- Check active participants. Removed/historical rows do not block.
    SELECT EXISTS (
        SELECT 1 FROM crm_entity_participants
        WHERE tenant_id = NEW.tenant_id
          AND entity_type = 'CONTACT'
          AND entity_id = NEW.id
          AND user_id = NEW.owner_user_id
          AND removed_at IS NULL
    ) INTO has_active_participant;
    IF has_active_participant THEN
        RAISE EXCEPTION 'CRM_CONTACT_PARTICIPANT_CANNOT_BECOME_OWNER: user % is currently an active CONTACT participant on contact %; cannot become the owner',
            NEW.owner_user_id, NEW.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_contact_owner_not_active_participant ON crm_contacts;
CREATE TRIGGER trg_contact_owner_not_active_participant
    BEFORE UPDATE OF owner_user_id ON crm_contacts
    FOR EACH ROW
    EXECUTE FUNCTION crm_check_contact_owner_not_active_participant();
