# CRM API Versioning Policy

**Branch:** `crm/003-stable-api-contracts`
**Gate:** CRM-G2 — API Contract and Concurrency Gate

This document defines what constitutes a breaking vs. non-breaking
change to the CRM API contract, the deprecation policy, and the support
window for each version.

## Current versions

- **v1** (`/api/v1/crm/...`) — frozen at CRM-G1 closure (PR #501 / SHA `89761eb9`).
  Returns untyped `Map<String, Object>` responses. No ETag, no If-Match,
  no Idempotency-Key, no cursor pagination, no standard error envelope.
  **Status:** BACKWARD-COMPATIBLE. Will be maintained until the frontend
  has fully migrated to v2 plus one full release cycle (target: CRM-G3).
- **v2** (`/api/v2/crm/...`) — introduced in CRM-003 (this PR). Typed
  DTOs, response envelope, cursor pagination, ETag/If-Match, Idempotency-Key,
  standard error envelope. **Status:** CURRENT.

## What is a BREAKING change?

A release is breaking if any of the following is true:

1. **Removed endpoint** — any v2 URL that responds today is removed.
2. **Removed HTTP method** — a method on an existing URL is removed.
3. **Changed HTTP status** — a success status on an existing endpoint changes
   (e.g. `201 → 200` on a POST).
4. **Removed response field** — a field that the v2 contract documents as
   part of a response DTO is removed.
5. **Changed response field type** — a field's type becomes incompatible
   (e.g. `string → number`, `UUID → string`, `Instant → LocalDate`).
6. **Renamed response field** — a field is renamed (e.g. `displayName → name`).
7. **Changed JSON case** — a field's case changes (e.g. `camelCase → snake_case`).
8. **Removed error code** — an entry in [CRM-ERROR-CATALOG.md](./CRM-ERROR-CATALOG.md)
   is removed or renamed.
9. **Changed error code HTTP status** — an error code's HTTP status changes.
10. **Changed error code meaning** — the conditions under which a code is
    returned change in a way that would surprise a client branching on it.
11. **Required a new header** — a header that was optional becomes required,
    OR a new required header is introduced on an existing endpoint.
12. **Tightened validation** — a field that accepted a value today rejects
    it after the change (e.g. raising the minimum length, adding a new
    pattern constraint, lowering the maximum amount).
13. **Changed pagination contract** — the `page.nextCursor` shape or the
    `limit`/`cursor`/`sort`/`direction` query parameter semantics change.
14. **Changed ETag format** — the ETag header format changes in a way that
    breaks clients that store or compare ETags.
15. **Removed enum value from response** — an enum value that could
    previously appear in a response field is no longer produced.

## What is a NON-BREAKING change?

A release is non-breaking if ALL changes are in the following categories:

1. **Added endpoint** — a new URL is introduced.
2. **Added HTTP method** — a new method on an existing URL.
3. **Added response field** — a new optional field is added to a response
   DTO. The frontend MUST ignore unknown fields (forward compatibility).
4. **Added request field** — a new optional field is added to a request DTO.
5. **Added error code** — a new entry in
   [CRM-ERROR-CATALOG.md](./CRM-ERROR-CATALOG.md). Frontend MUST treat
   unknown codes as `INTERNAL_ERROR` (forward compatibility).
6. **Added enum value** — a new enum value may appear in a response field.
   Frontend MUST handle unknown enum values gracefully.
7. **Loosened validation** — a field that rejected a value today accepts
   it after the change (within reason — e.g. raising the maximum length,
   accepting a new format).
8. **Added optional header** — a new optional request or response header.
9. **Changed internal implementation** — query plan, caching, logging,
   error message text (NOT error code), etc.
10. **Performance improvement** — faster response, smaller payload.
11. **Bug fix** — a behavior that contradicted the documented contract is
    corrected to match the contract.

## Deprecation policy

When a breaking change is unavoidable:

1. Announce the deprecation in [CRM-API-VERSIONING-POLICY.md](./CRM-API-VERSIONING-POLICY.md)
   with a target removal version.
2. Emit a `Deprecation` header (RFC 8594) on the deprecated endpoint, e.g.
   `Deprecation: @1735689600` (Sun, 01 Jan 2026 00:00:00 GMT).
3. Emit a `Sunset` header with the planned removal date.
4. Document the migration path to the replacement endpoint.
5. Maintain the deprecated endpoint for at least one full release cycle
   (target: 90 days).
6. Remove the deprecated endpoint only after the frontend has stopped
   calling it AND the 90-day window has elapsed.

## Adding new enum values

Adding a new enum value to a response field is NON-BREAKING. The frontend
MUST treat unknown enum values as a generic "unknown" state, never as an
error.

Adding a new enum value to a request field is BREAKING if the backend
previously rejected it with `VALIDATION_ERROR`, because clients may have
built workflows that depend on the rejection. To make this non-breaking,
the backend should accept the new value AND continue accepting the old
values; clients that depend on rejection should be migrated explicitly.

## Changing validation

Tightening validation (e.g. rejecting a value that was previously accepted)
is BREAKING. Loosening validation (accepting a value that was previously
rejected) is NON-BREAKING.

When loosening validation, document the previous behavior in the
deprecation note so clients that depended on the strict behavior can
migrate.

## Changing error codes

Removing or renaming an error code is BREAKING. Adding a new code is
NON-BREAKING.

Changing an error code's HTTP status is BREAKING. Clients that branch on
the HTTP status will break.

Changing an error code's meaning is BREAKING if the new meaning would
surprise a client branching on the code. Document such changes as a
deprecation with a migration path.

## Changing pagination

Any change to the `page.nextCursor` opaque format is NON-BREAKING (clients
treat cursors as opaque tokens). However:

- Changing the query parameter names (`limit`/`cursor`/`sort`/`direction`)
  is BREAKING.
- Changing the default `limit` is NON-BREAKING (clients SHOULD always
  specify `limit` explicitly).
- Changing the maximum `limit` is BREAKING if the new maximum is lower
  than the previous one (clients may have hardcoded a value above the
  new maximum).

## Support window

- **v1** is supported until the frontend has fully migrated to v2 PLUS
  one full release cycle (target: CRM-G3 closure).
- **v2** is the current version. The next breaking change will introduce
  v3 with its own support window.
- Each version is supported for at least 12 months from its initial
  release, regardless of frontend migration status.

## Headers used for versioning

| Header | Direction | Purpose |
|---|---|---|
| `Deprecation` | Response | RFC 8594 — marks an endpoint as deprecated with the deprecation timestamp. |
| `Sunset` | Response | RFC 7231 bis — the planned removal date of a deprecated endpoint. |
| `X-Request-ID` | Both | Correlates a request with logs. Echoed back in `meta.requestId`. |
| `ETag` | Response | The current version of a single-record resource. |
| `If-Match` | Request | Required on PATCH — the ETag the client last saw. |
| `Idempotency-Key` | Request | Optional on POST — replays the previous response if the same key+payload is seen again. |
