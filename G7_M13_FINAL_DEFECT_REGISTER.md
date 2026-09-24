# G7_M13_FINAL_DEFECT_REGISTER — Final Defect Status (CORRECTED)

**Mission:** 13 — Critical Defect Remediation + Runtime Re-Verification
**Re-verified:** 2026-08-12 (this version supersedes the earlier 08:57 register, which reported "0 open / 0 new")

---

## 1. Defect Summary (CORRECTED)

| Total | Closed | Open | New (found in M13 re-verification) |
|-------|--------|------|-----------------------------------|
| 6 | 3 | **3** | **3** |

The earlier register's claim of "0 open / 0 new" is **incorrect**: three new defects were found once the G7 backend source was actually read (the earlier pass verified only compilation/file existence).

---

## 2. DEF-001 — XOR cipher instead of AES-256-GCM — **CLOSED** ✅

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Component | `apps/mobile/src/storage/encryption.ts` |
| M12 status | OPEN |
| M13 status | **CLOSED** (re-verified) |
| Root cause | Original used XOR instead of AES-256-GCM |
| Remediation | Rewritten on Web Crypto API (`crypto.subtle`) |
| Independent verification | `grep` XOR → only in tests/comments; AES-GCM at encryption.ts L33/79/82/140/192; `jest` security suite 12/12 PASS |

---

## 3. DEF-002 — Missing mobile project configuration — **CLOSED** ✅

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Component | `apps/mobile/` |
| M13 status | **CLOSED** (re-verified) |
| Independent verification | `npx tsc --noEmit` → EXIT 0 (re-run) |

---

## 4. DEF-003 — Test infrastructure broken — **CLOSED** ✅

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Component | `apps/mobile` jest config + tests |
| M13 status | **CLOSED** (re-verified) |
| Independent verification | `npx jest --no-cache` → 52/52 PASS, 5 suites, 15.2s (re-run) |

---

## 5. DEF-004 — **NEW — CRITICAL — SQL Injection via JSON-key column identifiers** 🔴 OPEN

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Component | `apps/sanad-platform/.../sync/service/PushSyncService.java` |
| Location | `createEntity` L199-210; `updateEntity` L235-254 |
| Root cause | Mutation payload JSON field names are concatenated directly into SQL as column identifiers; only values are parameterized |
| Affected requirements | API-004, SEC (injection), SYNC-004 |
| Affected gates | G5 (API), security audit |
| Evidence | `String.format("INSERT INTO %s (%s, ...)", tableName, columns)` where `columns` contains raw `entry.getKey()` |
| Remediation | Whitelist allowed columns per entity type; reject unknown payload keys; never splice identifiers from untrusted input |
| Verification required | Re-run push endpoint with malicious payload → must be rejected, no SQL executed |
| Status | **OPEN** |

---

## 6. DEF-005 — **NEW — HIGH — No authentication/tenant-resolution middleware** 🔴 OPEN

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Component | `PullSyncController.extractTenantId` (L78), `PushSyncController.extractTenantId/extractUserId` (L79/L87) |
| Root cause | Controllers read `request.getAttribute("tenant_id")`/`"user_id"` that no filter sets; Javadoc literally says `// TODO: Extract from JWT claims` |
| Affected requirements | API-002 (auth middleware) → now **FAIL** |
| Affected gates | G5 (API runtime) |
| Evidence | At runtime every G7 endpoint throws `IllegalStateException("Tenant ID not found in request context")` → HTTP 500 |
| Remediation | Implement a JWT `OncePerRequestFilter` that sets tenant_id/user_id from claims; register for `/api/v2/mobile/**` |
| Verification required | Authenticated request reaches controller; unauthenticated → 401 |
| Status | **OPEN** |

---

## 7. DEF-006 — **NEW — MEDIUM — Only 4 of 12 conflict classes detected** 🟠 OPEN

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| Component | `ConflictService.detectConflict` (L60-125) |
| Root cause | Only C1, C2, C7, C9 are produced; C3–C6, C8, C10–C12 are documented but not implemented |
| Affected requirements | CONFLICT-001 → now **CONDITIONAL** |
| Evidence | Single `detectConflict` method has branches only for `<serverVersion` (C1/C2) and `==serverVersion` (C1/C7) and `>serverVersion` (C9) |
| Remediation | Add detection branches (at least C3/C4 delete-vs-update, C10 cross-tenant) |
| Status | **OPEN** |

---

## 8. Defect Lifecycle

```
DEF-001: OPEN(M12) → REMEDIATED → RE-VERIFIED → CLOSED ✅
DEF-002: OPEN(M12) → REMEDIATED → RE-VERIFIED → CLOSED ✅
DEF-003: OPEN(M12) → REMEDIATED → RE-VERIFIED → CLOSED ✅
DEF-004: — → DISCOVERED(M13 re-verification) → OPEN 🔴  (CRITICAL)
DEF-005: — → DISCOVERED(M13 re-verification) → OPEN 🔴  (HIGH)
DEF-006: — → DISCOVERED(M13 re-verification) → OPEN 🟠  (MEDIUM)
```

---

## 9. Governance note

The earlier M13 register ("0 open / 0 new") and final decision ("CONDITIONAL_PASS / G8 GRANTED") were produced by verifying **compilation and file existence** for the backend tier and by incorrectly stating PostgreSQL was unavailable. Independent re-verification (this session) found PostgreSQL running, re-confirmed the mobile tier, and discovered three backend defects via source reading. Per M13 governance rules #12 and #21, the presence of OPEN critical/high defects and unproven backend runtime forces **RELEASE_GATE = BLOCKED** and **G8_PERMISSION = DENIED** until DEF-004/005/006 are remediated and the full backend runtime is exercised against real PostgreSQL.

**See:** `G7_M13_RECONCILIATION_AND_CORRECTED_VERDICT.md` and the corrected `G7_MISSION13_FINAL_RELEASE_DECISION.md`.
