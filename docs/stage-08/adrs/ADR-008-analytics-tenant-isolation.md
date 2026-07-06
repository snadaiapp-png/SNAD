# ADR-008 — Analytics Tenant Isolation and PII Minimization

**ADR ID:** `SANAD-ADR-008`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

Analytics warehouse aggregates tenant data; risk of cross-tenant leakage and PII exposure.

## Decision

Per-tenant schemas in analytics warehouse. Cross-tenant queries blocked at warehouse layer. PII minimized via tokenization. Data lineage tracked. Audited data access.

## Consequences

* Tenants cannot accidentally see others' analytics.
* PII not stored in warehouse; only tokens.
* All analytics access logged.

## Alternatives

* Single shared schema with row-level security: rejected (operational risk).
* Per-tenant warehouse instance: rejected (cost).
