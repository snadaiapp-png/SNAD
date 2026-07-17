# CRM-G1 Evidence Hardening Addendum

> **Parent report:** `G1-STAGE-REPORT-V1`  
> **Parent implementation PR:** `#552`  
> **Parent verified head:** `b8ff660650d6ee836271957c08591b5f3bc0be8c`  
> **Parent merge SHA:** `f1eee10480cf3416edcf3824dab66a5450310e8a`  
> **Evidence-hardening PR:** `#558`  
> **Evidence-hardening verified head:** `ebca701322daba41f55396d9502c99e8672b6813`  
> **Evidence-hardening merge SHA:** `49a58cca1aac87595e26207fe4167f32115e2498`  
> **Addendum status:** `MERGED_AND_EXACT_SHA_VERIFIED`  
> **CRM-G1 gate:** `OPEN / PRODUCTION_EVIDENCE_PENDING`

## 1. Purpose

The parent stage report conclusively verifies the merged eight-table and
26-index CRM-G1 source contract. This addendum closes two evidence-quality gaps
without changing Flyway migration `20260717.6`:

1. prove cross-tenant rejection with an actual PostgreSQL write attempt rather
   than catalog inspection alone;
2. provide an immutable exact-SHA evidence artifact and a controlled production
   evidence process.

## 2. Behavioral PostgreSQL isolation test

`CrmG1TenantIsolationPostgresTest` executes the complete Flyway chain on
PostgreSQL 16 and performs the following sequence:

1. creates Tenant A and Tenant B;
2. creates an account and contact owned by Tenant A;
3. attempts to insert a `crm_contact_lookup_index` row owned by Tenant B while
   referencing Tenant A's contact;
4. requires PostgreSQL to reject the write with a data-integrity violation;
5. inserts the same lookup relationship under Tenant A and requires success;
6. confirms that no Tenant B row referencing Tenant A was persisted.

This test proves the behavior of
`fk_crm_contact_lookup_contact_same_tenant`. It complements—but does not
replace—the read-only catalog verifier and the authenticated API/UI isolation
suite.

## 3. Exact-SHA evidence

The evidence-hardening head
`ebca701322daba41f55396d9502c99e8672b6813` passed all pull-request workflows,
including:

- `CRM G1 Schema Isolation`;
- `CRM Authenticated Acceptance` and Playwright;
- platform `CI` and `Web CI`;
- API contract and modular architecture validation;
- security, backup/restore, performance, deployment-readiness, provenance, and
  production-readiness gates.

The dedicated G1 workflow run was `29601659475`. Its immutable evidence artifact
is:

```text
artifact_id: 8415255083
artifact_name: crm-g1-schema-isolation-ebca701322daba41f55396d9502c99e8672b6813
artifact_digest: sha256:a762678fef84eb4cb9bd65f7a2d5b1375835b3c5a2f9d8a95bd5ee62698aa5a2
```

The downloaded artifact proves:

- candidate SHA matched the tested head;
- Flyway `20260717.6` succeeded exactly once;
- all eight G1 extension tables existed;
- exactly 26 explicit `idx_crm_%` indexes existed;
- all eight tenant-root foreign keys existed;
- the catalog verifier passed every check;
- `CrmPostgresMigrationTest` passed 3 of 3 tests;
- `CrmG1TenantIsolationPostgresTest` passed 1 of 1 test;
- failures, errors, and skipped tests were zero.

PR `#558` was squash-merged using `expected_head_sha` protection as merge SHA
`49a58cca1aac87595e26207fe4167f32115e2498`.

## 4. Production control package

The controlled migration procedure is documented in:

`docs/crm/CRM-G1-PRODUCTION-MIGRATION-RUNBOOK.md`

The required production evidence record is:

`docs/crm/evidence/CRM-G1-PRODUCTION-MIGRATION-EVIDENCE.md`

The evidence record deliberately begins as `PENDING_PRODUCTION_EXECUTION`.
Repository CI must not be represented as proof that migration `20260717.6` has
been applied to the controlled production PostgreSQL/Supabase database.

## 5. Acceptance matrix

| Requirement | Result |
|---|---|
| Parent eight-table source contract | `PASS` |
| Parent 26-index source contract | `PASS` |
| Parent exact-SHA implementation CI | `PASS` |
| Parent authenticated two-tenant source acceptance | `PASS` |
| Behavioral PostgreSQL cross-tenant rejection test | `PASS` |
| Exact-SHA immutable schema artifact | `PASS` |
| Evidence hardening merged with expected-head protection | `PASS` |
| Production migration runbook | `PRESENT` |
| Formal production evidence record | `PRESENT / PENDING EXECUTION` |
| Production Flyway application | `PENDING` |
| Post-deployment two-tenant smoke | `PENDING` |
| Database and application owner approval | `PENDING` |

## 6. Gate decision

```text
CRM-G1_SOURCE_IMPLEMENTATION: MERGED_AND_VERIFIED
CRM-G1_EVIDENCE_HARDENING: MERGED_AND_EXACT_SHA_VERIFIED
BEHAVIORAL_POSTGRESQL_ISOLATION: PASS
IMMUTABLE_EXACT_SHA_ARTIFACT: PASS
PRODUCTION_MIGRATION_EVIDENCE: REQUIRED
POST_DEPLOYMENT_TWO_TENANT_SMOKE: REQUIRED
OWNER_APPROVAL: REQUIRED
CRM-G1: OPEN / PRODUCTION_EVIDENCE_PENDING
```

The repository implementation and source-evidence hardening are complete. This
addendum does not authorize production execution and does not close CRM-G1.
