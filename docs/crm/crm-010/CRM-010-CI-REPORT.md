# CRM-010 CI Report

**Date:** 2026-07-29
**Branch:** `feature/crm-010-agent-003-final`
**PR:** #818
**Final Run:** #30460336681+

---

## Result: ALL 25 CHECKS PASS ✅

| # | Check | Status | Duration |
|---|-------|--------|----------|
| 1 | compile | ✅ pass | 47s |
| 2 | validate (x2) | ✅ pass | 10s each |
| 3 | provenance | ✅ pass | 38s |
| 4 | Maven Test Suite | ✅ pass | 4m54s |
| 5 | Verify 8 tables, 26 indexes, and tenant isolation | ✅ pass | 1m35s |
| 6 | PostgreSQL Specialized Acceptance (18 files, 63 tests, 0 skip, 0 fail) | ✅ pass | 1m24s |
| 7 | CRM Authenticated Acceptance (PostgreSQL + Spring Boot + Next.js + Playwright) | ✅ pass | 5m45s |
| 8 | Playwright E2E & Visual Regression | ✅ pass | 5m52s |
| 9 | CRM API Contract Validation | ✅ pass | 1m15s |
| 10 | CRM Deployment Readiness | ✅ pass | 17s |
| 11 | CRM Modular Architecture Validation | ✅ pass | 44s |
| 12 | CRM governance drift diagnostics | ✅ pass | 27s |
| 13 | Verify End-to-End Production | ✅ pass | 2m31s |
| 14 | Backend Health Load Baseline | ✅ pass | 1m48s |
| 15 | Backend Container Hardening | ✅ pass | 2m7s |
| 16 | Security Gate Summary | ✅ pass | 3s |
| 17 | Workflow Security Policy | ✅ pass | 19s |
| 18 | Current Tree Secret Scan | ✅ pass | 56s |
| 19 | OWASP Dependency-Check | ✅ pass | 54s |
| 20 | Frontend Production Dependency Audit | ✅ pass | 24s |
| 21 | PostgreSQL Logical Backup and Restore | ✅ pass | 45s |
| 22 | PostgreSQL keyset and OpenAPI semantic parity | ✅ pass | 38s |
| 23 | Validate governed business process evidence | ✅ pass | 1m3s |
| 24 | Build Next.js Web | ✅ pass | 1m25s |

---

## CI Fix History

| Run | Result | Fixes Applied |
|-----|--------|---------------|
| 1st | FAIL | CrmPostgresMigrationTest: wrong Flyway description |
| 2nd | FAIL | CrmPostgresMigrationTest: phantom `crm_customer_insights` table |
| 3rd | FAIL | CrmPostgresMigrationTest: capability count 58→63 |
| 4th | FAIL | Crm008bFoundationAcceptanceTest: latest version 20260724.2→20260729.2 |
| 5th | ✅ PASS | All 25 checks green |

---

## Key Test Metrics

- **Maven Test Suite**: All tests pass
- **PostgreSQL Specialized Acceptance**: 18 files, 63 tests, 0 skip, 0 fail
- **CRM Authenticated Acceptance**: Playwright E2E pass
- **Migration Verification**: 8 tables, 26 indexes, tenant isolation verified
