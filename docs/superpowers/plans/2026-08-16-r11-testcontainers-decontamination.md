# R11 Testcontainers Decontamination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Testcontainers/Docker-daemon assumptions from active backend test execution while preserving real PostgreSQL 16 semantics and required CI check names.

**Architecture:** GitHub Actions owns an explicit `postgres:16-alpine` service for database-dependent tests. Backend test classes consume the datasource through environment variables and never create containers. A Clean-Room contract test prevents reintroduction of Testcontainers Maven dependencies or Testcontainers-specific active workflow controls.

**Tech Stack:** Java 21 CI runtime, Spring Boot 3.5.6, Maven, PostgreSQL 16, Flyway, GitHub Actions, Python unittest Clean-Room contracts.

## Global Constraints

- Work only on `infra/backend-clean-room-v1`; never implement directly on `main`.
- PostgreSQL Direct/governed PostgreSQL path only; Docker/Testcontainers database orchestration is deprecated.
- Preserve required check names exactly: `Maven Test Suite` and `CRM Integration Tests`.
- No Production DB connection/migration, Render mutation/deploy, Vercel mutation, credential rotation, or new Flyway migration.
- Docker remains valid for application image packaging and GitHub Actions service containers; the prohibition is application-created Testcontainers/database Docker orchestration.
- Do not rewrite historical docs solely because they mention Testcontainers.

---

### Task 1: Add fail-closed R11 contract

**Files:**
- Create: `scripts/ops/test_testcontainers_decontamination.py`

**Interfaces:**
- Consumes: `apps/sanad-platform/pom.xml`, `.github/workflows/ci.yml`, `.github/workflows/postgres-acceptance.yml`, `.github/workflows/development-security-acceptance.yml`.
- Produces: Clean-Room unittest contract enforcing zero active Testcontainers controls and explicit PostgreSQL service use.

- [ ] **Step 1: Write the failing test**

Create tests that assert:

```python
assert '<testcontainers.version>' not in pom
assert '<groupId>org.testcontainers</groupId>' not in pom
assert 'TESTCONTAINERS_' not in ci
assert 'Testcontainers logs' not in ci
assert 'Verify Docker availability (for Testcontainers)' not in ci
assert 'name: Maven Test Suite' in ci
assert 'name: CRM Integration Tests' in ci
assert 'image: postgres:16-alpine' in ci
assert '@Testcontainers' not in postgres_acceptance
assert 'TESTCONTAINERS_' not in postgres_acceptance
assert 'PostgreSQL Testcontainers Acceptance' not in postgres_acceptance
assert 'image: postgres:16-alpine' in postgres_acceptance
assert 'SPRING_DATASOURCE_URL' in postgres_acceptance
assert 'Verify Docker' not in development_security
assert 'image: postgres:16-alpine' in development_security
assert 'SPRING_DATASOURCE_URL' in development_security
```

Also scan `apps/sanad-platform/src/test/java/**/*.java` and fail if `org.testcontainers`, `@Testcontainers`, `PostgreSQLContainer`, or `GenericContainer` is present.

- [ ] **Step 2: Verify RED**

Run through the existing Clean-Room audit workflow:

```bash
python -m unittest discover -s scripts/ops -p 'test_*.py'
```

Expected: the new R11 tests fail because `pom.xml`, `ci.yml`, `postgres-acceptance.yml`, and `development-security-acceptance.yml` still contain legacy controls.

- [ ] **Step 3: Commit RED contract**

```text
test(r11): enforce PostgreSQL-only backend test path
```

---

### Task 2: Remove Testcontainers dependencies and clean required CI jobs

**Files:**
- Modify: `apps/sanad-platform/pom.xml`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: explicit PostgreSQL service already present in both required CI jobs.
- Produces: Maven dependency graph without Testcontainers and required checks that execute directly against PostgreSQL 16.

- [ ] **Step 1: Remove Maven dependency residue**

Delete:

```xml
<testcontainers.version>1.20.4</testcontainers.version>
```

and both test dependencies:

```xml
<groupId>org.testcontainers</groupId>
<artifactId>postgresql</artifactId>
```

```xml
<groupId>org.testcontainers</groupId>
<artifactId>junit-jupiter</artifactId>
```

- [ ] **Step 2: Clean `ci.yml` without renaming checks**

Preserve:

```yaml
name: Maven Test Suite
name: CRM Integration Tests
services:
  postgres:
    image: postgres:16-alpine
```

Remove both Docker-daemon verification steps, all `TESTCONTAINERS_*` variables, Testcontainers-specific comments, `/tmp/testcontainers*` artifact paths, and Testcontainers-specific artifact names. Keep Surefire report artifacts.

- [ ] **Step 3: Verify focused contract**

Run:

```bash
python -m unittest scripts.ops.test_testcontainers_decontamination -v
```

Expected: POM/CI assertions pass; acceptance-workflow assertions may remain RED until Tasks 3-4.

- [ ] **Step 4: Commit**

```text
refactor(test): remove Testcontainers dependencies from backend CI
```

---

### Task 3: Convert PostgreSQL Acceptance to an explicit PostgreSQL service

**Files:**
- Modify: `.github/workflows/postgres-acceptance.yml`

**Interfaces:**
- Consumes: `RefreshTokenConcurrencyPostgresTest`, which already reads datasource environment variables.
- Produces: `PostgreSQL Acceptance` workflow that verifies the concurrency test against a declared PostgreSQL 16 service with zero skipped/failure/error results.

- [ ] **Step 1: Add PostgreSQL service and datasource contract**

Under the job, declare:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    env:
      POSTGRES_USER: sanad
      POSTGRES_PASSWORD: sanad_pass
      POSTGRES_DB: sanad
    ports:
      - 5432:5432
    options: >-
      --health-cmd "pg_isready -U sanad -d sanad"
      --health-interval 5s
      --health-timeout 5s
      --health-retries 20
```

Add job env:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://127.0.0.1:5432/sanad?prepareThreshold=0
SPRING_DATASOURCE_USERNAME: sanad
SPRING_DATASOURCE_PASSWORD: sanad_pass
SPRING_DATASOURCE_DRIVER_CLASS_NAME: org.postgresql.Driver
FLYWAY_ENABLED: 'true'
FLYWAY_BASELINE_ON_MIGRATE: 'true'
FLYWAY_VALIDATE_ON_MIGRATE: 'false'
JPA_DDL_AUTO: validate
```

- [ ] **Step 2: Remove Testcontainers discovery and Docker checks**

Delete Docker version/info checks, `@Testcontainers` grep/discovery, Testcontainers version/image summary, and “all Testcontainers tests” execution.

- [ ] **Step 3: Keep deterministic PostgreSQL acceptance**

Run exactly `RefreshTokenConcurrencyPostgresTest`, require its Surefire XML, and fail unless tests > 0, failures = 0, errors = 0, skipped = 0. Summary text must say `PostgreSQL 16 service`, not Testcontainers.

- [ ] **Step 4: Commit**

```text
refactor(test): move PostgreSQL acceptance off Testcontainers
```

---

### Task 4: Fix Development Security Acceptance database authority

**Files:**
- Modify: `.github/workflows/development-security-acceptance.yml`

**Interfaces:**
- Consumes: the existing four acceptance test classes including `RefreshTokenConcurrencyPostgresTest`.
- Produces: deterministic development security acceptance backed by PostgreSQL 16 service instead of Docker availability assumptions.

- [ ] **Step 1: Add the same PostgreSQL 16 service**

Declare the same isolated service and datasource/Flyway/JPA environment used by `ci.yml`.

- [ ] **Step 2: Remove `Verify Docker`**

Delete the step that runs `docker version` and `docker info`.

- [ ] **Step 3: Preserve acceptance tests and report verification**

Keep the test class list, report existence checks, and zero skipped/failure/error gate unchanged unless runtime evidence proves a specific profile adjustment is required.

- [ ] **Step 4: Commit**

```text
refactor(test): provision PostgreSQL for security acceptance
```

---

### Task 5: Full GREEN verification and R11 evidence

**Files:**
- Create: `ops/database/R11-TESTCONTAINERS-DECONTAMINATION.md`

**Interfaces:**
- Consumes: all changes from Tasks 1-4.
- Produces: auditable R11 PASS/BLOCKED evidence.

- [ ] **Step 1: Run all Clean-Room contract tests**

```bash
python -m unittest discover -s scripts/ops -p 'test_*.py'
```

Expected: PASS.

- [ ] **Step 2: Run Clean-Room inventory**

```bash
python scripts/ops/audit_github_workflows.py --root . --output-dir clean-room-audit --fail-on-policy-violations
```

Expected:

```text
unexpected_production_writers=0
secret_candidate_files=0
render_env_writers=0
```

- [ ] **Step 3: Verify CI runs from PR #862**

Require successful relevant runs for the resulting SHA where GitHub emits them, especially `Maven Test Suite`, `CRM Integration Tests`, `PostgreSQL Acceptance`, and `Development Security Acceptance`. If an unrelated pre-existing test failure blocks completion, record the exact class/error and leave R11 `BLOCKED` rather than weakening the gate.

- [ ] **Step 4: Write R11 evidence**

Record exact source SHA, final SHA, Testcontainers active-import/dependency/workflow counts, PostgreSQL route, required-check preservation, test/audit outcomes, and mutation counters.

- [ ] **Step 5: Final commit**

```text
docs(r11): certify PostgreSQL-only backend test path
```
