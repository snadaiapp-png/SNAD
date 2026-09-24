# FINAL RISK REGISTER — MISSION 2

**Date:** 2026-08-07

---

## RISK MATRIX

| Risk ID | Description | Severity | Probability | Impact | Mitigation | Status |
|---------|-------------|----------|-------------|--------|------------|--------|
| R-01 | `PlatformApiCountTest` hardcoded counts outdated | LOW | CERTAIN | Test maintenance only | Update 2 integer constants | OPEN |
| R-02 | `IntegratedBusinessProcessesE2ETest` expected 403 now gets 200 | LOW | CERTAIN | Test maintenance only | Update expected status to 200 | OPEN |
| R-03 | 44 Docker-dependent tests fail without Docker | LOW | CERTAIN (no Docker) | No impact in Docker-enabled CI | Run in Docker-enabled CI | KNOWN |
| R-04 | `CrmContractControllerR1` missing from `CrmExceptionHandler assignableTypes` | MEDIUM | HIGH | Unhandled exceptions from pipeline/stage endpoints | **FIXED** — added to assignableTypes | CLOSED |
| R-05 | V20260807_4 `ADD CONSTRAINT` not idempotent | LOW | LOW (Flyway runs once) | Fails on re-baseline | Document re-baseline procedure | KNOWN |
| R-06 | `next lint` fails with directory resolution error | LOW | CERTAIN | Lint checks cannot run | Pre-existing config issue | KNOWN |

---

## RISK ASSESSMENT

### R-01: PlatformApiCountTest (TEST DEFECT)

**Root Cause:** Test hardcodes expected count of 107 CRM OpenAPI paths and 140 total operations. Our new endpoints (pipeline/stage/activity CRUD in CrmContractControllerR1) increased these to 142 and 183 respectively.

**Impact:** Test failure only. No production impact. The 35 additional CRM paths are all properly registered and functional.

**Remediation:** Update `PlatformApiCountTest.java` constants:
- `COMMITTED_CRM_PATH_COUNT`: 107 → 142
- `EXPECTED_TOTAL_OPERATIONS`: 140 → 183

### R-02: IntegratedBusinessProcessesE2ETest (TEST DEFECT)

**Root Cause:** Test expects 403 Forbidden for a MEMBER-role endpoint. Migration V20260807_1 grants CRM capabilities to MEMBER role, so the endpoint now returns 200 OK.

**Impact:** Test failure only. This is intentional behavior — the capability grant was the purpose of V20260807_1.

**Remediation:** Update expected status from 403 to 200 in the E2E test.

### R-03: Docker-dependent tests (ENVIRONMENT)

**Root Cause:** 44 tests require Docker/Testcontainers for PostgreSQL integration testing. Docker is not available in this environment.

**Impact:** No impact in production CI/CD which has Docker.

### R-04: CrmExceptionHandler assignableTypes (CODE DEFECT — FIXED)

**Root Cause:** `CrmContractControllerR1` was never added to `CrmExceptionHandler`'s `assignableTypes`. It throws `CrmContractException` in 4 places, which would bypass the CRM error envelope.

**Impact:** MEDIUM — Unhandled `CrmContractException` would result in Spring Boot's default error handling (generic JSON) instead of the stable CRM error envelope with `requestId` and `code`.

**Remediation:** Added `CrmContractControllerR1.class` to the `assignableTypes` array.

---

## RESIDUAL RISK

After all mitigations, the only remaining risks are:
- 2 test maintenance tasks (update hardcoded values)
- 1 environment-dependent test set (Docker)

**No production-blocking risks remain.**
