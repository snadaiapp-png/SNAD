# CRM-007 Archive 500 — Exception Capture

**Date:** 2026-07-22
**Request ID:** 0126b1f1-25d6-4eac-810e-8af4857411b2
**Failed Release SHA:** cfceac8bf8ab016e2e893428124811bedf9443ca
**CRM-007 Run:** 29875526270

## Exception class

```
org.springframework.jdbc.BadSqlGrammarException
```

## Most specific cause

```
org.postgresql.util.PSQLException: ERROR: column "archived_at" is of type
timestamp with time zone but expression is of type text
```

## SQLSTATE

```
42804 (datatype_mismatch)
```

## Constraint name

None — this is a type mismatch, not a constraint violation.

## Failing SQL operation

```sql
UPDATE crm_communication_methods SET status=?,
  preferred=CASE WHEN ?='ARCHIVED' THEN FALSE ELSE preferred END,
  preferred_slot=CASE WHEN ?='ARCHIVED' THEN NULL ELSE preferred_slot END,
  archived_at=CASE WHEN ?='ARCHIVED' THEN ? ELSE NULL END,
  updated_by=?,updated_at=?,version=version+1
WHERE tenant_id=? AND id=? AND version=?
```

The bound parameter `:now` (`java.sql.Timestamp`) is not cast inside the `CASE WHEN` expression. PostgreSQL infers the `CASE WHEN` result as `text` type, which is incompatible with the `archived_at` column (`TIMESTAMP WITH TIME ZONE`).

## Top application stack frame

```
com.sanad.platform.crm.party.infrastructure.JdbcAddressCommunicationRepository
  .changeCommunicationStatus(JdbcAddressCommunicationRepository.java:330)
```

## Transaction rollback status

The `BadSqlGrammarException` propagates through `PersistenceExceptionTranslationInterceptor` to `CrmExceptionHandler.handleAny(Exception)` which returns HTTP 500. The UPDATE transaction is **rolled back** — the communication method remains ACTIVE with its original version.

## Affected code paths

1. `JdbcAddressCommunicationRepository.changeCommunicationStatus()` — line 333
   `archived_at=CASE WHEN :status='ARCHIVED' THEN :now ELSE NULL END`
2. `JdbcAddressCommunicationRepository.changeAddressStatus()` — line 168
   Same pattern, same latent bug.

**Root cause:** PostgreSQL cannot infer the result type of a `CASE WHEN` expression containing a named parameter (`:now`). The parameter is typed as `text`, which is incompatible with the `archived_at` column (`TIMESTAMP WITH TIME ZONE`). This is a PostgreSQL-specific limitation — H2 in `MODE=PostgreSQL` does not exhibit this behavior.

## Fix

Add explicit `CAST(:now AS TIMESTAMP)` to force PostgreSQL type inference inside `CASE WHEN`:

```sql
archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END
```

## Redacted fields

- JWT tokens: [REDACTED]
- Database credentials: [REDACTED]
- Tenant IDs: [REDACTED]
- Communication raw values: [REDACTED]

## Render log entry ID

```
14959004-4939-4506-9a3c-a1f32da36528
```
