# FINAL CHANGELOG

## [sanad-commercial-20260807-19dd4e94] — 2026-08-07

### Security
- Added `capabilities` field to `AuthResponse` and `MeResponse` DTOs for immediate bootstrap
- Added `ProductionMockGuard` EnvironmentPostProcessor (3-layer defense against mock data in prod)
- Fixed `CrmContractControllerR1` missing from `CrmExceptionHandler` assignableTypes
- Granted CRM capabilities to MEMBER, VIEWER, MANAGER, SALES_MANAGER, SALES_REPRESENTATIVE roles
- All 19 write endpoints in `CrmContractControllerR1` protected by `@RequireCapability`

### Database
- `V20260807_1`: Grant CRM capabilities to non-admin roles
- `V20260807_2`: Seed default pipeline (5 stages) and sample accounts per tenant
- `V20260807_3`: Case-insensitive tag unique index (PostgreSQL vendor directory)
- `V20260807_4`: Add `result` column and `related_type` CHECK constraint to `crm_activities`

### Frontend
- Fixed React Rules of Hooks violation in `crm-shell.tsx`
- Added `capabilities` to `AuthResponse` interface and `authResponseToMe`
- Added `CrmStage.active` field and 6 missing fields to `CrmActivity`
- Added `updateActivity` frontend function
- Added capability utility functions and `CRM_CAPABILITIES` constants
- Pipeline/stage CRUD with capability-gated UI
- Activity, tag, case management pages
- Updated all test mocks to include capabilities

### Backend
- Pipeline/stage CRUD with domain/application/infrastructure layers
- Batch capability query in `RoleCapabilityRepository`
- `CrmDtoMapper` overloads for `StageRecord` and `ActivityRecord`
- `CrmUpdateDtos` for pipeline/stage/activity operations

### Bug Fixes
- Fixed missing CSS module in control-plane execution dashboard
- Fixed `CrmMapperContractTest` overloaded method ambiguity
