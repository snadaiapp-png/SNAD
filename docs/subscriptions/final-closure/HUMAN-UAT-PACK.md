# Human UAT Pack — Subscription Platform Closure (Issue #1055)

> **Status: NOT EXECUTED — awaiting the human operator (controller/user).**
> This session does not claim, and cannot claim, completion of Human UAT on anyone's behalf.
> Issue #1055 acceptance clause: *"Final closure is permitted only when all eight workstreams are merged, production-aligned, and the human-UAT scenarios are rerun successfully."*
> Historical reference: the original UAT screenshots were captured 2026-09-13; this pack re-derives the scenario set from the eight workstream requirement bullets of Issue #1055 so the re-run covers exactly what changed since.

## How to execute

1. Environment: a non-production staging environment with `PAYMENTS=OFF`, seeded acceptance data (the same isolated fixtures used by the automated suites may be reproduced per `.github/workflows/playwright-ci.yml` job `subscription-authenticated`).
2. Run each scenario below in the UI (branded Chrome, both `ar-rtl-light` and `en-ltr-dark` where the surface is bilingual).
3. For every scenario capture: screenshot of the final state, the request IDs/correlation visible in the UI (if any), timestamp, operator name.
4. Record PASS/FAIL per scenario in the table at the end. A FAIL re-opens the corresponding workstream — do not annotate around failures.

## WS1 — Tenant UI recovery

- **UAT-01 Modal focus:** open Create Tenant modal; type a 20-character name continuously. Expected: no character loss (modal focus regression fixed); labels visible; governed client-side validation messages on invalid input.
- **UAT-02 Selectors:** country / timezone / currency selectors render and persist a tenant.
- **UAT-03 Regression coverage handoff:** create then edit a tenant; both operations audited (check the audit trail entry).

## WS2 — Application catalog lifecycle

- **UAT-04 Catalog create/update:** create a catalog item; edit it. Expected: **no delete affordance anywhere**; deactivate/archive path is governed (confirmation + capability required).
- **UAT-05 Capability gating:** attempt a mutation with a role lacking the capability → blocked with a governed error (no silent no-op).

## WS3 — Subscription lifecycle integrity

- **UAT-06 No generic bypass:** on a subscription detail, attempt lifecycle action via any generic control if present — expected: fail-closed (automated proof: generic-route CANCEL returns 409; operator confirms no generic UI path remains).
- **UAT-07 State-aware commands:** walk ACTIVE → (governed CANCEL with reason) → CANCELLED; attempt ACTIVATE/RENEW where illegal per state → blocked; only authoritative provisioning/renewal paths operate.

## WS4 — Subscription navigation / RBAC

- **UAT-08 403 regression:** as EXECUTIVE_VIEW, open `subscriptions/v2?tenantId=…` → loads (no HTTP 403); as a tenant-scoped non-executive principal → denied per boundary.
- **UAT-09 Cross-tenant denial:** attempt access to another tenant's subscription → denied, no data leakage in the UI.

## WS5 — Billing / read-model correctness

- **UAT-10 Billing scoping:** billing view shows only the acting tenant's rows.
- **UAT-11 Pricing/currency:** seat-quantity pricing converges with plan version; currency pinned to plan version (no locale drift).
- **UAT-12 Period/timestamps:** usage period shows the current UTC period; timestamps render localized per active locale.

## WS6 — Tenant / domain provisioning

- **UAT-13 Default subdomain:** creating a tenant auto-creates its default subdomain from `SANAD_BASE_DOMAIN`.
- **UAT-14 Custom domain lifecycle:** verify → activate → set primary for a custom domain; global uniqueness enforced (duplicate hostname rejected).

## WS7 — Website / store monetization

- **UAT-15 Auto domains & multiplicity:** a tenant can hold multiple websites/stores, each with its auto-generated default domain.
- **UAT-16 Entitlement limits:** exceeding subscription add-on/entitlement limits is blocked with a governed message.

## WS8 — Branch / operating-unit subscriptions

- **UAT-17 Branch model:** create branch under tenant; branch-scoped applications/entitlements visible only in scope.
- **UAT-18 PER_BRANCH pricing & profiles:** PER_BRANCH pricing behavior; consolidated vs per-branch billing profiles render correctly.
- **UAT-19 Branch attribution:** usage attribution and RBAC behave branch-aware; sites/stores/POS link to the branch.

## Result table (fill during the run)

| Scenario | Result (PASS/FAIL) | Screenshot / evidence ref | Operator | Timestamp |
| --- | --- | --- | --- | --- |
| UAT-01 … UAT-19 | — | — | — | — |

Upon completion: attach the result table + screenshots to Issue #1055 and PR #1536; only then may the final closure decision (and any FULLY_CERTIFIED claim, which remains forbidden until all gates + authorizations complete) be considered by the controller.
