# EXEC-PROMPT-CRM-007 — Addresses and Communication Methods

## Status

```text
EXEC-PROMPT-CRM-007: VERIFICATION CANDIDATE
CRM-G3D: OPEN
STARTING_MAIN_SHA: 1c76e879d91d95e29f505250b445463f5ea7a6d4
BASE_MAIN_SHA: 8ef57062e3dc9aa18ff466f230470d62cf1a0a70
CONTRACT_SURFACE: 72 paths / 95 operations
```

## Entry gate

CRM-006 is merged through `d0fe9bdd2aec9f080450b509e9d1a53c9d0ec275`; the trusted `main` production rollout is READY and the required exact-head workflows passed. The later `/api/system/release` runtime regression is tracked independently in issue #545 and is not part of this domain stage.

## As-built discovery

- `crm_accounts.primary_email`, `crm_accounts.primary_phone`, and `crm_contacts.primary_email` / `primary_phone` are compatibility projections.
- CRM-005 introduced account-only `crm_account_addresses`; CRM-007 migrates those rows into a canonical owner-scoped address model while retaining the legacy table during the compatibility window.
- `crm_contacts.consent_summary` remains the existing consent projection; CRM-007 references it and does not create a parallel consent engine.
- Tenant identity is derived only from the authenticated context.
- Central `AuditPort` and `TimelineEventPort` remain authoritative.

## Implemented design

Canonical owner types are `ACCOUNT` and `PERSON`. Address and communication records preserve raw input, deterministic normalized values, lifecycle, versioning, verification and privacy metadata. Composite tenant/owner foreign-key enforcement is implemented through owner-specific constrained columns rather than trusting application filtering.

The implemented surface includes tenant-safe CRUD, ETag and idempotency protection, privacy masking, verification and lifecycle transitions, governed search/import/export, Audit/Timeline/History, CRM-005 compatibility projections, Arabic/English operational UI, deterministic OpenAPI generation, generated TypeScript contracts, PostgreSQL upgrade acceptance, and an additive rollback runbook.

## Exact-head candidate gate

This document update intentionally creates the single immutable candidate on which every required pull-request workflow must complete. No merge or production action is permitted if the branch head changes after the successful run set.

## Required evidence before closure

PostgreSQL clean-install and CRM-006 upgrade, legacy row-count preservation, Arabic data preservation, international phone normalization, tenant isolation, RBAC and masking, Audit, Timeline, rollback, OpenAPI/type drift zero, exact-head CI, expected-head merge, backend Production migration verification, Vercel Production verification on the merge SHA, production smoke tests, and runtime-error inspection.
