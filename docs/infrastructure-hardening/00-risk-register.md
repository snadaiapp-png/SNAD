# SNAD Infrastructure Hardening — Stage 00 Risk Register

## BLOCKER

None at this stage. All checks that could be run locally PASSED. Backend and container checks are NOT_RUN (delegated to CI which passes on main).

## CRITICAL

| ID | Title | Severity | Evidence | File | Observed | Expected | Risk | Recommended Stage |
|---|---|---|---|---|---|---|---|---|
| C-01 | No Maven Wrapper in backend | CRITICAL | No `mvnw` file exists | `apps/sanad-platform/` | Maven version depends on CI runner or local install | Reproducible builds require pinned Maven version | Build reproducibility depends on environment | Stage 01 |
| C-02 | No pagination on any list endpoint | CRITICAL | Every list API returns `List<T>` | All controllers | Full result set loaded into memory | `Page<T>` with `Pageable` | OOM with large datasets | Stage 02 |
| C-03 | Rate limiting is in-memory only (Caffeine) | CRITICAL | Caffeine cache, no distributed store | `AuthService.java` | Does not work across multiple instances | Distributed rate limiting (Redis or shared store) | Brute force protection fails with horizontal scaling | Stage 03 |

## HIGH

| ID | Title | Severity | Evidence | File | Observed | Expected | Risk | Recommended Stage |
|---|---|---|---|---|---|---|---|---|
| H-01 | HF-01: Historical admin password in git history | HIGH | Gitleaks history: 8 raw detections | `set-admin-password.yml` (deleted) | Password value in git history, rotation NOT VERIFIED | Rotated and old value rejected | Credential compromise if history is accessible | Issue #173 (owner action) |
| H-02 | HF-06: Historical email-proxy fallback in git history | HIGH | Gitleaks history | `route.ts` (commit a6b11112) | Fallback value in history, operational use NOT DETERMINED | Owner verification of operational status | Potential credential exposure | Issue #173 (owner action) |
| H-03 | No global exception handler catch-all | HIGH | No `Exception.class` in any `@RestControllerAdvice` | All exception handlers | Unhandled exceptions fall to Spring default error handler | Global catch-all with structured error response | Stack trace leakage in non-prod | Stage 02 |
| H-04 | No structured JSON logging | HIGH | SLF4J text logs, no JSON encoder | All profiles | Log parsing requires regex, no correlation IDs | Structured JSON with correlation IDs | Poor observability, slow incident response | Stage 03 |
| H-05 | No distributed tracing | HIGH | No OpenTelemetry/Sleuth/Micrometer Tracing | pom.xml | No trace propagation across requests | Correlation IDs + distributed traces | Cannot trace requests across services | Stage 03 |
| H-06 | No metrics endpoint (beyond health) | HIGH | Actuator exposes only `health` in prod | `application-prod.yml` | No JVM/HTTP/DB metrics exposed | Prometheus metrics endpoint | Cannot monitor performance or detect degradation | Stage 03 |

## MEDIUM

| ID | Title | Severity | Evidence | File | Observed | Expected | Risk | Recommended Stage |
|---|---|---|---|---|---|---|---|---|
| M-01 | Dev profile uses H2 (not PostgreSQL) | MEDIUM | `application-local.yml` uses H2 | `application-local.yml` | Environment parity gap between dev and prod | Testcontainers PostgreSQL in all profiles | H2-specific behavior may mask PostgreSQL issues | Stage 01 |
| M-02 | No frontend middleware | MEDIUM | No `middleware.ts` | `apps/web/` | No request-level middleware for auth redirects | Middleware for route protection | Auth state checks only in components | Stage 02 |
| M-03 | No error boundary in frontend | MEDIUM | No `error.tsx` | `apps/web/app/` | Unhandled client errors show blank page | Next.js error boundary | Poor UX on client errors | Stage 02 |
| M-04 | Single Docker stage (no layer caching optimization) | MEDIUM | Dockerfile copies all files before build | `apps/sanad-platform/Dockerfile` | Source changes invalidate dependency cache | Separate dependency resolution from source copy | Slower CI builds | Stage 01 |
| M-05 | No audit_log table | MEDIUM | Audit data in operational tables + log lines | Backend | No dedicated audit trail entity | `audit_log` table with entity + aspect | Compliance gap for ERP | Stage 04 |

## LOW

| ID | Title | Severity | Evidence | File | Observed | Expected | Risk | Recommended Stage |
|---|---|---|---|---|---|---|---|---|
| L-01 | No vercel.json configuration | LOW | No file | `apps/web/` | Vercel auto-detects Next.js | Explicit configuration for headers, redirects | Suboptimal caching/headers | Stage 02 |
| L-02 | Root package-lock.json is minimal (83 bytes) | LOW | `{ "lockfileVersion": 1 }` | Root | No root-level dependencies tracked | Remove or populate | Confusing for tooling | Stage 01 |
| L-03 | No .nvmrc or .node-version file | LOW | No file | Root | Node version only in `package.json` engines | `.nvmrc` file | Developer environment mismatch | Stage 01 |
