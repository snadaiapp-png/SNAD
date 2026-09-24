# Senior Management Governance Completion Design

## Status
Approved design for the next Senior Management completion mission. The immutable implementation reference is `v20260815.3` (`708bc0cbabf0e94ad46a0b6a5cc7192e932243ce`). Render remains deferred and is out of scope.

## Goal
Complete the Senior Management governance layer so it can operate over the certified CRM and Finance modules and provide a reusable governance contract for future modules such as ERP, HRM, POS, ECOMMERCE_CX and later modules.

## Scope
### P0-A: Finance Executive Integration
- Add a read-only `FinanceManagementIntegrationService` under the management application layer.
- Query existing tenant-scoped Finance tables; do not create duplicate Finance tables or business logic.
- Add `GET /api/v1/management/finance/overview` protected by the existing `EXECUTIVE_COMMAND_CENTER.VIEW` capability.
- Expose executive finance metrics: invoice totals/status distribution, invoice value, paid amount, outstanding amount, payment totals/status distribution, and collected revenue.
- Use existing Finance schema only: `finance_invoices` and `finance_payments` are the primary executive aggregation sources.
- Preserve tenant isolation on every query.

### P0-B: Module Registry Governance
- Add a `ModuleGovernanceService` under the management application layer.
- Read the existing module registry and capability/entitlement structures; do not replace the existing Registry controller or create a second module registry.
- Add `GET /api/v1/management/modules/status` protected by `EXECUTIVE_COMMAND_CENTER.VIEW`.
- Return an executive governance projection containing module identity, enabled/registry status, capabilities, and governance/readiness state.
- Keep Registry/Entitlement APIs separate from the executive governance projection.

## Future Module Governance Contract
The governance projection establishes a stable contract for every future module. A module is considered governance-ready only when its registry identity, capabilities, health/readiness metadata, KPI exposure, alert exposure, tenant/security requirements, and CI certification requirements are satisfied.

The first implementation phase uses registry-derived readiness and does not introduce dynamic plugin discovery, an event bus, or automatic runtime mutation. Future automation may consume this contract without redesigning the management layer.

## Command Center Integration
`ExecutiveCommandCenterService` remains the executive aggregation boundary. CRM and Finance integrations remain isolated read-only services. Module Governance remains a dedicated service. The Command Center may compose these projections without duplicating module business logic.

Target dependency flow:

Management Command Center -> CRM Integration
                         -> Finance Integration
                         -> Module Governance

## Security
- All cross-module queries are tenant-scoped.
- Existing RLS remains authoritative for persisted data.
- All new management endpoints require `EXECUTIVE_COMMAND_CENTER.VIEW`.
- No new capability is introduced unless implementation evidence proves the existing capability insufficient.
- No mutation endpoint is added to the integration services.

## Testing
Add focused integration coverage for:
- Finance aggregation correctness.
- Finance revenue/outstanding calculations.
- Finance tenant isolation.
- Module governance projection correctness.
- Module capability visibility.
- Module governance tenant/security boundaries where applicable.
- API capability enforcement.
- Command Center composition where changed.

Run the full backend/CRM test suites through CI. Certification requires zero failures, zero errors, and zero skipped tests.

## Migration Policy
No new database migration is required for P0-A or P0-B. Existing Finance and Module Registry schemas are reused.

## Certification
Do not create the next baseline until CI is fully green and repository state is clean. The target certification tag is:

`v20260815.4-senior-management-governance-complete-certification`

The following certified baselines must remain intact:
- `v20260814.8` AI
- `v20260815.1` Finance
- `v20260815.2` Analytics
- `v20260815.3` Senior Management CRM Integration

## Explicit Exclusions
- No Render changes, upgrades, redeployments, or diagnostics.
- No ERP/HRM/POS implementation in this mission.
- No Analytics runtime implementation in this mission; only the governance contract needed for future integration.
- No broad refactor unrelated to Finance integration or governance completion.
