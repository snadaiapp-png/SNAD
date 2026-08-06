# CRM-003R Corrective Implementation Summary

The corrective branch replaces the CRM v2 validation-only cursor path with a
tenant-scoped PostgreSQL keyset path for every collection operation that
advertises a cursor. Cursor state is bound to endpoint, sort, direction, tenant,
and normalized filters. Database queries use deterministic sort plus UUID
tie-breaker ordering with a one-row lookahead.

Runtime OpenAPI idempotency requiredness is also reconciled, and a dedicated
PostgreSQL 16 acceptance workflow prohibits skipped or zero-test success.

Final closure remains withheld until exact-head evidence is populated.
