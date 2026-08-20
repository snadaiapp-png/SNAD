# G7 UNKNOWN FINAL REGISTER

> **Report ID:** G7-UNKNOWN-V2
> **Date:** 2026-08-12
> **Status:** VERIFIED
> **Purpose:** All unknowns that must be resolved before or during implementation.

---

## 1. UNKNOWN REGISTER

| Unknown ID | Question | Why Unknown | Evidence Needed | Owner/Authority | Blocking? | Required Before Approval? |
|------------|----------|-------------|-----------------|----------------|-----------|--------------------------|
| UNKNOWN-001 | Which mobile framework? (React Native, Flutter, Capacitor, PWA) | No evaluation conducted | Framework comparison, prototype, team skills assessment | Product Team | YES (all client-side) | YES |
| UNKNOWN-002 | ADR-G7-001 final approval? | Operator hasn't reviewed | Operator sign-off | Architecture Team | YES (6 requirements) | YES |
| UNKNOWN-003 | Which encryption approach? (SQLCipher, OS-level, custom) | No security evaluation | Security risk assessment, performance impact analysis | Security Team | YES (SEC-001) | YES |
| UNKNOWN-004 | Payload optimization field list? | Not yet defined | Mobile UX requirements, bandwidth analysis | Product Team | NO | NO |
| UNKNOWN-005 | Sync frequency configuration? | Not yet defined | Battery impact analysis, data freshness requirements | Product Team | NO | NO |
| UNKNOWN-006 | Storage limits per device? | Not yet defined | Device capability analysis, data volume estimates | Technical Team | NO | NO |
| UNKNOWN-007 | Security threat model for mobile offline? | Not yet conducted | Threat modeling session | Security Team | NO | RECOMMENDED |
| UNKNOWN-008 | Device identity storage mechanism? | Not yet defined | OS-specific secure storage evaluation | Technical Team | NO (SEC-003 is P2) | NO |

---

## 2. BLOCKING UNKNOWNS (3)

| Unknown | Blocks | Required Before | Impact if Unresolved |
|---------|--------|-----------------|---------------------|
| UNKNOWN-001 (Framework) | All client-side requirements | Client implementation | Cannot start any client work |
| UNKNOWN-002 (ADR) | 6 sync/conflict requirements | WP-G | Cannot implement conflict resolution |
| UNKNOWN-003 (Encryption) | SEC-001, SEC-002 | WP-I | Cannot implement security layer |

---

## 3. NON-BLOCKING UNKNOWNS (5)

| Unknown | Impact | Can Defer Until |
|---------|--------|-----------------|
| UNKNOWN-004 (Payload) | Minor optimization | After initial API implementation |
| UNKNOWN-005 (Frequency) | User experience tuning | After basic sync works |
| UNKNOWN-006 (Storage) | Device-specific limits | After local storage implemented |
| UNKNOWN-007 (Threat Model) | Security hardening | Before production deployment |
| UNKNOWN-008 (Device Identity) | Device registration (P2) | After basic auth works |

---

## 4. UNKNOWN RESOLUTION STATUS

| Unknown | Status | Resolution Date |
|---------|--------|----------------|
| UNKNOWN-001 | OPEN | — |
| UNKNOWN-002 | OPEN | — |
| UNKNOWN-003 | OPEN | — |
| UNKNOWN-004 | OPEN | — |
| UNKNOWN-005 | OPEN | — |
| UNKNOWN-006 | OPEN | — |
| UNKNOWN-007 | OPEN | — |
| UNKNOWN-008 | OPEN | — |

**RESOLVED UNKNOWNS: 0**
**OPEN UNKNOWNS: 8 (3 blocking, 5 non-blocking)**

---

*Generated: 2026-08-12*
