# G4 RBAC Audit

**Module**: Opportunities & Pipeline (G4)
**Generated**: 2026-08-06
**HEAD**: 7bb72ffe

## RBAC Summary

| Metric | Status |
|--------|--------|
| @RequireCapability on all endpoints | ✅ VERIFIED |
| No unprotected endpoints | ✅ VERIFIED |
| BearerAuth required | ✅ VERIFIED |
| 401 returned without token | ✅ VERIFIED |

## Endpoint RBAC Matrix

### CrmController (v1)
| Method | Path | Annotation | Auth Required |
|--------|------|-----------|---------------|
| GET | /api/v1/crm/dashboard | @RequireCapability | ✅ |
| GET | /api/v1/crm/pipelines | @RequireCapability | ✅ |
| POST | /api/v1/crm/pipelines | @RequireCapability | ✅ |
| GET | /api/v1/crm/pipelines/{id} | @RequireCapability | ✅ |
| PUT | /api/v1/crm/pipelines/{id} | @RequireCapability | ✅ |
| DELETE | /api/v1/crm/pipelines/{id} | @RequireCapability | ✅ |
| GET | /api/v1/crm/opportunities | @RequireCapability | ✅ |
| POST | /api/v1/crm/opportunities | @RequireCapability | ✅ |
| GET | /api/v1/crm/opportunities/{id} | @RequireCapability | ✅ |
| PUT | /api/v1/crm/opportunities/{id} | @RequireCapability | ✅ |
| DELETE | /api/v1/crm/opportunities/{id} | @RequireCapability | ✅ |
| POST | /api/v1/crm/opportunities/{id}/move | @RequireCapability | ✅ |
| GET | /api/v1/crm/leads/{id}/convert-preview | @RequireCapability | ✅ |
| POST | /api/v1/crm/leads/{id}/convert | @RequireCapability | ✅ |
| GET | /api/v1/crm/stages | @RequireCapability | ✅ |
| POST | /api/v1/crm/stages | @RequireCapability | ✅ |

### CrmContractController (v2 read)
| Method | Path | Annotation | Auth Required |
|--------|------|-----------|---------------|
| GET | /api/v2/crm/pipelines | @RequireCapability | ✅ |
| POST | /api/v2/crm/pipelines | @RequireCapability | ✅ |
| GET | /api/v2/crm/opportunities | @RequireCapability | ✅ |
| POST | /api/v2/crm/opportunities | @RequireCapability | ✅ |

### CrmContractControllerR1 (v2 mutation)
| Method | Path | Annotation | Auth Required |
|--------|------|-----------|---------------|
| PATCH | /api/v2/crm/opportunities/{id} | @RequireCapability | ✅ |
| DELETE | /api/v2/crm/opportunities/{id} | @RequireCapability | ✅ |

## Production Verification

| Test | Result |
|------|--------|
| GET /api/v1/crm/dashboard (no token) | 401 ✅ |
| GET /api/v1/crm/pipelines (no token) | 401 ✅ |
| GET /api/v1/crm/opportunities (no token) | 401 ✅ |
| GET /api/v1/crm/stages (no token) | 401 ✅ |
| GET /api/v2/crm/pipelines (no token) | 401 ✅ |
| GET /api/v2/crm/opportunities (no token) | 401 ✅ |
