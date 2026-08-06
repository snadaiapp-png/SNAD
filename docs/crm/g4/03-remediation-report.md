# G4 Remediation Report

**Module**: Opportunities & Pipeline (G4)
**Generated**: 2026-08-06
**HEAD**: 7bb72ffe

## Remediation Summary

| Category | Issues Found | Issues Fixed | Remaining |
|----------|-------------|-------------|-----------|
| OpenAPI Contract | 1 | 1 | 0 |
| Dead Code | 1 | 1 | 0 |
| Test Alignment | 1 | 1 | 0 |
| **Total** | **3** | **3** | **0** |

## Remediation Details

### R1: Add POST /pipelines to OpenAPI Spec
- **File**: `docs/crm/contracts/openapi/crm-openapi.json`
- **Change**: Added `CreatePipelineRequest` schema with `name` (required), `currencyCode`, `stages` (required, min 2, max 20)
- **Change**: Added POST /pipelines endpoint with Idempotency-Key header parameter, 201 response, BearerAuth security
- **Impact**: OpenAPI operations: 180 → 181
- **Verification**: All 9 CrmOpenApiContractTest tests pass

### R2: Delete Orphan crm-overview.tsx
- **File**: `apps/web/app/crm/crm-overview.tsx` (DELETED)
- **Change**: Removed unused placeholder file with static KPIs
- **Impact**: Dead code eliminated; no import references exist
- **Verification**: grep confirms no imports; live overview at `(operational)/overview/page.tsx`

### R3: Update CrmOpenApiContractTest
- **File**: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/contract/CrmOpenApiContractTest.java`
- **Change**: `EXPECTED_OPERATIONS` 180 → 181
- **Change**: Added `/pipelines` to `createResponsesUse201` test array
- **Change**: Added `/pipelines` to `idempotencyKeysAreRequiredOnGovernedCreatesAndActions` test array
- **Impact**: Contract test now validates POST /pipelines correctly
- **Verification**: All 9 CrmOpenApiContractTest tests pass

## Git Commit

```
7bb72ffe fix(crm): G4 contract remediation — add POST /pipelines to OpenAPI, remove orphan crm-overview.tsx
```

**Files changed**: 3 (1 modified, 1 deleted, 1 modified)
**Insertions**: 74
**Deletions**: 158
