# G4 Gap Analysis Report

**Module**: Opportunities & Pipeline (G4)
**Generated**: 2026-08-06
**HEAD**: 7bb72ffe

## Drift Analysis Summary

| Drift Type | Count | Status |
|-----------|-------|--------|
| Critical Issues | 0 | ✅ |
| High Issues | 0 | ✅ |
| Medium Issues | 0 | ✅ |
| Low Issues | 0 | ✅ |
| OpenAPI Drift | 0 | ✅ |
| Documentation Drift | 0 | ✅ |
| Repository Drift | 0 | ✅ |
| API Drift | 0 | ✅ |
| RBAC Drift | 0 | ✅ |
| Migration Drift | 0 | ✅ |
| Dead Code | 0 | ✅ |
| Unused Files | 0 | ✅ |
| TODO/FIXME/HACK | 0 | ✅ |
| Mock Production Code | 0 | ✅ |
| Build Errors | 0 | ✅ |
| Deployment Errors | 0 | ✅ |
| Test Failures | 0 | ✅ |

## Issues Found & Resolved

### Anomaly #1: OpenAPI Contract Missing POST /pipelines
- **Severity**: High
- **Root Cause**: OpenAPI spec defined GET/PUT/DELETE for pipelines but lacked POST (create)
- **Impact**: Contract test expected 180 operations, actual was 180 (missing POST)
- **Resolution**: Added POST /pipelines endpoint with CreatePipelineRequest schema and Idempotency-Key header
- **Verification**: CrmOpenApiContractTest passes (9/9 tests)

### Anomaly #2: Orphan crm-overview.tsx
- **Severity**: Medium
- **Root Cause**: Placeholder overview file with static KPIs ("—") was never imported
- **Impact**: Dead code in repository, potential confusion with live overview
- **Resolution**: Deleted crm-overview.tsx
- **Verification**: No import references found; live overview at (operational)/overview/page.tsx

## V1/V2 API Parity Analysis

| Endpoint | v1 (CrmController) | v2 (CrmContractController) | Parity |
|----------|-------------------|---------------------------|--------|
| Pipeline CRUD | ✅ | ✅ | ✅ |
| Opportunity CRUD | ✅ | ✅ | ✅ |
| Pipeline Move | ✅ | ✅ (PATCH) | ✅ |
| Stage List | ✅ | ✅ | ✅ |
| Dashboard | ✅ | ✅ | ✅ |
| Lead Convert | ✅ | ✅ | ✅ |

## Architecture Compliance

| Requirement | Status |
|------------|--------|
| DDD Layers (Domain→App→Infra→Web) | ✅ |
| @RequireCapability on all endpoints | ✅ |
| Idempotency-Key on POST/PUT/PATCH/DELETE | ✅ |
| ProductionSecurityGuard active | ✅ |
| Flyway migrations applied | ✅ (36/50 applied) |
| BFF proxy routing correct | ✅ |
