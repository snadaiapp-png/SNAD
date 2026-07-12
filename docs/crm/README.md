# SNAD Global CRM Build Readiness

This directory is the controlled entry point for the SNAD CRM platform.

## Status

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED
PRODUCTION_AUTHORIZATION: PARTIAL — runtime deployed and smoke-passing; formal GO decision record still required for commercial launch.
```

> The previous `CRM_PRODUCT_BUILD: NOT STARTED` claim is **no longer accurate**
> and is retained only as a historical reference. CRM backend, frontend, and
> database code is merged on `main` at the baseline SHA recorded below. The
> drift-check script (`scripts/crm/governance-drift-check.sh`) fails any pull
> request that re-introduces the old status.

## Baseline reference

- **Baseline SHA:** `cee332e7f86a6ea64fbb5f72120ae77c441f6eac`
- **Branch:** `crm/001-baseline-governance-ci-recovery`
- **Authoritative baseline document:** [`CRM-CURRENT-BASELINE.md`](./CRM-CURRENT-BASELINE.md)
- **Authoritative execution roadmap:** [`CRM-ENTERPRISE-EXECUTION-ROADMAP.md`](./CRM-ENTERPRISE-EXECUTION-ROADMAP.md)

Where this README disagrees with `CRM-CURRENT-BASELINE.md`, the baseline
document wins.

## Current state at a glance

- **Backend:** 30+ CRM endpoints under `/api/v1/crm/*`, deployed on the
  self-hosted production server, exercised end-to-end by the authenticated
  two-tenant smoke workflow (`.github/workflows/crm-real-smoke.yml`).
- **Database:** 11 unified CRM core tables applied to Supabase PostgreSQL
  via `V20260702_1` / `V20260702_2` / `V20260702_3`. 18 active `CRM.*`
  capabilities. Production `FLYWAY_ENABLED=false`; migrations applied
  manually per `CRM-DEPLOYMENT-READINESS.md`.
- **Frontend:** CRM Command Center deployed to Vercel with 16 tabs.
  `overview` and `executionBoard` render real content; the remaining 14
  tabs render `CrmEmptyState` and are tracked as `NOT_STARTED` /
  `PARTIALLY_IMPLEMENTED` in the roadmap.
- **Tests:** 8 backend `@Test` methods across 4 CRM integration classes,
  1 frontend test file (`crm-interactions.test.tsx`), no E2E, no Flyway
  history assertion test. Gaps tracked in
  `CRM-ENTERPRISE-EXECUTION-ROADMAP.md`.
- **CI/CD:** 3 CRM-specific workflows exist
  (`crm-deployment-readiness.yml`, `crm-real-smoke.yml`,
  `crm-web-lint-diagnostics.yml`) but none are verified as required status
  checks; no CRM-specific job in `ci.yml`; Issue #189 not referenced
  anywhere.

## Authoritative documents

1. [`CRM-CURRENT-BASELINE.md`](./CRM-CURRENT-BASELINE.md) — as-built
   baseline. **Supersedes any older `NOT STARTED` claim.** The drift-check
   script consumes this file.
2. [`CRM-ENTERPRISE-EXECUTION-ROADMAP.md`](./CRM-ENTERPRISE-EXECUTION-ROADMAP.md)
   — forward execution plan with `CRM-G0` … `CRM-G8` milestones and
   `EXEC-PROMPT-CRM-001` … `EXEC-PROMPT-CRM-034` prompts.
3. `CRM-GLOBAL-BUILD-REFERENCE.md` — product scope, principles, global
   requirements, and acceptance model.
4. `CRM-DOMAIN-AND-SERVICE-BOUNDARIES.md` — bounded contexts, modules,
   ownership, dependencies, and integration rules.
5. `CRM-DATA-API-EVENT-CONTRACT.md` — canonical data concepts, API rules,
   events, audit, privacy, and localization contracts.
6. `CRM-MVP-EXECUTION-BACKLOG.md` — execution-grade MVP epics, features,
   stories, dependencies, estimates, and sprint sequence. (Pending refresh
   under `EXEC-PROMPT-CRM-002`.)
7. `CRM-READINESS-GATE.md` — mandatory conditions before CRM implementation
   starts.
8. `CRM-DEPLOYMENT-READINESS.md` — runtime configuration and verification
   contract for production-like deployment.
9. `CRM-UI-LICENSE-REVIEW.md` — third-party UI dependency license review.
10. `CRM-TEST-AND-QUALITY-PLAN.md` — test strategy and quality gates.
11. `CRM-REVIEW-CHECKLIST.md` — code-review checklist for CRM changes.
12. `CRM-INTEGRATION-WORKLOG-20260702.md` — historical integration log.
13. `CRM-BUILD-DECISION.md` — original decision record for the CRM build.

Top-level rollups (outside this directory):

- [`docs/crm-gap-analysis.md`](../crm-gap-analysis.md) — environment and
  feature gap matrix.
- [`docs/crm-readiness-assessment.md`](../crm-readiness-assessment.md) —
  per-component readiness assessment.

## Governing project references

- `CONSTITUTION.md`
- `docs/system/SNAD-SYSTEM-REFERENCE.md`
- `docs/governance/CURRENT-IMPLEMENTATION-STATUS.md`
- `docs/governance/OWNER-AUTHORITY-MODEL.md`
- `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md`
- `docs/executor-23/README.md`
- architecture decisions under `docs/architecture/adr/`
- `docs/release/OWNER-PRODUCTION-GO-CHECKLIST.md`
- Development Gate Issue #101
- Build Readiness Issue #184
- Platform Core Sprint Issue #185
- Credential Incident Issue #173
- CRM CI/CD Issue #189

## Non-negotiable principles

- Multi-Tenant SaaS.
- Arabic-first and globally localizable.
- API-first.
- Workflow-first.
- AI-ready without bypassing human or policy controls.
- Security by design and zero implicit trust.
- Centralized audit and observability.
- Modular service-oriented implementation; no premature microservice
  fragmentation.
- Tenant isolation at data, authorization, cache, event, search, and
  analytics boundaries.

## Authorization boundary

The CRM runtime is deployed and smoke-passing on the self-hosted production
backend. **Commercial go-live is not authorized.** A formal production GO
decision record at `docs/release/CRM-PRODUCTION-GO.md`, signed by the project
owner and the single external approver, is required before any commercial
launch claim. The drift-check script fails any commercial go-live claim that
lacks that record.

## Governance drift enforcement

Run `bash scripts/crm/governance-drift-check.sh` from the repository root
before merging any pull request that touches CRM paths. The script is invoked
automatically by `.github/workflows/crm-deployment-readiness.yml` on every
pull request against `main`.
