# HRM G2 Canonical Remediation — Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remediate PR #1133 so authenticated G2 HR flows use the canonical SANAD authorization transport and identity model, enforce SELF/TEAM/HR scope boundaries server-side, preserve tenant/RLS isolation, and produce deterministic exact-head certification evidence.

**Architecture:** Extend the existing SANAD auth stack rather than create a parallel mechanism. Browser code must route authenticated G2 traffic through the shared `apiClient`, while backend APIs derive or validate employee scope from the authenticated principal and expose scope-specific contracts for self, team, and HR administration. PostgreSQL Direct + FORCE RLS remain the final data-isolation boundary.

**Tech Stack:** Next.js/React/TypeScript, shared `apiClient`, Spring Boot/Java, PostgreSQL/Flyway/RLS, Workflow Engine, Playwright, Maven, Vitest.

**Specs:**
- `docs/superpowers/specs/2026-09-23-hrm-g2-time-attendance-leave-design.md`
- `docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md`

**Baseline:** `8d0d49c7bdd3ad3a886a23cffc1e735e61998712`

**Observed pre-remediation head:** `992f987cab330885a23e8310055b0432394b1925`

## Global Constraints

- Preserve tenant isolation: no role, override, policy, partner delegation, or scope may expand access beyond tenant or partner boundaries.
- Preserve current `CapabilityEvaluationService`, `@RequireCapability`, RLS, Workflow Engine, and Subscription Control Plane foundations; do not create competing authorization systems.
- Missing/invalid authorization context, tenant mismatch, unresolved scope, or missing entitlement fails closed.
- PostgreSQL Direct is the canonical application DB verification path; Docker/Testcontainers remain out of scope.
- SELF operations must not trust client-supplied `employmentId` as proof of ownership.
- TEAM access must be restricted to relationship-authorized employees/direct reports; HR access must be explicit and tenant-scoped.
- Existing capability annotations and API compatibility are preserved where safe; incompatible G2-only contracts may be replaced before merge because PR #1133 is not yet merged.
- No weakening of RLS, no `BYPASSRLS`, no superuser test shortcuts, no disabling architecture tests.
- No merge until fresh exact-head CI, authenticated desktop/mobile evidence, PostgreSQL/RLS verification, OpenAPI sync, closure evidence, and required approval are all green.

## Repository Forensics Summary

At `992f987c`, the G2 browser pages call `/api/platform/api/v2/hr/...` with raw `fetch()` while the established auth provider stores the bearer token only inside the shared `apiClient`. This produces authenticated-login success followed by API 401s because the bearer token is absent from G2 page requests. The current G2 capability model is also internally inconsistent: several pages use legacy/non-canonical capability names, manager/HR pages call endpoints protected by SELF capabilities, and SELF mutations send `me.id` or caller-supplied `employmentId` where the backend should bind the HR employee identity from the authenticated principal. Leave queue code sends `state=PENDING` although the service performs exact state comparison against canonical states such as `PENDING_MANAGER` and `PENDING_HR`. The Playwright suite additionally contains selector and transport workarounds that mask product-contract defects rather than assert stable UI/API contracts.

## Wave Sequence

1. **Authenticated Frontend Transport** — introduce a focused G2 API facade over the shared `apiClient`; remove authenticated raw `fetch()` from G2 pages and acceptance helpers.
2. **Capability & Scoped API Contracts** — align frontend capability names and backend annotations/routes with SELF/TEAM/HR scope semantics.
3. **Principal-to-Employment Binding & Isolation** — derive SELF employment identity server-side, validate team relationships, and add tenant/RLS negative tests.
4. **Leave Lifecycle & Workflow Contract** — make DRAFT→SUBMIT explicit, fix approval queue semantics, and preserve Workflow Engine as the approval owner.
5. **UI State & Navigation Contracts** — make page titles, buttons, loading states, and role-visible surfaces deterministic and capability-correct.
6. **Authenticated E2E Hardening** — rewrite Playwright around stable selectors and real product flows; remove `networkidle`, raw `page.evaluate(fetch)`, and soft workarounds.
7. **Targeted Verification & Certification** — run targeted frontend/backend/PostgreSQL tests first, then fresh exact-head CI and evidence reconciliation.

## Review Focus

- A valid authenticated browser session whose access token exists only in memory must still authorize every G2 API call through the canonical client.
- A user with SELF capability must never read or mutate another employee by supplying another `employmentId` inside the same tenant.
- A manager must see only relationship-authorized team data; TEAM capability must not imply tenant-wide HR visibility.
- A leave request must not become manager-approvable until the explicit submit transition has created/bound the canonical Workflow instance.
- Missing tenant context or cross-tenant identifiers must remain fail-closed under PostgreSQL FORCE RLS and service-level scope validation.

## Definition of Done

`G2_CANONICAL_REMEDIATION=PASS` requires all seven waves complete on one fresh branch head, targeted tests green, G2 authenticated acceptance green without retries or soft skips, Maven/PostgreSQL/RLS/OpenAPI/architecture gates green, evidence docs updated to the exact SHA, and no stale approval carried across a changed head.

## Implementation Gate

This document and the seven wave plans are the required planning package before implementation. Product-code changes must not begin until the owner explicitly approves this `PLAN_READY` package.