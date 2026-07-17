# CRM-G1 Evidence Hardening Addendum

> **Parent report:** `G1-STAGE-REPORT-V1`  
> **Parent implementation PR:** `#552`  
> **Parent verified head:** `b8ff660650d6ee836271957c08591b5f3bc0be8c`  
> **Parent merge SHA:** `f1eee10480cf3416edcf3824dab66a5450310e8a`  
> **Addendum status:** `SOURCE_HARDENING_IMPLEMENTED / EXACT_HEAD_CHECK_REQUIRED`  
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

## 3. Exact-SHA evidence workflow

`CRM G1 Schema Isolation` is hardened to:

- check out the actual pull-request head SHA;
- verify `git rev-parse HEAD` equals the candidate SHA;
- build the migration classpath;
- apply the full Flyway chain to PostgreSQL 16;
- run `scripts/crm/verify-g1-tenant-isolation.sql`;
- run `CrmPostgresMigrationTest` and
  `CrmG1TenantIsolationPostgresTest`;
- record Flyway, table, index, and tenant-FK inventories;
- upload Surefire reports and database evidence in an artifact named with the
  exact candidate SHA.

A workflow definition is not evidence of success. The current pull-request or
release check must complete successfully, and its artifact ID and digest must
be retained in the decision record.

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
| Behavioral PostgreSQL cross-tenant rejection test | `IMPLEMENTED / CURRENT EXACT-HEAD CHECK REQUIRED` |
| Exact-SHA immutable schema artifact | `IMPLEMENTED / CURRENT EXACT-HEAD CHECK REQUIRED` |
| Production migration runbook | `PRESENT` |
| Formal production evidence record | `PRESENT / PENDING EXECUTION` |
| Production Flyway application | `PENDING` |
| Post-deployment two-tenant smoke | `PENDING` |
| Database and application owner approval | `PENDING` |

## 6. Gate decision

```text
CRM-G1_SOURCE_IMPLEMENTATION: MERGED_AND_VERIFIED
CRM-G1_EVIDENCE_HARDENING: IMPLEMENTED / CURRENT_CI_REQUIRED
BEHAVIORAL_POSTGRESQL_ISOLATION: IMPLEMENTED
IMMUTABLE_EXACT_SHA_ARTIFACT: IMPLEMENTED
PRODUCTION_MIGRATION_EVIDENCE: REQUIRED
POST_DEPLOYMENT_TWO_TENANT_SMOKE: REQUIRED
OWNER_APPROVAL: REQUIRED
CRM-G1: OPEN / PRODUCTION_EVIDENCE_PENDING
```

This addendum strengthens evidence quality. It does not authorize production
execution and does not close CRM-G1.
