-- ============================================================================
-- G1 P0 WS7: WEBSITE-MODULE REFERENTIAL INTEGRITY COMPLETION (F1 / F2 / F3).
-- Forward-only migration. Do not edit already-applied Flyway versions.
--
-- RECONCILIATION PROVENANCE (2026-11-25): this fix was originally authored
-- and locally verified as V20261124_1 (run g1x-2026-11-24: 261/261 chain
-- apply, WS7 15/15 fail-closed checks) but the executing environment was
-- recycled before the file was committed; commit d7eba448 is unrecoverable.
-- It is restored here from (a) the verified object definitions captured in
-- the isolated cluster catalog and (b) the recorded verification contract.
-- RENUMBERED V20261124_1 -> V20261124_3 to resolve a version collision with
-- the signed tenant-context package (F-DB-07 / RT-01), which retains
-- V20261124_1/V20261124_2 as applied in its own execution run. Neither file
-- was ever applied to production; the migration DATE (20261124) is
-- unchanged. See docs/g1-reconciliation/G1-RECONCILIATION-REGISTER-20261125.md.
--
-- F1: website_publications.page_id must reference a page OF THE SAME TENANT.
-- F2: website_navigation_items PAGE targets must carry target_page_id
--     (type-conditional CHECK) and reference a page OF THE SAME TENANT.
-- F3: exactly one primary row per scope — websites per tenant,
--     website_domains per (tenant, website), tenant_domains per
--     (tenant, domain_type) — enforced with partial unique indexes, which
--     also closes the concurrent-promote race (unique index is the arbiter).
-- Requires unique (tenant_id,id) on website_pages/websites (pre-checked).
-- Existing single-column keys and behavior are retained; nothing is dropped.
-- Flyway must execute transactionally as the authorized schema owner/migrator.
-- ============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $pre$
BEGIN
  IF to_regclass('public.website_publications') IS NULL
     OR to_regclass('public.website_navigation_items') IS NULL
     OR to_regclass('public.website_pages') IS NULL
     OR to_regclass('public.websites') IS NULL
     OR to_regclass('public.website_domains') IS NULL
     OR to_regclass('public.tenant_domains') IS NULL THEN
    RAISE EXCEPTION 'G1_WS7_MISSING_TABLE';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.website_pages'::regclass
      AND conname = 'uk_website_pages_tenant_id' AND contype = 'u'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.websites'::regclass
      AND conname = 'uk_websites_tenant_id' AND contype = 'u'
  ) THEN
    RAISE EXCEPTION 'G1_WS7_MISSING_REFERENCED_TENANT_KEY';
  END IF;
  IF EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname IN ('fk_website_pub_page', 'fk_website_nav_items_page',
                      'ck_website_nav_items_page_required')
  ) OR EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public' AND indexname IN (
      'uq_websites_tenant_single_primary',
      'uq_website_domains_website_single_primary',
      'uq_tenant_domains_type_single_primary')
  ) THEN
    RAISE EXCEPTION 'G1_WS7_ALREADY_EXISTS_CHECK_MIGRATION_HISTORY';
  END IF;
END
$pre$;

-- F1 — publication.page_id composite tenant-scoped FK.
ALTER TABLE public.website_publications
  ADD CONSTRAINT fk_website_pub_page
  FOREIGN KEY (tenant_id, page_id)
  REFERENCES public.website_pages (tenant_id, id)
  NOT VALID;

ALTER TABLE public.website_publications
  VALIDATE CONSTRAINT fk_website_pub_page;

-- F2a — navigation item PAGE targets composite tenant-scoped FK.
ALTER TABLE public.website_navigation_items
  ADD CONSTRAINT fk_website_nav_items_page
  FOREIGN KEY (tenant_id, target_page_id)
  REFERENCES public.website_pages (tenant_id, id)
  NOT VALID;

ALTER TABLE public.website_navigation_items
  VALIDATE CONSTRAINT fk_website_nav_items_page;

-- F2b — a PAGE navigation item cannot be stored without its target page.
ALTER TABLE public.website_navigation_items
  ADD CONSTRAINT ck_website_nav_items_page_required
  CHECK (target_type::text <> 'PAGE' OR target_page_id IS NOT NULL);

-- F3 — single-primary per scope; partial unique indexes arbitrate the race.
CREATE UNIQUE INDEX uq_websites_tenant_single_primary
  ON public.websites (tenant_id)
  WHERE (is_primary = true);

CREATE UNIQUE INDEX uq_website_domains_website_single_primary
  ON public.website_domains (tenant_id, website_id)
  WHERE (is_primary = true);

CREATE UNIQUE INDEX uq_tenant_domains_type_single_primary
  ON public.tenant_domains (tenant_id, domain_type)
  WHERE (is_primary = true);
