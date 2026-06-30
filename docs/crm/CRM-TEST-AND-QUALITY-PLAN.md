# CRM Test and Quality Plan

## Purpose

Define the minimum quality evidence required for SNAD CRM from the first implementation sprint.

## Test layers

### Domain tests

- aggregate invariants;
- lifecycle transitions;
- currency and date rules;
- lead-conversion idempotency;
- opportunity outcome validation;
- merge and duplicate rules.

### Application tests

- use-case orchestration;
- capability checks;
- audit publication;
- workflow and AI integration fallbacks;
- event publication and idempotency;
- optimistic-concurrency conflicts.

### API tests

- request validation;
- stable error codes;
- cursor pagination and sorting;
- tenant isolation;
- authorization and field-level access;
- idempotency keys;
- concurrency preconditions.

### Persistence and migration tests

- migrations on empty and representative databases;
- constraints and indexes;
- tenant-scoped uniqueness;
- rollback or forward-recovery procedure;
- PostgreSQL integration through Testcontainers or approved equivalent.

### Event and contract tests

- schema validation;
- backward compatibility;
- tenant and correlation metadata;
- replay safety;
- consumer idempotency;
- minimal personal-data payload.

### Frontend tests

- Arabic RTL and English LTR;
- authenticated and unauthorized routes;
- capability-aware actions;
- account, contact, lead, opportunity, activity, and pipeline flows;
- keyboard navigation and screen-reader semantics;
- responsive layouts;
- safe localized error mapping.

### Security tests

- cross-tenant read and write rejection;
- privilege escalation rejection;
- object-identifier enumeration resistance;
- field-level restriction;
- export and bulk-action authorization;
- injection and unsafe filtering;
- log and audit redaction;
- rate-limit behavior for search, import, and bulk actions.

### Privacy tests

- consent change history;
- retention and deletion workflow integration;
- subject export authorization;
- legal hold and deletion restriction;
- search/read-model removal propagation;
- sensitive custom-field handling.

### Performance tests

- indexed account/contact search;
- opportunity pipeline board;
- Customer 360 projection;
- large-tenant pagination;
- bounded asynchronous import;
- duplicate-detection workload;
- event-outbox processing.

## Release-blocking paths

The following are release-blocking for every CRM increment:

- tenant isolation;
- capability authorization;
- PostgreSQL migration validation;
- Arabic and English critical user flows;
- API and event compatibility;
- no unresolved Critical or High security finding;
- audit evidence for high-risk operations;
- recovery instructions for migration and deployment changes.

## Test data

- generated and non-production only;
- at least two tenants with overlapping human-readable identifiers;
- users with distinct roles and capabilities;
- Arabic and English customer records;
- multiple currencies, locales, time zones, and address formats;
- duplicate and near-duplicate records;
- large but bounded datasets for pagination and import tests.

## Evidence package

Each CRM Pull Request must link:

- exact commit SHA;
- test commands and results;
- changed migrations and recovery notes;
- tenant-isolation evidence;
- authorization evidence;
- API/event contract results;
- UI localization and accessibility results where applicable;
- security findings and disposition;
- known risks and technical debt.

## Quality gate

```text
CRM QUALITY GATE: NOT ACTIVE UNTIL CRM BUILD GO
CURRENT ACTION: TEST DESIGN PREPARED
```
