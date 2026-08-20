# Phase 14: Master Risk Register

Risk register for G7: Mobile Sync Architecture.

---

## RISK-001: ADR not approved

| Field | Value |
|-------|-------|
| **ID** | RISK-001 |
| **Description** | Architecture Decision Record (ADR-G7-001) for conflict resolution policy may not be approved in time |
| **Probability** | HIGH |
| **Impact** | HIGH |
| **Mitigation** | Expedite ADR review |

---

## RISK-002: No mobile framework selected

| Field | Value |
|-------|-------|
| **ID** | RISK-002 |
| **Description** | Mobile app framework (React Native, Flutter, Capacitor, or PWA) has not been selected |
| **Probability** | MEDIUM |
| **Impact** | HIGH |
| **Mitigation** | Select framework early |

---

## RISK-003: Offline encryption not defined

| Field | Value |
|-------|-------|
| **ID** | RISK-003 |
| **Description** | Encryption strategy for offline data (SQLCipher, OS-level, or application-level) has not been determined |
| **Probability** | HIGH |
| **Impact** | HIGH |
| **Mitigation** | Define encryption strategy |

---

## RISK-004: Conflict resolution complexity

| Field | Value |
|-------|-------|
| **ID** | RISK-004 |
| **Description** | The 12 conflict classes for sync may prove more complex than anticipated |
| **Probability** | MEDIUM |
| **Impact** | HIGH |
| **Mitigation** | Start with simple policies, extend later |

---

## RISK-005: Performance requirements not met

| Field | Value |
|-------|-------|
| **ID** | RISK-005 |
| **Description** | Mobile API response time may exceed the 200ms target |
| **Probability** | MEDIUM |
| **Impact** | MEDIUM |
| **Mitigation** | Early performance testing |

---

## RISK-006: Scope creep (G8 dependencies)

| Field | Value |
|-------|-------|
| **ID** | RISK-006 |
| **Description** | Dependencies on G8 may introduce scope creep into G7 |
| **Probability** | MEDIUM |
| **Impact** | MEDIUM |
| **Mitigation** | Strict scope enforcement |

---

## RISK-007: Multi-device conflict complexity

| Field | Value |
|-------|-------|
| **ID** | RISK-007 |
| **Description** | Handling conflicts across multiple devices simultaneously is significantly more complex than single-device |
| **Probability** | HIGH |
| **Impact** | HIGH |
| **Mitigation** | Start with single-device, add multi-device later |

---

## RISK-008: Test coverage gaps

| Field | Value |
|-------|-------|
| **ID** | RISK-008 |
| **Description** | The 26 planned tests may not adequately cover all sync scenarios and edge cases |
| **Probability** | HIGH |
| **Impact** | MEDIUM |
| **Mitigation** | Test-driven development |

---

## Summary

| Probability | Impact | Count | IDs |
|-------------|--------|-------|-----|
| HIGH | HIGH | 3 | RISK-001, RISK-003, RISK-007 |
| HIGH | MEDIUM | 2 | RISK-008 |
| MEDIUM | HIGH | 2 | RISK-002, RISK-004 |
| MEDIUM | MEDIUM | 2 | RISK-005, RISK-006 |

### Risk Heatmap

| | LOW Impact | MEDIUM Impact | HIGH Impact |
|-------------|------------|---------------|-------------|
| **HIGH Prob** | - | RISK-008 | RISK-001, RISK-003, RISK-007 |
| **MEDIUM Prob** | - | RISK-005, RISK-006 | RISK-002, RISK-004 |
| **LOW Prob** | - | - | - |
