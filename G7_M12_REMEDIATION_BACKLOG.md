# G7 Mission 12 — Remediation Backlog

**Date:** 2026-08-12
**Mission:** Verification only — no fixes applied

---

## FIX-001: Replace XOR with AES-256-GCM Encryption

| Field | Value |
|-------|-------|
| **FIX_ID** | G7-FIX-001 |
| **DEFECT** | G7-DEF-001 (XOR encryption) |
| **REQUIREMENT** | SEC-001 |
| **PRIORITY** | P0 — CRITICAL |
| **FILES** | apps/mobile/src/storage/encryption.ts |
| **REMEDIATION** | Replace XOR cipher (lines 57-62, 104-106) with proper AES-256-GCM implementation using expo-crypto's encryptAsync/decryptAsync. Ensure: (1) random IV per encryption, (2) GCM authentication tag verified on decrypt, (3) no plaintext fallback on decrypt failure. |
| **TEST_REQUIRED** | Encryption roundtrip test, tampered ciphertext detection, key rotation |
| **DEPENDENCIES** | expo-crypto AES-256-GCM API availability |

---

## FIX-002: Create Mobile Project Configuration

| Field | Value |
|-------|-------|
| **FIX_ID** | G7-FIX-002 |
| **DEFECT** | G7-DEF-002 (Missing build config) |
| **REQUIREMENT** | ARCH-002 |
| **PRIORITY** | P0 — CRITICAL |
| **FILES** | apps/mobile/package.json, apps/mobile/tsconfig.json, apps/mobile/app.json, apps/mobile/babel.config.js, apps/mobile/jest.config.js |
| **REMEDIATION** | Create Expo managed workflow project: (1) package.json with dependencies (expo, expo-crypto, expo-secure-store, expo-sqlite, react, react-native), (2) tsconfig.json with strict mode, (3) app.json for Expo, (4) babel.config.js, (5) jest.config.js for test runner. Run npm install. |
| **TEST_REQUIRED** | TypeScript compilation, Jest test execution, Expo build |
| **DEPENDENCIES** | Node.js v24.18.1 (available) |

---

## FIX-003: Recalculate and Correct Acceptance Gates

| Field | Value |
|-------|-------|
| **FIX_ID** | G7-FIX-003 |
| **DEFECT** | G7-DEF-003 (Inflated gates) |
| **REQUIREMENT** | Governance integrity |
| **PRIORITY** | P1 — HIGH |
| **FILES** | G7_ACCEPTANCE_GATES.md, G7_DOD_FINAL.md |
| **REMEDIATION** | Update gate status to reflect actual runtime evidence: 5 PASS, 9 CONDITIONAL, 1 FAIL, 2 BLOCKED. Update DoD from 84.8% to 63.0%. |
| **TEST_REQUIRED** | Manual review of recalculation |
| **DEPENDENCIES** | None |

---

## Backlog Summary

| Fix | Priority | Effort | Dependencies |
|-----|----------|--------|-------------|
| FIX-001 | P0 | Medium | expo-crypto API |
| FIX-002 | P0 | Low | None |
| FIX-003 | P1 | Low | None |

**Total remediation items: 3**
**Estimated effort: 2-4 hours**
