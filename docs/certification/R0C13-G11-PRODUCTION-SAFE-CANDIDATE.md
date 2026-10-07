# R0C13 — R13-G11 Production-Safe Deployment Candidate

## Decision

R13-G10 is closed. R13-G11 is now the active gate.

This candidate authorizes only a **production-safe deployment with payment collection disabled**.
It does not authorize LIVE payment collection.

```text
R0C13_PROVIDER_MODE = DISABLED
LIVE_PAYMENT_COLLECTION = OFF
R13-G11 = CANDIDATE
```

## Baseline

- G10 merge SHA: `0ca5d46034d2b125364eddbc6e9bb67013f9c7f1`
- G10 PMV #1199: SUCCESS
- G11 branch base: `d6375dcc850db33f41c0c3763d1c62cd97f59cd8`
- One intervening main commit exists after G10 and affects HRM G4-T3 only.
- R0C13 subscription/billing scope drift: **NONE**.

Current production Render baseline:

- Service: `sanad-backend`
- Service ID: `srv-d8ragqkm0tmc73bviqq0`
- Auto deploy: disabled
- Live deployment: `dep-davtgfbtqb8s73dof3gg`
- Live image: `ghcr.io/snadaiapp-png/snad-backend:d3ecd3d40cc2804b5d11868eb0d9f96b7d3997eb`
- Live image digest: `sha256:62db2d6f54e290b3d517f2bef2cfb3772d4bc8d3a7e1aa371c0eda06f11550e8`

The current production image is not the G11 candidate. Therefore exact-SHA deployment is required.

## G11 verification added to canonical release

The protected release candidate extends the existing canonical
`.github/workflows/production-release.yml` chain with R0C13-specific
fail-closed verification:

1. authenticated provider readiness must report:
   - `providerCode=DISABLED`
   - `mode=DISABLED`
   - `liveCollectionEnabled=false`
2. an unsigned provider webhook must be rejected;
3. the exact checked-out source must expose no billing API route capable of
   creating a provider customer/payment intent/charge/refund;
4. `DisabledBillingPaymentProvider` must fail closed for every external
   provider operation;
5. sanitized runtime evidence is uploaded with the canonical release artifact.

## Required closeout

```text
PROTECTED REVIEW
→ EXACT-HEAD CI
→ SQUASH MERGE WITH PRODUCTION-RELEASE-AUTHORIZED MARKER
→ Publish Render Backend Image
→ Workflow Y2 Production Release Orchestrator
→ SANAD Production Release (rollback_on_failure=true)
→ exact-image Render verification
→ readiness/Flyway/security/SCP smoke
→ R0C13 G11 disabled-provider negative smoke
→ production evidence
→ baseline drift recheck
→ G11 CLOSED
```

No manual Render deployment is authorized by this candidate.
