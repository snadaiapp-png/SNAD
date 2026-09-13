# R0C13 R13-G07 — Re-anchor Addendum

- **Date:** 2026-09-13
- **Repository:** `snadaiapp-png/SNAD`
- **Design:** `docs/superpowers/specs/2026-09-13-r0c13-g07-rbac-api-operator-ui-design.md`
- **Previous design baseline:** `a7fd5c0428ea6c774ff52e564cd791f9d1faf3a3`
- **Current protected main:** `5e6c9c0139b096fe2dda8ff14d3be3ae06142a19`
- **Re-anchor merge commit:** `dac4fe9c0ea30ebe2393d63ae340ac5d88b84709`
- **Implementation status:** NOT STARTED
- **Live payment collection:** FORBIDDEN

## Drift classification

The protected-main delta from `a7fd5c0428ea6c774ff52e564cd791f9d1faf3a3` to `5e6c9c0139b096fe2dda8ff14d3be3ae06142a19` is exactly one commit and changes only:

- `apps/sanad-platform/.release/workflow-y2-production-release-authorized.md`
- `apps/web/e2e/workflow-y2-release.spec.ts`
- `docs/certification/WORKFLOW-FIC-FINAL-EVIDENCE-CORRECTED.md`

No Billing/Finance/provider/RBAC/operator-API source, billing migration, billing acceptance matrix, Billing Operations page, i18n locale source, or R13-G07 authoritative scope artifact is changed by this delta.

```text
G07_REANCHOR_BASELINE = 5e6c9c0139b096fe2dda8ff14d3be3ae06142a19
G07_BRANCH_REANCHOR   = dac4fe9c0ea30ebe2393d63ae340ac5d88b84709
MAIN_DRIFT_COMMITS    = 1
R0C13_SCOPE_CONFLICT  = NONE_FOUND
RELEASE_E2E_OVERLAP   = YES
REQUIRED_RESPONSE     = KEEP_RELEASE_E2E_IN_G07_WEB_REGRESSION
PRODUCTION_MUTATION   = 0
LIVE_PAYMENT_COLLECTION = FORBIDDEN
```

## Governing effect

The existing G07 design remains semantically unchanged. This addendum supersedes only its repository-baseline statement.

All approved decisions remain binding:

- internal workstream order: RBAC/Capabilities -> Operator API -> Billing Operations UI -> integrated G07 acceptance;
- no new formal `G07.1/G07.2` gates;
- legacy-compatible behavior without privilege expansion;
- explicit tenant path for every new operator API;
- existing `AccessCheckV2` extended with exact backend-authoritative `BILLING.*` capabilities;
- initial grants limited to control-plane `ADMIN`; no legacy mirroring;
- only `PROVIDER_PAID_FINANCE_PENDING` may be repaired; every other discrepancy is report-only;
- full refunds only, and provider `ACCEPTED` does not mean Finance/local `REFUNDED`;
- provider readiness diagnostics are passive and zero-side-effect;
- routed Billing Operations workspace under `/executive/billing` and `/executive/tenants/{tenantId}/billing`;
- PostgreSQL Direct / host-native only for governed database acceptance;
- Docker/Testcontainers forbidden;
- LIVE payment collection remains outside R13-G07 authority.

## Re-anchor acceptance

Before implementation begins:

```text
BRANCH_CONTAINS_CURRENT_MAIN = REQUIRED
DESIGN_SCOPE_CONFLICT        = NONE
WRITTEN_SPEC_USER_REVIEW     = REQUIRED
IMPLEMENTATION_PLAN          = NOT_STARTED
CODE_IMPLEMENTATION          = NOT_STARTED
```

If protected `main` moves again, implementation stops and this same exact-SHA drift classification is repeated before further mutation.
