# G7 Mobile Offline Foundation — Correctness Hardening Design

Date: 2026-08-19
Branch: `g7-correctness-hardening`
Status: implementation baseline

## Objective

Correct G7 without rebuilding it. The release invariant is **zero silent data loss** across offline mutation, push, pull, conflict, retry, pagination and multi-device use.

## Repository findings that govern this correction

1. `PullSyncService` uses row-local `sync_version` as a pagination cursor. Equal versions across multiple rows can be skipped permanently when a page boundary is crossed.
2. `PushSyncService` nests dynamic update parameters inside an `Object[]`, so JDBC bindings do not match placeholders.
3. Push payload fields are stringified and the mobile aliases do not match the canonical CRM schema (`name` vs `display_name`, `first_name` vs `given_name`, etc.).
4. A single outer push transaction catches per-mutation exceptions, which does not provide PostgreSQL failure isolation after a statement error.
5. `crm_activities` receives sync columns but no sync-version trigger.
6. No global server-side change feed/tombstone source exists.
7. Mobile multi-page pull persists `nextCursor` but does not advance the in-memory cursor in the current loop.
8. Mobile encryption is applied on the network boundary instead of the persistence boundary; mutation/conflict payloads are stored plaintext.
9. Device registry tables exist but no complete registration/heartbeat lifecycle is wired into the client sync start.
10. Conflict skip uses a resolution-like value for a status transition. Deferral must not be written into the `resolution` column.

## Corrected architecture

### Cursor and change tracking

`sync_version` remains the **per-row optimistic concurrency version** only.

A new `mobile_change_log.change_id BIGSERIAL` is the **global monotonically increasing delta cursor**. Every syncable CRM insert/update/archive produces one change record with:

- tenant_id
- entity_type
- entity_id
- operation (`CREATE|UPDATE|DELETE`)
- entity_version
- canonical payload snapshot
- changed_at

Pull queries by `(tenant_id, entity_type, change_id > cursor)` ordered by `change_id` and requests `limit + 1`. The response cursor is the last returned `change_id`, never an entity version.

Existing CRM rows are bootstrapped into the change log during the forward migration, including rows whose `sync_version` is `0`, so first sync cannot omit legacy data.

### Push transaction model

The batch orchestrator has no encompassing database transaction. Each mutation executes through a separate Spring bean with `PROPAGATION_REQUIRES_NEW`. Unexpected SQL/runtime failures therefore roll back only that mutation and cannot poison the transaction used by later mutations.

Expected version conflicts return a per-mutation conflict result and commit their idempotency/conflict metadata.

### Mobile/CRM schema boundary

The existing mobile SQLite contract is preserved for backward compatibility. A server-side schema adapter translates legacy mobile names to canonical CRM columns and translates canonical change snapshots back to the existing mobile shape.

Canonical database values are bound using their JSON scalar type; numeric and boolean values are never converted to strings.

### Deletion semantics

Mobile DELETE maps to the canonical CRM archival lifecycle rather than introducing a hidden `deleted_at` state that the rest of CRM would ignore. Change-log triggers emit tombstones for canonical archive transitions. For entity types whose business lifecycle does not expose a dedicated archive marker, the mobile delete executor records the tombstone explicitly after the canonical state transition.

### Encryption boundary

Network requests carry application plaintext over authenticated TLS. Offline persistence encrypts sensitive entity fields at write time and decrypts them transparently at read time. Durable mutation/conflict payloads are encrypted before SQLite persistence and decrypted before application/network use.

### Device lifecycle

A mobile device must be registered for the authenticated tenant/user before sync. Sync start registers/upserts the device; heartbeat/last-sync updates keep the registry operational. Cross-tenant device reuse is rejected by tenant scoping/RLS.

### Idempotency

Mobile mutation idempotency uses a dedicated tenant-scoped table with a unique `(tenant_id, idempotency_hash)` key. A mutation claims the key inside its own transaction before changing CRM state; a rollback also rolls back the claim. Generic CRM HTTP idempotency remains separate because it has a different request scope.

## Acceptance invariants

- More rows sharing one entity version than the page limit cannot cause data loss.
- Bootstrap includes pre-G7 rows at version zero.
- DELETE/archive reaches clients as a tombstone.
- Every UPDATE binds flat, typed JDBC arguments to canonical columns.
- One failing mutation cannot roll back or block a sibling mutation.
- UPDATE/DELETE require an expected version.
- Duplicate idempotency keys cannot apply the same mutation twice.
- Activity versioning is consistent with the other six entities.
- Pull loop advances cursor in memory and detects a non-advancing server cursor.
- Sensitive offline data is encrypted at rest, not sent to CRM as device ciphertext.
- Conflict deferral changes status only; resolution remains null until resolved.
- All new mobile metadata tables are tenant isolated with ENABLE + FORCE RLS.

## Stop condition

Implementation may create commits and a pull request and run CI/runtime verification. It must stop before merging into `main` and before any Render/Vercel/production deployment.
