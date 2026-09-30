# HRM G3 RED Evidence

**Status:** RED EXECUTION PENDING  
**Branch:** `feat/hrm-g3-performance-reviews-goals`  
**G2 baseline:** `d2b93450b6774f14814a825e83b7e2dc76a6cc81`  
**RED test commit:** `af42051fa58adf2d1bf6fa50f78d458984df7d2a`  
**Execution model:** PostgreSQL Direct / host-native only  

## Contract under test

1. `foreignTenantCannotReadPerformanceGoal()` — a goal belonging to tenant A must not be visible under tenant B context.
2. `goalRequiresCanonicalEmployment()` — G3 goal persistence must be constrained to canonical HR employment identity.

## Expected RED reason

Before any G3 implementation or migration exists, `hr_performance_goals` is absent. The contract must therefore fail at the explicit table-existence assertion. An infrastructure failure, authentication failure, Docker/Testcontainers path, compilation error, or skipped test does **not** qualify as RED evidence.

## Observed RED evidence

Pending GitHub Actions execution on the exact RED SHA. This section must be replaced with the exact workflow run ID, job ID, test failure signature, and timestamp after the run completes.

## Governance

No G3 migration or product implementation may be added until the expected test failure is observed on PostgreSQL Direct and recorded here.
