-- V20261117_1 — WS7 WEBSITE MODULE TENANT-SCOPED INTEGRITY (Session 8)
--               Re-implementation of the certified Session 7 migration whose
--               local commit (71c8a51f, unified WS7+PR1537) was lost with the
--               recycled execution environment. Behavior contract restored
--             exactly as certified by the WS7 hardening matrix:
--             ws7-hardening-v2/WS7-HARDENING-TRANSCRIPT-v2-PG17.12.txt and the
--             preserved Session 7 transcript (20-probe matrix, PG 17.12).
--
-- Mandate: Session 6 Continuation §3 (WS7 hardening) + Session 7 §02.
--
-- WHAT THIS LOCKS DOWN (all tenant-scoped, no new roles, no BYPASSRLS,
--                      no SECURITY DEFINER — pure declarative integrity):
--   W1  website_publications(tenant_id, page_id) → website_pages(tenant_id, id)
--       — dangling page references and CROSS-TENANT page references become
--         impossible (SQLSTATE 23503), matching probes F1a/F1b.
--   W2  website_navigation_items(tenant_id, target_page_id) →
--         website_pages(tenant_id, id)
--       — same fail-closed semantics for navigation targets (F2a/F2b).
--   W3  One PRIMARY website per tenant — partial unique index on
--         websites(tenant_id) WHERE is_primary (race probe F3a: exactly one
--         winner, SQLSTATE 23505).
--   W4  One PRIMARY domain per tenant+website — partial unique index on
--         website_domains(tenant_id, website_id) WHERE is_primary (F3b).
--   W5  Referential protection for published pages — the composite FKs of
--         W1/W2 keep referenced pages from being deleted or re-keyed out
--         from under publications/navigation items (probes 5a/5b, 23503).
--
-- WHY COMPOSITE FKs: every target already carries UNIQUE (tenant_id, id)
--   (V20260816_3), so the composite binding guarantees both existence AND
--   same-tenancy in one declarative constraint — enforced below the RLS
--   layer, immune to any future policy drift.
--
-- Forward-only. Idempotent. Never rewrites published history.
-- ============================================================================

-- W1: publications may only reference pages of the SAME tenant.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'website_publications'::regclass
           AND conname  = 'fk_website_pub_page_tenant_scoped'
    ) THEN
        ALTER TABLE website_publications
            ADD CONSTRAINT fk_website_pub_page_tenant_scoped
            FOREIGN KEY (tenant_id, page_id)
            REFERENCES website_pages (tenant_id, id);
    END IF;
END $$;
-- W2: navigation items may only target pages of the SAME tenant.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'website_navigation_items'::regclass
           AND conname  = 'fk_website_nav_items_target_page_tenant_scoped'
    ) THEN
        ALTER TABLE website_navigation_items
            ADD CONSTRAINT fk_website_nav_items_target_page_tenant_scoped
            FOREIGN KEY (tenant_id, target_page_id)
            REFERENCES website_pages (tenant_id, id);
    END IF;
END $$;
-- W3: exactly one primary website per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uk_websites_tenant_primary
    ON websites (tenant_id)
    WHERE is_primary;
-- W4: exactly one primary domain per tenant+website.
CREATE UNIQUE INDEX IF NOT EXISTS uk_website_domains_tenant_website_primary
    ON website_domains (tenant_id, website_id)
    WHERE is_primary;
-- G3: post-apply verification — fail closed on any drift.
DO $$
DECLARE
    v_fk_w1   integer;
    v_fk_w2   integer;
    v_idx_w3  integer;
    v_idx_w4  integer;
BEGIN
    SELECT count(*) INTO v_fk_w1 FROM pg_constraint
     WHERE conrelid = 'website_publications'::regclass
       AND conname  = 'fk_website_pub_page_tenant_scoped';
    SELECT count(*) INTO v_fk_w2 FROM pg_constraint
     WHERE conrelid = 'website_navigation_items'::regclass
       AND conname  = 'fk_website_nav_items_target_page_tenant_scoped';
    SELECT count(*) INTO v_idx_w3 FROM pg_indexes
     WHERE tablename = 'websites' AND indexname = 'uk_websites_tenant_primary';
    SELECT count(*) INTO v_idx_w4 FROM pg_indexes
     WHERE tablename = 'website_domains'
       AND indexname = 'uk_website_domains_tenant_website_primary';

    IF v_fk_w1 <> 1 OR v_fk_w2 <> 1 OR v_idx_w3 <> 1 OR v_idx_w4 <> 1 THEN
        RAISE EXCEPTION 'WS7_INTEGRITY_DRIFT: fk_w1=% fk_w2=% idx_w3=% idx_w4=% — expected 1/1/1/1',
            v_fk_w1, v_fk_w2, v_idx_w3, v_idx_w4;
    END IF;
END $$;
