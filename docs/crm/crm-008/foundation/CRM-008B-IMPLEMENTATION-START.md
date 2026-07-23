# CRM-008B — Authorized Implementation Start

```text
AUTHORIZATION_ISSUE: #597
AUTHORIZATION_DATE: 2026-07-22
AUTHORIZED_BASE_SHA: 5f9b2cb88cfbc232e74f2fec026ca97d9a23885c
AUTHORIZED_BRANCH: feature/crm-008b-foundation-20260722
AUTHORIZED_SCOPE: CRM-008B_FOUNDATION_ONLY
AUTHORIZED_MIGRATIONS: V20260722_1..V20260722_9
FIRST_POST_AUTHORIZATION_COMMIT: THIS_COMMIT
CURRENT_WAVE: WP-01_MIGRATION_FOUNDATION
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```

## Active controls

- Sequential integer migrations only.
- Forward-only, exact-state, fail-closed migrations.
- PostgreSQL 16 Testcontainers before review.
- No Flyway repair or schema-history edit.
- No manual Production SQL.
- No tenant or capability bypass.
- No expected HTTP 500.
- No skip, timeout inflation, retry masking or `continue-on-error`.
- Protected merge requires one unchanged head and all 17 implementation criteria.

## Initial execution order

1. Reconfirm reserved migration range.
2. Implement `V20260722_1` through `V20260722_9` with preconditions and postconditions.
3. Add PostgreSQL clean/baseline/partial/concurrency tests.
4. Prove 14 tables, 40 indexes and 21 tenant/composite foreign keys.
5. Proceed to domain and persistence work only after migration contract is stable.

This file records branch start only. It is not implementation-completion evidence.