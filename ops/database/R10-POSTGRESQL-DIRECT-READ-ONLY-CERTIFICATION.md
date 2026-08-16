# R10 — PostgreSQL Direct Read-Only Certification

## Verdict

`BLOCKED_BY_DIRECT_NETWORK`

The Clean-Room read-only certification control is implemented and contract-certified. The first real GitHub-hosted runner attempt reached the Direct-route/network gate and stopped before opening a database session because the Direct Supabase hostname could not be resolved/reached from that runner.

## Control-plane source

```text
IMPLEMENTATION_SHA=c75e6e555838403d6c0a2c5ae7ee65953d5fcf5a
STABILIZED_SHA=caf0a65769422b135e0d8471e2444b45684f6126
DIRECT_ATTEMPT_RUN=31921107041
DIRECT_ATTEMPT_JOB=95101006708
AUDIT_RUN=31921218634
AUDIT_JOB=95101274355
```

## Direct attempt result

```text
REQUESTED_ROUTE=POSTGRESQL_DIRECT
REQUESTED_PORT=5432
POOLER_FALLBACK=FORBIDDEN
ROUTE_GATE=BLOCKED_BY_DIRECT_NETWORK
DATABASE_SESSION_OPENED=NO
PSQL_EXECUTED=NO
FLYWAY_INFO_EXECUTED=NO
FLYWAY_VALIDATE_EXECUTED=NO
FLYWAY_MIGRATE_EXECUTED=NO
DATABASE_MUTATION_PERFORMED=NO
```

The workflow rejects `.pooler.supabase.com` and port `6543`. It does not silently switch to a Supavisor route when Direct connectivity is unavailable.

## Read-only protections prepared for the next manual re-check

If Direct network access becomes available, every certification connection is constrained by:

```text
JDBC readOnly=true
JDBC readOnlyMode=always
PostgreSQL default_transaction_read_only=on
statement_timeout=60000
Flyway commands allowed: info, validate
Pending migrations ignored for applied-history validation: *:pending
Flyway commands forbidden: migrate, repair, clean, baseline
```

The workflow fingerprints `flyway_schema_history` before and after the read-only certification and fails with `DATABASE_MUTATION_DETECTED` if the fingerprint or row count changes.

## Evidence artifacts

First Direct attempt:

```text
ARTIFACT_ID=9256374921
ARTIFACT_NAME=database-direct-certification-evidence-31921107041
ARTIFACT_ZIP_SHA256=7a042d6129231191fcc551505936b9ab74c9efe655c08aba2281336a403a09eb
```

Latest Clean-Room audit:

```text
CONTRACT_TESTS=69/69 PASS
CANONICAL_PRODUCTION_WRITERS=3
UNEXPECTED_PRODUCTION_WRITERS=0
SECRET_CANDIDATE_FILES=0
RENDER_ENV_WRITERS=0
AUDIT_ARTIFACT_ID=9256400638
AUDIT_ARTIFACT_ZIP_SHA256=fe227208508966df7886a89c24e6ae33f1bc1f35a60e321c1f4a025d754cf41c
```

## Operating decision

After the first confirmed network blocker, `database-direct-certification.yml` is manual-only. Re-run it only after Direct network topology changes (for example, an IPv4-capable Direct path or an IPv6-capable runner). The project continues without using a pooler as a migration fallback.

Credential rotation remains deferred until final project/cutover by project-owner decision.

## Safety boundary

```text
PRODUCTION_DATABASE_MUTATION=0
PRODUCTION_FLYWAY_MIGRATION=0
PRODUCTION_RENDER_MUTATION=0
VERCEL_CUTOVER=0
CREDENTIAL_ROTATION=0
```

## Next gate

`R11 — Testcontainers / Legacy Docker Test-Path Decontamination`
