# G7_FINAL_IMPLEMENTATION_REPORT

**Date:** 2026-08-12
**Scope:** G7 Mobile Offline Foundation — defect remediation + implementation pass executed against the approved requirement set (no scope/requirement/architecture changes).
**Authority:** Actual code edits + re-compiled + re-tested in this session.

---

## 1. Implementation performed this pass

### DEF-004 (CRITICAL — SQL injection) → CLOSED ✅
- **Root cause:** `PushSyncService` spliced mutation-payload JSON keys directly into SQL as column identifiers.
- **Fix:** Added a hardcoded per-entity column allowlist (`ALLOWED_COLUMNS`). Payload fields not in the allowlist are dropped; only allowlisted identifiers are ever placed in SQL; all values stay parameterized.
- **Files:** `apps/sanad-platform/.../sync/service/PushSyncService.java` (`createEntity`, `updateEntity`).
- **Test:** `G7DefectFixesTest.pushCreateDropsNonAllowlistedPayloadKeys` — pushes a payload containing `evil) VALUES (1)--` and `tenant_id`; asserts the generated INSERT contains only `name` from the payload and none of the injection fragments. **PASS.**

### DEF-005 (HIGH — no auth/tenant middleware) → CLOSED ✅
- **Root cause:** G7 controllers read `request.getAttribute("tenant_id")` which no filter sets → every endpoint 500'd.
- **Fix:** Resolved tenant/user identity from the platform-standard `TenantContextPort` (backed by `SecurityContextHolder`, populated by the existing `JwtAuthenticationFilter`). Removed the broken `extractTenantId`/`extractUserId` helpers. No new architecture invented; the existing JWT+RLS+tenant stack is now used as designed. Tenant identity is never trusted from client input (Phase 7 rule).
- **Files:** `PullSyncController`, `PushSyncController`, `SyncStatusController`, `ConflictController`.

### DEF-006 (MEDIUM — conflict class coverage) → NON-BLOCKING, expanded (4→7/12) 🟠
- **Root cause:** `ConflictService.detectConflict` produced only C1/C2/C7/C9.
- **Fix:** Added a context-rich overload classifying **C3** (delete-vs-update), **C4** (update-vs-delete), **C10** (cross-tenant attempt) in addition to C1/C2/C7/C9. C5/C6/C8/C11/C12 remain documented — they require entity-state or batch context not available at this classification layer (non-blocking for core sync).
- **Files:** `apps/sanad-platform/.../conflict/service/ConflictService.java`.
- **Test:** `G7DefectFixesTest.conflictClassificationCoversDeleteUpdateAndCrossTenant` — asserts C10/C3/C4/C1. **PASS.**

### DEF-007 (HIGH — RLS GUC mismatch) → CLOSED ✅
- **Root cause:** platform's `TenantRlsConnectionHandler` sets `SET LOCAL app.tenant_id`, but the G7 migration policies checked `app.current_tenant_id` and the G7 services manually `SET app.current_tenant_id` (string-concatenated, session-scoped leak risk). Two parallel GUCs ⇒ G7 RLS would never match at runtime.
- **Fix:** Aligned the G7 migration policies to the platform GUC `app.tenant_id` (fail-closed via `missing_ok => true`); removed the manual `SET`/`RESET` from `PushSyncService`/`PullSyncService` (they now rely on `TenantRlsConnectionHandler` within `@Transactional`, exactly like the rest of the platform); `recordIdempotency` now binds `tenantId` as a parameter instead of reading a GUC.
- **Files:** `db/migration/V20260812_1__...sql`, `PushSyncService.java`, `PullSyncService.java`.

---

## 2. Build & test evidence (re-executed this session)

| Check | Command | Result |
|-------|---------|--------|
| Backend compile | `./mvnw -q compile` | **EXIT 0** |
| Backend defect-fix tests | `./mvnw test -Dtest=G7DefectFixesTest` | **Tests run: 2, Failures: 0, Errors: 0** |
| Mobile typecheck | `npx tsc --noEmit` | **EXIT 0** |
| Mobile unit tests | `npx jest --no-cache` | **52/52 PASS (5 suites)** |

---

## 3. Files changed

| Path | Change |
|------|--------|
| `crm/mobile/sync/web/PullSyncController.java` | DEF-005: identity via TenantContextPort |
| `crm/mobile/sync/web/PushSyncController.java` | DEF-005 |
| `crm/mobile/sync/web/SyncStatusController.java` | DEF-005 |
| `crm/mobile/conflict/web/ConflictController.java` | DEF-005 |
| `crm/mobile/sync/service/PushSyncService.java` | DEF-004 (allowlist) + DEF-007 (RLS GUC) |
| `crm/mobile/sync/service/PullSyncService.java` | DEF-007 |
| `crm/mobile/conflict/service/ConflictService.java` | DEF-006 (C3/C4/C10) |
| `db/migration/V20260812_1__create_mobile_sync_tables.sql` | DEF-007 (RLS GUC → app.tenant_id, fail-closed) |
| `src/test/java/.../crm/mobile/G7DefectFixesTest.java` | NEW: DEF-004 + DEF-006 unit tests |

---

## 4. What remains (BLOCKED on infrastructure, not code)

Per governance: PostgreSQL credentials are not available and the Docker daemon is stopped. We do **not** invent credentials, weaken auth, disable RLS, or substitute H2 for the G7 DB runtime. Therefore the following are implemented-in-source but **runtime-unverified**:

- Flyway migration execution (tables/RLS/trigger) on real PostgreSQL
- Spring Boot startup + G7 endpoint curl tests (ETag/If-Match/412/idempotency/cursor/pull/push/status/conflict)
- The 20 offline/sync runtime scenarios
- RLS tenant-isolation runtime test
- Backend integration tests (`@SpringBootTest`, need context + DB)

See `G7_FINAL_ACCEPTANCE_REPORT.md` for the resulting gate/verdict.
