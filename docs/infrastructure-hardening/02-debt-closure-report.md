# SNAD Infrastructure Hardening — Stage 02A Debt Closure Report

## Execution Context

| Field | Value |
|---|---|
| Branch | infra/02a-debt-closure |
| Base Commit | 36ca1d4a1792eedea232745a609f61287aecaa20 |
| Commit SHA | (to be recorded after commit) |
| Date | 2026-06-30 |

## Debt Closure Summary

### Closed Debt (6 items)

| ID | Title | Closure Evidence |
|---|---|---|
| CD-01-P1-001 | Maven Wrapper was custom | Replaced with official Apache Maven Wrapper 3.3.4 (only-script distribution). `mvn wrapper:wrapper -Dmaven=3.9.9` used. SHA-256: mvnw=cae96cef..., mvnw.cmd=46eedb84..., properties=b0e7995e... |
| CD-01-P1-003 | Backend tests not run locally | `./mvnw -B -ntp clean test` = 434 passed, 0 failed, 11 skipped (Testcontainers/Docker) |
| CD-01-P1-006 | PASS/SKIP semantics not enforced | verify-local.sh now returns exit 2 (PARTIAL) when --full skips mandatory checks. Exit codes: 0=PASS, 1=FAIL, 2=PARTIAL, 3=BLOCKED |
| CD-01-P1-007 | Java selectors contradictory | .java-version changed from 17 to 21. Now: .java-version=21, .tool-versions=java temurin-21, pom.xml source/target=17 |
| CD-01-P1-009 | No .nvmrc | .nvmrc created with "24" in Stage 01 |
| CD-02-P1-003 | Migration immutability not proven | scripts/ci/check-migration-immutability.sh created, tested, passes. Integrated into quality-gate.yml repository-policy job |
| CD-02-P1-004 | Maven Wrapper integrity not recorded | Official Apache Maven Wrapper 3.3.4, Maven 3.9.9, HTTPS distribution URL, SHA-256 hashes recorded |

### Open P0 Debt (BLOCKING)

| ID | Title | Status | Blocking Reason |
|---|---|---|---|
| CD-00-P0-001 | Historical admin password (HF-01) | BLOCKED | Issue #173 OPEN — owner access required for credential rotation |
| CD-00-P0-002 | Historical email-proxy fallback (HF-06) | BLOCKED | Issue #173 OPEN — owner verification required |

### Open/Blocked Non-P0 Debt

| ID | Title | Status | Notes |
|---|---|---|---|
| CD-01-P1-002 | CI uses system Maven | OPEN | Legacy workflows; quality-gate.yml uses ./mvnw |
| CD-01-P1-004 | Docker not available locally | BLOCKED | CI is authority |
| CD-01-P1-005 | Flyway not run locally | BLOCKED | CI is authority |
| CD-01-P1-008 | PostgreSQL not run locally | BLOCKED | CI is authority |
| CD-02-P1-001 | Remote quality gate not executed | OPEN | Branch not pushed |
| CD-02-P1-002 | Stage 02 docs incomplete | IN_PROGRESS | Being created now |
| CD-02-P1-005 | Local full gate not executed | BLOCKED | Docker unavailable |
| CD-02-P2-001 | Coverage baseline not proven | OPEN | JaCoCo/Vitest not configured |

## Backend Test Evidence

```
Command: cd apps/sanad-platform && ./mvnw -B -ntp clean test
Result: BUILD SUCCESS
Tests run: 434, Failures: 0, Errors: 0, Skipped: 11
Duration: 29.687s
Commit SHA: 36ca1d4
```

Security regression tests verified as part of the 434:
- OrganizationTenantIsolationTest
- TenantBindingSecurityIntegrationTest
- TokenRevocationIntegrationTest
- CredentialRotationIntegrationTest
- ApiRegressionSuiteTest

## Maven Wrapper Integrity Evidence

| File | SHA-256 |
|---|---|
| mvnw | cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02 |
| mvnw.cmd | 46eedb8419bd14fe70d5bb2916d7b6f51806e51b39d5b76a42610384ca929c1c |
| .mvn/wrapper/maven-wrapper.properties | b0e7995e6522a43fb50b482c0ae81bf639fe7b290d11015c9cb3367f99f1bbb8 |

- Wrapper version: 3.3.4 (official Apache Maven Wrapper)
- Distribution type: only-script (no JAR needed)
- Maven version: 3.9.9
- Distribution URL: https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
- No credentials in URL
- No custom download logic
- Generated via: `mvn wrapper:wrapper -Dmaven=3.9.9`

## Java Parity Evidence

| Selector | Value | Source |
|---|---|---|
| .java-version | 21 | File |
| .tool-versions | java temurin-21 | File |
| pom.xml java.version | 17 | Source/target compatibility |
| pom.xml maven.compiler.source | 17 | Language features |
| pom.xml maven.compiler.target | 17 | Bytecode target |
| Dockerfile build JDK | 21 (maven:3.9-eclipse-temurin-21) | Dockerfile |
| Dockerfile runtime JRE | 21 (eclipse-temurin:21-jre-alpine) | Dockerfile |
| java -version | 21.0.11 | Runtime |
| ./mvnw -v Java version | 21.0.11 | Maven Wrapper |

No contradictions remain.

## P0 Debt Assessment

CD-00-P0-001 and CD-00-P0-002 remain BLOCKED because:
1. Issue #173 is OPEN
2. Credential rotation requires owner access to Resend, Vercel, Render dashboards
3. Old-value rejection verification requires owner-controlled testing
4. The executor does not have owner access

This is documented in `SNAD-Credential-Rotation-and-Email-Activation-Report.md` on main.

## Final Status

```
LOCAL IMPLEMENTATION: PASS
OPEN P0 DEBT: 2 (BLOCKED — owner access required)
DEBT GATE: BLOCKED
FINAL STATUS: BLOCKED
REASON: OPEN_P0_CLOSURE_DEBT
NEXT ACTION: CLOSE SECURITY CREDENTIAL DEBT (Issue #173 — owner action)
```
