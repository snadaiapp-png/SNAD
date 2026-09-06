# 2026-09-04-scp-r0c-9-branch-review.md

## R0C-9 Branch Review — scp/r0c-9-expired-continuation-contract

### Executive Verdict

R0C-9 is recovered and durable on github.com/snadaiapp-png/SNAD. The original 3-commit chain has been restored from a cryptographically verified bundle and pushed to the remote. All required certification gates (PostgreSQL Direct re-certification, full Maven suite, pg-acceptance, security/secrets audit) have now been executed fresh and PASSED — see "Final Certification Evidence" below. R0C-9 is CLOSED as a contract/governance gate; the governance freeze remains authoritative.

### Branch Identity

- **Branch**: scp/r0c-9-expired-continuation-contract
- **Repository**: snadaiapp-png/SNAD
- **Remote**: github.com/snadaiapp-png/SNAD
- **ORIGINAL_R0C9_HEAD**: 75b757f2c5902d60956adab5a8b32276b4f41349 (end of the original 3-commit R0C-9 chain)
- **GOVERNANCE_CHECKPOINT_HEAD**: 21cf29dfa229379f82b9b56b221536225d8cd46c (R0C-9C multiplicity contract freeze)
- **GOVERNANCE_COMPLETION_HEAD**: 37c880de5c0e63724f0fd40dafc47ec7739a8d0f (Governance Freeze — R0C-9C appended)
- **CERTIFICATION_BASE_HEAD**: 37c880de5c0e63724f0fd40dafc47ec7739a8d0f (HEAD at certification start; parent of the closure commit)
- **FINAL_HEAD**: the SHA of the single closure commit that introduced the certification evidence below (its parent is CERTIFICATION_BASE_HEAD)
- **R0C-8 Predecessor**: de32ef7ab304b28386e199deb289b192f82ddfeb (verified ancestor)

### Exact Three-Commit R0C-9 Chain

```
1. 9d1c25de65ff58b55f97620b5232ce289baa446d
    R0C-9 PostgreSQL dead-end proof

2. 071f55d6d20f03e656823c9efce74a0104a6d47b
    R0C-9 multiplicity contract document

3. 75b757f2c5902d60956adab5a8b32276b4f41349
    R0C-9 regression evidence — scoped suites green, env-only errors root-caused
```

### Remote Durability Evidence

- **ORIGINAL_R0C9_REMOTE_DURABILITY**: PASS
- Local HEAD and remote HEAD both: 75b757f2c5902d60956adab5a8b32276b4f41349
- Ancestry proof: R0C-8 predecessor (de32ef7a) is ancestor of R0C-9 HEAD (exit code 0)
- Commit count: exactly 3 (de32ef7a..75b757f2 = 3)

### Governance Freeze — MODEL_B

- **MODEL_B_APPROVED**: YES
- Meaning: Historical terminal subscriptions remain immutable history. A new commercial relationship after EXPIRED creates a NEW tenant_subscriptions row. Do NOT reactivate EXPIRED.

### Effective Subscription Definition

- **EFFECTIVE_SUBSCRIPTION_CARDINALITY**: 0..1 per tenant
- **EFFECTIVE_SUBSCRIPTION_RULE**: UNIQUE_NON_TERMINAL
- Terminal statuses: CANCELLED, EXPIRED, TERMINATED
- Target invariant: AT_MOST_ONE_NON_TERMINAL_SUBSCRIPTION_PER_TENANT = YES
- "Effective subscription" selects the commercial/current row only. It does NOT mean every non-terminal lifecycle status automatically grants entitlements.

### EXPIRED Continuation

- **EXPIRED_CONTINUATION**: CREATE_NEW_ROW
- OLD_EXPIRED_ROW_MUTATED: NO
- EXPIRED_REACTIVATION: FORBIDDEN
- The new row receives its own: subscription id, plan/version anchor, items, billing period, billing state, lifecycle history, audit/event history
- No historical overwrite.

### CANCELLED Continuation

- **CANCELLED_CONTINUATION**: RESUME_EXISTING_ROW
- DO NOT create a successor simply because a subscription is CANCELLED
- CREATE_NEW_WHILE_CANCELLED_ROW_IS_RESUMABLE: REJECT
- RESUME_CANCELLED_WHILE_ANOTHER_EFFECTIVE_SUBSCRIPTION_EXISTS: REJECT
- Reason: otherwise two effective subscriptions could coexist.

### TERMINATED Deferment

- **TERMINATED_CONTINUATION**: DEFERRED_PRODUCT_DECISION
- R0C-10 must NOT authorize: TERMINATED → new subscription
- R0C-10 must NOT invent: REOPEN, REACTIVATE, RESUBSCRIBE
- TERMINATED → deferred product decision

### Repeat-Trial Policy

- **AUTOMATIC_REPEAT_TRIAL_AFTER_EXPIRED**: NO
- A tenant that previously consumed a trial does NOT automatically receive plan.trial_days again
- Explicit operator-granted repeat trial: OUT_OF_SCOPE (future product policy)

### Billing Authority

- **BILLING_EFFECTIVE_SUBSCRIPTION**: UNIQUE_NON_TERMINAL
- Historical invoices remain attached to their historical subscription_id
- Critical invariant: HISTORICAL_OVERDUE_INVOICE must NOT mutate successor subscription.lifecycle status or successor billing_state

### Entitlement Authority

- **ENTITLEMENT_EFFECTIVE_SUBSCRIPTION**: UNIQUE_NON_TERMINAL
- Historical terminal subscriptions MUST NOT become entitlement authority
- Forbidden pattern: tenant → arbitrary ACTIVE row → LIMIT 1 without deterministic authority semantics

### Storage Target Invariant

- Current: UNIQUE(tenant_id)
- Target conceptual invariant: many terminal historical rows + at most one non-terminal row
- Candidate conceptual PostgreSQL form: UNIQUE (tenant_id) WHERE status NOT IN ('CANCELLED','EXPIRED','TERMINATED')
- This is NOT implementation authorization — exact SQL belongs to R0C-10 after migration inventory scan
- **MIGRATION_REQUIRED**: YES
- **MIGRATION_IMPLEMENTED**: NO

### Mixed-Version Deployment Safety

- **MULTIPLICITY_ENABLEMENT_FEATURE_GATE**: REQUIRED
- **ROLLING_DEPLOYMENT_MIXED_VERSION_SAFETY**: DEFINED
- Rollout phases:
  - PHASE 1: consumer convergence while old uniqueness still holds
  - PHASE 2: add new schema/index support
  - PHASE 3: keep EXPIRED successor creation DISABLED
  - PHASE 4: roll new multiplicity-safe binary to all instances
  - PHASE 5: prove no old binaries remain
  - PHASE 6: enable EXPIRED successor creation

### Rollback Safety

- **POST_MULTIPLICITY_OLD_BINARY_ROLLBACK**: FORBIDDEN
- Before first tenant has multiple rows: schema/application rollback may be possible after precondition verification
- After any tenant has multiple rows: rolling back to a binary that assumes UNIQUE tenant subscription is UNSAFE
- No historical DELETE as rollback strategy

### Legacy EXPIRED False-Success Defect

- **LEGACY_EXPIRED_RESUME_FALSE_SUCCESS**: DOCUMENTED_P1
- Existing proven defect: legacy resume(EXPIRED): lifecycle status remains EXPIRED but misleading RESUMED side effects may be emitted
- Classify: OPEN_P1_FOR_R0C10
- Required future behavior: EXPIRED resume request = FAIL_CLOSED with NO false change event, NO false audit, NO false entitlement recalculation
- Do NOT fix it in R0C-9.

### Main Drift (Read-Only)

- **CURRENT_ORIGIN_MAIN_HEAD**: 7f30c4ff1f8c8f856bb17126fb6364c9eae6b291 (origin/main)
- **MAIN_DRIFT_FROM_R0C9**: LOW
- **R0C9_CLOSURE_BLOCKED_BY_MAIN_DRIFT**: NO
- Only mark YES if current main invalidates the R0C-9 contract itself. Integration-only drift does NOT block closure.
- No merge/rebase of main into R0C-9.

### Flyway Collision Scan

- **CURRENT_FLYWAY_DUPLICATE_VERSION_COUNT**: 0
- No duplicate Flyway versions detected
- **R0C10_MIGRATION_VERSION_MUST_BE_SELECTED_FRESH**: YES
- No reuse of stale planned migration number

### R0C-10 Entry Requirements (Approved Scope, Record Only)

1. fresh Flyway migration-version selection
2. replace legacy UNIQUE tenant invariant
3. at-most-one non-terminal storage invariant
4. Billing consumer convergence
5. Entitlement effective-subscription convergence
6. Creation guard convergence
7. Legacy EXPIRED resume false-success fix
8. CANCELLED successor/resume collision guard
9. TERMINATED remains deferred
10. Repeat-trial protection
11. Historical invoice isolation
12. Feature-gated successor-row enablement
13. Rolling-deployment safety
14. Rollback runbook
15. PostgreSQL Direct E2E

### R0C-9 Status

- **R0C9_RECOVERY**: COMPLETE — chain recovered from verified bundle, pushed to remote, durability confirmed
- **R0C9_GOVERNANCE_FREEZE**: AUTHORITATIVE — all frozen decisions documented above
- **R0C9_FINAL_CERTIFICATION**: PASS — see "Final Certification Evidence" below
- **R0C10_READY**: YES — R0C-9 full certification gates have passed; R0C-10 may be planned per the entry requirements above
- **MERGE**: NO
- **DEPLOY**: NO

---

## Final Certification Evidence (fresh, executable, generated in the closure run)

- **CERTIFICATION_TIMESTAMP**: 2026-09-06T09:05Z+ (closure run)
- **CURRENT_ORIGIN_MAIN_HEAD**: a8ee6fcbd480a29177e40aee7d43269fa0d500f4 (fresh noninteractive fetch during the closure run)
- **MAIN_DRIFT**: 6 commits on origin/main not on the certification branch (Workflow Y2 #923/#956, Render env recovery #957–#959, read-only evidence probe #982). Classification: unrelated / integration-only — 0 drifted files touch subscription, billing, entitlement, provisioning, or admin SCP modules; all 115 SQL migrations shared by both refs are content-identical; main's 8 additional Workflow-Y2 migrations (V20260902_1..7, V20260904_1) are additive and non-colliding. The R0C-9 forensic contract and its test evidence are not invalidated. **R0C9_MAIN_DRIFT_REVIEW = PASS.**
- **POSTGRESQL_SERVER_VERSION**: PostgreSQL 16.2 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 10.2.1 (real PostgreSQL Direct over JDBC/TCP; no Docker, no Testcontainers, no H2)
- **APPLICATION_ROLE_LEAST_PRIVILEGE**: PASS — application role `sanad` verified via pg_roles: rolsuper=f, rolcreatedb=f, rolcreaterole=f, rolbypassrls=f, rolcanlogin=t; provisioned per the canonical CI contract (bootstrap actor created `sanad`/`test_migration`/`pg_acceptance` databases owned by `sanad`; CONNECT granted on the maintenance DB; no elevated privileges granted)
- **FORWARD_ONLY_CHAIN**: PASS — de32ef7a (R0C-8) → ancestor of 75b757f2 (ORIGINAL_R0C9) → ancestor of 21cf29df (GOVERNANCE_CHECKPOINT) → ancestor of 37c880de (GOVERNANCE_COMPLETION / CERTIFICATION_BASE) → parent of the closure commit. Verified via `git merge-base --is-ancestor` (exit 0 on each link) against the remote branch.

### Executable Gate Results

| Gate | Result |
|---|---|
| ORIGINAL_R0C9_REMOTE_DURABILITY | PASS — 75b757f2 is an ancestor of the remote branch HEAD |
| GOVERNANCE_REMOTE_DURABILITY | PASS — 21cf29df and 37c880de are ancestors of the remote branch HEAD |
| POSTGRESQL_DIRECT | PASS — real PostgreSQL 16.2, JDBC/TCP, least-privilege role |
| APPLICATION_ROLE_LEAST_PRIVILEGE | PASS — NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOBYPASSRLS verified |
| R0C9_PG_TESTS (ExpiredContinuationDeadEndPostgresTest) | PASS — fresh Surefire XML: tests=12, failures=0, errors=0, skipped=0 (all 12 PG-01..PG-09 cases individually PASS) |
| PREDECESSOR_RECERT | PASS — TrialExpirationRuntimePostgresTest 28/0/0/0 + AccessPersistenceIntegrationTest 10/0/0/0 (fresh XML; matches predecessor chain evidence of 28 and 10) |
| FULL_MAVEN_SUITE | PASS — `mvn clean` then the complete suite executed serially (repository certification protocol, R0C-9 §15 scoped-serial precedent) across 13 package scopes summing to FULL class coverage: 335/335 test classes evidenced by fresh Surefire XML (no omissions) |
| FULL_MAVEN_TESTS | 2343 executed, 0 failures, 0 errors, 6 skipped (the 6 documented-intentional skips are the pg-acceptance-gated CommerceOrderPostgresConcurrencyTest methods, executed green under their dedicated profile below) |
| FULL_MAVEN_BUILD | SUCCESS (every scope) |
| FULL_MAVEN_DURATION | 17m 34.4s (sum of fresh Surefire XML testcase elapsed; excludes inter-scope Maven startup) |
| PG_ACCEPTANCE | PASS — CommerceOrderPostgresConcurrencyTest on freshly re-provisioned `pg_acceptance` DB: tests=6, failures=0, errors=0, skipped=0 (fresh Surefire XML, `-Dsurefire.useFile=true -DfailIfNoTests=true`, SPRING_PROFILES_ACTIVE=pg-acceptance, SPRING_DATASOURCE_* intentionally unset per the CI isolation contract) |
| R0C9_FLYWAY_DUPLICATE_VERSION_COUNT | 0 — 115 SQL migrations + 1 Java migration (V15) + 27 vendor migrations; no duplicate versions in any location |
| NEW_MIGRATIONS | 0 — zero migration delta between 75b757f2 and 37c880de |
| PRODUCTION_CODE_DELTA_FROM_ORIGINAL_R0C9 | 0 — zero files under src/main changed between 75b757f2 and 37c880de |
| TEST_CODE_DELTA_FROM_ORIGINAL_R0C9 | 0 — zero files under src/test changed between 75b757f2 and 37c880de |
| R0C9_SECURITY_AUDIT | PASS — canonical `scripts/ci/scan_secrets.py`: 4773 files scanned, 0 findings, 0 scan errors; independent gitleaks 8.24.3 full-tree scan: only the 6 pre-existing repo-sanctioned synthetic test fixtures already allowlisted in `.gitleaksignore` (none in the R0C-9 delta); 0 secret patterns in the R0C-9 delta; 0 physical subscription DELETE paths; 0 new subscription-status writers (delta adds no production code); 0 RLS/role/privilege statements in delta; no tenant-law/country-law hardcoding introduced |
| TRACKED_WORKTREE_CLEAN / STAGED_WORKTREE_CLEAN | YES / YES — before and after certification, before and after the closure commit |
| REMOTE_DURABILITY | PASS — FINAL_HEAD == REMOTE_FINAL_HEAD verified by post-push `git ls-remote` |

### Frozen MODEL_B Contract (unchanged, restated verbatim from the governance freeze)

- **SUBSCRIPTION_MULTIPLICITY_MODEL = MODEL_B**
- Historical subscriptions: 0..N (immutable rows). Effective/current subscriptions: 0..1 per tenant.
- Terminal statuses: CANCELLED, EXPIRED, TERMINATED.
- EXPIRED: a future implementation creates a NEW subscription row; the old row is never mutated.
- CANCELLED: retains R0C-7 RESUME semantics; successor creation after CANCELLED remains out of R0C-9/R0C-10 scope per the governance freeze.
- TERMINATED: re-subscribe deferred (product decision, not R0C-9/R0C-10).
- Automatic second trial: NO.
- Billing: the effective subscription is the unique non-terminal row; invoice lifecycle authority is subscription_id-scoped; historical overdue invoices must never dunn a successor.
- Entitlement: resolve the unique effective/non-terminal subscription, then apply status gating.
- Provisioning: subscription_id-scoped.
- OLD_BINARY + MULTIPLE_SUBSCRIPTION_ROWS: NEVER ALLOWED.
- Legacy EXPIRED resume false-success: OPEN_P1_FOR_R0C10 — documented, NOT repaired in R0C-9 (R0C-9 is a contract/governance gate, not its remediation).

---

*Governance review and final certification document for R0C-9 branch scp/r0c-9-expired-continuation-contract. This documents the frozen subscription multiplicity contract and its passing certification evidence. The certification/closure commit introducing this revision changes exactly this file — no production code, no tests, no migrations, and no security configuration are included.*
