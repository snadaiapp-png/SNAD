# SNAD Global CRM Build Readiness

This directory is the controlled entry point for the SNAD CRM platform.

## Status

```text
CRM_PRODUCT_BUILD: NOT STARTED
CRM_BUILD_READINESS: IN PREPARATION
PLATFORM_CORE_DEPENDENCY: ACTIVE
PRODUCTION_AUTHORIZATION: NO
```

## Authoritative documents

1. `CRM-GLOBAL-BUILD-REFERENCE.md` — product scope, principles, global requirements, and acceptance model.
2. `CRM-DOMAIN-AND-SERVICE-BOUNDARIES.md` — bounded contexts, modules, ownership, dependencies, and integration rules.
3. `CRM-DATA-API-EVENT-CONTRACT.md` — canonical data concepts, API rules, events, audit, privacy, and localization contracts.
4. `CRM-MVP-EXECUTION-BACKLOG.md` — execution-grade MVP epics, features, stories, dependencies, estimates, and sprint sequence.
5. `CRM-READINESS-GATE.md` — mandatory conditions before CRM implementation starts.

## Governing project references

- `CONSTITUTION.md`
- `docs/system/SNAD-SYSTEM-REFERENCE.md`
- `docs/governance/CURRENT-IMPLEMENTATION-STATUS.md`
- `docs/executor-23/README.md`
- architecture decisions under `docs/architecture/adr/`
- Development Gate Issue #101
- Build Readiness Issue #184
- Platform Core Sprint Issue #185
- Credential Incident Issue #173

## Non-negotiable principles

- Multi-Tenant SaaS.
- Arabic-first and globally localizable.
- API-first.
- Workflow-first.
- AI-ready without bypassing human or policy controls.
- Security by design and zero implicit trust.
- Centralized audit and observability.
- Modular service-oriented implementation; no premature microservice fragmentation.
- Tenant isolation at data, authorization, cache, event, search, and analytics boundaries.

## Current authorization boundary

The repository may prepare CRM architecture, contracts, backlog, testing strategy, and non-production Platform Core dependencies. CRM business implementation must not start until `CRM-READINESS-GATE.md` is satisfied and the project owner records an explicit GO decision.
