# R0C13 — S12 Engineering Closure Candidate

- **Date:** 2026-10-08
- **Track:** Subscription & Billing / R0C13
- **Authoritative plan:** `docs/superpowers/plans/2026-09-11-r0c13-revenue-billing-integration-closure-implementation.md`
- **Current main:** `f1d7b4ac79cd9644c5dff44480fc973cd3138a19`
- **Production service:** `sanad-backend` / `srv-d8ragqkm0tmc73bviqq0`

## Closure predicate

S12 may close only when:

```text
R13-G01..R13-G11 = PASS
R0C13_ENGINEERING = CLOSED_PRODUCTION_VERIFIED
LIVE_PAYMENT_COLLECTION = NOT_ACTIVATED
LIVE_PAYMENT_AUTHORITY = NOT_GRANTED_BY_R0C13_CLOSURE
```

## Current execution state

G01-G10 were completed through the governed R0C13 implementation,
compatibility, security/evidence, and protected-release-candidate sequence.
S12 does not reopen those gates. The only remaining mandatory gate is G11
production verification.

### Exact-current-main evidence

- PR #1313 merged to
  `f1d7b4ac79cd9644c5dff44480fc973cd3138a19`.
- Publish Render Backend Image #432 / run `37776339363`: **SUCCESS**.
- Post-Merge Main Verification #1216 / run `37776339495`: **SUCCESS**.
- Stage 07 Artifact Provenance #5749 / run `37776339370`: **SUCCESS**.
- Vercel Main Production Reconcile #49 / run `37776339412`: **SUCCESS**.
- Production Operational Smoke #52 / run `37776496693`: **SUCCESS**.

### Production binding evidence

Render currently reports:

```text
service = sanad-backend
service_id = srv-d8ragqkm0tmc73bviqq0
auto_deploy = disabled
live_image = ghcr.io/snadaiapp-png/snad-backend:d3ecd3d40cc2804b5d11868eb0d9f96b7d3997eb
target_image = ghcr.io/snadaiapp-png/snad-backend:f1d7b4ac79cd9644c5dff44480fc973cd3138a19
```

The target immutable image exists, but it is not yet the live Render image.

The most recent canonical SANAD Production Release is #95 / run
`37707157309`, which failed on older SHA
`d73795a2b7f344047df03dc934393656cb0cdb2c` at the exact-image verification
stage before PR #1313 fixed publication for every main SHA.

## G11 terminal checklist

| Predicate | Status |
|---|---|
| Immutable exact-main image exists | PASS |
| Exact-main post-merge verification | PASS |
| Exact-main provenance | PASS |
| Canonical production release on current main | BLOCKED |
| Render live image equals current main SHA | BLOCKED |
| Production provider mode = DISABLED | BLOCKED_PENDING_RELEASE_EVIDENCE |
| LIVE collection remains off | REQUIRED / NOT ACTIVATED |
| Unsigned webhook rejected on released SHA | BLOCKED_PENDING_RELEASE_EVIDENCE |
| Health/readiness/Flyway/security/R0C12 production smoke on released SHA | BLOCKED_PENDING_RELEASE_EVIDENCE |
| Live charge path inoperable on released SHA | BLOCKED_PENDING_RELEASE_EVIDENCE |
| Post-release drift = NONE | BLOCKED_PENDING_RELEASE |

## Decision

```text
R13-G11 = BLOCKED_ON_EXACT_PRODUCTION_RELEASE
R0C13_S12 = CANDIDATE
R0C13_ENGINEERING = NOT_YET_CLOSED
LIVE_PAYMENT_COLLECTION = NOT_ACTIVATED
```

The next action is singular and governed:

1. Run **SANAD Production Release** for exact SHA
   `f1d7b4ac79cd9644c5dff44480fc973cd3138a19` with rollback enabled.
2. Require the workflow's exact-image, provider-disabled, unsigned-webhook,
   readiness/Flyway/security/R0C12, and no-live-charge checks to pass.
3. Re-read Render and prove the live image is the exact SHA.
4. Recheck repository/production drift.
5. Only then promote G11 to PASS and issue the terminal S12 state:
   `R0C13_ENGINEERING = CLOSED_PRODUCTION_VERIFIED`.

No manual Render deployment or LIVE payment activation is authorized by this
candidate.
