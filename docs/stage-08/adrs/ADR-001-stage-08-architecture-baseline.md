# ADR-001 — Stage 08 Architecture Baseline Adoption

**ADR ID:** `SANAD-ADR-001`
**Date:** 2026-07-06
**Status:** APPROVED
**Stage:** 08

---

## Context

SANAD transitions from Stage 07 (commercially deployable product) to Stage 08 (scale, growth, global expansion). The Stage 08 architecture must extend existing principles without breaking changes.

## Decision

Adopt the Stage 08 Architecture Baseline as defined in `docs/stage-08/architecture/STAGE-08-ARCHITECTURE-BASELINE.md`. Maintain modular service-oriented architecture. No microservices split without justified need. All new domains are bounded contexts with no cross-DB coupling.

## Consequences

* Existing Stage 07 code base remains valid.
* New Stage 08 domains add bounded contexts.
* Architecture is frozen except through approved ADR.
* Tenant isolation enforced at every new layer.

## Alternatives Considered

* Full microservices split: rejected (premature, operational overhead).
* Monolith: rejected (would violate modular principle).

## Approval

Project Manager: APPROVED 2026-07-06
