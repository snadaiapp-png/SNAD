# CRM Technical Debt Register — v2.0.0 Baseline

| Field | Value |
|-------|-------|
| Register Date | 2026-07-30 |
| Baseline Version | crm-v2.0.0 |
| Authority | Release Baseline Authority |

---

## 1. Security Debt

| # | Item | Category | Priority | Risk | Owner | Suggested Milestone |
|---|------|----------|----------|------|-------|---------------------|
| S-01 | RLS integration tests (9 scenarios) require Docker | Testing | HIGH | MEDIUM — Tests cannot run locally without Docker; only exercised in CI | Backend squad | G5 |
| S-02 | 25 Testcontainers Postgres tests require Docker | Testing | MEDIUM | LOW — Pre-existing, tests skipped gracefully without Docker | Backend squad | G5 |
| S-03 | `CrmContractControllerExceptionHandler` overlaps with `CrmExceptionHandler` | Architecture | MEDIUM | LOW — Duplicate handler registration may cause unexpected error responses | Backend squad | G5 |
| S-04 | No secret scanning in CI (Gitleaks present but not enforced) | CI/CD | MEDIUM | MEDIUM — Secrets could be committed without detection until post-merge | DevOps | G7 |

---

## 2. Performance Debt

| # | Item | Category | Priority | Risk | Owner | Suggested Milestone |
|---|------|----------|----------|------|-------|---------------------|
| P-01 | Customer 360 intelligence queries lack dedicated indexes | Database | MEDIUM | MEDIUM — Scoring model queries may degrade under load | Backend squad | G5 |
| P-02 | No pagination on CRM list endpoints (accounts, contacts, opportunities) | API | HIGH | HIGH — Large datasets cause slow responses and potential timeouts | Backend squad | G6 |
| P-03 | No caching layer for customer intelligence scoring results | Performance | MEDIUM | LOW — Scores recalculated on every request; acceptable at current scale | Backend squad | G6 |

---

## 3. Architecture Debt

| # | Item | Category | Priority | Risk | Owner | Suggested Milestone |
|---|------|----------|----------|------|-------|---------------------|
| A-01 | Missing ADR (Architecture Decision Record) for CRM-010 | Documentation | LOW | LOW — Blueprint exists but no formal ADR | Backend squad | G5 |
| A-02 | `CustomerScores` and `ScoreHistoryEntry` domain records lack explicit validation | Design | MEDIUM | LOW — Application-layer validation exists; records are type-safe | Backend squad | G5 |
| A-03 | No dedicated REST controller for customer intelligence endpoints | API | LOW | LOW — Functionality exists via Customer 360 endpoint | Backend squad | G5 |
| A-04 | `set-state-in-effect` lint pattern (6 errors) in pipeline/customers/contacts/opportunities tabs | Code Quality | LOW | LOW — Established pattern; functionally correct but violates recommended React pattern | Frontend squad | G5 |
| A-05 | No `typecheck` npm script (uses `npx tsc --noEmit` directly) | Build | LOW | LOW — Works but not formalized | Frontend squad | G5 |
| A-06 | `@MockBean` deprecation warnings in 11 test files | Testing | LOW | LOW — Spring Boot 3.5 deprecation; functional but noisy | Backend squad | G6 |

---

## 4. Documentation Debt

| # | Item | Category | Priority | Risk | Owner | Suggested Milestone |
|---|------|----------|----------|------|-------|---------------------|
| D-01 | Committed OpenAPI contract (`crm-openapi.json`) may be stale | Documentation | MEDIUM | MEDIUM — Committed spec may not reflect latest API changes | Backend squad | G5 |
| D-02 | No API changelog visible to consumers | Documentation | LOW | LOW — Changes tracked in CHANGELOG.md | Frontend squad | G6 |
| D-03 | Stage report references pre-release branch state | Documentation | LOW | LOW — Historical, not actionable | Governance | — |

---

## 5. Infrastructure Debt

| # | Item | Category | Priority | Risk | Owner | Suggested Milestone |
|---|------|----------|----------|------|-------|---------------------|
| I-01 | Vercel `rootDirectory` set to `apps/web` — `vercel deploy` fails from subdirectory | DX | MEDIUM | LOW — Git-based deploys work; CLI from `apps/web` is broken | DevOps | G5 |
| I-02 | No automated smoke test pipeline post-deployment | CI/CD | HIGH | MEDIUM — No regression detection for production after deploy | DevOps | G7 |
| I-03 | Backend deployment process not automated for Render | CI/CD | MEDIUM | MEDIUM — Manual deploy steps required | DevOps | G7 |

---

## 6. Developer Experience Debt

| # | Item | Category | Priority | Risk | Owner | Suggested Milestone |
|---|------|----------|----------|------|-------|---------------------|
| DX-01 | `application-local.yml` uses H2 — `SET LOCAL` RLS code must be disabled | Config | MEDIUM | LOW — Fixed in v2.0.0 with `snad.rls.enabled=false` | Backend squad | ✅ RESOLVED |
| DX-02 | Pre-existing Flyway version collision (`V20260722.1` in both `db/migration/` and `db/vendor/h2/`) | Build | MEDIUM | LOW — Affects `@SpringBootTest` local runs, not production | Backend squad | G5 |
| DX-03 | 12 lint warnings (unused vars in e2e tests + CRM tab patterns) | Code Quality | LOW | LOW — No functional impact; noisy | Frontend squad | G5 |
| DX-04 | No Docker environment available for local Testcontainers tests | DX | MEDIUM | LOW — 25 Postgres tests require Docker; skipped without it | DevOps | G5 |

---

## 7. Summary

| Category | HIGH | MEDIUM | LOW | Total |
|----------|------|--------|-----|-------|
| Security | 0 | 3 | 1 | 4 |
| Performance | 1 | 2 | 0 | 3 |
| Architecture | 0 | 1 | 5 | 6 |
| Documentation | 0 | 1 | 2 | 3 |
| Infrastructure | 1 | 2 | 0 | 3 |
| Developer Experience | 0 | 2 | 2 | 4 |
| **Total** | **2** | **11** | **10** | **23** |

### HIGH Priority Items (must address)

| # | Item | Risk if unaddressed |
|---|------|---------------------|
| P-02 | No pagination on CRM list endpoints | Production incidents with large datasets |
| I-02 | No automated smoke test pipeline | Regressions undetected post-deployment |

---

*Register compiled 2026-07-30 by Release Baseline Authority*
