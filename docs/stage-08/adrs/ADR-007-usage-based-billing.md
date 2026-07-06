# ADR-007 — Usage-Based Billing and Metering Architecture

**ADR ID:** `SANAD-ADR-007`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

Growth requires usage-based billing beyond per-seat pricing.

## Decision

Per-event metering for API calls, AI tokens, emails, webhooks. Aggregated per tenant per day. Stored 13 months rolling. Used for billing and analytics.

## Consequences

* Billing computed from meter records.
* Disputes resolvable via meter log.
* Cost analytics feed back to cost governance.

## Alternatives

* Per-seat only: rejected (limits monetization of usage).
* Estimated billing: rejected (audit risk).
