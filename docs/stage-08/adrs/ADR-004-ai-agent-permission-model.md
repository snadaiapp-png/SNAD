# ADR-004 — AI Agent Permission Model and L0–L4 Autonomy Levels

**ADR ID:** `SANAD-ADR-004`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

AI Agents require clear permission model and autonomy bounds to prevent unsafe actions.

## Decision

Adopt L0–L4 autonomy levels. No new Agent above L1 without approval. Tool-level authorization. Read-only default. Explicit write permissions. Tenant-isolated memory.

## Consequences

* All agent executions audited.
* Cross-tenant data leakage blocked.
* High-risk actions require human approval.

## Alternatives

* Single autonomy level: rejected (insufficient granularity).
* Per-tenant autonomy config: rejected (consistency issues).
