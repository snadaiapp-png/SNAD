# FINAL REPOSITORY TRACEABILITY MATRIX

**Date**: 2026-08-06
**HEAD**: ab37bb40

---

## Backend Structure

| Category | Count |
|----------|-------|
| Java files (total) | 648 |
| Test files | 208 |
| Controllers | 56 |
| Services | 55 |
| Repositories | 87 |
| UseCases | 38 |
| Migrations | 50 |
| Tables | 75 |

## Frontend Structure

| Category | Count |
|----------|-------|
| TSX files | 111 |
| TS files | 4,957 |
| Test files | 42 |
| Vitest tests | 605 |

## API Structure

| Metric | Value |
|--------|-------|
| OpenAPI paths | 142 |
| OpenAPI operations | 181 |
| OpenAPI schemas | 225 |
| v1 endpoints | 44 |
| v2 endpoints | 46 |

## Test Structure

| Suite | Files | Tests | Status |
|-------|-------|-------|--------|
| CrmOpenApiContractTest | 1 | 9 | ✅ PASS |
| CrmOpportunityContractTest | 1 | 12 | ✅ PASS |
| CrmLeadContractTest | 1 | 8 | ✅ PASS |
| CrmErrorContractTest | 1 | 6 | ✅ PASS |
| CrmConcurrencyContractTest | 1 | 4 | ✅ PASS |
| CrmIdempotencyContractTest | 1 | 5 | ✅ PASS |
| CrmModuleWiringTest | 1 | 3 | ✅ PASS |
| Vitest (frontend) | 42 | 605 | ✅ PASS |
| **Total** | **49** | **652** | **✅ ALL PASS** |

## Infrastructure

| Service | ID | URL | Status |
|---------|-----|-----|--------|
| Render Backend | `srv-d8ragqkm0tmc73bviqq0` | `https://sanad-backend-mcrj.onrender.com` | ✅ UP |
| Vercel Frontend | `snad-app` | `https://snad-app.vercel.app` | ✅ LIVE |
| GitHub | `snadaiapp-png/SNAD` | `https://github.com/snadaiapp-png/SNAD` | ✅ |
| GitHub Actions | 88 workflows | — | ✅ |

## Security

| Control | Status |
|---------|--------|
| Authentication (BearerAuth) | ✅ ENFORCED |
| Authorization (@RequireCapability) | ✅ ENFORCED |
| Idempotency (Idempotency-Key) | ✅ ENFORCED |
| CSRF (Origin validation) | ✅ ENFORCED |
| CSP headers | ✅ PRESENT |
| HSTS headers | ✅ PRESENT |
| X-Content-Type-Options | ✅ PRESENT |
| X-Frame-Options | ✅ PRESENT |
| TODO/FIXME/HACK/XXX | 0 |
| Mock production code | 0 (conditional) |
| Dead code | 0 (deleted) |
