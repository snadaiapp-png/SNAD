# SNAD Infrastructure Hardening — Central Closure Debt Register

## Status Legend
- OPEN: Debt identified, not yet addressed
- IN_PROGRESS: Being worked on
- BLOCKED: Cannot close without external action (owner access, remote CI, etc.)
- READY_FOR_VERIFICATION: Implementation complete, awaiting evidence
- CLOSED: Evidence verified and recorded
- REOPENED: Previously closed, now reopened
- ACCEPTED_RISK: Explicitly accepted by decision (requires ADR)

## Inherited Debt (from Stage 00 and 01)

| ID | Title | Severity | Status | Blocking? | Evidence |
|---|---|---|---|---|---|
| CD-00-P0-001 | Historical admin password in git history (HF-01) | P0 | BLOCKED | YES | Issue #173 OPEN, owner access required for rotation |
| CD-00-P0-002 | Historical email-proxy fallback (HF-06) | P0 | BLOCKED | YES | Issue #173 OPEN, owner verification required |
| CD-01-P1-001 | Maven Wrapper was custom (not official) | P1 | CLOSED | NO | Replaced with official Apache Maven Wrapper 3.3.4 in Stage 02 |
| CD-01-P1-002 | CI uses system Maven, not ./mvnw | P1 | OPEN | NO | quality-gate.yml uses ./mvnw; legacy workflows still use mvn |
| CD-01-P1-003 | Backend tests not run locally | P1 | CLOSED | NO | `./mvnw -B -ntp clean test` = 434 passed, 0 failed, 11 skipped |
| CD-01-P1-004 | Docker not available locally | P1 | BLOCKED | NO | Docker not installed in execution environment; CI is authority |
| CD-01-P1-005 | Flyway live validation not run locally | P1 | BLOCKED | NO | Requires Docker; quality-gate.yml has flyway-validation job for CI |
| CD-01-P1-006 | PASS/SKIP semantics not enforced | P1 | CLOSED | NO | verify-local.sh now returns exit 2 (PARTIAL) when --full skips mandatory checks |
| CD-01-P1-007 | Java selectors contradictory (.java-version=17, .tool-versions=21) | P1 | CLOSED | NO | .java-version changed to 21; pom.xml source/target remains 17 |
| CD-01-P1-008 | PostgreSQL integration not run locally | P1 | BLOCKED | NO | Requires Docker; quality-gate.yml has backend-postgres-integration job |
| CD-01-P1-009 | No .nvmrc or .node-version | P1 | CLOSED | NO | .nvmrc created with "24" in Stage 01 |

## New Debt (from Stage 02)

| ID | Title | Severity | Status | Blocking? | Evidence |
|---|---|---|---|---|---|
| CD-02-P1-001 | Mandatory remote quality gate not executed | P1 | OPEN | YES (remote) | Branch not pushed; quality-gate.yml exists but not validated on CI |
| CD-02-P1-002 | Stage 02 documentation not proven complete | P1 | IN_PROGRESS | NO | Missing docs being created in Stage 02A |
| CD-02-P1-003 | Migration immutability not proven as independent check | P1 | CLOSED | NO | Script exists and passes; integrated into repository-policy job in quality-gate.yml |
| CD-02-P1-004 | Maven Wrapper integrity evidence not recorded | P1 | CLOSED | NO | SHA-256 hashes recorded; official Apache Maven Wrapper 3.3.4 confirmed |
| CD-02-P1-005 | Local full quality gate not executed | P1 | BLOCKED | NO | Docker not available locally; CI is authority |
| CD-02-P2-001 | Coverage baseline not proven | P2 | OPEN | NO | JaCoCo not in pom.xml; Vitest coverage not configured |

## Summary

| Metric | Count |
|---|---|
| Total inherited debt | 11 |
| Total new debt | 6 |
| Closed | 6 |
| Open (non-blocking) | 3 |
| Open (blocking - remote only) | 1 (CD-02-P1-001) |
| Blocked (P0 - owner access) | 2 (CD-00-P0-001, CD-00-P0-002) |
| Blocked (P1 - Docker unavailable) | 3 |
| Open P0 debt | 2 |
| Open blocking P1 debt (local) | 0 |
| Open blocking P1 debt (remote only) | 1 |
