# R11 — Testcontainers Decontamination / PostgreSQL-Only Test Path

Status: **PASS**

## Certified source

- Branch: `infra/backend-clean-room-v1`
- Certified SHA: `a8bc3739c403337aa59d7d2adefd1b37993349db`
- Governing test database path: PostgreSQL 16 GitHub Actions service
- Docker/Testcontainers database path: DEPRECATED / NOT ACTIVE

## Structural gates

- `apps/sanad-platform/pom.xml` contains no `org.testcontainers` dependencies and no `testcontainers.version` property.
- Active Java tests under `apps/sanad-platform/src/test/java` are guarded against Testcontainers imports, annotations, `PostgreSQLContainer`, and `GenericContainer` construction by `scripts/ops/test_testcontainers_decontamination.py`.
- Required CI jobs use PostgreSQL 16 service containers supplied by GitHub Actions; test code does not create or manage database containers.
- `postgres-acceptance.yml` and `development-security-acceptance.yml` are PostgreSQL-service-backed and contain no Testcontainers/Docker availability gates.

## Fresh verification evidence

Clean-Room Control Plane Audit run: `31942333601`

- Contract tests: `76/76 PASS`
- `unexpected_production_writers=0`
- `secret_candidate_files=0`
- `render_env_writers=0`
- `canonical_production_writers=3`
- Audit conclusion: `success`

CI run: `31942335954`

- `Maven Test Suite` = PASS
- `CRM Integration Tests` = PASS
- `R11 PostgreSQL Acceptance Certification` = PASS
- `R11 Development Security Certification` = PASS

## Mutation and production safety

- Production Render mutations: `0`
- Production database mutations: `0`
- Production deployments: `0`
- New production migrations executed: `0`
- Vercel cutover: `0`
- Credential rotation: `0`

## Final R11 verdict

```text
TESTCONTAINERS_ACTIVE_USAGE=0
TESTCONTAINERS_MAVEN_DEPENDENCIES=0
POSTGRESQL_TEST_ROUTE=POSTGRESQL_16_GITHUB_SERVICE
DOCKER_DATABASE_TEST_ROUTE=0
CLEAN_ROOM_CONTRACT_TESTS=76/76_PASS
MAVEN_TEST_SUITE=PASS
CRM_INTEGRATION_TESTS=PASS
POSTGRESQL_ACCEPTANCE=PASS
DEVELOPMENT_SECURITY=PASS
UNEXPECTED_PRODUCTION_WRITERS=0
SECRET_CANDIDATE_FILES=0
RENDER_ENV_WRITERS=0
PRODUCTION_RENDER_MUTATIONS=0
PRODUCTION_DB_MUTATIONS=0
R11_STATUS=PASS
```

R11 closes only the test-path decontamination. It does **not** authorize a Production database migration, Render deploy, Render environment mutation, Vercel cutover, or credential rotation.
