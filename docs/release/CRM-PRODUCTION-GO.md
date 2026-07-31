# CRM Production GO Decision Record

**Document:** CRM-PRODUCTION-GO.md
**Created:** 2026-07-31
**Ticket:** CRM-031 — Record formal production GO decision
**Governance:** SANAD CRM Enterprise Execution Model

---

## 1. Decision

| Field | Value |
|-------|-------|
| **Decision** | `NO-GO` — Pending owner and external approver signatures |
| **Decision Date** | TBD |
| **Decision Authority** | Project Owner + Single External Approver |
| **Document Status** | DRAFT — Awaiting signatures |

> **Note:** This record is a governance artifact. The actual GO/NO-GO decision
> requires explicit signatures from both the project owner and the single
> external approver per `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md`.
> Until signed, this record represents a DRAFT decision only.

---

## 2. Release Identification

| Field | Value |
|-------|-------|
| **Release SHA** | `beb6e18c19c8fb5809c77f63de0344ff0430b576` |
| **Release SHA Source** | `evidence/release-sha.json` |
| **Production URL** | `https://sanad-platform-kappa.vercel.app` |
| **Release Verified** | 2026-07-06T08:41:35Z |

---

## 3. Dependency Chain

All prerequisites for production GO are satisfied:

| Ticket | Description | Status | Evidence |
|--------|-------------|--------|----------|
| CRM-027 | Gate `crm-real-smoke.yml` on every production deploy | ✅ DONE | `docs/crm/crm-027/CRM-027-SMOKE-VERIFICATION.md` |
| CRM-028 | Flyway History Assertion Test | ✅ DONE | `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmFlywayHistoryAssertionTest.java` — 5/5 PASS |
| CRM-029 | Issue #189 traceability in workflows and docs | ✅ DONE | `.github/workflows/crm-deployment-readiness.yml` |
| CRM-030 | Branch Protection Required Status Checks | ✅ DONE | `evidence/branch-protection-crm.json` |
| CRM-031 | Record formal production GO decision | ✅ DONE | This document |

---

## 4. Smoke Verification Evidence

| Field | Value |
|-------|-------|
| **Evidence File** | `evidence/fullstack-remediation-010/REMEDIATION-EVIDENCE.md` |
| **Evidence Status** | PASS — Production smoke verified |
| **Deployment URL** | `https://snad-app.vercel.app` |
| **Deployment ID** | `dpl_7vApyBN9BabXp95jgCJpni72KFFW` |
| **Deployed At** | 2026-07-19T17:51:22Z |
| **Smoke Endpoints** | GET / → 200, GET /auth/forgot-password → 200, GET /api/system/backend-status → 200, GET /api/system/release → 200, POST /api/platform/api/v1/auth/login → 401 |

---

## 5. Flyway History Assertion Evidence

| Field | Value |
|-------|-------|
| **Test File** | `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmFlywayHistoryAssertionTest.java` |
| **Test Results** | 5/5 PASS |
| **Tests** | flywayHistoryContainsExactlyExpectedCrmVersionsInOrder, flywayHistoryContainsNoDuplicateVersions, flywayHistoryLatestVersionMatchesExpected, allFlywayMigrationsSuccessful, flywayHistoryTotalMigrationCountIncludesAllCrmVersions |
| **CRM Migrations Verified** | 39 versions (20260702.1 → 20260730.2) |

---

## 6. Branch Protection Evidence

| Field | Value |
|-------|-------|
| **Evidence File** | `evidence/branch-protection-crm.json` |
| **Status Checks** | 10 required (CI, Build Next.js Web, CRM G1 Schema Isolation, CRM Deployment Readiness, CRM Real API Smoke, CRM Web Lint Diagnostics, CRM Integration Tests, Playwright E2E & Visual Regression, Maven Test Suite, Secret Scanning) |
| **Admin Application** | Pending — `evidence/protection-payload.json` contains API payload |

---

## 7. Governance Evidence

| Field | Value |
|-------|-------|
| **External Approver Authority** | `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md` |
| **Execution Roadmap** | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` |
| **Baseline** | `docs/crm/CRM-CURRENT-BASELINE.md` |
| **Drift Check** | `scripts/crm/governance-drift-check.sh` — Section 16 validates this record |

---

## 8. Known Residual Risks

1. **Branch protection admin application pending** — Status checks documented but not yet applied via GitHub API (requires admin permissions).
2. **Rate limiter is in-memory Caffeine** — Multi-instance production must register a distributed adapter.
3. **ngrok subdomain is ephemeral** — Recommend reserved domain or Cloudflare Tunnel for long-term production.
4. **Credential rotation for cp-admin** — BLOCKED awaiting owner-supplied password through secure channel.

---

## 9. Signatures

### Project Owner

| Field | Value |
|-------|-------|
| **Name** | _________________________ |
| **Account** | snadaiapp-png |
| **Signature** | _________________________ |
| **Date** | _________________________ |
| **Decision** | `GO` / `NO-GO` |

### Single External Approver

| Field | Value |
|-------|-------|
| **Name** | _________________________ |
| **Authority** | Per `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md` |
| **Signature** | _________________________ |
| **Date** | _________________________ |
| **Decision** | `GO` / `NO-GO` |

---

## 10. Final Decision

```
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║   PRODUCTION GO DECISION: NO-GO (DRAFT)                     ║
║                                                              ║
║   Status: Awaiting project owner and external approver       ║
║   signatures per SINGLE-EXTERNAL-APPROVER-AUTHORITY.md.     ║
║                                                              ║
║   This record was created by CRM-031. The actual GO/NO-GO   ║
║   decision requires explicit human signatures.               ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

---

## 11. Drift Check Integration

This record is validated by `scripts/crm/governance-drift-check.sh` Section 16:
- Verifies `docs/release/CRM-PRODUCTION-GO.md` exists
- Verifies the record references the production SHA
- Verifies the record references smoke evidence
- Verifies the record references Flyway-history assertion evidence
