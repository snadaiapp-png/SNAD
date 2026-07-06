# ADR-003 — Marketplace Package Signing and Verification

**ADR ID:** `SANAD-ADR-003`
**Date:** 2026-07-06
**Status:** APPROVED

## Context

Marketplace accepts third-party packages. Supply-chain risk must be mitigated.

## Decision

All packages must be signed (SHA-256 + signature). Signature verified at submission, install, and runtime. Supply-chain provenance SLSA Level 3 target for Verified tier.

## Consequences

* Unsigned packages rejected.
* Tampered packages rejected.
* Audit trail of signature verification.

## Alternatives

* Trust on first use: rejected (no supply-chain protection).
* Central-only signing: rejected (publishers need to sign their own).
