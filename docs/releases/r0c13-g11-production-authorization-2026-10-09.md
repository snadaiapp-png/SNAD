# R0C13 G11 Production Authorization — 2026-10-09

Authorized base SHA: `4ee4554a1385729ec6e4be9b141c5f7dad703d47`

Purpose: authorize the canonical production release chain after the governed G2 employer-foundation repair merged via PR #1325.

Required invariants:
- LIVE payment collection remains OFF.
- Release must use the immutable backend image for the authorized main SHA.
- Canonical Y2 orchestrator must dispatch SANAD Production Release.
- G2 production identity provisioning must bootstrap employer foundation only when active-link count is zero.
- Any ambiguous employer context remains fail-closed.
- R0C13 G11 production-disabled payment boundary must pass before closure.
