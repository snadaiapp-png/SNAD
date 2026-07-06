# ADR-002 — Multi-Region Data Residency Model

**ADR ID:** `SANAD-ADR-002`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

Stage 08 supports global expansion. Different jurisdictions (KSA, EU, US) have data residency requirements.

## Decision

Configuration-first country model. Tenant selects residency zone at onboarding. Data pinned to zone. Cross-region backup only where matrix permits. No hardcoded rules.

## Consequences

* Tenant data stays in compliance zone.
* Cross-region queries blocked at storage layer.
* Configuration is versioned and audited.

## Alternatives

* Single region with replication: rejected (violates residency).
* Per-tenant DB instance: rejected (operational overhead).
