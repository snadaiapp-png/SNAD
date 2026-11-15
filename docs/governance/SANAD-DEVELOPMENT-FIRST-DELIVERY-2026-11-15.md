# SANAD — Development-First Governance and Commercial-Release Separation

**Decision ID:** DEC-GOV-DEV-FIRST-2026-11-15-001  
**Owner directive:** عبدالرحمن سنان, project owner / official developer (user-directed 2026-11-15)  
**Project GitHub account:** `snadaiapp-png`  
**Scope:** SNAD development lifecycle through planned module completion; not a production or customer-data approval  
**Baseline source SHA:** `f9d827342d22c5995d0d3355efa78be48c77fff7`  
**Adoption status:** PROPOSED on this branch until reviewed, required checks pass, and protected PR merges to `main`.

## 1. Decision

Adopt a **development-first, risk-based** delivery lane so implementation, test, documentation, integrated staging, module readiness, and ordinary PR merges need **no recurring stand-alone CAB decision, independent G1 certificate, signed declaration, or paperwork-only per-module approval**. Existing actual GitHub branch protections, CI requirements and test gates remain enforceable. A development merge is not a production authorization.

This decision complements [Canonical Continuous Security & Release Governance](SANAD-CONTINUOUS-SECURITY-RELEASE-GOVERNANCE-2026-11-14.md). It does not remove a risk-based specialist review where needed, nor override statutory, contractual, provider, or customer-data duties.

## 2. Exact operational lanes

| Lane | Allowed without CAB | Required gates | Never implied |
|---|---|---|---|
| LOCAL_DEV / disposable isolated DB | feature work, refactor, schema migrations against **disposable non-production data**, fixtures, tests | secure secret handling; required relevant unit/integration/security tests; no customer-data exposure | production database rights |
| CI / EPHEMERAL_PREVIEW | test builds, ephemeral demo data, PR review, workflow evidence | branch rules, exact-SHA CI; security negative/positive tests for affected auth/tenant/RLS/ACL/payments code | live commercial rollout |
| STAGING | staging deployments with synthetic data and **confirmed non-production credentials** | staging target identity proof, smoke/role tests, explicit staging operator authorization where environment rules require it | authorization to read/clone real customer backups |
| PRODUCTION / CUSTOMER DATA | **nothing authorized by this governance document alone** | exact-SHA owner GO, risk disposition, protected restore/rollback, DB drift check, security tests, controlled production deploy | release/payment GO by merging this PR |
| PAYMENTS | sandbox simulation only where sandbox identities/keys and provider terms allow | verified sandbox configuration and no real fund movements | live payment activation |

**Development unblock:** Treat G1 P0 / CAB / restore HOLD as a **production-only gate**. Continue non-production development in the ordinary merge lane when change-specific checks are green. A discovered critical vulnerability in the changed area may still block merge if policy and threat model require it; no general waiver of material security risk.

## 3. Module workflow

1. Every module uses named owners, linked backlog issues, definition-of-done, test evidence, and clear `IMPLEMENTED`, `TESTED`, `INTEGRATED`, `RELEASE_READY` states. Never mark commercial readiness from a green build alone.
2. Use existing protected PR checks; test auth/tenant/RLS/permissions positive and negative paths and cross-tenant isolation when affected.
3. Record actual failures as `FAIL`, skipped as `SKIPPED`, inaccessible systems as `BLOCKED`, and untested requirements as `UNVERIFIED`. Do not convert missing evidence into PASS.
4. Staging and previews may be used for integration with synthetic or separately authorized test data; do not copy production datasets without explicit data-controller / recovery scope approval.
5. Maintain release risk register continuously. Address critical/high security findings at discovery; **administrative review timing** may be deferred but **technical remediation, access controls, and legal obligations** may not.
6. At agreed product scope completion, freeze the candidate SHA and create a commercial release packet: exact digest and configuration, CI/security/test evidence, module acceptance, application flows, compatibility, production DB migrations, full-scope authorized restore, RPO/RTO, rollback, privacy/provider/contract checks and owner GO / NO-GO.
7. Payments require a distinct commercial authorization after integration and reconciliation evidence. Keep real customer money movement disabled until then.

## 4. Existing blockers remain correctly scoped

- [#1462](https://github.com/snadaiapp-png/SNAD/issues/1462): `G1_SECURITY_HOLD=ACTIVE` for production; C4 technical audit (12 SECURITY DEFINER functions, 26 RLS-OFF exceptions, default ACL and trusted caller boundaries) remains open. Does **not** prohibit ordinary safe development.
- [#1530](https://github.com/snadaiapp-png/SNAD/issues/1530) / [#1481](https://github.com/snadaiapp-png/SNAD/issues/1481): no production-derived backup, raw customer-data access or isolated full-scope restoration without scope-specific authorization.
- [#1528](https://github.com/snadaiapp-png/SNAD/issues/1528): CAB production GO not issued. No protected migration, Render/Vercel production deploy or payment activation on this decision.
- An unsigned owner-recovery draft or written signature string cannot substitute for independently verifiable authority, resource identity, signed operational scope, data-controller consent and actual restore evidence.

## 5. Release readiness gates (deferred in timing, NOT cancelled)

**Commercial GO requires all applicable and verified:** module scope freeze and acceptance; CI/PMV exact SHA; PostgreSQL RLS/ACL and tenant negative/positive tests; secure role/caller trust boundaries; resolved or explicitly controlled risk; source→production Flyway compatibility; independently reviewable protected backup/full-scope isolated restore or documented provider-specific limits; real smoke and end-user acceptance; incident response, observability, recovery and rollback; data processing/legal controls; named authorized production GO; separate live-payments enablement.

**Safety flags until such a GO actually occurs:**
`P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `CAB=NOT APPROVED` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.

**Nonproduction delivery state on protected merge:** `DEVELOPMENT=ALLOWED` subject to CI/branch protection, `MODULE_IMPLEMENTATION=ALLOWED`, `SYNTHETIC_STAGING=ALLOWED_WHEN_AUTHORIZED`.

## 6. Evidence of adoption and rollback

- Record PR review, exact SHA, required checks and merge SHA; no privileged workflow/environment setting is changed here.
- This decision is documentation-only. It does **not** change runtime feature flags, GitHub protected branch settings, secrets, permissions, Flyway, production environment, database policies or payment gateways.
- If administrative gate conflicts arise, link specific file/setting and propose a separate bounded change with tests; do not blindly disable checks.
- Rollback: superseding governance decision by owner through protected PR, retaining historical record.
