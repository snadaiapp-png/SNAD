# ADR-010 — Industry Pack Lifecycle and Reversibility

**ADR ID:** `SANAD-ADR-010`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

Industry Packs modify tenant configuration; bad updates can break tenant.

## Decision

All packs versioned. Install/upgrade reversible. Migrations have `up` and `down`. Rollback restores previous configuration. Audit trail of all lifecycle events.

## Consequences

* Tenants can recover from bad upgrades.
* Migration authors must write `down` migrations.
* Lifecycle events auditable.

## Alternatives

* Forward-only migrations: rejected (no recovery path).
* Manual recovery: rejected (slow + error-prone).
