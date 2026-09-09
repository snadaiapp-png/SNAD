# SNAD SCP R0C-11 — Product Catalog Runtime Implementation Plan

- **Date:** 2026-09-06
- **Spec:** `docs/superpowers/specs/2026-09-06-scp-r0c-11-product-catalog-runtime-design.md` (frozen)
- **Base SHA:** `dabf4867b7e76410532bc85a2f6c5781eb0cc69e`
- **Branch:** `scp/r0c-11-product-catalog-runtime`
- **Method:** strict TDD. Every production line is justified by a RED test first:
  **RED → prove failure → GREEN → prove success → REFACTOR.**
  No production implementation before its RED proof.

## Environment Contract (PostgreSQL Direct)

- Host-native PostgreSQL 16.2 cluster, `127.0.0.1:5433` (pgserver distribution,
  data dir `/home/z/tools/pgdata`; bootstrap actor `pgadmin` via local trust socket).
- Least-privilege application role `sanad` (`rolsuper=f`, `rolcreatedb=f`,
  `rolcreaterole=f`, `rolcanlogin=t`, `rolbypassrls=f`); credentials outside the
  repository; never printed into logs or evidence.
- Disposable databases: `sanad` (shared read/seed) and `test_migration`
  (per-test isolated, pre-provisioned, owned by `sanad` — the
  `MigrationTestSchemaSupport` contract).
- Env contract for Maven: `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad`,
  `SPRING_DATASOURCE_USERNAME=sanad`, `SPRING_DATASOURCE_PASSWORD=<local secret>`.
- NO Docker, NO Testcontainers, NO Podman, NO H2 certification anywhere.
- JDK 21 (`/home/z/tools/jdk21-deb/...`), Maven 3.9.16 (`/home/z/tools/apache-maven-3.9.16`).

## Workstream Order (each gate must pass before the next)

### W1 — RED: product domain runtime tests
1. `ProductCatalogServiceTest` (unit, Mockito — mirrors
   `ApplicationCatalogServiceTest`):
   - create normalizes code (trim + uppercase), requires name, defaults status ACTIVE,
     rejects unknown type, rejects unknown status;
   - duplicate code → fail-closed deterministic error;
   - code never changes on update; status/type validated on update;
   - unknown application FK → deterministic domain error;
   - nullable application allowed; findAvailable returns ACTIVE only;
   - NO delete method (compile-level absence asserted by reflection test).
2. Prove RED: `mvn test -Dtest=ProductCatalogServiceTest` fails to compile
   (types absent) → capture as the RED proof.

### W2 — GREEN: product domain runtime
1. `ProductEntity`, `ProductRepository` (JdbcTemplate, raw SQL,
   `ApplicationRepository` conventions), `ProductCatalogService`
   (create/update/findById/findByCode/findAll/findAvailable).
2. Uniqueness: `existsByCode` pre-check + deterministic mapping of the
   `uk_products_code` violation for the concurrent race.
3. Unknown application FK checked via `applications` existence query.
4. Prove GREEN: W1 suite passes 0F/0E/0S.
5. REFACTOR: extract shared validation constants; no behavior change; re-run W1.

### W3 — RED→GREEN: product API + RBAC + audit
1. RED: controller-level unit tests (`CatalogControllerProductEndpointsTest`):
   - GET list (all + availableOnly) requires `catalog.read` and guard;
   - GET by id requires `catalog.read` and guard; unknown id → 400-class domain error;
   - POST requires `catalog.manage`, emits `PRODUCT_CREATE` audit
     (`resourceType=product`, `resourceId=<id>`), no audit on rejection;
   - PUT requires `catalog.manage`, emits `PRODUCT_UPDATE` audit with before/after;
   - NO DELETE endpoint (reflection absence);
   - DTO validation (`ProductRequest`/`ProductResponse` round-trip).
2. GREEN: additive `ProductRequest`/`ProductResponse` in `ScpDtos`; additive
   endpoints in `CatalogController` (`catalog.read`/`catalog.manage`).
3. Audit exactly as §8 of the spec (success-only, before/after on update).
4. REFACTOR + re-run W1+W3.

### W4 — RED→GREEN: subscription item integrity
1. RED: extend `SubscriptionItemServiceTest`:
   - ADD_ON item with unknown product → fail-closed;
   - ADD_ON item referencing APPLICATION/OTHER/METERED product → rejected;
   - ADD_ON item referencing INACTIVE/ARCHIVED ADD_ON product → rejected;
   - METERED item type-compatible-only ACTIVE METERED product (mirror cases);
   - valid ADD_ON/METERED attachments succeed and snapshot the product name;
   - product later INACTIVE/ARCHIVED → existing items untouched, historical
     references resolve (no cancellation, no deletion);
   - PLAN items unaffected (productId irrelevant to PLAN validation).
2. GREEN: `SubscriptionItemService.addItem` product-backed integrity block
   (existence + type compatibility + ACTIVE contract) using the new
   `ProductRepository`.
3. REFACTOR + re-run W1+W3+W4.

### W5 — PostgreSQL Direct acceptance battery (Phase 12)
1. `ProductCatalogRuntimePostgresTest` — full directive battery on the isolated
   disposable database (Flyway clean/migrate/validate per test,
   `MigrationTestSchemaSupport` isolation, least-privilege `sanad` role):
   schema exists; allowed types; invalid types rejected; allowed statuses;
   invalid status rejected; unique code; case-normalized collision; concurrent
   collision; create/read/update; available-only filtering; nullable application;
   valid application; invalid application; stable code; archive without delete;
   price FK; entitlement FK; ADD_ON attachment; METERED attachment; unknown
   product rejection; inactive/archived new-sale rejection; historical-reference
   preservation; audit; RBAC read; RBAC manage; unauthorized denial;
   ControlPlaneAccessGuard; no physical delete; R0C-10 effective-subscription
   regression.
2. Require: `R0C11_PG_ACCEPTANCE_FAILURES=0`, `…_ERRORS=0`, `…_SKIPPED=0`;
   record the actual test count.

### W6 — Regression & recertification (Phases 13–14)
1. R0C-10 canonical acceptance suite re-run (the 8 `*PostgresTest` classes,
   48 tests at certification) → `R0C10_REGRESSION=PASS`.
2. Predecessor suites (discovered, not invented): `ApplicationCatalogServiceTest`,
   `SubscriptionItemServiceTest`, `PlanVersionServiceTest`, `PriceResolverTest`,
   `PriceCalculatorTest`, `ItemAwareEntitlementResolverTest`,
   `UsageMeteringServiceTest`, `SubscriptionChangeServiceTest`,
   `ExecutiveReadModelsTest`, `ScpFrontendContractTest` +
   subscription API route tests → `PREDECESSOR_RECERT=PASS` (0F/0E).

### W7 — Flyway, security, canonical run (Phases 15–18)
1. Flyway inventory: branch + origin/main duplicate-version scan; expect
   `R0C11_NEW_MIGRATIONS=0`, `FLYWAY_DUPLICATE_VERSION_COUNT=0`; validate chain
   (the acceptance battery already runs Flyway validate on every isolated run).
2. Security + data integrity sweeps (secrets, SQL injection surface, physical
   deletes, unguarded writes, privilege expansion, R0C-10 status-writer
   regression, broken FKs).
3. Code freeze of test surface, then **exactly ONE** canonical run:
   `mvn clean -B -ntp` then `mvn test -B -ntp -Dsurefire.useFile=false`
   (no sharding, no parallel invocations, no class batching).
   Require `FULL_MAVEN_FAILURES=0`, `FULL_MAVEN_ERRORS=0`,
   `FULL_MAVEN_BUILD=SUCCESS`, count reconciliation PASS on fresh Surefire XML.
4. `CANONICAL_RUN_CODE_HEAD=<SHA>`; after success no source/test/migration
   change (`POST_CANONICAL_CODE_DELTA=0`).

### W8 — Certification (Phase 19)
1. `docs/superpowers/plans/2026-09-06-scp-r0c-11-final-certification.md` —
   actual values only.
2. Commit exactly: `docs(scp): certify and close R0C-11 product catalog runtime`;
   push noninteractively; verify `FINAL_HEAD == REMOTE_FINAL_HEAD` and
   `dabf4867b7e76410532bc85a2f6c5781eb0cc69e` is an ancestor.
3. `MERGE=NO`, `DEPLOY=NO`, `R0C12=FORBIDDEN`. STOP.

## Commit Plan

| # | Message | Content |
|---|---|---|
| 1 | `docs(scp): freeze R0C-11 product catalog runtime design` | design + implementation plan |
| 2 | `test(scp): product catalog runtime red tests` | W1/W3/W4 RED tests |
| 3 | `feat(scp): product catalog domain runtime` | W2 GREEN |
| 4 | `feat(scp): product catalog API with granular RBAC and audit` | W3 GREEN |
| 5 | `feat(scp): subscription item product integrity` | W4 GREEN |
| 6 | `test(scp): R0C-11 PostgreSQL Direct acceptance battery` | W5 |
| 7 | `docs(scp): certify and close R0C-11 product catalog runtime` | W8 (final) |

## Risk Register

| Risk | Mitigation |
|---|---|
| Granular capability codes not granted to operator roles in local fixture | acceptance tests seed grants explicitly in the isolated DB (same pattern as `V20260830_2`); production seeds untouched |
| Concurrent duplicate-code race | pre-check + deterministic mapping of SQL unique violation; concurrency proven by two-thread test |
| R0C-10 regression from item validation | validation only fires for NEW product-backed items; PLAN path untouched; R0C-10 suite re-run proves it |
| `test_migration` DB drift | Flyway clean/migrate/validate per test (established convention) |
| Sandbox reaping background Maven | canonical launcher uses `start_new_session=True` (R0C-10 proven pattern) |
