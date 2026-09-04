# 2026-09-04-scp-r0c-9-branch-review.md

## R0C-9 Branch Review — scp/r0c-9-expired-continuation-contract

### Executive Verdict

R0C-9 is recovered and durable on github.com/snadaiapp-png/SNAD. The original 3-commit chain has been restored from a cryptographically verified bundle and pushed to the remote. R0C-9 may not yet be declared CLOSED pending full certification through all acceptance gates (PostgreSQL Direct re-certification, full Maven suite, pg-acceptance, security/secrets audit). However, the recovery is complete and the governance freeze is now authoritative.

### Branch Identity

- **Branch**: scp/r0c-9-expired-continuation-contract
- **Repository**: snadaiapp-png/SNAD
- **Remote**: github.com/snadaiapp-png/SNAD
- **Current HEAD**: 75b757f2c5902d60956adab5a8b32276b4f41349
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
- **R0C9_FINAL_CERTIFICATION**: PENDING — must complete PostgreSQL Direct re-certification, full Maven suite, pg-acceptance, and security audit before CLOSED verdict
- **R0C10_READY**: NO — per R0C-9C §43, DO NOT START R0C-10 until R0C-9 full certification gates pass
- **MERGE**: NO
- **DEPLOY**: NO

---

*Governance review document for R0C-9 branch scp/r0c-9-expired-continuation-contract. This documents the frozen subscription multiplicity contract and is part of the R0C-9C governance checkpoint. No production code, migrations, or security configuration changes are included.*
