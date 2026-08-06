# CRM-032 Architecture Review

## Date: 2026-07-31
## Ticket: CRM-032 — Penetration test closure for CRM surface
## Reviewer: ZCode automated architecture gate

---

## 1. Scope

CRM-032 requires a penetration test report covering the CRM API and UI,
committed under `docs/audit/CRM-PENTEST-REPORT.md`. All Critical and High
findings must be remediated or formally risk-accepted by the project owner.

---

## 2. CRM Surface Definition

### 2.1 API Surface

| Endpoint Category | Examples | Authentication |
|-------------------|----------|----------------|
| Authentication | `/api/platform/api/v1/auth/login`, `/api/platform/api/v1/auth/logout` | Public / Token |
| Account Management | `/api/platform/api/v1/accounts/*` | Bearer + RBAC |
| Contact Management | `/api/platform/api/v1/contacts/*` | Bearer + RBAC |
| Lead Management | `/api/platform/api/v1/leads/*` | Bearer + RBAC |
| Opportunity Management | `/api/platform/api/v1/opportunities/*` | Bearer + RBAC |
| Import/Export | `/api/platform/api/v1/imports/*` | Bearer + RBAC |
| Custom Fields | `/api/platform/api/v1/custom-fields/*` | Bearer + RBAC |
| Dashboard | `/api/platform/api/v1/dashboard/*` | Bearer + RBAC |
| System Health | `/api/system/backend-status`, `/api/system/release` | Public |

### 2.2 UI Surface

| Route | Component | Access |
|-------|-----------|--------|
| `/crm/overview` | Dashboard | Authenticated |
| `/crm/accounts` | Accounts List | Authenticated + RBAC |
| `/crm/contacts/[id]` | Contact Detail | Authenticated + RBAC |
| `/crm/leads/[id]` | Lead Detail | Authenticated + RBAC |
| `/crm/opportunities/[id]` | Opportunity Detail | Authenticated + RBAC |
| `/crm/imports` | Import Management | Authenticated + RBAC |
| `/crm/command-center` | Command Center | Authenticated + RBAC |
| `/crm/settings/custom-fields` | Custom Fields Admin | Authenticated + RBAC |

### 2.3 Security Controls Already in Place

| Control | Implementation | Evidence |
|---------|----------------|----------|
| Multi-tenant isolation | Row-level security (RLS) | CRM-018: `V20260730_1__enable_crm_row_level_security.sql` |
| RBAC | Capability-based authorization | `@RequireCapability` annotations |
| Rate limiting | Caffeine-based login rate limiter | CRM-007R2: `LoginRateLimiter` |
| Input validation | Bean validation + custom validators | Various `@Valid` annotations |
| CORS | Same-origin policy | BFF proxy pattern |
| CSRF | HttpOnly cookies + SameSite | Refresh token cookie |
| Secrets | GitHub secret scanning | `evidence/secret-scan-evidence.json` |
| Dependency audit | Frontend production dependency audit | Security Baseline workflow |

---

## 3. Affected Artifacts

| Artifact | Type | Risk |
|----------|------|------|
| `docs/audit/CRM-PENTEST-REPORT.md` | NEW — markdown | Minimal (documentation only) |
| `scripts/crm/governance-drift-check.sh` | MODIFIED — script | Low (add Section 17) |
| Remediation files (if Critical/High findings) | NEW/MODIFIED | Depends on findings |

---

## 4. Dependency Graph

```
CRM-032
 ├── CRM-018 (Row-level security) — DONE
 └── CRM-026 (CRM E2E test) — DONE
```

All dependencies are satisfied. No circular dependencies.

---

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Critical finding in pentest | Medium | High | Must remediate or risk-accept before GO |
| High finding in pentest | Medium | Medium | Must remediate or risk-accept |
| Pentest scope too narrow | Low | Medium | Define comprehensive test cases |
| Pentest report incomplete | Low | Medium | Template with required sections |
| Remediation introduces regression | Low | Medium | CI verification after remediation |

---

## 6. Architecture Assessment

### 6.1 No code changes required
CRM-032 is primarily a documentation and audit task. The penetration test
report is a markdown document. Code changes are only required if Critical
or High findings require remediation.

### 6.2 Existing security infrastructure
The CRM surface already has:
- Row-level security (RLS) for multi-tenant isolation
- Capability-based RBAC authorization
- Rate limiting for authentication endpoints
- Input validation and sanitization
- Secret scanning and dependency auditing

### 6.3 Pentest methodology
The penetration test should cover:
- OWASP Top 10 vulnerabilities
- API security (authentication, authorization, injection, etc.)
- UI security (XSS, CSRF, clickjacking, etc.)
- Multi-tenant isolation bypass attempts
- RBAC escalation attempts
- Rate limiting bypass attempts

---

## 7. Conclusion

**Architecture: ✅ APPROVED**

CRM-032 is a security audit task with minimal code risk. The CRM surface
has existing security controls that can be verified through penetration
testing. The architecture supports the required audit workflow.
