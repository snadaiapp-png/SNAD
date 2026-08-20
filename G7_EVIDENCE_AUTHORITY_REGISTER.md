# G7 Evidence Authority Register

**Established:** 2026-08-11
**Purpose:** Authority hierarchy for all G7 evidence sources
**Scope:** Complete evidence provenance chain for G7 conflict resolution and implementation

---

## Authority Hierarchy (Priority Order)

| Priority | Source | Authority Level | Trust Level |
|----------|--------|----------------|-------------|
| 1 | Production Code | HIGHEST | TRUSTED |
| 2 | Database / Flyway | HIGH | TRUSTED |
| 3 | Executed Tests | HIGH | TRUSTED |
| 4 | API Contracts | MEDIUM-HIGH | TRUSTED |
| 5 | Approved ADRs | MEDIUM | PARTIAL |
| 6 | Architecture | MEDIUM | PARTIAL |
| 7 | Master Documentation | LOW-MEDIUM | PARTIAL |
| 8 | Backlog | LOW | UNTRUSTED |
| 9 | Historical Documents | LOWEST | UNTRUSTED |

---

## Detailed Source Registry

### 1. Production Code

**Authority Level:** HIGHEST
**Trust Level:** TRUSTED
**Location:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/`
**Last Verified:** 2026-08-11
**Scope:** Complete CRM implementation with 465 Java files

**Key Evidence Sources:**
- `ETagService.java` — ETag handling for cache validation
- `IdempotencyService.java` — Idempotency key management
- `CrmErrorCode.java` — Error code definitions
- `CrmExceptionHandler.java` — Exception handling logic
- `JdbcContactRepository.java` — Contact data access
- `JdbcActivityRepository.java` — Activity data access

**Trust Concerns:**
- Source code is authoritative but may contain incomplete implementations
- Verify actual behavior against executed tests

---

### 2. Database / Flyway

**Authority Level:** HIGH
**Trust Level:** TRUSTED
**Location:** `apps/sanad-platform/src/main/resources/db/migration/`
**Last Verified:** 2026-08-11
**Scope:** 30+ migration files defining CRM schema

**Key Evidence Sources:**
- `V20260702_1__create_unified_crm_core.sql` — CRM tables with version columns
- `V20260713_1__create_crm_idempotency_records.sql` — Idempotency tracking
- `V20260716_1__create_crm_tasks.sql` — Task management tables
- `V20260716_2__create_crm_notes.sql` — Notes functionality
- `V20260716_3__create_crm_tags.sql` — Tag management

**Trust Concerns:**
- Schema is authoritative but may not reflect runtime state
- Migration execution order matters for conflict resolution
- Verify against actual database state

---

### 3. Executed Tests

**Authority Level:** HIGH
**Trust Level:** TRUSTED
**Location:** `surefire-reports/`
**Last Verified:** 2026-08-11
**Scope:** Test execution results

**Key Evidence Sources:**
- JUnit test results
- Integration test outcomes
- Performance test metrics

**Trust Concerns:**
- **NO G7-SPECIFIC TESTS EXIST YET**
- Test coverage may be incomplete for G7 features
- Manual verification may be required for untested code paths

---

### 4. API Contracts

**Authority Level:** MEDIUM-HIGH
**Trust Level:** TRUSTED
**Location:** `EXECUTION-API-CONTRACT.md`
**Last Verified:** 2026-08-11
**Scope:** API specifications and contracts

**Key Evidence Sources:**
- `EXECUTION-API-CONTRACT.md` — Formal API contract
- Existing CRM API controllers
- Endpoint definitions and request/response schemas

**Trust Concerns:**
- Contract may not reflect actual implementation
- Version drift between contract and code possible
- Verify against running API endpoints

---

### 5. Approved ADRs

**Authority Level:** MEDIUM
**Trust Level:** PARTIAL
**Location:** `ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md`
**Last Verified:** 2026-08-11
**Scope:** Architecture Decision Records

**Key Evidence Sources:**
- `ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md` — Mobile conflict resolution decision

**Trust Concerns:**
- **STATUS: REQUIRES_REVISION (NOT APPROVED)**
- Decision is not yet finalized
- Cannot be used as authoritative source until approved
- Pending stakeholder review and sign-off

---

### 6. Architecture

**Authority Level:** MEDIUM
**Trust Level:** PARTIAL
**Location:** Project root and documentation directories
**Last Verified:** 2026-08-11
**Scope:** Architectural specifications and boundaries

**Key Evidence Sources:**
- `G7_MOBILE_FOUNDATION_MASTER_BASELINE.md` — Master baseline
- `G7_IMPLEMENTATION_BOUNDARY.md` — Implementation boundaries

**Trust Concerns:**
- Architecture documents may be aspirational vs. actual
- Verify against implemented code
- May contain outdated information

---

### 7. Master Documentation

**Authority Level:** LOW-MEDIUM
**Trust Level:** PARTIAL
**Location:** Documentation directories
**Last Verified:** 2026-08-11
**Scope:** Comprehensive documentation suite

**Key Evidence Sources:**
- `G7_CONFLICT_RESOLUTION_DECISION_REPORT.md`
- `G7_C2_C3_ARCHITECTURAL_DECISION.md`
- `G7_CONFLICT_TEST_SPEC.md`
- `G7_TRACK_C_FORENSIC_REPORT.md`
- `G7_FORENSIC_EXTRACTION_REPORT.md`

**Trust Concerns:**
- Documentation may be incomplete or outdated
- Multiple versions may exist
- Cross-reference with code and database

---

### 8. Backlog

**Authority Level:** LOW
**Trust Level:** UNTRUSTED
**Location:** Baseline documentation
**Last Verified:** 2026-08-11
**Scope:** Implementation backlog and task lists

**Key Evidence Sources:**
- Implementation backlog in baseline
- Task status and priorities
- Planned vs. actual work

**Trust Concerns:**
- May contain planned but unimplemented features
- Status may be outdated
- Not reliable for current state verification

---

### 9. Historical Documents

**Authority Level:** LOWEST
**Trust Level:** UNTRUSTED
**Location:** `agent-ctx/MISSION-*.md` files
**Last Verified:** 2026-08-11
**Scope:** Previous mission reports and historical context

**Key Evidence Sources:**
- `agent-ctx/MISSION-*.md` files
- Previous mission reports
- Historical decision logs

**Trust Concerns:**
- May contain superseded information
- Historical context only, not authoritative for current state
- Reference for understanding evolution, not current authority

---

## Trust Level Definitions

### TRUSTED
- Source is authoritative and reliable
- Can be used as primary evidence
- Minimal verification required

### PARTIAL
- Source is relevant but may contain gaps
- Requires cross-referencing with higher authority sources
- Use with caution

### UNTRUSTED
- Source is informational only
- Cannot be used as primary evidence
- Historical or aspirational content only

---

## Evidence Usage Guidelines

1. **Conflict Resolution:** Always reference sources in priority order
2. **Verification:** Cross-reference multiple sources when possible
3. **Gaps:** If authoritative source is missing, escalate immediately
4. **Updates:** Register should be updated when sources change
5. **Disputes:** When sources conflict, higher authority wins

---

## Current Trust Concerns Summary

1. **No G7-specific tests exist** — Executed Tests source is incomplete
2. **ADR-G7-001 is NOT APPROVED** — Cannot be used as authority
3. **Architecture documents may be aspirational** — Verify against code
4. **Documentation may be outdated** — Cross-reference with implementation

---

**Last Updated:** 2026-08-11
**Maintained By:** G7 Evidence Management
**Review Cycle:** Weekly or upon significant changes
