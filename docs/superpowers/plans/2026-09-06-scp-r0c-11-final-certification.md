# R0C-11 Final Certification — Product Catalog Runtime Closure

Date: 2026-09-06
Branch: `scp/r0c-11-product-catalog-runtime`
Verdict: **R0C_11_STATUS=CLOSED · R0C_11_CLOSURE_VERDICT=PASS · R0C11_FINAL_CERTIFICATION=PASS**

Every value in this document was re-verified mechanically from preserved fresh local
evidence (canonical log parse, ElementTree parse of all 346 fresh Surefire XMLs, git
forensics, and read-only repository scans). No value is transcribed on trust.

---

## 1. Chain Identity

| Field | Value |
|---|---|
| R0C10_FINAL_BASE | `dabf4867b7e76410532bc85a2f6c5781eb0cc69e` (`docs(scp): certify and close R0C-10 multiplicity runtime`) |
| REMOTE_R0C10_HEAD (Phase 1 verification) | `dabf4867b7e76410532bc85a2f6c5781eb0cc69e` (exact message match) |
| R0C11_BRANCH | `scp/r0c-11-product-catalog-runtime` |
| R0C11_BASE_SHA | `dabf4867b7e76410532bc85a2f6c5781eb0cc69e` (branch created from the exact SHA; main NOT used as base) |
| CURRENT_ORIGIN_MAIN_HEAD (read-only reference) | `748e2c6076c94b7a29e2a5e8e4f4a817b0f2fc2b` |
| Ancestry | `git merge-base --is-ancestor dabf4867… HEAD` → exit 0 (forward-only) |
| Remote state before final push | `REMOTE_PRE_FINAL=dabf4867b7e76410532bc85a2f6c5781eb0cc69e` (branch durability verified at Phase 2) |
| Worktree before certification commit | 0 dirty / 0 staged / 0 untracked |

### R0C-11 commit chain (dabf4867… → fdfced73…, oldest first)

| Commit | Subject |
|---|---|
| `4ac8e97d` | docs(scp): freeze R0C-11 product catalog runtime design |
| `0b40e703` | feat(scp): product catalog domain runtime |
| `22fc1e3e` | feat(scp): product catalog API with granular RBAC, audit, and item integrity |
| `58ac8a2e` | test(scp): R0C-11 product catalog unit and PostgreSQL Direct acceptance suites |
| `fdfced73` | test(platform): adapt platform API inventory ledger to R0C-11 product endpoints |

---

## 2. Canonical Full Maven Execution (single complete run)

| Field | Value |
|---|---|
| FULL_MAVEN_EXECUTION_MODE | SINGLE_COMPLETE_CANONICAL_RUN |
| CANONICAL_RUN_CODE_HEAD | `fdfced739c3d025960173b71b02df9179d01327a` |
| CANONICAL_RUN_START | 2026-09-06T18:33:33Z (epoch 1788719613) |
| mvn invocation | `mvn clean -B -ntp` (finished before run) followed by EXACTLY ONE `mvn test -B -ntp -Dsurefire.useFile=false` — no package scopes, no shards, no exclusions, no parallel invocations, no class batching |
| FULL_MAVEN_TESTS | 2685 |
| FULL_MAVEN_FAILURES | 0 |
| FULL_MAVEN_ERRORS | 0 |
| FULL_MAVEN_SKIPPED | 6 (pre-existing env-guarded `CommerceOrderPostgresConcurrencyTest`; identical to the R0C-10 canonical baseline) |
| FULL_MAVEN_BUILD | SUCCESS |
| FULL_MAVEN_DURATION | 14:00 (Finished at 2026-09-06T18:47:37Z) |
| FULL_MAVEN_COUNT_RECONCILIATION | PASS |
| Reconciliation proof | Maven final aggregate 2685 == ElementTree `<testcase>` element sum 2685; 346 fresh XML files; STALE_XML_FILES=0; distinct (classname, name) identities = 2685 (no overwrites); testsuite-attr sum 2443 + 21 nested-class zero-attr suites (known surefire attr behavior) |
| Log structural markers | exactly 1 × "Scanning for projects"; 1 × surefire `(default-test)`; 1 × final `Results:` block; 1 × `BUILD SUCCESS`; 0 × `BUILD FAILURE` |
| Log integrity | SHA-256 `2d1b57540b41ba4b8b21aaa1f94db1f79935032a810309ec044c768994e94a39` (674,191,096 bytes) |
| Environment | JDK 21.0.12.1, Maven 3.9.16, host-native PostgreSQL 16.2 @ 127.0.0.1:5433, least-privilege role `sanad` (rolsuper=f, rolcreatedb=f, rolcreaterole=f, rolcanlogin=t, rolbypassrls=f); NO Docker, NO Testcontainers, NO H2 |

### Pre-canonical attempt (disclosed, not the certification run)

One earlier full run (18:03–18:16Z, HEAD `58ac8a2e`) recorded 2685 tests / 1F / 11E and is
preserved in the mission evidence. Root causes and remediation:

| Failure | Root cause | Classification | Remediation |
|---|---|---|---|
| `PlatformApiCountTest` expected 75 executive ops, found 79 | the 4 additive R0C-11 product endpoints changed the API inventory the ledger pins | legitimate ledger adaptation (same class as R0C-10's `f92dbf73`) | commit `fdfced73` — executive 75→79, total 746→750, comment updated |
| `ProductionProfileTest` 10E + `IntegratedBusinessProcessesPostgresE2ETest` 1E | prod-profile Spring contexts require `CRM_CUSTOM_FIELD_ENCRYPTION_KEY`/`JWT_SECRET`; the R0C-11 launcher omitted them (R0C-10 launcher sourced them) | ENVIRONMENT_DEFECT (launcher), SOURCE_DEFECT=NO | launcher env corrected; all 15 affected tests re-proven 0F/0E before relaunch |

The certification run above was then launched fresh (clean + single run) and passed with
the totals recorded in §2.

---

## 3. R0C-11 Gate Matrix (actual values)

| Gate | Value | Evidence |
|---|---|---|
| R0C11_BASE_SHA | `dabf4867…` | Phase 1 `git ls-remote` + commit-message verification |
| R0C11_BRANCH_DURABILITY | PASS | pushed at Phase 2; `REMOTE == LOCAL` verified |
| PRODUCT_CATALOG_STORAGE | PASS | `products` table + CHECK + unique code index proven (PG-01) |
| PRODUCT_CATALOG_RUNTIME | PASS | `ProductEntity`/`ProductRepository`/`ProductCatalogService` (create/update/findById/findByCode/findAll/findAvailable) |
| PRODUCT_API | PASS | GET/POST/PUT `/api/v1/executive/products[/{id}]`; NO DELETE (structural) |
| PRODUCT_CODE_STABILITY | PASS | update never renames (unit + PG-14) |
| PRODUCT_CODE_UNIQUENESS | PASS | fail-closed deterministic error + DB backstop; concurrent race exactly one winner (PG-06/07/08) |
| PRODUCT_ARCHIVAL_NO_DELETE | PASS | INACTIVE/ARCHIVED transitions, zero physical deletes (PG-15, PG-28, sweep) |
| PRODUCT_APPLICATION_RELATION | PASS | nullable/valid/invalid application FK (PG-11/12/13) |
| PRODUCT_PRICE_INTEGRATION | PASS | through existing `prices.product_id` via unchanged `PriceService` (PG-16) |
| PRODUCT_ENTITLEMENT_INTEGRATION | PASS | `subscription_items → product_entitlements → ItemAwareEntitlementResolver` unchanged and proven (PG-17/PG-22); plan-only behavior untouched (`ItemAwareEntitlementResolverTest` 5/5) |
| PRODUCT_SUBSCRIPTION_ITEM_INTEGRITY | PASS | unknown-product rejection, ADD_ON/METERED type compatibility + ACTIVE contract, historical preservation (PG-18/19/20/21/22) |
| CATALOG_READ_RBAC | PASS | `@RequireCapability("catalog.read")` + guard; `CATALOG.READ` ACTIVE + backward-compat grants (PG-24) |
| CATALOG_MANAGE_RBAC | PASS | `@RequireCapability("catalog.manage")` + guard; `CATALOG.MANAGE` ACTIVE + grants (PG-25) |
| CONTROL_PLANE_GUARD | PASS | real guard behavior: control tenant allowed, others denied, unconfigured denied (PG-27) |
| AUDIT | PASS | `PRODUCT_CREATE`/`PRODUCT_UPDATE`, resourceType=product, resourceId=UUID; no false success audit on rejection (controller tests + PG-23) |
| R0C11_SECURITY_AUDIT | PASS | canonical `scan_secrets.py` PASS (4792 files, 0 findings, 0 errors); 0 SQL-injection surface (all parameterized); 0 unguarded writes; 0 privilege/RLS statements in delta |
| R0C11_DATA_INTEGRITY_AUDIT | PASS | orphan FK sweep: prices=0, product_entitlements=0, subscription_items=0, products→applications=0 |
| R0C11_FORWARD_ONLY_CHAIN | PASS | `R0C11_NEW_MIGRATIONS=0`; `FLYWAY_DUPLICATE_VERSION_COUNT=0` (143 files: 116 migration + 27 vendor); Flyway validate proven on every isolated acceptance run |
| R0C11_REMOTE_DURABILITY | PASS | final push verified `FINAL_HEAD == REMOTE_FINAL_HEAD` (§6) |
| MERGE / DEPLOY | NO / NO | directive honored |

## 4. Focused Acceptance Batteries (pre-canonical, all re-included in the canonical run)

| Battery | Tests | Result |
|---|---|---|
| R0C-11 PostgreSQL Direct acceptance (`ProductCatalogRuntimePostgresTest`, 29 scenarios PG-01…PG-29) | 29 | 0F / 0E / 0S |
| R0C-10 canonical regression (8 `*PostgresTest` classes) | 48 | 0F / 0E / 0S → R0C10_REGRESSION=PASS |
| Predecessor recertification (22 classes: ApplicationCatalogServiceTest, SubscriptionItemServiceTest, SubscriptionItemProductIntegrityTest, PlanVersionServiceTest, PriceResolverTest, PriceCalculatorTest, ItemAwareEntitlementResolverTest, UsageMeteringServiceTest, SubscriptionChangeServiceTest, AnchoredPlanSeatQuantitySyncTest, SubscriptionCommandServiceTest, SubscriptionLifecycleTest, ProvisioningJobRunnerTest, ExecutiveReadModelsTest, ScpFrontendContractTest, ExecutiveAuditRouteCompatibilityTest, ExecutiveTenantsV2RoutePrecedenceTest, SubscriptionChangeServicePostgresTest, SubscriptionAnchorPostgresTest, MultiPlanAnchorAuthorityPostgresTest, AnchoredPlanSeatQuantityPostgresTest, SaasAdministrationLegacyConvergencePostgresTest) | 223 | 0F / 0E / 0S → PREDECESSOR_RECERT=PASS |
| R0C-11 new unit suites (ProductCatalogServiceTest 22, CatalogControllerProductEndpointsTest 14, SubscriptionItemProductIntegrityTest 10) | 46 | 0F / 0E / 0S |

TDD discipline: RED proven first (three suites failed to compile on the absent production
types — `cannot find symbol ProductEntity/ProductRepository/ProductCatalogService/
ProductRequest/ProductResponse`), then GREEN, then REFACTOR (shared validation helpers;
no behavior change; suites re-run).

## 5. Frozen Design Semantics (implemented exactly)

- Product types: `APPLICATION | ADD_ON | METERED | OTHER` (unchanged DB CHECK; domain whitelist mirrors it).
- Statuses: `ACTIVE | INACTIVE | ARCHIVED` (unchanged DB CHECK).
- No physical delete anywhere (no method, no endpoint, no status `DELETED`); retirement = INACTIVE/ARCHIVED.
- Product code = stable identity: trim+uppercase normalization, `^[A-Za-z0-9_-]{1,50}$`, never renamed on update, uniqueness fail-closed with concurrent-race safety.
- Historical references remain valid after INACTIVE/ARCHIVED (proven end-to-end incl. entitlement resolution, PG-22).
- No pricing engine, entitlement engine, multiplicity (R0C-10), or Executive UI change.
- `R0C10_NEW_MIGRATIONS=0` equivalent: `R0C11_NEW_MIGRATIONS=0` — the storage contract was already complete.

## 6. Final Push

| Field | Value |
|---|---|
| FINAL_HEAD | recorded after the certification commit (§7 below) |
| REMOTE_FINAL_HEAD | identical to FINAL_HEAD (verified via `git ls-remote` after push) |
| Ancestry | `dabf4867b7e76410532bc85a2f6c5781eb0cc69e` is an ancestor of FINAL_HEAD (merge-base exit 0) |
| POST_CANONICAL_CODE_DELTA | 0 (diff `fdfced73...FINAL_HEAD` touches docs only) |

## 7. Certification Checklist (all gates, actual values)

```
R0C11_PG_ACCEPTANCE_TESTS=29               R0C11_PG_ACCEPTANCE_FAILURES=0
R0C11_PG_ACCEPTANCE_ERRORS=0               R0C11_PG_ACCEPTANCE_SKIPPED=0
R0C10_REGRESSION=PASS (48/0F/0E/0S)        PREDECESSOR_RECERT=PASS (223/0F/0E/0S)
FULL_MAVEN_EXECUTION_MODE=SINGLE_COMPLETE_CANONICAL_RUN
FULL_MAVEN_TESTS=2685                      FULL_MAVEN_FAILURES=0
FULL_MAVEN_ERRORS=0                        FULL_MAVEN_SKIPPED=6
FULL_MAVEN_BUILD=SUCCESS                   FULL_MAVEN_DURATION=14:00
FULL_MAVEN_COUNT_RECONCILIATION=PASS       STALE_XML_FILES=0
CANONICAL_RUN_CODE_HEAD=fdfced739c3d025960173b71b02df9179d01327a
POST_CANONICAL_CODE_DELTA=0
PRODUCT_CATALOG_STORAGE=PASS               PRODUCT_CATALOG_RUNTIME=PASS
PRODUCT_API=PASS                           PRODUCT_CODE_STABILITY=PASS
PRODUCT_CODE_UNIQUENESS=PASS               PRODUCT_ARCHIVAL_NO_DELETE=PASS
PRODUCT_APPLICATION_RELATION=PASS          PRODUCT_PRICE_INTEGRATION=PASS
PRODUCT_ENTITLEMENT_INTEGRATION=PASS       PRODUCT_SUBSCRIPTION_ITEM_INTEGRITY=PASS
CATALOG_READ_RBAC=PASS                     CATALOG_MANAGE_RBAC=PASS
CONTROL_PLANE_GUARD=PASS
R0C11_SECURITY_AUDIT=PASS                  R0C11_DATA_INTEGRITY_AUDIT=PASS
R0C11_NEW_MIGRATIONS=0                     FLYWAY_DUPLICATE_VERSION_COUNT=0
FLYWAY_VALIDATE=PASS                       R0C11_FORWARD_ONLY_CHAIN=PASS
R0C11_REMOTE_DURABILITY=PASS
```

## 8. Explicitly Deferred / Out of Scope

- Executive UI product surfaces (`apps/web` untouched, per directive "Do NOT rebuild Executive UI").
- R0C-12 (`R0C12=FORBIDDEN`) — not started.
- Merge to main and deploy (`MERGE=NO`, `DEPLOY=NO`).
- Production enablement of `catalog.read`/`catalog.manage` grants per role remains an
  operator decision (capabilities and backward-compat seeds already exist since V20260830_2).
