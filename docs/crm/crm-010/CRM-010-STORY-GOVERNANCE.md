# CRM-010 Story Governance

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** APPROVED

---

## 1. Definition of Ready (DoR)

A story is ready for implementation when ALL of the following are met:

| # | Criterion | Verified By |
|---|-----------|-------------|
| 1 | Story has unique ID (E#-###) | Backlog |
| 2 | Story has clear acceptance criteria | Product Owner |
| 3 | Story has estimated effort | Agent Lead |
| 4 | Dependencies are identified and resolved | Coordinator |
| 5 | Database migrations (if needed) are designed | Agent 1 |
| 6 | Domain model (if needed) is defined | Agent 2 |
| 7 | Port/adapter contract (if needed) is defined | Architecture |
| 8 | No blocking architectural questions remain | Architecture |

---

## 2. Definition of Done (DoD)

A story is done when ALL of the following are met:

| # | Criterion | Verified By |
|---|-----------|-------------|
| 1 | Code compiles without errors | CI |
| 2 | All tests pass (unit + integration) | CI |
| 3 | Code follows DDD Hexagonal pattern | Reviewer |
| 4 | AuditPort integration complete (if mutation) | Agent 5 |
| 5 | TimelineEventPort integration complete (if event) | Agent 5 |
| 6 | RBAC capability enforced (if endpoint) | Agent 4 |
| 7 | Tenant isolation verified | Agent 6 |
| 8 | No SQL injection vectors | Security |
| 9 | No hardcoded secrets | Security |
| 10 | Documentation updated | Agent 8 |

---

## 3. Acceptance Criteria Template

```markdown
### Acceptance Criteria

**Given** [precondition]
**When** [action]
**Then** [expected result]

#### Business Rules
- [ ] Rule 1: [description]
- [ ] Rule 2: [description]

#### Non-Functional
- [ ] Performance: [target]
- [ ] Security: [requirement]
- [ ] Tenant isolation: [verified]
```

---

## 4. Testing Checklist

### 4.1 Unit Tests

| # | Check | Required |
|---|-------|----------|
| 1 | Domain model invariants tested | ✅ |
| 2 | Value object equality tested | ✅ |
| 3 | Use case business logic tested | ✅ |
| 4 | Error paths tested | ✅ |
| 5 | Edge cases (null, empty, boundary) tested | ✅ |

### 4.2 Integration Tests

| # | Check | Required |
|---|-------|----------|
| 1 | Repository CRUD tested (H2) | ✅ |
| 2 | PostgreSQL-specific features tested (Testcontainers) | ✅ |
| 3 | Transaction rollback tested | ✅ |
| 4 | Concurrent access tested (if applicable) | ✅ |
| 5 | Outbox pattern tested (if applicable) | ✅ |

### 4.3 API Tests

| # | Check | Required |
|---|-------|----------|
| 1 | Happy path returns 2xx | ✅ |
| 2 | Unauthorized returns 401 | ✅ |
| 3 | Forbidden (missing capability) returns 403 | ✅ |
| 4 | Not found returns 404 | ✅ |
| 5 | Validation error returns 400 | ✅ |
| 6 | ETag/If-Match concurrency tested | ✅ |

---

## 5. Documentation Checklist

| # | Document | Required For |
|---|----------|-------------|
| 1 | API endpoint documented | All endpoints |
| 2 | Migration documented | All migrations |
| 3 | Architecture decision recorded | Significant decisions |
| 4 | Operational runbook | Production features |
| 5 | Test coverage report | All stories |

---

## 6. Security Checklist

| # | Check | Status |
|---|-------|--------|
| 1 | No hardcoded secrets | ☐ |
| 2 | Secrets via environment variables | ☐ |
| 3 | @RequireCapability on all endpoints | ☐ |
| 4 | Tenant isolation on all queries | ☐ |
| 5 | Input validation on all endpoints | ☐ |
| 6 | SQL parameterized (no string concat) | ☐ |
| 7 | AI outputs require human confirmation | ☐ |
| 8 | Fail-closed on all external calls | ☐ |
| 9 | No sensitive data in logs | ☐ |
| 10 | HTTPS only (production guard) | ☐ |

---

## 7. Performance Checklist

| # | Check | Target |
|---|-------|--------|
| 1 | Query latency | <500ms |
| 2 | AI request timeout | ≤8000ms |
| 3 | Batch size (scoring) | ≤100/batch |
| 4 | Pagination on all list endpoints | ✅ |
| 5 | Indexes on all WHERE clauses | ✅ |
| 6 | No N+1 queries | ✅ |
| 7 | Cache TTL configured | 5 min |

---

## 8. Review Workflow

```
Story Implemented
       │
       ▼
   Self-Review (DoD checklist)
       │
       ▼
   Peer Review (code quality, patterns)
       │
       ▼
   Security Review (security checklist)
       │
       ▼
   QA Review (test coverage)
       │
       ▼
   Architecture Review (compliance)
       │
       ▼
   ✅ APPROVED → Merge
```

---

## 9. Quality Gates

| Gate | Threshold | Blocking |
|------|-----------|----------|
| Compilation | 0 errors | ✅ Yes |
| Unit Tests | 100% pass | ✅ Yes |
| Integration Tests | 100% pass | ✅ Yes |
| Code Coverage | ≥80% | ✅ Yes |
| Security Review | 0 critical | ✅ Yes |
| Performance | <500ms p99 | ⚠️ Advisory |
| Documentation | Complete | ✅ Yes |

---

**Story Governance Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ APPROVED
