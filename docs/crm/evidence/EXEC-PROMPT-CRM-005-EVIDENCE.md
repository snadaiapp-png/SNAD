# EXEC-PROMPT-CRM-005 — Evidence Ledger

## Governance state

```text
PROMPT: EXEC-PROMPT-CRM-005
TITLE: Enterprise Account and Customer Master
BRANCH: crm/005-enterprise-account-master
PR: #514
STATUS: IN PROGRESS
GATE: CRM-G3 OPEN
MERGE: PROHIBITED
NEXT PROMPT: NOT AUTHORIZED
```

## Baseline

- CRM-004 final merge: `aa4fa037fcf24ae5fa65a31a99a8ca5cd18cdde8`
- CRM-005 branch starting main recorded in PR: `920fd484fed2ab605333d63651da0cb75991dc30`
- Main advanced after branch creation; final verification must run on the exact merge candidate with the current base.

## Implemented evidence

### Data model

- `V20260716_5__expand_enterprise_account_master.sql`
- Tenant-scoped enterprise profile
- Configurable classifications and segments
- Relationship model with lifecycle
- Provider/system-scoped external identifiers
- Status and ownership histories
- Projection snapshot contracts
- Nine granular RBAC capabilities
- Existing account backfill

### Architecture

- `AccountMasterRepository` domain port
- `AccountMasterUseCases` transaction/application boundary
- `JdbcAccountMasterRepository` infrastructure adapter
- No source-domain types in the domain port
- ArchUnit rule preventing Account Master dependencies on ERP, Accounting, Ecommerce and Customer Service implementations

### API

- Typed Account Master, risk, relationship, identifier, history, projection and taxonomy endpoints
- Strong ETag/If-Match concurrency for profile, risk and relationship state transitions
- Tenant context required on every endpoint
- Granular capability annotations

### UI

- Enterprise fields added to Account create and list
- Customer 360 converted to a nine-tab workspace
- Editable profile and risk sections
- Relationship and external identifier management
- Honest projection states with no synthetic values
- Arabic and English visible copy

### Tests added

- `AccountMasterUseCasesIntegrationTest`
- `AccountMasterHttpIntegrationTest`
- Extended `CrmArchitectureTest`

Tests cover:

- profile creation and optimistic concurrency;
- taxonomy references;
- hierarchy cycle prevention;
- external identifier uniqueness and tenant scope;
- status and ownership history;
- cross-tenant denial;
- migration table/capability presence;
- HTTP ETag requirements;
- granular RBAC;
- relationship and identifier HTTP mutations;
- honest `NOT_CONNECTED` projection contracts.

## Verification matrix

The following results must be populated from the **same final Head SHA** before closure:

```text
FINAL_HEAD_SHA: PENDING
CURRENT_MAIN_BASE_SHA: PENDING
MERGE_CANDIDATE_SHA: PENDING

Compile Diagnostics: PENDING
Maven Test Suite: PENDING
PostgreSQL/Testcontainers: PENDING
Flyway migration/upgrade: PENDING
CRM Architecture: PENDING
CRM API Contract: PENDING
CRM Authenticated Acceptance: PENDING
Web lint: PENDING
Web unit tests: PENDING
Web production build: PENDING
Playwright E2E/visual: PENDING
Security Baseline: PENDING
OWASP: PENDING
Backup/Restore: PENDING
Performance Baseline: PENDING

Surefire actual testcase elements: PENDING
Failures: PENDING
Errors: PENDING
Skipped: PENDING
```

## Acceptance decision

```text
EXEC-PROMPT-CRM-005: NOT YET ACCEPTED
CRM-G3: OPEN
PR #514: DRAFT / MERGE PROHIBITED
UNRESOLVED BLOCKERS: VERIFICATION PENDING
```

No PR description, commit message or partial workflow run is acceptance evidence.
