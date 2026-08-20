# Phase 15: Unknown Register

Document all unknowns that affect G7 implementation.

---

## UNKNOWN-001: Exact mobile app framework

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-001 |
| **Question** | React Native, Flutter, Capacitor, or PWA? |
| **Evidence Missing** | No framework selected |
| **Impact** | Affects client-side implementation |
| **Owner** | Mobile Team |
| **Decision Needed** | Framework selection |
| **Blocking** | YES (affects GAP-005) |

---

## UNKNOWN-002: Conflict resolution policy final approval

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-002 |
| **Question** | Is the proposed policy acceptable? |
| **Evidence Missing** | Operator review pending |
| **Impact** | Blocks all conflict-related implementation |
| **Owner** | Operator |
| **Decision Needed** | Policy approval |
| **Blocking** | YES (affects GAP-004) |

---

## UNKNOWN-003: Encryption strategy

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-003 |
| **Question** | SQLCipher, OS-level, or application-level? |
| **Evidence Missing** | No encryption analysis |
| **Impact** | Affects offline data security |
| **Owner** | Security Team |
| **Decision Needed** | Encryption approach |
| **Blocking** | YES (affects GAP-006) |

---

## UNKNOWN-004: Mobile API payload optimization

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-004 |
| **Question** | Which fields to include/exclude? |
| **Evidence Missing** | No field analysis |
| **Impact** | Affects mobile performance |
| **Owner** | Mobile Team |
| **Decision Needed** | Payload schema |
| **Blocking** | NO (can default to all fields) |

---

## UNKNOWN-005: Sync frequency guidance

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-005 |
| **Question** | How often should mobile sync? |
| **Evidence Missing** | No operational guidance |
| **Impact** | Affects data freshness and battery |
| **Owner** | Product Team |
| **Decision Needed** | Sync policy |
| **Blocking** | NO (can use defaults) |

---

## UNKNOWN-006: Offline storage size limit

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-006 |
| **Question** | Maximum local storage per device? |
| **Evidence Missing** | No size analysis |
| **Impact** | Affects device storage |
| **Owner** | Product Team |
| **Decision Needed** | Storage policy |
| **Blocking** | NO (can use defaults) |

---

## UNKNOWN-007: Agent F (Security) complete analysis

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-007 |
| **Question** | Full security threat model for mobile sync? |
| **Evidence Missing** | No dedicated security output |
| **Impact** | Security gaps may be missed |
| **Owner** | Security Team |
| **Decision Needed** | Security review |
| **Blocking** | NO (partial analysis exists) |

---

## UNKNOWN-008: 101 requirements count discrepancy

| Field | Value |
|-------|-------|
| **ID** | UNKNOWN-008 |
| **Question** | Why does mission plan say 101 requirements when baseline has 39? |
| **Evidence Missing** | Reconciliation of requirement counts |
| **Impact** | May indicate missing requirements |
| **Owner** | Requirements Team |
| **Decision Needed** | Requirement count validation |
| **Blocking** | NO (current 39 are sufficient for implementation) |

---

## Summary

| Blocking | Count | IDs |
|----------|-------|-----|
| YES | 3 | UNKNOWN-001, UNKNOWN-002, UNKNOWN-003 |
| NO | 5 | UNKNOWN-004, UNKNOWN-005, UNKNOWN-006, UNKNOWN-007, UNKNOWN-008 |
