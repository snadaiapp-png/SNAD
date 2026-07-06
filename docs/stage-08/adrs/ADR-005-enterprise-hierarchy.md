# ADR-005 — Enterprise Hierarchy and Delegated Administration

**ADR ID:** `SANAD-ADR-005`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

Enterprise customers require organizational hierarchy and delegated administration across multiple legal entities.

## Decision

Group → Legal Entity → Business Unit → Department → Team. Permissions delegable at any level. Delegated admin scoped; revocable. Cross-company workflows supported.

## Consequences

* Existing tenant model extended (no breaking change).
* New hierarchy tables added.
* Permission resolution walks hierarchy.

## Alternatives

* Flat tenant: rejected (enterprise needs unmet).
* Per-legal-entity tenant: rejected (operational fragmentation).
