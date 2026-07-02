# Branch Readiness Report

## Main Branch (origin/main)
- **SHA**: 95fd84fb12254beeea24db309712edddef94de07
- **Status**: STABLE — all CRM, Flyway V15, SaaS admin, control plane merged
- **Build**: PASS (compile)
- **Tests**: 361 pass, 2 errors (Testcontainers/Docker only)
- **Recommendation**: Ready as integration base

## Active Branch Analysis

### 1. fix/flyway-forward-migrations-20260703
- **Ahead/Behind**: 0/4 (main has 4 commits ahead — branch was reverted)
- **Purpose**: Forward-only V20260703_x audit/idempotency/RLS migrations
- **Last commit**: dc7e4c8 (revert of incomplete transplant)
- **Files changed**: 0 (all reverted)
- **Status**: SUPERSEDED — content was reverted to match main
- **Recommendation**: Obsolete — can be deleted after review

### 2. feature/crm-production-closure-20260702
- **Ahead/Behind**: 2/41 (2 commits ahead of main)
- **Purpose**: CRM production closure with Flyway alignment
- **Files changed**: 27
- **Status**: Needs review — may contain stale Flyway changes
- **Recommendation**: Needs Manual Review

### 3. feat/crm-runtime-environment
- **Ahead/Behind**: 62/231 (62 ahead, 231 behind)
- **Purpose**: CRM runtime environment configuration
- **Files changed**: 133
- **Status**: Significantly diverged from main
- **Recommendation**: Obsolete — main already has CRM core

### 4. crm-runtime-migrations-v3
- **Ahead/Behind**: 54/224
- **Purpose**: CRM runtime dedicated migrations
- **Files changed**: 17
- **Status**: Significantly diverged
- **Recommendation**: Obsolete — main has CRM migrations

### 5. infra/flyway-v15-hotfix
- **Ahead/Behind**: 3/113
- **Purpose**: Original Flyway V15 hotfix (superseded by PR #205)
- **Status**: SUPERSEDED BY MAIN PR #205
- **Recommendation**: Obsolete — do not merge

### 6. infra/05a291-denial-control-integrity-closure
- **Purpose**: Security denial classification (Stage 05A.2.9.1)
- **Status**: Contains JWT/security changes not on main
- **Recommendation**: Needs Manual Review (security changes may be needed)

### 7. infra/05a292-ci-regression-evidence-closure
- **Purpose**: CI regression fixes for Stage 05A.2.9.2
- **Status**: Contains CI fixes not on main
- **Recommendation**: Needs Manual Review

## Summary

| Branch | Recommendation | Action |
|--------|---------------|--------|
| main | Ready | Use as integration base |
| fix/flyway-forward-migrations-20260703 | Obsolete | Delete after review |
| feature/crm-production-closure-20260702 | Needs Review | Evaluate 2 extra commits |
| feat/crm-runtime-environment | Obsolete | Main has CRM |
| crm-runtime-migrations-v3 | Obsolete | Main has CRM |
| infra/flyway-v15-hotfix | Superseded | Delete after review |
| infra/05a291-* | Needs Review | Security changes |
| infra/05a292-* | Needs Review | CI fixes |

