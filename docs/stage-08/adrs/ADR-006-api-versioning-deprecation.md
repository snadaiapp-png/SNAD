# ADR-006 — Developer Platform API Versioning and Deprecation

**ADR ID:** `SANAD-ADR-006`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

Public API must evolve without breaking integrators.

## Decision

URL versioning (`/api/v1/`). Backward-compatible changes within major version. Breaking changes require new major version. Sunset: 12 months notice. Deprecation banner in API responses.

## Consequences

* Multiple major versions may run concurrently.
* Integrators get 12-month migration window.
* Documentation tracks per-version changelog.

## Alternatives

* Header versioning: rejected (poor observability).
* No versioning: rejected (breaks integrators).
