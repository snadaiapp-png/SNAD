# SANAD — Development to Commercial Release: Execution Register

**Working baseline:** `main@f9d827342d22c5995d0d3355efa78be48c77fff7` (2026-11-15)  
**Policy:** [Development-First Delivery](../governance/SANAD-DEVELOPMENT-FIRST-DELIVERY-2026-11-15.md)  
**This is an execution plan and evidence index, NOT a certification of current commercial readiness.**

## Evidence and source of truth

Relevant project references to reconcile: [Master execution manifest](../governance/MASTER-EXECUTION-MANIFEST.md) (2026-08 historic snapshot); [Stage-08 master backlog](../stage-08/backlog/STAGE-08-MASTER-BACKLOG.md); [CRM MVP backlog](../crm/CRM-MVP-EXECUTION-BACKLOG.md); [Commercial launch checklist](../production-readiness/go-live-checklist.md) (historic planning baseline, not proof of AWS deployment); [Security Hold #1462](https://github.com/snadaiapp-png/SNAD/issues/1462); [CAB #1528](https://github.com/snadaiapp-png/SNAD/issues/1528); [Recovery #1530](https://github.com/snadaiapp-png/SNAD/issues/1530).

A historic report marked DONE/GO is not evidence of present production security or integration; independently reconcile its source SHA, actual tests, environment and artifacts.

## Module tracks and current release status

| Track | Scope | Required acceptance evidence | Status at creation |
|---|---|---|---|
| A | SaaS Core, IAM, multi-tenancy, subscriptions | Provisioning, negative auth, A/B tenant access, lifecycle | UNVERIFIED for commercial release |
| B | ERP, inventory, procurement, operations | DB/API/UI E2E, permissioned workflows, consistency | UNVERIFIED |
| C | CRM and customer lifecycle | Lead/customer/pipeline activity E2E and isolation | UNVERIFIED |
| D | HRM, employee/manager/HR roles | RBAC and authenticated 3-role UAT | UNVERIFIED |
| E | Accounting, billing and taxation | Double-entry, reversal, ledger and reconciliation | UNVERIFIED |
| F | Workflow and AI Platform | Idempotency, approvals, scoped data/tool access, audit | UNVERIFIED |
| G | Ecommerce, POS and industry solutions | Orders, inventory, sandbox checkout, secure operations | UNVERIFIED |
| H | Security, QA, DevOps, reliability and monitoring | CI, C4 risk disposition, migration drift, restore, observability | PARTIAL; production hold active |
| I | Commercial launch, real customer payments | Operational acceptance and explicitly authorized GO | BLOCKED |

UNVERIFIED is an inventory status, not a claim of failure or lack of implementation.

## Three-month development window

**Proposed period:** 2026-11-15 through 2027-02-15 inclusive, subject to actual governance PR adoption and owner oversight.

- Development, ephemeral CI, previews, and staging with **synthetic non-customer data** are permitted subject to actual branch protection, passing required CI and environment-specific controls.
- **Payment sandbox/test mode** is permitted only with verified test keys/accounts, clear UI labeling and controls preventing real fund movement, settlement, external charges or customer-facing sales.
- No automatic expiration can change a production safety determination. A temporary calendar waiver **does not suspend** tenant isolation, auth/ACL/RLS, privacy, rollback, customer-data protection or relevant compliance duties.
- **Real production remains NO-GO**, and **live payment processing remains OFF**, until a separate exact-SHA authorization backed by actual security, recovery and operational test evidence. A limited pilot can be proposed in a separate bounded approval only after its controls and risks are validated; no automatic blanket GO.
- At 2027-02-15, reassess module inventory, unresolved vulnerabilities, technical readiness, pilot eligibility and launch evidence; renew development planning explicitly if needed. Do not pretend a background review has been scheduled.

## Delivery and verification sequence

1. Reconcile every module against source paths, backlog issues, tests and evidence and assign explicit owner and acceptance criteria.
2. Continue implementation through protected PRs without stand-alone per-module administrative certificate/CAB; required checks and targeted security tests remain mandatory.
3. Test modules A–G with synthetic staging transactions and payment sandbox; record PASS/FAIL/BLOCKED/UNVERIFIED per exact SHA.
4. In parallel, technically close C4 (12 SECURITY DEFINER signatures, 26 RLS-OFF exceptions, default ACL, trusted caller tenant binding), and resolve actual failures.
5. Obtain **scoped authorization** and protected full-scope production-derived recovery proof before any customer-data copying; test Flyway and isolated A/B behavior; measure RPO/RTO.
6. Verify candidate source, production configuration and deployed digest; run real authorized customer-facing UAT, security checks, operational runbooks and rollback.
7. Record exact-SHA CAB/owner GO or HOLD with evidence and a **separate** live-payments release decision. No unconditional 3-month waiver.
8. Preserve auditable logs and date-scoped findings; use final commercial acceptance rather than paperwork-only completion.

## Standard evidence entry

`module | requirement | source_sha | environment | test_run/artifact | positive/negative scenarios | PASS/FAIL/SKIPPED/BLOCKED/UNVERIFIED | finding | owner | remediation | verified_at`.

Do not claim project completion, payment readiness or production GO solely because this register or its policy PR merges.
