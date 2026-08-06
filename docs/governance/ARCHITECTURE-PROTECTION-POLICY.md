# SANAD Architecture Protection Policy

> **STATUS:** PERMANENT — MANDATORY — NON-NEGOTIABLE
> **SCOPE:** Every commit, pull request, merge, release, and deployment in the SANAD repository
> **SUPERSEDES:** Any conflicting convention in tooling configs, README files, or historical code
> **ADOPTED:** 2026-08-07
> **AMENDMENTS:** Require explicit Architecture Decision Record (ADR) approved by the project owner

This policy protects the SANAD architecture against future architectural drift, module coupling,
boundary violations, hidden dependencies, unauthorized refactoring, and accidental regressions.

No code may be merged unless every rule below passes. This policy is permanent and applies to
all future development.

---

## 1. Objective

Protect the SANAD architecture against:

- Architectural drift
- Module coupling
- Boundary violations
- Hidden dependencies
- Unauthorized refactoring
- Accidental regressions

This policy is mandatory for every commit, pull request, merge, release, and deployment.
No code may be merged unless every rule below passes.

---

## 2. Architectural Immutability

The following **bounded contexts** are architecturally independent business
modules — they MUST NOT import each other directly:

| Business Bounded Context | Frontend Root | Backend Package |
|---|---|---|
| Executive Management | `apps/web/app/executive/` | `com.sanad.platform.executive` |
| System Health | `apps/web/app/system-health/` | `com.sanad.platform.health` |
| CRM | `apps/web/app/crm/` | `com.sanad.platform.crm` |
| ERP | (planned) | (planned) |
| HRM | (planned) | (planned) |
| Accounting | (planned) | (planned) |
| POS | (planned) | (planned) |
| Workflow Engine | (planned) | `com.sanad.platform.businessprocess` |
| AI Platform | (planned) | (planned) |

The following are **supporting modules** (cross-cutting concerns, NOT business
bounded contexts). They MAY be imported by any business bounded context, but
they themselves remain subject to layering rules (§5):

| Supporting Module | Purpose | Backend Package |
|---|---|---|
| Identity / Access | Authentication, RBAC, session management | `com.sanad.platform.access`, `com.sanad.platform.security` |
| Organization | Org/membership hierarchy | `com.sanad.platform.organization` |
| User | User identity and profile | `com.sanad.platform.user` |
| Tenant | Tenant context resolution | `com.sanad.platform.tenant` |
| Admin | Cross-cutting audit + shared admin DTOs | `com.sanad.platform.admin` |
| Notification | Email / SMS / push (planned) | (planned) |
| Audit | Structured audit log (planned) | (planned) |
| Core Platform | Utilities, framework adapters, config | `com.sanad.platform.shared`, `com.sanad.platform.config`, `com.sanad.platform.infrastructure` |

**Business bounded contexts MUST NOT import each other directly.**
**Supporting modules MAY be imported by business contexts** (cross-cutting
concerns like audit, identity, tenant resolution are legitimate).

Each bounded context owns:

- UI
- Routes
- Navigation
- API
- Controllers
- Application Layer
- Domain Layer
- Infrastructure Layer
- Services
- DTOs
- Events
- Permissions
- Feature Flags
- Tests

**No module may own another module's business logic.**

---

## 3. Forbidden Patterns

The following are **permanently forbidden** in any merged code:

- Cross-module imports
- Shared business services
- Shared business DTOs
- Shared controllers
- Shared application services
- Shared domain services
- Shared repositories
- Shared permissions
- Shared feature flags
- Shared route registries
- Shared navigation registries
- Shared module registries
- Circular dependencies
- Hardcoded tenant IDs
- Hidden runtime coupling
- Business logic inside infrastructure
- Business logic inside UI
- Temporary shortcuts
- Quick fixes
- Technical debt without approval

---

## 4. Import Rules

### 4.1 Frontend (TypeScript / React / Next.js)

**Executive MUST NEVER import:**

- `apps/web/app/system-health/**`
- `apps/web/lib/api/system-health-*`
- `apps/web/lib/navigation/system-health-*`
- `apps/web/lib/routes/system-health-*`
- `apps/web/lib/modules/system-health-*`
- `apps/web/lib/feature-flags/system-health-*`
- `apps/web/app/crm/**`
- `apps/web/app/erp/**`
- `apps/web/app/accounting/**`
- `apps/web/app/hrm/**`
- `apps/web/app/pos/**`

**System Health MUST NEVER import:**

- `apps/web/app/executive/**`
- `apps/web/lib/api/executive-*`
- `apps/web/lib/navigation/executive-*`
- `apps/web/lib/routes/executive-*`
- `apps/web/lib/modules/executive-*`
- `apps/web/lib/feature-flags/executive-*`
- `apps/web/app/crm/**`
- `apps/web/app/erp/**`
- `apps/web/app/accounting/**`
- `apps/web/app/hrm/**`
- `apps/web/app/pos/**`

The same rule applies symmetrically to every bounded context.

### 4.2 Backend (Java / Spring Boot)

**`com.sanad.platform.executive.*` MUST NEVER import:**

- `com.sanad.platform.health.*`
- `com.sanad.platform.crm.*`
- `com.sanad.platform.erp.*`
- `com.sanad.platform.accounting.*`
- `com.sanad.platform.hrm.*`
- `com.sanad.platform.pos.*`

**`com.sanad.platform.health.*` MUST NEVER import:**

- `com.sanad.platform.executive.*`
- `com.sanad.platform.crm.*`
- `com.sanad.platform.erp.*`
- `com.sanad.platform.accounting.*`
- `com.sanad.platform.hrm.*`
- `com.sanad.platform.pos.*`

The same rule applies symmetrically to every bounded context.

### 4.3 Communication Allowed Only Through

- Public APIs (REST controllers under `/api/v1/<context>/**`)
- Domain Events
- Message Bus
- Approved Interfaces

**Never through direct internal imports.**

---

## 5. Dependency Direction Rules

Allowed dependency direction **only**:

```
Presentation  →  Application  →  Domain  →  Infrastructure
```

- Never reverse.
- Never bypass layers.
- Never create cycles.

### 5.1 Layer Ownership (Backend)

| Layer | Allowed to depend on |
|---|---|
| `api` (Presentation — Controllers) | `application`, `domain` |
| `application` (Application Services) | `domain` |
| `domain` (Pure Domain Model) | nothing (pure) |
| `infrastructure` (Repositories, Adapters) | `domain` (ports only) |

### 5.2 Layer Ownership (Frontend)

| Layer | Allowed to depend on |
|---|---|
| `app/<context>/*` (UI / pages / components) | `lib/api/<context>-*`, `lib/navigation/<context>-*`, `lib/routes/<context>-*`, `lib/modules/<context>-*`, `lib/feature-flags/*`, `lib/i18n/*`, `lib/design-system/*` |
| `lib/api/<context>-*` (API client for that context) | `lib/http`, `lib/types` |
| `lib/navigation/<context>-*` | `lib/routes/<context>-*` |
| `lib/routes/<context>-*` | nothing |
| `lib/modules/<context>-*` | `lib/routes/<context>-*`, `lib/navigation/<context>-*`, `lib/feature-flags/*` |
| `lib/feature-flags/feature-flags.ts` | nothing (central flag registry only) |

---

## 6. Core Rule

`com.sanad.platform.shared` and `apps/web/lib/` (shared utilities only) may contain:

- Utilities
- Framework adapters
- Logging
- Configuration
- Security
- Caching
- Messaging
- Telemetry
- Shared abstractions

**Core MUST NEVER contain business logic.**

---

## 7. RBAC Rules

Each module owns:

- Capabilities
- Roles
- Permission Guards
- Navigation Guards
- Feature Visibility

**Never reuse another module's permissions.**

| Module | Owns Capabilities |
|---|---|
| Executive Management | `EXECUTIVE_VIEW`, `EXECUTIVE_MANAGE`, `EXECUTIVE_BILLING` |
| System Health | `SYSTEM_HEALTH_VIEW`, `SYSTEM_HEALTH_MONITOR`, `SYSTEM_HEALTH_ALERTS` |

---

## 8. Multi-Tenant Rules

**Hardcoded tenant IDs are forbidden.** This includes (non-exhaustive):

- `"default"`
- `"tenant-default"`
- `"00000000-0000-0000-0000-000000000000"`
- Any string literal used as a tenant identifier
- Any environment variable used as a tenant identifier

Tenant resolution MUST come from:

- Authenticated Session (JWT claim `tenant_id`)
- Tenant Context (resolved by `TenantContextHolder`)
- Organization Context
- Workspace Context

---

## 9. Feature Flag Rules

Each module owns its own feature flags.

**No global business feature flag.** Cross-cutting infrastructure flags (e.g. `MAINTENANCE_MODE`)
may live in the central registry but **must not** gate business behavior of a single bounded context.

| Module | Owns Flag |
|---|---|
| Executive Management | `EXECUTIVE_MODULE` |
| System Health | `SYSTEM_HEALTH_MODULE` |

---

## 10. CI/CD Gates

Every Pull Request MUST automatically execute:

1. Architecture Tests
2. Dependency Analysis
3. Circular Dependency Detection
4. Layer Validation
5. Import Boundary Validation
6. RBAC Validation
7. Feature Flag Validation
8. Route Validation
9. Navigation Validation
10. Module Registry Validation
11. API Validation
12. Multi-Tenant Validation (no hardcoded tenant IDs)
13. Unit Tests
14. Integration Tests
15. End-to-End Tests
16. Security Scan (gitleaks)
17. Production Build

**If ANY check fails: BLOCK THE MERGE.**

The workflow file is `.github/workflows/architecture-protection-gate.yml`.
Enforcement scripts live under `scripts/architecture/`.

---

## 11. Dependency Analysis

Automatically verify:

- Zero circular dependencies
- Zero cross-module imports
- Zero forbidden dependencies
- Zero architecture violations

Generate dependency graph. Archive report to `evidence/architecture/<date>-dep-report.json`.

---

## 12. Architecture Tests

Automatically fail when:

- Executive imports System Health
- System Health imports Executive
- Any bounded context imports another bounded context directly
- Business logic exists inside UI
- Business logic exists inside Infrastructure
- DTO leakage
- Application layer leakage
- Repository leakage

---

## 13. Protected Directories

The following directories are **protected**. Changes require architecture approval via ADR:

| Path | Owner Bounded Context |
|---|---|
| `apps/web/app/executive/` | Executive Management |
| `apps/web/app/system-health/` | System Health |
| `apps/web/app/crm/` | CRM |
| `apps/web/app/erp/` | ERP |
| `apps/web/app/accounting/` | Accounting |
| `apps/web/app/hrm/` | HRM |
| `apps/web/app/pos/` | POS |
| `apps/sanad-platform/src/main/java/com/sanad/platform/executive/` | Executive Management |
| `apps/sanad-platform/src/main/java/com/sanad/platform/health/` | System Health |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/` | CRM |
| `apps/sanad-platform/src/main/java/com/sanad/platform/businessprocess/` | Workflow Engine |
| `apps/sanad-platform/src/main/java/com/sanad/platform/access/` | Identity |
| `apps/sanad-platform/src/main/java/com/sanad/platform/shared/` | Core Platform |

Changes to protected directories in a PR MUST include:

- Architecture impact analysis
- Dependency analysis
- Risk analysis
- Migration strategy
- Rollback strategy
- Updated documentation

---

## 14. Code Review Policy

Every architectural change MUST include:

- Architecture impact analysis
- Dependency analysis
- Risk analysis
- Migration strategy
- Rollback strategy
- Updated documentation

---

## 15. Release Policy

**No release is allowed unless:**

- Architecture tests PASS
- Dependency analysis PASS
- Production build PASS
- Smoke tests PASS
- Security checks PASS
- Performance checks PASS

---

## 16. Permanent Acceptance Rule

Any Pull Request that violates any rule above SHALL be **automatically rejected**.

- No exceptions.
- No manual override.
- No temporary bypass.
- No "fix later".

**Architecture integrity takes precedence over implementation speed.**

This policy is permanent and applies to all future development.

---

## 17. Enforcement Artifacts

The following artifacts enforce this policy in CI:

| Artifact | Purpose |
|---|---|
| `.github/workflows/architecture-protection-gate.yml` | CI/CD gate that runs on every PR to `main` |
| `apps/web/.dependency-cruiser.cjs` | Frontend import boundary + layer validation |
| `scripts/architecture/check_frontend_boundaries.py` | Frontend architecture validator (cross-import detection, hardcoded tenant IDs) |
| `scripts/architecture/check_backend_boundaries.py` | Backend Java package boundary validator |
| `scripts/architecture/check_protected_directories.py` | Validates protected directory changes include required analysis |
| `scripts/architecture/check_tenant_hardcoding.py` | Multi-tenant validation — fails on hardcoded tenant IDs |
| `scripts/architecture/architecture_gate.py` | Orchestrator that runs all architecture checks and exits non-zero on any failure |

---

## 18. Relationship to Existing Governance

This policy is **additive** to the existing `CONSTITUTION.md` and does not supersede it.
Where the two documents address the same concern, the **stricter** rule applies.

Related governance documents:

- `CONSTITUTION.md` — Project Constitution (identity, principles, non-negotiable rules)
- `docs/governance/OWNER-AUTHORITY-MODEL.md` — Owner authority model
- `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md` — Single external approver authority
- `docs/governance/STATUS-DOCUMENTATION-POLICY.md` — Status documentation policy
- `docs/architecture/adr/` — Architecture Decision Records

---

## 19. Acceptance

This policy was adopted on **2026-08-07** as the permanent repository governance policy
for the SANAD repository (`snadaiapp-png/SNAD`).

It is binding on:

- All current and future contributors
- All current and future branches
- All current and future pull requests, merges, releases, and deployments
- All current and future automated CI/CD pipelines

**End of Policy.**
