# G4 Repository Traceability Matrix

**Module**: Opportunities & Pipeline (G4)
**Generated**: 2026-08-06
**HEAD**: 7bb72ffe

## Backend Files

| File | Layer | Status |
|------|-------|--------|
| `CrmController.java` | Web (v1) | 8 G4 endpoints |
| `CrmContractController.java` | Web (v2 read) | Pipeline, Opportunity, Stage CRUD |
| `CrmContractControllerR1.java` | Web (v2 mutation) | Pipeline, Opportunity mutations |
| `CrmService.java` | Application Facade | Validation + orchestration |
| `OpportunityUseCases.java` | Application | 10 methods: CRUD, move, bulk, stats |
| `LeadConversionUseCases.java` | Application | Atomic Account+Contact+Opportunity from Lead |
| `PipelineRepository.java` | Domain Port | Pipeline CRUD + stage ordering |
| `OpportunityRepository.java` | Domain Port | Opportunity CRUD + pipeline moves |
| `StageRepository.java` | Domain Port | Stage CRUD + pipeline association |
| `LegacyCrmInfrastructureService.java` | Infrastructure | Legacy queries (active) |
| `LegacyOpportunityService.java` | Infrastructure | Legacy mutations (active) |
| `CrmV2AtomicMutationInfrastructureService.java` | Infrastructure | Atomic v2 mutations |
| `ProductionSecurityGuard.java` | Infrastructure | Startup gate (security config) |

## Frontend Files

| File | Role | Status |
|------|------|--------|
| `crm-pipeline-board.tsx` | Kanban board | Drag-and-drop, keyboard nav |
| `opportunities-tab.tsx` | Opportunities table | CRUD, create form, move dialog |
| `pipeline-tab.tsx` | Pipeline wrapper | Data-fetching, optimistic moves |
| `leads-tab.tsx` | Leads table | LeadsConvertDialog integration |
| `(operational)/overview/page.tsx` | Overview | Live KPIs via crmApi.dashboard() |
| `lib/api/crm.ts` | API client | All G4 endpoint methods |

## API Endpoints (17 total)

| Method | Path | Controller | RBAC |
|--------|------|-----------|------|
| GET | /api/v1/crm/dashboard | CrmController | @RequireCapability |
| GET | /api/v1/crm/pipelines | CrmController | @RequireCapability |
| POST | /api/v1/crm/pipelines | CrmController | @RequireCapability |
| GET | /api/v1/crm/pipelines/{id} | CrmController | @RequireCapability |
| PUT | /api/v1/crm/pipelines/{id} | CrmController | @RequireCapability |
| DELETE | /api/v1/crm/pipelines/{id} | CrmController | @RequireCapability |
| GET | /api/v1/crm/opportunities | CrmController | @RequireCapability |
| POST | /api/v1/crm/opportunities | CrmController | @RequireCapability |
| GET | /api/v1/crm/opportunities/{id} | CrmController | @RequireCapability |
| PUT | /api/v1/crm/opportunities/{id} | CrmController | @RequireCapability |
| DELETE | /api/v1/crm/opportunities/{id} | CrmController | @RequireCapability |
| POST | /api/v1/crm/opportunities/{id}/move | CrmController | @RequireCapability |
| GET | /api/v1/crm/leads/{id}/convert-preview | CrmController | @RequireCapability |
| POST | /api/v1/crm/leads/{id}/convert | CrmController | @RequireCapability |
| GET | /api/v1/crm/stages | CrmController | @RequireCapability |
| POST | /api/v1/crm/stages | CrmController | @RequireCapability |
| GET | /api/v2/crm/pipelines | CrmContractController | @RequireCapability |
| POST | /api/v2/crm/pipelines | CrmContractController | @RequireCapability |
| GET | /api/v2/crm/opportunities | CrmContractController | @RequireCapability |
| POST | /api/v2/crm/opportunities | CrmContractController | @RequireCapability |
| PATCH | /api/v2/crm/opportunities/{id} | CrmContractControllerR1 | @RequireCapability |
| DELETE | /api/v2/crm/opportunities/{id} | CrmContractControllerR1 | @RequireCapability |

## Database Tables (Flyway)

| Table | Migrations | Status |
|-------|-----------|--------|
| crm_pipeline | V50, V51 | Applied |
| crm_opportunity | V50, V51 | Applied |
| crm_stage | V50, V51 | Applied |
| crm_lead | V50 | Applied |
| crm_contact | V50 | Applied |

## Test Files

| File | Type | Tests |
|------|------|-------|
| CrmOpenApiContractTest.java | Contract | 9 tests |
| CrmOpportunityContractTest.java | Contract | 12 tests |
| CrmLeadContractTest.java | Contract | 8 tests |
| CrmErrorContractTest.java | Contract | 6 tests |
| CrmConcurrencyContractTest.java | Contract | 4 tests |
| CrmIdempotencyContractTest.java | Contract | 5 tests |
| CrmModuleWiringTest.java | Architecture | 3 tests |
| Frontend vitest | Unit | 480 tests |
