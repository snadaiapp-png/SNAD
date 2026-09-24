# WAVE 6 — Notifications + Global / Per-Partner / Partner-Self Dashboards + Invoice Dashboards (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence. **Revision D two-class doctrine:** implementation tasks follow test-first RED → minimal implementation → GREEN → affected regression → commit; verification/evidence/cutover-stage tasks follow PRECONDITION → VERIFY → EVIDENCE with untracked evidence and NO tracked evidence commit after the gate (no fabricated RED). Exact commands and commit boundaries are listed per task.

**Spec:** **Revision D** — §21 (notification events + mandatory PLATFORM_OWNER in-product deliveries), §21.1 (denormalized scope columns + consistency CHECKs + explicit RLS model + audience-source separation), §22 (three dashboard scopes), §22.4 (exact typed projection schema — no `subscriptions_*` placeholders), §22.5 (GLOBAL = DIRECT SANAD TENANTS + Σ(PARTNER DASHBOARDS)), §23 (invoice screens), §24 (IA), §26 (event-driven projections), §31.4/§31.6.
**Depends on:** W2 (event sources), W3 (portal commercial screen), W4/W5 (metric sources). **Migrations:** `V20260929_1`..`V20260929_5` · **Flags:** `SANAD_NOTIFICATIONS_ENABLED`, `SANAD_DASHBOARDS_ENABLED` (default `false`).
**Revision D (R3) changes in this wave:** the wave header identifies the effective planning contract as Revision D / R3; Task 10 is restated as a PRECONDITION → VERIFY → EVIDENCE task ending with NO TRACKED COMMIT — evidence external/untracked.
**Rev C (R2) changes in this wave:** (1) Task 1 — the `COALESCE(current_setting('app.current_user_id', true), recipient_user_id::text)` recipient-congruence pattern is DELETED (it makes the WITH CHECK vacuous whenever the GUC is unset — exactly the forbidden bypass form); replaced by an explicit two-branch write rule: session context (GUC set) ⇒ `recipient_user_id` must equal `app.current_user_id`; background context (GUC unset) ⇒ writes allowed ONLY from the verified control-plane context (carrier tenant GUC + `app.partner_id` NULL); (2) Task 1 — AUDIENCE SCOPE vs SOURCE ATTRIBUTION separated: each event row carries exactly ONE audience scope (the scope/`tenant_id`/`partner_id` triple RLS is computed from) plus pure attribution columns `source_partner_id`/`source_tenant_id` (nullable, never used by RLS, negative-tested); (3) W6 CONSUMES `app.current_user_id` read-only (introduced by W2); (4) Task 6 — canonical projection-backfill semantics stated (deterministic rebuild, idempotent, version-stamped — same rule W7 Task 3 enforces for the authorization projection).

## Goal

Deliver auditable in-product notifications with enforceable per-scope isolation, and governed dashboard projections whose schema is written column-exactly, applied idempotently through a typed per-scope event log, reconciling `GLOBAL = DIRECT + Σ(PARTNERS)`.

## Architecture

Notifications carry denormalized scope columns so every row is independently RLS-scoped WITHOUT joins (spec §21.1 — a bare "+ FORCE RLS" with no USING/WITH CHECK model is forbidden). Projection DDL enumerates every metric column explicitly. The apply-log is typed: `projection_scope` + nullable `partner_id` + nullable `bucket_month` with a scope CHECK — a single UUID `projection_key` is never overloaded to mean both a partner UUID and a platform `bucket_month DATE`. One governed query service powers all three dashboard scopes (metric drift prevention, §22).

## Tech Stack

Java 21 · Spring Boot single-module Maven · Flyway · PostgreSQL 16 (composite FORCE RLS with explicit policies) · Next.js `apps/web` (13 execution providers today → 14th `PARTNER`; `PROTECTED_ROOTS` currently `["/workspace","/crm","/control-plane","/executive"]`).

## Spec

§22.5 (Revision D) states the reconciliation identity directly and it replaces any historical `GLOBAL = SUM(PARTNERS)` assertion: direct tenants are intentionally supported, so the reconciliation formula is `GLOBAL = DIRECT SANAD TENANTS + SUM(PARTNER DASHBOARDS)` under identical filters.

## Implementation Baseline

Repository evidence at `8d0d49c7`: NO top-level `notification_*` tables (only `workflow_notification_policies/_intents/_user_notifications`, `V20260912_1`/`V20260902_6`); `apps/web/app/notifications/page.tsx` is a 6-line redirect to `/system-health`; `ExecutiveOverviewService` (`subscription/read/ExecutiveOverviewService.java`) provides tenant/subscription read models; `apps/web/app/providers.tsx` lines 12–14 define `PROTECTED_ROOTS` guarded by `providers-protected-roots.test.ts` (exact-equality); `app/control-plane/execution/page.tsx registerAllProviders()` (line 33) registers 13 providers, contract test `lib/execution/platform-contract-tests.test.ts`; `BillingOutbox` event types are `BILLING.*` (`subscription_billing_outbox`, no dispatcher — inline-transactional consumption per master risk A6); owner constants: control-plane tenant `00000000-0000-0000-0000-000000000001` (`V20260921_1` line 18), canonical owner user `00000000-0000-0000-0000-000000000010` (`JwtAuthenticationFilter.CANONICAL_PROJECT_OWNER_USER_ID`, lines 29–31).

## Global Constraints

1. DOCS-ONLY branch: not executed until the owner authorizes WAVE 6.
2. Notification RLS policies are written OUT in the migration (USING/WITH CHECK per scope) — never just "+ FORCE RLS".
3. Dashboard projection DDL is column-exact — the strings `subscriptions_*`/`invoices_*` MUST NOT appear as schema placeholders anywhere in this plan.
4. `dashboard_projection_events` uses the typed scope design of spec §22.4 verbatim.
5. Notifications never delete audit evidence (`platform_audit_logs` row counts/content unchanged by any notification path — tested).
6. Dashboards accept NO caller-supplied partner authority.

## Review Focus

Explicit RLS text; four mandatory notification isolation negatives; typed apply-log CHECK; direct-tenant reconciliation fixture; FE contract points (provider registered in BOTH files; protected-roots test updated in same commit).

---

Java paths relative to `apps/sanad-platform/src/main/java/com/sanad/platform/` (new packages `notification/`, `dashboard/`); tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`.

### Task 1: Notification schema — denormalized scope + explicit RLS policies

Files:
- Create: `db/migration/V20260929_1__notification_events_deliveries.sql`
- Test: `notification/NotificationSchemaRlsPostgresTest.java` (Create)

Interfaces:
- Consumes: `users`, `partners`, `tenants`; FORCE-RLS template `V20260905_5`.
- Produces: `notification_events`, `notification_deliveries` with consistency CHECKs and fully-written policies.

- [ ] Step 1: exact failing test — `notification/NotificationSchemaRlsPostgresTest.java` (plain-JDBC + `MigrationTestSchemaSupport`): (a) `notification_events` scope CHECKs enforced (`scope='TENANT'` without tenant_id ⇒ 23514; `scope='PARTNER'` with tenant_id ⇒ 23514; `scope='PLATFORM'` with either ⇒ 23514); (b) deliveries denormalize scope columns NOT NULL with the same CHECKs; (c) RLS: with GUC `app.partner_id` = partner A → partner-A rows only; partner-B rows invisible; with `app.tenant_id` = tenant A → tenant-A rows only; PLATFORM-owner rows (`scope='PLATFORM'`) invisible to a partner GUC context AND to a plain tenant context — readable only in control-plane context; NULL GUCs ⇒ zero rows (FORCE fail-closed).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.notification.NotificationSchemaRlsPostgresTest test` → relations missing (red).
- [ ] Step 3: exact minimal implementation — `V20260929_1__notification_events_deliveries.sql`:
  `notification_events(id uuid pk, scope text NOT NULL CHECK (scope IN ('PLATFORM','PARTNER','TENANT')), tenant_id uuid NULL REFERENCES tenants(id), partner_id uuid NULL REFERENCES partners(id), source_partner_id uuid NULL REFERENCES partners(id), source_tenant_id uuid NULL REFERENCES tenants(id), event_type text NOT NULL CHECK (event_type IN ('PARTNER_CREATED_USER','PARTNER_CREATED_TENANT','PARTNER_UPDATED_TENANT','PARTNER_ACTIVATED_TENANT','PARTNER_SUSPENDED_TENANT','PARTNER_CREATED_SUBSCRIPTION','PARTNER_UPDATED_SUBSCRIPTION','PARTNER_UPGRADED_SUBSCRIPTION','PARTNER_DOWNGRADED_SUBSCRIPTION','PARTNER_CANCELLED_SUBSCRIPTION','PARTNER_UPDATED_BUSINESS_PROFILE','PARTNER_CHANGED_ADMIN','TENANT_INVOICE_CREATED','TENANT_INVOICE_ISSUED','TENANT_PAYMENT_RECORDED','PARTNER_SETTLEMENT_CALCULATED','PARTNER_INVOICE_CREATED','PARTNER_INVOICE_ISSUED','TRIAL_ENDING_SOON')), actor_user_id uuid, target_type text, target_id uuid, summary jsonb NOT NULL DEFAULT '{}', correlation_id uuid, created_at timestamptz NOT NULL DEFAULT now(), read_at timestamptz NULL, CONSTRAINT ck_notif_events_scope_tenant CHECK ((scope='TENANT' AND tenant_id IS NOT NULL AND partner_id IS NULL) OR (scope='PARTNER' AND partner_id IS NOT NULL AND tenant_id IS NULL) OR (scope='PLATFORM' AND tenant_id IS NULL AND partner_id IS NULL)))` + explicit FORCE RLS: `DROP POLICY IF EXISTS tenant_isolation ON notification_events; CREATE POLICY notification_scope ON notification_events FOR ALL USING ( (scope='TENANT' AND tenant_id::text = current_setting('app.tenant_id', true)) OR (scope='PARTNER' AND partner_id::text = current_setting('app.partner_id', true)) OR (scope='PLATFORM' AND current_setting('app.tenant_id', true) = '00000000-0000-0000-0000-000000000001' AND current_setting('app.partner_id', true) IS NULL) ) WITH CHECK (same predicates);` — REV C AUDIENCE-SOURCE SEPARATION: `scope`+`tenant_id`+`partner_id` are the SINGLE audience-scope triple that RLS is computed from (ONE audience per event — a partner-action event surfaced to the platform owner is scope='PLATFORM' with `source_partner_id` set as ATTRIBUTION ONLY; the same event is never dual-scoped, so no actor can widen visibility via source columns); `source_partner_id`/`source_tenant_id` appear in NO policy predicate (negative-tested: forged source values cannot make any row visible).
  `notification_deliveries(id uuid pk, notification_event_id uuid NOT NULL REFERENCES notification_events(id), scope text NOT NULL CHECK (scope IN ('PLATFORM','PARTNER','TENANT')), tenant_id uuid NULL, partner_id uuid NULL, channel text NOT NULL CHECK (channel IN ('IN_PRODUCT','EMAIL','SMS','PUSH')), recipient_user_id uuid NOT NULL, status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DELIVERED','READ','FAILED')), delivered_at timestamptz, acknowledged_at timestamptz, created_at/updated_at, CONSTRAINT ck_notif_del_scope CHECK ((scope='TENANT' AND tenant_id IS NOT NULL AND partner_id IS NULL) OR (scope='PARTNER' AND partner_id IS NOT NULL AND tenant_id IS NULL) OR (scope='PLATFORM' AND tenant_id IS NULL AND partner_id IS NULL)))` + FORCE RLS with the same three-branch policy PLUS the Rev C recipient-congruence write rule (NO COALESCE bypass): `WITH CHECK (... scope branches ... AND ( (current_setting('app.current_user_id', true) IS NOT NULL AND recipient_user_id::text = current_setting('app.current_user_id', true)) OR (current_setting('app.current_user_id', true) IS NULL AND current_setting('app.tenant_id', true) = '00000000-0000-0000-0000-000000000001' AND current_setting('app.partner_id', true) IS NULL) ) )` — a session context can only ever write rows whose recipient is ITSELF (ack/read state), while mandatory platform fan-out writes happen only from the verified control-plane context; unset GUCs outside the control plane ⇒ no branch matches ⇒ zero rows (fail-closed); index `(recipient_user_id, status, created_at DESC)`. Notifications and audit are separate (§21): no code path deletes `platform_audit_logs` — enforced by Task 3.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave6(schema): notification scope columns, consistency checks, explicit rls (C1)"`.

### Task 2: Notification service + mandatory owner fan-out + emission wiring

Files:
- Create: `notification/NotificationService.java` (`emit(scope, eventType, actor, target, summary, correlationId)` — transactional insert + mandatory IN_PRODUCT delivery to canonical owner user `00000000-0000-0000-0000-000000000010` for every §21 event, SAME transaction; optional EMAIL/SMS/PUSH rows created PENDING, no provider calls), `notification/api/NotificationFeedController.java` (`/api/v1/notifications` list/unread-count/`POST /{id}/ack`; `/api/v1/partner/notifications` claim-scoped)
- Modify (one emission line each, all behind `NotificationService` no-op when flag off): `partner/application/PartnerAdminService.java`, `partner/application/PartnerTenantBindingService.java`, `partner/application/PartnerTenantProvisioningAdapter.java`, subscription delegation path in `partner/application/` (CREATED/UPDATED/UPGRADED/DOWNGRADED/CANCELLED_SUBSCRIPTION), `commercial/application/BusinessIdentityService.java`, `partner/billing/PartnerInvoiceService.java`, `partner/billing/CreditNoteService.java`, payment webhook path `subscription/billing/application/BillingSettlementService.java` (TENANT_PAYMENT_RECORDED), `partner/settlement/PartnerSettlementService.java`, `subscription/lifecycle/TrialContinuationScheduler.java` (TRIAL_ENDING_SOON)
- Test: `notification/MandatoryOwnerNotificationPostgresTest.java` (Create)

Interfaces:
- Consumes: W2/W3/W4/W5 services at their existing commit boundaries; `JwtAuthenticationFilter.CANONICAL_PROJECT_OWNER_USER_ID`.
- Produces: 12 partner-action + 6 billing/settlement + TRIAL_ENDING_SOON event coverage.

- [ ] Step 1: exact failing test — for EACH of the 12 partner-action events + 6 billing/settlement events: perform the mutation through the corresponding W2/W4/W5 service ⇒ exactly one PLATFORM-scope event + one IN_PRODUCT owner delivery row exists in the same transaction; a missing event type fails the enumerated list (red first).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.notification.MandatoryOwnerNotificationPostgresTest test` → emissions absent (red).
- [ ] Step 3: exact minimal implementation — service, controller, and the ten one-line wiring points.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerServiceTest,com.sanad.platform.partner.billing.PartnerInvoiceIssuancePostgresTest,com.sanad.platform.partner.settlement.PartnerSettlementCalculatePostgresTest test` → green (wiring did not alter service behavior).
- [ ] Step 6: exact commit — `git commit -m "wave6(notify): mandatory owner fan-out + emission wiring (C2)"`.

### Task 3: Notification isolation negatives + audit independence + ack ownership

Files:
- Create: `notification/NotificationIsolationPostgresTest.java`, `notification/NotificationAuditIndependencePostgresTest.java`
- Modify: none
- Test: both classes

Interfaces:
- Consumes: Task 1 policies; `platform_audit_logs` counts.
- Produces: spec §21.1/§31.6 negatives — Partner A cannot see Partner B deliveries; Tenant A cannot see Tenant B deliveries; partner cannot read PLATFORM-owner deliveries; a recipient cannot acknowledge another recipient's delivery (RLS WITH CHECK + service re-validation).

- [ ] Step 1: exact failing test — four isolation scenarios (SQLState/empty-result assertions) + cross-recipient ack rejected (23514 via WITH CHECK under session GUC `app.current_user_id`) + service-level `AckOwnershipException`; audit test: ack/read of 1_000 notifications leaves `platform_audit_logs` row count and content hash unchanged.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.notification.NotificationIsolationPostgresTest,com.sanad.platform.notification.NotificationAuditIndependencePostgresTest test` → cross-recipient ack succeeds (red).
- [ ] Step 3: exact minimal implementation — none (policies + service semantics shipped in Task 2; fix there if red).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.notification.MandatoryOwnerNotificationPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave6(notify): isolation negatives + audit independence proven (C2)"`.

### Task 4: Dashboard projections — every column explicit

Files:
- Create: `db/migration/V20260929_2__dashboard_projections.sql`
- Test: `dashboard/ProjectionSchemaExactPostgresTest.java` (Create)

Interfaces:
- Consumes: `partners`, `tenants`, `tenant_subscriptions`, `billing_invoices`, `finance_payments`, W5 settlement tables.
- Produces: `partner_dashboard_projection` + `platform_dashboard_projection` with EVERY column enumerated below (no `subscriptions_*`-style placeholders).

- [ ] Step 1: exact failing test — assert BOTH tables exist with EXACTLY the column lists below (name-by-name via `information_schema`), plus FORCE RLS (control-plane-only policy on the platform table; partner policy on the partner table).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.ProjectionSchemaExactPostgresTest test` → relations missing (red).
- [ ] Step 3: exact minimal implementation — `V20260929_2__dashboard_projections.sql`:
  `partner_dashboard_projection(partner_id uuid PRIMARY KEY REFERENCES partners(id), as_of timestamptz NOT NULL, tenants_total integer NOT NULL DEFAULT 0, tenants_active integer NOT NULL DEFAULT 0, tenants_inactive integer NOT NULL DEFAULT 0, tenants_suspended integer NOT NULL DEFAULT 0, subscriptions_active integer NOT NULL DEFAULT 0, subscriptions_trial integer NOT NULL DEFAULT 0, subscriptions_trial_ending integer NOT NULL DEFAULT 0, subscriptions_pending_continuation integer NOT NULL DEFAULT 0, subscriptions_active_billable integer NOT NULL DEFAULT 0, subscriptions_suspended integer NOT NULL DEFAULT 0, subscriptions_cancelled integer NOT NULL DEFAULT 0, subscriptions_expired integer NOT NULL DEFAULT 0, invoices_issued_count bigint NOT NULL DEFAULT 0, invoices_collected_count bigint NOT NULL DEFAULT 0, invoices_outstanding_count bigint NOT NULL DEFAULT 0, invoices_overdue_count bigint NOT NULL DEFAULT 0, invoices_draft_count bigint NOT NULL DEFAULT 0, invoices_credited_count bigint NOT NULL DEFAULT 0, invoices_gross_collected_minor bigint NOT NULL DEFAULT 0, eligible_net_collected_minor bigint NOT NULL DEFAULT 0, estimated_settlement_minor bigint NOT NULL DEFAULT 0, finalized_settlement_minor bigint NOT NULL DEFAULT 0, sanad_invoices_outstanding_minor bigint NOT NULL DEFAULT 0, computed_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0)` + partner FORCE RLS.
  `platform_dashboard_projection(bucket_month date NOT NULL, as_of timestamptz NOT NULL, partners_total integer NOT NULL DEFAULT 0, partners_active integer NOT NULL DEFAULT 0, partners_suspended integer NOT NULL DEFAULT 0, tenants_total integer NOT NULL DEFAULT 0, tenants_active integer NOT NULL DEFAULT 0, tenants_inactive integer NOT NULL DEFAULT 0, tenants_suspended integer NOT NULL DEFAULT 0, tenants_direct_total integer NOT NULL DEFAULT 0, trials_active integer NOT NULL DEFAULT 0, trials_trial_ending integer NOT NULL DEFAULT 0, trials_converted integer NOT NULL DEFAULT 0, trials_expired integer NOT NULL DEFAULT 0, subscriptions_active integer NOT NULL DEFAULT 0, subscriptions_trial integer NOT NULL DEFAULT 0, subscriptions_trial_ending integer NOT NULL DEFAULT 0, subscriptions_pending_continuation integer NOT NULL DEFAULT 0, subscriptions_active_billable integer NOT NULL DEFAULT 0, subscriptions_suspended integer NOT NULL DEFAULT 0, subscriptions_cancelled integer NOT NULL DEFAULT 0, subscriptions_expired integer NOT NULL DEFAULT 0, accounts_active integer NOT NULL DEFAULT 0, accounts_inactive integer NOT NULL DEFAULT 0, accounts_pending integer NOT NULL DEFAULT 0, invoices_issued_count bigint NOT NULL DEFAULT 0, invoices_collected_count bigint NOT NULL DEFAULT 0, invoices_outstanding_count bigint NOT NULL DEFAULT 0, invoices_overdue_count bigint NOT NULL DEFAULT 0, invoices_draft_count bigint NOT NULL DEFAULT 0, invoices_credited_count bigint NOT NULL DEFAULT 0, revenue_gross_collected_minor bigint NOT NULL DEFAULT 0, revenue_eligible_net_collected_minor bigint NOT NULL DEFAULT 0, revenue_settlement_base_minor bigint NOT NULL DEFAULT 0, settlement_calculated_count bigint NOT NULL DEFAULT 0, settlement_pending_approval_count bigint NOT NULL DEFAULT 0, settlement_finalized_count bigint NOT NULL DEFAULT 0, settlement_invoiced_count bigint NOT NULL DEFAULT 0, settlement_paid_outstanding_minor bigint NOT NULL DEFAULT 0, computed_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY (bucket_month))` + FORCE RLS control-plane-only policy.
  NOTE: `tenants_direct_total` and `trials_*`/`accounts_*` families exist precisely so the §22.5 identity `GLOBAL = DIRECT + Σ(PARTNERS)` is computable from the SAME governed projections; the derived display statistic `weighted_effective_fee_percent` is NOT a column (computed at read time per §18.7).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave6(schema): column-exact dashboard projections (C3)"`.

### Task 5: Typed projection event log

Files:
- Create: `db/migration/V20260929_3__projection_event_links.sql`
- Test: `dashboard/ProjectionEventTypingPostgresTest.java` (Create)

Interfaces:
- Consumes: `partners` (FK target).
- Produces: `dashboard_projection_events` with the spec §22.4 typed design verbatim.

- [ ] Step 1: exact failing test — `PARTNER` row with `bucket_month` set ⇒ 23514; `PLATFORM` row without `bucket_month` ⇒ 23514; `PLATFORM` row with `partner_id` set ⇒ 23514; `PARTNER` row without `partner_id` ⇒ 23514; idempotent UNIQUE `(projection_scope, partner_id, bucket_month, source_event_type, source_event_id)` enforced.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.ProjectionEventTypingPostgresTest test` → relation missing (red).
- [ ] Step 3: exact minimal implementation — `V20260929_3__projection_event_links.sql`: `dashboard_projection_events(id uuid pk, projection_scope text NOT NULL CHECK (projection_scope IN ('PLATFORM','PARTNER')), partner_id uuid NULL REFERENCES partners(id), bucket_month date NULL, source_event_type text NOT NULL, source_event_id uuid NOT NULL, applied_at timestamptz NOT NULL DEFAULT now(), CHECK ((projection_scope='PARTNER' AND partner_id IS NOT NULL AND bucket_month IS NULL) OR (projection_scope='PLATFORM' AND partner_id IS NULL AND bucket_month IS NOT NULL)), UNIQUE (projection_scope, partner_id, bucket_month, source_event_type, source_event_id))`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave6(schema): typed per-scope projection event log (C3)"`.

### Task 6: Projection service + read models + indexes

Files:
- Create: `db/migration/V20260929_4__projection_read_indexes.sql`, `db/migration/V20260929_5__projection_metric_contract_docs.sql`, `dashboard/DashboardProjectionService.java`
- Modify: none
- Test: `dashboard/PartnerProjectionPostgresTest.java` (Create)

Interfaces:
- Consumes: Tasks 4–5 tables; source tables (`tenants`, `tenant_subscriptions`, `billing_invoices`, `finance_payments`, W5 periods).
- Produces: `refreshPartner(partnerId)` (one transaction, idempotent via event log — no double count); `refreshPlatform(bucketMonth)` = Σ partner projections + DIRECT SANAD TENANTS computed from the same governed code path; on-read fallback when `computed_at` older than 5 minutes; §22 metric-family documentation as migration comment.

- [ ] Step 1: exact failing test — seeded fixtures compute exact typed metrics (spot-checked per column); event re-apply is idempotent (no double count); fallback triggers on stale `computed_at`; partner context reads own row only.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.PartnerProjectionPostgresTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the two migrations (indexes + §22 metric-family contract comment) + the service.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementReconciliationPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave6(projection): governed refresh service + read indexes (C4)"`.

### Task 7: Global reconciliation — DIRECT + Σ(PARTNERS) with the mandated fixture

Files:
- Create: `dashboard/DashboardReconciliationPostgresTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: §22.5 Rev B invariant; fixture requirements (≥2 partners, multiple tenants per partner, ≥1 direct SANAD tenant).
- Produces: machine proof replacing the WRONG `GLOBAL = SUM(PARTNERS)` identity.

- [ ] Step 1: exact failing test — fixture: partner P1 with tenants T1a/T1b, partner P2 with tenants T2a/T2b, DIRECT tenant TD (no binding); every governed metric family (tenants, trials, subscriptions, accounts, invoices, revenue, settlement) asserted as `platform_dashboard_projection.X == (direct-tenant contribution) + (P1 projection X) + (P2 projection X)` under identical period/plan/status/invoice-status/currency filters; ALSO assert the direct contribution is non-zero (guards against silently re-introducing `GLOBAL = Σ(PARTNERS)`).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.DashboardReconciliationPostgresTest test` → identity absent (red).
- [ ] Step 3: exact minimal implementation — none (shared query service from Task 6; fix there if red).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green (all metric families reconcile).
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.PartnerProjectionPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave6(dashboard): global equals direct plus partners, reconciled per metric (C4)"`.

### Task 8: Dashboard controllers + isolation

Files:
- Create: `dashboard/api/DashboardController.java` (`/api/v1/executive/dashboard/global`, `/api/v1/executive/dashboard/partners/{partnerId}`, `/api/v1/executive/invoices/oversight`, `/api/v1/partner/dashboard/self`, `/api/v1/partner/invoices/dashboard`), DTOs `GlobalDashboardResponse`, `PartnerDashboardResponse`, `PartnerSelfDashboardResponse`, `InvoiceDashboardResponse` (typed fields mirroring projection columns 1:1 — no free-form maps)
- Modify: none
- Test: `dashboard/DashboardIsolationPostgresTest.java`, `dashboard/api/DashboardControllerIT.java` (Create both)

Interfaces:
- Consumes: `DashboardProjectionService` (Task 6); `ControlPlaneAccessGuard`; `PartnerClaimResolver` (self endpoint claim-scoped, rejects `?partnerId=` ⇒ 403).
- Produces: §22.1 filters (period/partner/tenant/plan/status) + §22.2/§22.3 scopes + §23 invoice dashboards.

- [ ] Step 1: exact failing test — isolation: partner token on `/executive/dashboard/partners/{other}` ⇒ 403/empty; forged `?partnerId=` on `/partner/dashboard/self` ⇒ 403; server-side enforcement (not UI navigation). Controller IT: filters honored (exact fixture deltas); tenant-plane token on executive endpoints ⇒ 403.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.DashboardIsolationPostgresTest,com.sanad.platform.dashboard.api.DashboardControllerIT test` → 404 routes (red).
- [ ] Step 3: exact minimal implementation — controller + DTOs.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.api.PartnerPortalControllerIT test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave6(api): dashboard surfaces with server-side scope enforcement (C5)"`.

### Task 9: Frontend — feed, portal, dashboards, provider registration

Files:
- Create: `apps/web/lib/api/notifications-api.ts`, `apps/web/lib/api/notifications-api.test.ts`, `apps/web/app/notifications/page.tsx` (replaces the `/system-health` redirect with the feed UI: list/unread badge/ack, 30 s polling per `system-health-console` precedent), `apps/web/app/executive/dashboard/global/page.tsx`, `apps/web/app/executive/dashboard/partners/[id]/page.tsx`, `apps/web/app/partner/page.tsx`, `apps/web/app/partner/layout.tsx`, portal pages `apps/web/app/partner/{dashboard,commercial,tenants,subscriptions,invoices,collections,settlement,users,notifications,audit}/page.tsx`, `apps/web/app/executive/invoices/oversight/page.tsx`, `apps/web/app/partner/partner-execution-provider.ts`
- Modify: `apps/web/app/control-plane/execution/page.tsx` (`registerAllProviders()` gains the 14th `PARTNER` provider — BOTH registration points mandatory), `apps/web/lib/execution/platform-contract-tests.test.ts` (14th provider case), `apps/web/app/providers.tsx` (`PROTECTED_ROOTS` += `"/partner"`), `apps/web/app/providers-protected-roots.test.ts` (exact-equality update in the SAME commit), i18n `ar.ts`/`en.ts` (`notifications.*`, `dashboard.*`, `partner.portal.*`, same commit)
- Test: the API test + portal gating tests + protected-roots test

Interfaces:
- Consumes: `CommercialTaxInformationForm` (W3) reused for the portal commercial page; `ScpAccessProvider` fail-closed gating; perf budget watch `exec_dashboard_js` 350 KB (route-level code splitting, no new deps).
- Produces: §24.2 Partner Portal shell (NEW protected root) + §22/§23 dashboard pages.

- [ ] Step 1: exact failing test — `notifications-api.test.ts` URL assertions (`/api/v1/notifications`, `/api/v1/partner/notifications`, `POST /{id}/ack`); `platform-contract-tests.test.ts` fails with 13 providers until the 14th is registered; `providers-protected-roots.test.ts` fails until `PROTECTED_ROOTS` includes `/partner`.
- [ ] Step 2: exact command proving RED — `cd apps/web && npm test -- notifications-api && npm test -- platform-contract-tests && npm test -- providers-protected-roots` → red on all three.
- [ ] Step 3: exact minimal implementation — client + feed + portal shell + 10 portal pages + 3 dashboard pages + provider + registrations + i18n.
- [ ] Step 4: exact command proving GREEN — `cd apps/web && npm test -- notifications-api && npm test -- platform-contract-tests && npm test -- providers-protected-roots` → green.
- [ ] Step 5: exact affected regression — `cd apps/web && npm run typecheck && npm run lint && npm test && npm run build && python3 scripts/ci/check_i18n_keys.py` → green; visual-matrix configs pick up the new pages (baselines added in this commit).
- [ ] Step 6: exact commit — `git commit -m "wave6(web): notifications feed, partner portal, dashboards, 14th provider (C6)"`.

### Task 10: Wave exit evidence battery (verification/evidence task — PRECONDITION → VERIFY → EVIDENCE)

Files:
- Modify: none (evidence only)
- Test: full local battery

Interfaces:
- Consumes: PostgreSQL 16 local battery.
- Produces: `snad-evidence/evidence-<FINAL_WAVE_SHA>.log` (untracked).

- [ ] Step 1: PRECONDITION — the FINAL_WAVE_SHA candidate exists, `git status --short` is clean, and every W6 implementation task is committed green; battery environment up.
- [ ] Step 2: VERIFY — no new test is invented for this task (verification/evidence task class); the battery below IS the verification.
- [ ] Step 3: EVIDENCE ARTIFACT PLAN — one untracked log `snad-evidence/evidence-<FINAL_WAVE_SHA>.log` records `git rev-parse HEAD`, `git status --short`, every command, and every count; no tracked file is created or modified by this task.
- [ ] Step 4: exact command proving GREEN —
  `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build`
  `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false`
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest'` → 31/31 (6/15/10 unchanged).
- [ ] Step 5: exact affected regression — battery IS the regression; log at `snad-evidence/evidence-<FINAL_WAVE_SHA>.log`.
- [ ] Step 6: **NO TRACKED COMMIT — evidence external/untracked; report FINAL_WAVE_SHA** — the gate runs at ONE identical HEAD SHA recorded in the evidence log header, with `git status --short` clean at run time; any tracked commit after the gate invalidates the evidence and the FULL battery must be re-run at the new SHA. Evidence artifacts live ONLY in `snad-evidence/` (untracked) or CI artifacts — never as tracked commits after the gate.

## Dependencies, security, rollback

**Dependencies:** W2 event sources; W3 portal commercial screen; W4/W5 metric sources. Blocks W7. **Security:** dashboards accept NO caller-supplied partner authority (§22.3; negative-tested Task 8); owner vs partner vs tenant scopes physically separated by FORCE RLS with fully-written policies (§21.1); notifications never delete audit (tested); portal joins `PROTECTED_ROOTS` with fail-closed provider (client UX only — server authoritative). **Rollback:** both flags off ⇒ controllers 404-guarded, projections stop refreshing (stale but harmless), portal root hidden (`PROTECTED_ROOTS` revert is a 1-line revert with its exact-equality test); emission wiring is additive one-liners behind a `NotificationService` no-op when the flag is off; DDL additive; revert commits safe.
