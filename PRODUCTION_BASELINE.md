# PRODUCTION BASELINE

**Frozen:** 2026-08-07T15:00:00+03:00
**SHA:** `62559e47d2e66ef79ea2401c992de60a52e1de58`
**Tag:** `sanad-commercial-20260807-19dd4e94`
**Branch:** `main`

---

## Repository State

| Metric | Value |
|--------|-------|
| HEAD SHA | `62559e47` |
| HEAD == origin/main | ✅ YES |
| Release Tag | `sanad-commercial-20260807-19dd4e94` |
| Tag on remote | ✅ YES |
| GitHub Release | ✅ Published |
| Working Tree | ✅ CLEAN (no modified tracked files) |
| Pending Migrations | 0 |
| Pending Commits | 0 |
| Pending Releases | 0 |

## Deployment State

| Metric | Value |
|--------|-------|
| Vercel Status | ✅ READY |
| Production URL | https://snad-app.vercel.app |
| Deploy SHA | `62559e47d2e66ef79ea2401c992de60a52e1de58` |
| Build Time | 26s |
| Health Check | ✅ ALL ENDPOINTS 200 |

## Backend State

| Metric | Value |
|--------|-------|
| Compile | ✅ PASS |
| Unit Tests | ✅ PASS |
| Integration Tests (H2) | ✅ PASS |
| Integration Tests (PG) | ⚠️ Docker required |
| Flyway Migrations | ✅ All applied |
| OpenAPI | ✅ 183 operations |

## Frontend State

| Metric | Value |
|--------|-------|
| TypeScript | ✅ PASS |
| Build | ✅ PASS |
| Vitest | ✅ 669/669 PASS |
| Lint | ⚠️ Pre-existing config issue |

## Security State

| Metric | Value |
|--------|-------|
| RBAC | ✅ 19/19 write endpoints protected |
| Capabilities | ✅ Propagated end-to-end |
| Mock Guard | ✅ 3-layer defense |
| Tenant Isolation | ✅ Verified |

## Database Migrations

| Version | Description | Status |
|---------|-------------|--------|
| V20260807_1 | Grant CRM capabilities to non-admin roles | ✅ APPLIED |
| V20260807_2 | Seed default pipeline and sample accounts | ✅ APPLIED |
| V20260807_3 | Case-insensitive tag unique index (PostgreSQL) | ✅ APPLIED |
| V20260807_4 | Add activity result column and CHECK constraint | ✅ APPLIED |

---

**This baseline is FROZEN. No further modifications to this SHA are permitted without a new release cycle.**
