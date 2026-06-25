# Sprint 0 — Approved Backlog

## Secure Development Baseline

**Status:** PROPOSED — Awaiting owner approval
**Date:** 2026-06-25
**Execution Order:** EXEC-PROMPT-010R6

---

## Sprint 0 Objective

Establish a secure, stable, and observable development baseline before any commercial feature development begins. Sprint 0 focuses on repository governance, CI stability, authentication contracts, and core infrastructure — not business features.

---

## Sprint 0 Stories

### S0-01: Repository Governance Finalization
- **Description:** Complete branch reconciliation, delete merged/superseded branches, and finalize branch protection rules.
- **Business value:** Reduces repository noise and prevents accidental merges of unreviewed code.
- **Acceptance criteria:**
  - All merged branches deleted
  - All superseded branches deleted
  - Branch protection verified via API
  - Auto-delete enabled
- **Definition of Done:** Branch count reduced to <20 (main + active PR branches + unique-work branches under review)
- **Dependencies:** None
- **Security impact:** Low
- **Tenant impact:** None
- **Estimate:** 2 points
- **Priority:** P0
- **Owner role:** DevOps Engineer

### S0-02: CI Stability Hardening
- **Description:** Ensure all CI workflows pass deterministically on every PR and main push.
- **Business value:** Developers can trust CI results; no false positives or flaky tests.
- **Acceptance criteria:**
  - All 26 workflows pass on main
  - No flaky tests
  - Test count reconciled (427 deterministic)
  - summarize-maven-tests.sh integrated into CI
- **Definition of Done:** 10 consecutive main pushes with 0 CI failures
- **Dependencies:** S0-01
- **Security impact:** Low
- **Tenant impact:** None
- **Estimate:** 3 points
- **Priority:** P0
- **Owner role:** CI/CD Engineer

### S0-03: Backend Authentication Contract Finalization
- **Description:** Finalize the JWT-based authentication contract with tenant binding, refresh rotation, and session revocation.
- **Business value:** Secure foundation for all authenticated API operations.
- **Acceptance criteria:**
  - All auth tests pass (AuthApiIntegrationTest, TokenRevocationIntegrationTest, TenantBindingSecurityIntegrationTest)
  - RefreshTokenConcurrencyPostgresTest executes on CI (not skipped)
  - No tenantId from query parameters in any controller
  - JWT claims documented in OpenAPI
- **Definition of Done:** All auth tests green on main, PostgreSQL acceptance workflow passes
- **Dependencies:** S0-02
- **Security impact:** High
- **Tenant impact:** High
- **Estimate:** 5 points
- **Priority:** P0
- **Owner role:** Backend Security Engineer

### S0-04: Frontend Authentication Integration Boundary
- **Description:** Define and implement the frontend-backend authentication boundary per ADR-039 (once approved).
- **Business value:** Enables secure session management for the frontend.
- **Acceptance criteria:**
  - ADR-039 owner decision recorded (Model A/B/C)
  - Frontend auth implementation matches chosen model
  - Browser-level integration tests pass (Playwright/Cypress)
  - No access token in localStorage/sessionStorage
- **Definition of Done:** ADR-039 approved + frontend auth implemented + browser tests green
- **Dependencies:** ADR-039 owner approval
- **Security impact:** High
- **Tenant impact:** High
- **Estimate:** 8 points
- **Priority:** P1
- **Owner role:** Frontend Engineer + Security Engineer

### S0-05: Tenant Context Propagation
- **Description:** Ensure tenant context is propagated consistently from JWT through all layers (controller → service → repository).
- **Business value:** Prevents cross-tenant data leakage.
- **Acceptance criteria:**
  - All controllers extract tenantId from SecurityContext
  - All repository methods take tenantId as first parameter
  - Cross-tenant access returns 403 (not 404)
  - 10 tenant isolation tests pass
- **Definition of Done:** No tenantId query parameters in any endpoint; all tests green
- **Dependencies:** S0-03
- **Security impact:** High
- **Tenant impact:** High
- **Estimate:** 3 points
- **Priority:** P0
- **Owner role:** Backend Engineer

### S0-06: Core Organization Model
- **Description:** Finalize the Organization bounded context with CRUD operations, membership management, and tenant scoping.
- **Business value:** Foundation for all organization-level operations.
- **Acceptance criteria:**
  - Organization CRUD endpoints pass
  - Organization membership endpoints pass
  - Tenant isolation verified
  - OpenAPI documentation complete
- **Definition of Done:** All organization tests green on main
- **Dependencies:** S0-05
- **Security impact:** Medium
- **Tenant impact:** High
- **Estimate:** 5 points
- **Priority:** P1
- **Owner role:** Backend Engineer

### S0-07: Core User and Membership Model
- **Description:** Finalize the User bounded context with user lifecycle, role assignment, and membership linking.
- **Business value:** Foundation for user management and access control.
- **Acceptance criteria:**
  - User CRUD endpoints pass
  - UserMembershipController uses JWT tenant (no query param)
  - RBAC enforcement verified (44/44 endpoints)
  - V15 ADMIN role seeding verified
- **Definition of Done:** All user tests green on main
- **Dependencies:** S0-05, S0-06
- **Security impact:** Medium
- **Tenant impact:** High
- **Estimate:** 5 points
- **Priority:** P1
- **Owner role:** Backend Engineer

### S0-08: Workflow Engine Foundation
- **Description:** Establish the workflow engine foundation for business process orchestration.
- **Business value:** Enables structured business process automation.
- **Acceptance criteria:**
  - Workflow service interface defined
  - Workflow state machine documented
  - At least one sample workflow implemented
  - Integration tests pass
- **Definition of Done:** Workflow engine foundation merged to main
- **Dependencies:** S0-06, S0-07
- **Security impact:** Low
- **Tenant impact:** Medium
- **Estimate:** 8 points
- **Priority:** P2
- **Owner role:** Backend Engineer

### S0-09: Audit Logging Foundation
- **Description:** Implement structured JSON audit logging with correlation IDs.
- **Business value:** Enables forensic analysis and compliance.
- **Acceptance criteria:**
  - logstash-logback-encoder added to pom.xml
  - CorrelationIdFilter implemented
  - Auth events logged as structured JSON
  - Correlation ID in all log entries
- **Definition of Done:** Structured audit logging merged to main (resolves DEFECT-026)
- **Dependencies:** S0-03
- **Security impact:** Medium
- **Tenant impact:** Low
- **Estimate:** 5 points
- **Priority:** P1
- **Owner role:** Backend Engineer

### S0-10: Observability Foundation
- **Description:** Add OpenTelemetry tracing and metrics infrastructure.
- **Business value:** Enables distributed tracing and performance monitoring.
- **Acceptance criteria:**
  - OpenTelemetry dependency added
  - HTTP tracing enabled
  - Database query tracing enabled
  - Health metrics exported
- **Definition of Done:** Observability foundation merged to main
- **Dependencies:** S0-02
- **Security impact:** Low
- **Tenant impact:** None
- **Estimate:** 5 points
- **Priority:** P2
- **Owner role:** DevOps Engineer

### S0-11: Test Data Strategy
- **Description:** Define and implement a test data strategy for unit, integration, and Testcontainers tests.
- **Business value:** Ensures reproducible test runs and prevents test data pollution.
- **Acceptance criteria:**
  - Test data factories for all aggregates
  - Testcontainers for all PostgreSQL tests
  - No shared test state between test classes
  - Test data documentation
- **Definition of Done:** Test data strategy documented and implemented
- **Dependencies:** S0-03
- **Security impact:** Low
- **Tenant impact:** None
- **Estimate:** 3 points
- **Priority:** P1
- **Owner role:** QA Engineer

### S0-12: Local Developer Environment
- **Description:** Finalize local development environment setup with Docker Compose, hot reload, and debugging.
- **Business value:** Reduces onboarding time and improves developer experience.
- **Acceptance criteria:**
  - docker-compose.yml for local development
  - Hot reload for backend (Spring DevTools) and frontend (Next.js)
  - snad-init.ps1 validated on Windows
  - README updated with setup instructions
- **Definition of Done:** Local environment setup documented and validated
- **Dependencies:** S0-01
- **Security impact:** None
- **Tenant impact:** None
- **Estimate:** 3 points
- **Priority:** P1
- **Owner role:** DevOps Engineer

### S0-13: API Documentation
- **Description:** Complete OpenAPI documentation for all API endpoints.
- **Business value:** Enables frontend integration and third-party API consumption.
- **Acceptance criteria:**
  - All endpoints documented with @Operation annotations
  - Swagger UI enabled in dev profile
  - API examples in documentation
  - Error response schemas documented
- **Definition of Done:** OpenAPI spec complete and validated
- **Dependencies:** S0-06, S0-07
- **Security impact:** Low
- **Tenant impact:** None
- **Estimate:** 3 points
- **Priority:** P2
- **Owner role:** Backend Engineer

### S0-14: Security Regression Suite
- **Description:** Create a security regression test suite that runs on every PR.
- **Business value:** Prevents security regressions from reaching main.
- **Acceptance criteria:**
  - Development Security Acceptance workflow runs 41+ tests
  - All auth/tenant/RBAC tests included
  - Gitleaks enforcing
  - OWASP scan passes (HIGH=0, CRITICAL=0)
- **Definition of Done:** Security regression suite green on main
- **Dependencies:** S0-03, S0-05
- **Security impact:** High
- **Tenant impact:** High
- **Estimate:** 5 points
- **Priority:** P0
- **Owner role:** Security Engineer

---

## Sprint 0 Summary

| Metric | Value |
|--------|-------|
| Total stories | 14 |
| Total estimate | 63 points |
| P0 stories | 5 (S0-01, S0-02, S0-03, S0-05, S0-14) |
| P1 stories | 6 (S0-04, S0-06, S0-07, S0-09, S0-11, S0-12) |
| P2 stories | 3 (S0-08, S0-10, S0-13) |
| Dependencies on ADR-039 | 1 (S0-04) |
| Dependencies on staging | 0 (Sprint 0 is development-only) |

---

## Sprint 0 Entry Criteria

Sprint 0 can begin when:
1. ✅ PR #102 merged (Development Security Acceptance workflow)
2. ✅ Development Security Acceptance green (41 tests, 0 skipped)
3. ⏳ OWASP reaches terminal state (HIGH=0, CRITICAL=0)
4. ✅ Monitoring green on current main SHA
5. ✅ Branch inventory reviewed
6. ✅ No unresolved P0/P1 blocker for development
7. ⏳ This backlog approved by owner
8. ✅ ADR-039 constraints documented (PROPOSED)

---

## Sprint 0 Exit Criteria

Sprint 0 is complete when:
1. All P0 stories are done
2. All P1 stories are done (or explicitly deferred with owner approval)
3. Security regression suite is green
4. CI is stable (10 consecutive green main pushes)
5. Local developer environment is documented and validated
6. API documentation is complete
