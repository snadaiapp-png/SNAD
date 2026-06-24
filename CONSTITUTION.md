# SANAD Project Constitution

> This document defines the invariants, principles, and non-negotiable rules that govern
> every contribution to the SANAD repository. It overrides any conflicting convention
> found in tooling configs, README files, or historical code. Merging a change that
> violates a rule documented here requires an explicit ADR approved by the project owner.

---

## 1. Identity

- **Project name:** SANAD — Global AI ERP SaaS Platform
- **Repository:** `snadaiapp-png/SNAD`
- **Default branch:** `main`
- **Primary language (docs):** Arabic (RTL) with English for technical terms
- **Primary language (code):** English (Java/TypeScript/SQL)

---

## 2. Architectural Principles

The following eleven principles are binding on every layer of the system. A
contribution that breaks any of them is rejected at code review, regardless
of whether tests pass.

| # | Principle | Enforcement |
|---|-----------|-------------|
| 1 | **Multi-Tenant SaaS** — every request carries a tenant scope | `tenantId` is mandatory in every repository method and JWT claim |
| 2 | **Modular Architecture** — bounded contexts, no cross-domain DB sharing | Package structure under `com.sanad.platform.<context>` |
| 3 | **Domain-Driven Design** — pure domain, ports & adapters | `domain` package depends on nothing |
| 4 | **API-First** — every endpoint has a contract under `/api/v1` | Controllers validate with `@Valid` and OpenAPI annotations |
| 5 | **Event-Driven Architecture** — async integration via events | Command services publish `Created/Updated/StatusChanged/...` |
| 6 | **Workflow-First** — business processes are first-class services | Workflow services orchestrate aggregates, never bypass them |
| 7 | **AI-First** — AI is a peer of CRUD, not a side feature | AI agents use the same auth/tenant/multitenancy primitives |
| 8 | **Security by Design** — defense in depth, least privilege | Every endpoint declares `@RequireCapability` |
| 9 | **Observability** — structured logs, metrics, traces | Log correlation IDs; health/readiness probes |
| 10 | **Horizontal Scalability** — stateless services, distributed rate limits | No in-memory session state shared across instances |
| 11 | **Tenant Data Isolation** — every query is tenant-scoped | `JwtAuthenticationFilter` + repository scoping; IDOR returns 403 |

---

## 3. Non-Negotiable Rules

### 3.1 Secrets and credentials

- **NO secrets in code.** No passwords, API keys, JWT secrets, or tokens in
  source files, commit messages, or test fixtures. Test fixtures use
  clearly-fake values (e.g. `REPLACE_WITH_SECURE_PASSWORD`).
- Secrets live in GitHub Secrets (for CI) or Render environment variables
  (for runtime). Never in `.env` committed to the repo.
- `.gitleaks.toml` is enforcing — a leak fails the build.
- One-time credentials (e.g. admin bootstrap) are deleted immediately
  after use; the workflow files are removed from the repo.

### 3.2 Database

- **All schema changes go through Flyway migrations.** Never `ddl-auto=update`
  in production. Production profile uses `validate`.
- Migrations are append-only. No `DROP` or `TRUNCATE` without an
  explicit ADR.
- UUIDs are the only acceptable primary key type.
- All FK relationships have explicit named constraints
  (`fk_<child>_<parent>`).
- Tenant scoping is mandatory: every table that holds tenant-owned data
  has `tenant_id UUID NOT NULL` and an index on it.

### 3.3 Authentication and Authorization

- JWT tokens carry `tenant_id`, `user_id`, and `session_version` claims.
- Access tokens are short-lived (15 minutes default).
- Refresh tokens are HttpOnly cookies, rotated on every refresh, with
  replay detection.
- Access tokens are NEVER stored in localStorage or sessionStorage.
- Every endpoint declares `@RequireCapability`. The only exception is
  `/api/v1/auth/**` (login, refresh, password reset).
- Rate limiting is on every authentication endpoint. Distributed rate
  limit is required for multi-instance deployment (currently single
  instance — DEFECT-015 is open).

### 3.4 Tenant Isolation

- `tenantId` is extracted from the JWT, never from the request body.
- Every repository method takes `tenantId` as its first parameter.
- Cross-tenant access attempts return 403 (never 404 — that leaks
  existence).
- Tenant isolation tests are mandatory for every new aggregate.

### 3.5 CI/CD

- `main` is always green. The build, lint, tests, and security scans
  must pass before merge.
- No `set +e` patterns that swallow test failures. Exit codes are
  preserved.
- Every PR includes: build passed, tests passed, lint passed, security
  scan passed, code review approved, documentation updated.
- Force-push to `main` is forbidden.
- Auto-deploy is off; production deploys are manual via the
  `production-release.yml` workflow with SHA pinning.

### 3.6 Frontend

- TypeScript strict mode. No `any` without justification in a comment.
- Lint is enforcing: 0 errors allowed.
- Access tokens are in-memory only (DEFECT-013 fix). Session restore
  is via silent refresh using the HttpOnly refresh cookie.
- Server-side route protection via `middleware.ts` (DEFECT-019 fix).
- Security headers (CSP, HSTS, X-Frame-Options) are configured in
  `next.config.ts` (DEFECT-027 fix).

### 3.7 Documentation

- Every architectural decision is recorded in
  `docs/architecture/adr/ADR-XXX-<topic>.md`.
- README files are kept in sync with reality. A README that lies is
  worse than no README.
- Defects are tracked in `docs/audit/SANAD-DEFECT-REGISTER.md` with
  severity, status, and remediation details.

---

## 4. Branch and Commit Conventions

### 4.1 Branch naming

```
main                                 — protected, always green
analysis/<topic>                     — investigation only, not merged
feature/<module-name>                — new functionality
fix/<defect-id>-<short-description>  — bug fixes (DEFECT-XXX)
refactor/<component-name>            — internal restructuring, no behavior change
docs/<documentation-name>            — documentation only
chore/<topic>                        — tooling, dependencies, CI config
```

### 4.2 Commit message format

```
<type>(<scope>): <subject>

<optional body — explain WHY, not WHAT>

<optional footer — DEFECT-XXX, BREAKING CHANGE, etc.>
```

Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`, `security`, `perf`.

---

## 5. Definition of Done

A feature is "done" when ALL of the following are true:

- [ ] Code is merged to `main`
- [ ] All CI checks pass on `main`
- [ ] Tests exist for the new behavior and pass
- [ ] Documentation is updated (README, ADR if architectural)
- [ ] No new defects introduced (checked via `gitleaks` + manual review)
- [ ] Backward compatibility is preserved, OR a migration path is documented
- [ ] Operational impact is assessed (logs, metrics, runbooks)
- [ ] Security impact is assessed (RBAC, tenant isolation, secrets)

---

## 6. Governance

- **Project owner:** SNAD (`snad.ai.app@gmail.com`)
- **ADR approval:** requires project owner sign-off in the ADR PR
- **Production deployment:** requires project owner sign-off in the
  `production-release.yml` workflow
- **Constitution changes:** require a dedicated PR with the
  `constitution` label; merging requires explicit approval from the
  project owner.

---

## 7. Amendment History

| Date | Amendment | ADR |
|------|-----------|-----|
| 2026-06-24 | Initial constitution authored | — |
