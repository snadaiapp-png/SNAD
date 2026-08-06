# G4 OpenAPI Audit

**Module**: Opportunities & Pipeline (G4)
**Generated**: 2026-08-06
**HEAD**: 7bb72ffe
**OpenAPI File**: `docs/crm/contracts/openapi/crm-openapi.json`

## OpenAPI Summary

| Metric | Value |
|--------|-------|
| Total Paths | 142 |
| Total Operations | 181 |
| Schemas | 50+ |
| Security Schemes | BearerAuth |

## G4 Endpoint Coverage

### Pipelines
| Method | Path | Status | Schema | Idempotency |
|--------|------|--------|--------|-------------|
| GET | /pipelines | ✅ Defined | ListResponsePipelineResponse | N/A |
| POST | /pipelines | ✅ ADDED | CreatePipelineRequest → PipelineResponse | ✅ Required |
| GET | /pipelines/{id} | ✅ Defined | PipelineResponse | N/A |
| PUT | /pipelines/{id} | ✅ Defined | UpdatePipelineRequest → PipelineResponse | ✅ Required |
| DELETE | /pipelines/{id} | ✅ Defined | 204 | ✅ Required |

### Opportunities
| Method | Path | Status | Schema | Idempotency |
|--------|------|--------|--------|-------------|
| GET | /opportunities | ✅ Defined | ListResponseOpportunityResponse | N/A |
| POST | /opportunities | ✅ Defined | CreateOpportunityRequest → OpportunityResponse | ✅ Required |
| GET | /opportunities/{id} | ✅ Defined | OpportunityResponse | N/A |
| PATCH | /opportunities/{id} | ✅ Defined | UpdateOpportunityRequest → OpportunityResponse | ✅ Required |
| DELETE | /opportunities/{id} | ✅ Defined | 204 | ✅ Required |
| POST | /opportunities/{id}/move | ✅ Defined | MoveOpportunityRequest → OpportunityResponse | ✅ Required |

### Stages
| Method | Path | Status | Schema | Idempotency |
|--------|------|--------|--------|-------------|
| GET | /stages | ✅ Defined | ListResponseStageResponse | N/A |

### Lead Conversion
| Method | Path | Status | Schema | Idempotency |
|--------|------|--------|--------|-------------|
| GET | /leads/{id}/convert-preview | ✅ Defined | ConversionPreviewResponse | N/A |
| POST | /leads/{id}/convert | ✅ Defined | ConvertLeadRequest → ConversionResultResponse | ✅ Required |

## Contract Validation

| Test | Status |
|------|--------|
| CrmOpenApiContractTest (9 tests) | ✅ ALL PASS |
| OpenAPI operations count = 181 | ✅ VERIFIED |
| POST /pipelines returns 201 | ✅ VERIFIED |
| POST /pipelines requires Idempotency-Key | ✅ VERIFIED |
| All POST/PUT/PATCH/DELETE have Idempotency-Key | ✅ VERIFIED |
| All endpoints have BearerAuth security | ✅ VERIFIED |
