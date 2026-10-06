# G3 Final Evidence Manifest

> STATUS_AUTHORITY: CURRENT
> G3_FINAL_GATE = PASS
> G3_FULLY_CLOSED = PASS
> Reference implementation PR: #1234
> Production closure PR: #1281
> PRODUCTION_OPERATIONAL_CERTIFICATION = PASS

## Certified exact-SHA evidence

```text
REFERENCE_IMPLEMENTATION_PR = #1234
CLOSURE_EVIDENCE_MAIN_SHA = 0acdc42507d817766a0113ae418c7c36dde678d1
G3_AUTHENTICATED_ACCEPTANCE_RUN = 37121204831
CRM_G1_SCHEMA_ISOLATION_RUN = 37121204843
CI_RUN = 37121204852
POST_MERGE_MAIN_VERIFICATION_RUN = 37121204833
PLAYWRIGHT_E2E_VISUAL_REGRESSION_RUN = 37121204782
```

All listed runs completed successfully on the exact certified engineering SHA above.

## Production operational closure evidence

```text
PRODUCTION_CLOSURE_PR = #1281
PRODUCTION_CLOSURE_MAIN_SHA = 2ba84d4c146ca0b9b4f105fed53d81cfa7d2f581
VERCEL_MAIN_PRODUCTION_RECONCILE_RUN = 37329097684
PRODUCTION_OPERATIONAL_SMOKE_RUN = 37329356654
FINAL_POST_MERGE_MAIN_VERIFICATION_RUN = 37329097719
PRODUCTION_OPERATIONAL_CERTIFICATION = PASS
PRODUCTION_AUTHORIZATION = GOVERNED_G3_RELEASE_COMPLETED
```

All three final production-closure runs completed SUCCESS on the same exact SHA.
The final PMV includes SUCCESS for PostgreSQL Direct integration, HRM focused
security/RLS, security/governance scans, and final evidence aggregation.

## Required closure checks

```text
Build Next.js Web = SUCCESS
provenance = SUCCESS
CRM Integration Tests = SUCCESS
Maven Test Suite = SUCCESS
CRM Deployment Readiness = SUCCESS
Verify 8 tables, 26 indexes, and tenant isolation = SUCCESS
PostgreSQL Acceptance Tests = SUCCESS
G3 Authenticated Acceptance = SUCCESS
Post-Merge Main Verification = SUCCESS
Vercel = SUCCESS
```

## Canonical G3 execution state

```text
CANONICAL_TASK_T1 = DONE
CANONICAL_TASK_T2 = DONE
CANONICAL_TASK_T3 = DONE
CANONICAL_TASK_T4 = DONE
CANONICAL_TASK_T5 = DONE
CANONICAL_TASK_T6 = DONE
CANONICAL_TASK_T7 = DONE
CANONICAL_TASK_T8 = DONE

ROADMAP_G3_T01 = DONE
ROADMAP_G3_T02 = DONE
ROADMAP_G3_T03 = DONE
ROADMAP_G3_T04 = DONE

IMPLEMENTATION_COMPLETE = YES
ENGINEERING_CERTIFICATION = APPROVED
G3_FINAL_GATE = PASS
G3_FULLY_CLOSED = PASS
```

## Security / tenant isolation evidence

The certified SHA is backed by host-native PostgreSQL Direct execution. Docker
and Testcontainers are not closure authorities.

- PostgreSQL Acceptance Tests: SUCCESS
- HRM focused security/RLS: SUCCESS
- CRM G1 schema isolation: SUCCESS
- G3 authenticated browser acceptance: SUCCESS
- Unauthorized path enforcement: verified by authenticated acceptance
- Required check `Verify 8 tables, 26 indexes, and tenant isolation`: SUCCESS

## Independent authorities

The later production-operational closure is proven independently from the original
engineering certificate. Legal and Saudi-country certification remain separate:

```text
PRODUCTION_OPERATIONAL_CERTIFICATION = PASS
LEGAL_REVIEW = PENDING_HUMAN
SA_COUNTRY_PACK = DRAFT
SAUDI_LEGAL_COMPLIANT = NOT_CLAIMED
```

## Exact-SHA rule

This certificate is bound to
`0acdc42507d817766a0113ae418c7c36dde678d1`. Future source changes must be
verified on their own SHA and do not inherit this evidence automatically.
