# SCP Recovery Chain — STAGE-1 (R0C-2R) Re-Certification

**Task**: R0C-RECOVERY-CHAIN
**Stage**: STAGE-1 — R0C-2R (subscription pricing country authority, scalar
queryForObject defects, command-ledger schema overflow)
**Branch**: `scp/r0c-recovery-chain`
**Base**: `origin/main @ 7f30c4ff1f8c8f856bb17126fb6364c9eae6b291` (verified via
`git fetch origin --prune` + `git ls-remote` — base did not move)
**Date**: 2026-09-03

The original R0C-2R branch was lost in a sandbox reset (predecessor hashes
`ccb9c76f…`, `8fae3048…`, `2d5a9a72…` are unreachable: absent locally, absent
from origin's 1293 refs, rejected by direct SHA fetch, and no recovery bundle
exists). Its report is treated as **hypothesis only**. Every defect below was
re-discovered and re-proven against the current repository at 7f30c4ff.

---

## 1. Forensic re-discovery (current repository)

All targets re-located in the current tree:

| Target | File / Evidence |
|---|---|
| `tenants.country_code` | `V16__extend_platform_identity_and_tenants.sql:5` — `VARCHAR(2)`, the authoritative tenant country column. No other tenant country column exists. |
| `tenant_subscriptions` | `V19__create_saas_administration.sql:38–66` + `V20260829_2` (plan_version_id) + `V20260830_1` (13-status CHECK). **No country column has ever existed on this table.** |
| `subscription_commands` | `V20260830_1__scp_lifecycle_and_provisioning.sql:33–48` — `command VARCHAR(40)`, `from_status VARCHAR(24) NOT NULL`, `to_status VARCHAR(24) NOT NULL`, `reason VARCHAR(500)`. Never widened. |
| Pricing country flow | `LifecycleController.previewChange/executeChange` (`:79–95`) passed the client body `countryCode` (unvalidated, default `"GLOBAL"`) directly into `SubscriptionChangeService` → `PriceResolver`. The server never consulted `tenants.country_code`. |
| Scalar queries | `SubscriptionChangeService.requireSubscription` (`:174–182`) `SELECT tenant_id, plan_id` → `UUID.class`; `billingInterval` (`:184–193`) `SELECT billing_cycle, country_code` → `String.class`. |
| Ledger writer | `SubscriptionChangeService.execute` (`:153–161`) bound `"TARGET_VERSION=" + uuid` (51 chars) into `to_status VARCHAR(24)`. |
| Masking root cause | `SubscriptionChangeServiceTest.activeSubscriptionRow()` stubbed the exact broken SQL strings, so Mockito unit tests passed while real PostgreSQL failed. |

## 2. Defect verdicts (re-proven, not copied)

- **P0-A invalid tenant subscription country source — CONFIRMED.** The pricing
  country was taken from the client request body (authority = CLIENT), and the
  only server-side attempt read `tenant_subscriptions.country_code`, a column
  that does not exist.
- **P0-B multi-column scalar queryForObject — CONFIRMED.** Both scalar calls
  throw `IncorrectResultSetColumnCountException` ("expected 1, actual 2") on
  real PostgreSQL; every preview/execute 500ed at runtime.
- **P0-C subscription_commands.to_status overflow — CONFIRMED** (value differs
  from the lost report's example): the written value is
  `TARGET_VERSION=<uuid>` = 51 characters vs `VARCHAR(24)` — PostgreSQL
  SQLSTATE 22001. The command-ledger writer (`SubscriptionCommandService`) is
  within limits (max `PENDING_ACTIVATION` = 17) and was left untouched.

## 3. RED evidence (PostgreSQL Direct, pristine 7f30c4ff + new test only)

New test class (first `*PostgresTest` in the subscription package, following
the `JdbcCallEventRepositoryPostgresTest` + `MigrationTestSchemaSupport`
conventions, isolated `test_migration` database, Flyway clean+migrate+validate):

`src/test/java/com/sanad/platform/subscription/change/SubscriptionChangeServicePostgresTest.java`

RED run result (`mvn test -Dtest=SubscriptionChangeServicePostgresTest`):

```
Tests run: 8, Failures: 0, Errors: 4
```

- 4 × `IncorrectResultSetColumnCountException: Incorrect column count:
  expected 1, actual 2` at `SubscriptionChangeService.requireSubscription:176`
  → `preview:65` / `execute:116` — **P0-B proven on real PostgreSQL.**
- `historicalBillingCountrySqlTargetsNonexistentColumn` (passes on pristine as
  contract proof): `BadSqlGrammarException` rooted in
  `PSQLException: ERROR: column "country_code" does not exist` — **P0-A
  invalid source proven.**
- `ledgerToStatusColumnRejectsHistoricalOversizedValue` (passes on pristine as
  contract proof): `DataIntegrityViolationException … value too long for type
  character varying(24)` for the exact 51-char historical value — **P0-C
  proven.**
- `subscriptionTableHasNoCountryColumn`: `information_schema` shows
  `tenants.country_code` exists (1) and `tenant_subscriptions.country_code`
  does not (0).

## 4. Fixes (minimal, no migration, no harness change)

All changes confined to `SubscriptionChangeService.java` (+ its unit test):

1. **P0-B / P0-A source** — `requireSubscription()` and `billingInterval()`
   replaced by one `SubscriptionContext` RowMapper query:
   `SELECT s.tenant_id, s.plan_id, s.status, s.billing_cycle, t.country_code
   FROM tenant_subscriptions s JOIN tenants t ON t.id = s.tenant_id`. No
   multi-column scalar mapping remains.
2. **P0-A authority** — `preview`/`execute` price exclusively with
   `tenants.country_code` (fallback `GLOBAL` when the tenant has no country).
   The client-supplied `countryCode` parameter is retained on the wire for
   compatibility but is **never used for pricing** —
   **CLIENT_COUNTRY_AUTHORITY = NONE**.
3. **P0-C** — the ledger row now writes the subscription's actual lifecycle
   status into `from_status`/`to_status` (both ≤ 17 chars; a plan change does
   not transition status) and carries `TARGET_VERSION=<uuid>` inside
   `reason VARCHAR(500)`. No schema change. `NEW_MIGRATIONS = 0`.
4. `ChangePreview.fromStatus` now reports the real subscription status
   (previously the constant `"CURRENT"`, which is a billing-state vocabulary
   value, not a lifecycle status).

Wire compatibility: no route, DTO field, or successful-response shape
changed. `ChangePreview.fromStatus` semantics corrected from a bogus constant
to the actual status; the frontend displays it as free text with no pinned
value.

## 5. GREEN evidence (PostgreSQL Direct)

```
SubscriptionChangeServicePostgresTest  Tests run: 8,  Failures: 0, Errors: 0
SubscriptionChangeServiceTest          Tests run: 7,  Failures: 0, Errors: 0
Total: 15/15 PASS
```

Key GREEN assertions:
- `preview` with a rogue client country `AE` for an `SA` tenant returns the
  **SA** price (90000) — not AE (50000), not GLOBAL (70000).
- Tenant without country → GLOBAL price (70000), server-side.
- `execute` on real PostgreSQL: old PLAN item CANCELLED, exactly one ACTIVE
  PLAN item pinned to the target version at the tenant-country price, ledger
  row with `command=PLAN_CHANGE`, `from_status=to_status=ACTIVE`,
  `reason` contains `TARGET_VERSION=<uuid>`.
- Unknown subscription id still rejected with `IllegalArgumentException`.
- Unit tests now verify the resolver is called with the tenant country and
  `never()` with the client country, and that the ledger bind values fit the
  schema.

## 6. PostgreSQL Direct environment

- Real PostgreSQL 16.15 server (Debian trixie-pgdg binaries) started by the
  test harness operator script at `127.0.0.1:5432`, provisioned exactly per
  the `ci.yml` contract: bootstrap `postgres` superuser (provisioning only),
  least-privilege application role `sanad` (NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOBYPASSRLS, md5 TCP), `sanad` database owned by `sanad`, disposable
  `test_migration` database, `crm_contact_rls_test_user`.
- No Docker, no Testcontainers, no H2 used anywhere in STAGE-1 evidence.
- Acceptance runs used the same env contract as CI
  (`SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/sanad?prepareThreshold=0`,
  `sanad`/`sanad_pass`, Flyway enabled, JPA validate).

## 7. Stage-1 acceptance checklist

| Requirement | Result |
|---|---|
| P0_A = CLOSED | ✅ tenant country authoritative, client value ignored (PG + unit proof) |
| P0_B = CLOSED | ✅ single RowMapper context query; 4 RED errors → 0 |
| P0_C = CLOSED | ✅ ledger values fit VARCHAR(24); detail in VARCHAR(500) reason |
| CLIENT_COUNTRY_AUTHORITY = NONE | ✅ verified at resolver boundary (`never()` client country) |
| POSTGRESQL_DIRECT = PASS | ✅ 8/8 on real PG 16.15 |
| Red before green | ✅ 4 errors + 2 contract proofs on pristine 7f30c4ff |
| NEW_MIGRATIONS = 0 | ✅ no migration files touched |
| Harness unchanged | ✅ test uses existing `Crm009TestEnvironment` / `MigrationTestSchemaSupport` |
| FULL_MAVEN_SUITE | ✅ PASS — 2428 tests, 0 failures, 0 errors, 6 intentional skips |

## 8. Full Maven suite

`mvn test -B -ntp` with the CI env contract above, serial (no concurrent Maven
runs share `target/`).

**Result: BUILD SUCCESS — Tests run: 2428, Failures: 0, Errors: 0,
Skipped: 6.** The 6 skips are the pre-existing intentional concurrency-test
skips (the `pg-acceptance`-profile-gated classes, identical to CI behavior).

### 8.1 Pre-existing harness defects found and fixed (§17 protocol)

The first full-suite run failed in two CRM test classes. Per the harness
policy the failures were **reproduced on pristine 7f30c4ff first** (stash of
all STAGE-1 changes): pristine showed the SAME two failures
(`Tests run: 2418, Failures: 1, Errors: 1`):

- `CrmIntegrationOutboxWorkerTest.claimReturnsIncrementedVersionAndToken`
  (failure: `expected 1L but was 2L`) — leftover PENDING rows in
  `crm_integration_outbox` on the shared database; the claim CTE picks the
  oldest PENDING event, so assertions were execution-order-dependent.
- `CrmIntegrationOutboxRecoveryTest` (error: Flyway
  `V20260722.1 precondition failed: 1 of 2 target tables already exist`) —
  earlier classes (notably `Crm008bFoundationAcceptanceTest`, which by design
  walks intermediate migration states) leave `test_migration` with history at
  `20260721.2` while later-version tables exist; the recovery test's plain
  `migrate()` then detonates the `V20260722.1` guard.

Both failures are order-dependent cross-test state interference: GitHub CI
passes the same code (`ci` workflow green on `f6249d7`, and `7f30c4ff` = #942
did not touch `apps/sanad-platform`), while the sandbox's different filesystem
scan order surfaces them. Minimal **test-only** fixes (no production code, no
harness semantics change):

- `CrmIntegrationOutboxWorkerTest`: clear `crm_integration_outbox` in
  `@BeforeEach` before seeding, making claim-order assertions deterministic.
- `CrmIntegrationOutboxRecoveryTest`: clean+migrate the isolated
  `test_migration` database in `@BeforeAll` (same convention as
  `JdbcCallEventRepositoryPostgresTest`), making the class order-independent.

No surefire configuration, no run-order control, no production workaround.

## 9. Files changed (STAGE-1)

- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/change/SubscriptionChangeServicePostgresTest.java` (new — RED/GREEN evidence)
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/change/SubscriptionChangeService.java` (fix)
- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/change/SubscriptionChangeServiceTest.java` (stop masking; verify new contracts)
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/CrmIntegrationOutboxWorkerTest.java` (§17 test-only order-independence fix)
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/CrmIntegrationOutboxRecoveryTest.java` (§17 test-only order-independence fix)
- `docs/superpowers/plans/2026-09-03-scp-recovery-stage-1-r0c2r.md` (this document)
