# G7_M13_RECONCILIATION_AND_CORRECTED_VERDICT

**Mission:** 13 — Reconciliation of the prior (08:54–08:57) M13 evidence set
**Date:** 2026-08-12
**Purpose:** An independent re-verification was performed. Where the earlier M13 reports conflict with evidence gathered directly in this session, this file is authoritative. It records every correction, the new defects found, and the resulting corrected verdict.

**Bottom line:** The earlier `G7_MISSION13_FINAL_RELEASE_DECISION.md` verdict of `CONDITIONAL_PASS / G8 GRANTED` is **withdrawn**. The governance-compliant verdict is **`RELEASE_GATE = BLOCKED` / `G8_PERMISSION = DENIED`** (see corrected `G7_MISSION13_FINAL_RELEASE_DECISION.md`).

---

## 1. Independent re-verification performed (commands actually re-run)

| Check | Command | Independent result |
|-------|---------|--------------------|
| Mobile typecheck | `npx tsc --noEmit` | EXIT 0 ✅ |
| Mobile tests | `npx jest --no-cache` | 52/52 PASS, 5 suites, 15.2s ✅ |
| Encryption | read `encryption.ts` + XOR grep | AES-256-GCM via `crypto.subtle`; no XOR in prod source ✅ |
| Java build | `./mvnw -q compile` | EXIT 0 ✅ |
| Secrets in source | `grep`/`rg` over Java+TS src | No production secrets (only test fixtures / storage key names) ✅ |
| Postgres availability | `pg_isready` | **accepting connections on :5432** (contradicts "PostgreSQL not available") |
| Docker daemon | `docker ps` | **daemon stopped** (npipe unreachable) |
| Backend source | read PushSync/PullSync/Conflict/2 controllers | idempotency/optimistic-lock/412/cursor present; **3 new defects** |

---

## 2. Corrections to the prior M13 evidence set

### 2.1 Final verdict (was: CONDITIONAL_PASS / G8 GRANTED) → **BLOCKED / DENIED**
The prior verdict marked DB/API/SYNC/BACKEND_TESTS as ⛔ BLOCKED *and then* issued a PASS-ish verdict. This violates M13 governance rule #12 ("cannot run → BLOCKED, not PASS") and rule #21 (no conditional PASS for unproven elements). Corrected verdict: **BLOCKED / G8 DENIED.**

### 2.2 "PostgreSQL not available in verification environment" → **FALSE**
`pg_isready` confirms PostgreSQL 18.4 is running on `localhost:5432`. The blocker is **auth** (no credential in env; no `.pgpass`) and that the Docker daemon is stopped — not absence of PostgreSQL. The earlier reports' stated reason was incorrect.

### 2.3 `G7_M13_DATABASE_RUNTIME_VERIFICATION.md` — wrong table names
The report's "verify tables created" list (`mobile_sync_state`, `mobile_mutation_log`) is **wrong**. Migration `V20260812_1` actually creates:
- `mobile_device_registry`
- `mobile_sync_cursor`  (not `mobile_sync_state`)
- `mobile_sync_log`     (not `mobile_mutation_log`)
- `mobile_conflict_log`

There is **no** `mobile_mutation_log` table server-side (the mutation queue is a client-side SQLite concept).

### 2.4 `G7_M13_API_RUNTIME_REPORT.md` — "compilation verified ⇒ PASS" is invalid evidence
The report marked API-003/API-004 ✅ PASS based on `./mvnw compile` + controller existence. Phase 6 of the mission explicitly forbids "CONTROLLER EXISTS" as evidence. Corrected status for all API runtime items: **⛔ BLOCKED** (no running Spring Boot). Independent `./mvnw compile` → EXIT 0 confirms the **build** gate only, not API correctness.

### 2.5 `G7_M13_57_REQUIREMENTS_RUNTIME_MATRIX.md` — corrected counts
Prior: 38 PASS / 11 CONDITIONAL / 4 BLOCKED / 0 FAIL.
Corrected (items demoted from PASS because they relied on compilation/source alone, or on a missing auth layer):
- API-001 (REST structure): PASS → **CONDITIONAL** (compiles, no runtime)
- API-002 (auth middleware): CONDITIONAL → **FAIL** (DEF-005: no functioning tenant/JWT extraction)
- API-003 (pull API): PASS → **BLOCKED** (no runtime; depends on broken auth)
- API-004 (push API): PASS → **BLOCKED** (no runtime; + DEF-004 SQLi)
- CONFLICT-001 (12 classes): PASS → **CONDITIONAL** (only C1/C2/C7/C9 implemented; DEF-006)

Approx corrected tally (of 57): **~34 PASS / ~13 CONDITIONAL / ~9 BLOCKED / 1 FAIL.** (Exact row-level table to be finalized when backend runtime is unblocked; the gate outcome is unaffected: not-all-verified + 1 FAIL ⇒ BLOCKED.)

### 2.6 `G7_M13_FINAL_DEFECT_REGISTER.md` — "0 open / 0 new" → **2 open critical / 3 new**
Prior register declared all 3 defects CLOSED and 0 new defects. DEF-001/002/003 are indeed CLOSED (re-verified), but the register **missed 3 defects** discovered during re-verification (see §3).

### 2.7 Acceptance gates / DoD — recompute
Earlier: 6 PASS / 1 CONDITIONAL gates; DoD 88.9%. Those numbers counted compilation/source as PASS for DB & API. Corrected: G4 (DB), G5 (API runtime), and the corresponding DoD items are **BLOCKED**, and API auth is **FAIL**. Honest DoD true-pass ≈ 5/9 (~55%), and **below** any defensible release threshold.

---

## 3. New defects found during re-verification (static, in G7 backend source)

### DEF-004 — CRITICAL — SQL Injection via JSON-key column identifiers
- **File:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/mobile/sync/service/PushSyncService.java`
- **Lines:** `createEntity` L199-210 (column list), `updateEntity` L235-254 (SET clauses).
- **Mechanism:** The mutation payload's JSON field names are appended directly into the SQL string as column identifiers (`columns.append(", ").append(entry.getKey())`). Only the *values* are bound as parameters. A payload key such as `x) VALUES (1)--` or `name, (SELECT ...) = (` injects arbitrary SQL.
- **Fix:** Whitelist allowed columns per entity type (the service already has per-entity column knowledge in `PullSyncService.getSelectColumns`), and reject/ignore any payload key not in the whitelist. Never splice identifiers from untrusted input.
- **Status:** OPEN.

### DEF-005 — HIGH — Missing authentication / tenant-resolution middleware
- **Files:** `PullSyncController.extractTenantId` (L78-86), `PushSyncController.extractTenantId` (L79-85), `PushSyncController.extractUserId` (L87-93).
- **Mechanism:** Both controllers resolve tenant/user via `request.getAttribute("tenant_id")` / `"user_id"`. The Javadoc itself reads `// TODO: Extract from JWT claims via SecurityContext`. No security filter/interceptor in the G7 layer (or elsewhere observed) sets these attributes. Consequently every pull/push request throws `IllegalStateException("Tenant ID not found in request context")` → HTTP 500 at runtime.
- **Impact:** API-002 (Authentication middleware) is **not implemented** for the G7 endpoints; the entire G7 API is non-functional at runtime regardless of the database.
- **Fix:** Implement a JWT `OncePerRequestFilter` (or equivalent) that validates the Bearer token and sets `tenant_id`/`user_id` request attributes from claims; register it for `/api/v2/mobile/**`.
- **Status:** OPEN.

### DEF-006 — MEDIUM — Only 4 of 12 conflict classes are detected
- **File:** `ConflictService.detectConflict` (L60-125).
- **Mechanism:** `detectConflict` only ever returns C1, C2, C7, or C9. Classes C3 (delete-vs-update), C4 (update-vs-delete), C5 (state-transition), C6 (ownership), C8 (concurrent create), C10 (cross-tenant), C11 (batch partial), C12 (append) are enumerated in the class Javadoc and in the `mobile_conflict_log.conflict_class` CHECK constraint, but no detection branch produces them.
- **Impact:** CONFLICT-001 ("12 conflict classes") is partially implemented.
- **Fix:** Add detection branches for the remaining classes (at minimum C3/C4 delete-vs-update, C10 cross-tenant).
- **Status:** OPEN.

---

## 4. Security findings (P8)

| Finding | Status |
|---------|--------|
| Hardcoded secrets in source (Java/TS) | **CLEAN** — only test fixtures and SecureStore key names. |
| AES-256-GCM in mobile encryption | **PASS** (verified). |
| RLS tenant isolation at runtime | **BLOCKED** — policy SQL exists (`V20260812_1`) but not executed; needs PostgreSQL. |
| Live plaintext credentials in workspace env files | **NOTE** — `C:\Users\SNADA\ZCodeProject\.env` (deploy tokens) and `SNAD/.env.local` (Render/Vercel) contain real secrets. These are local/gitignored, but their presence on disk is an operational-hygiene risk; recommend rotation + secret-manager migration. |
| SQL injection in backend | **FAIL** — DEF-004 above. |

---

## 5. What is solid and can be carried forward

- **Mobile foundation (DEF-001/002/003): CLOSED and re-verified.** `tsc`, `jest`, encryption all green.
- **Java compilation: PASS.** The G7 backend compiles; idempotency, optimistic-locking, 412, and cursor logic are present in source.
- **G7 schema design (migrations): sound** (FKs, CHECK constraints for C1–C12, RLS policies, retention, `sync_version` trigger) — pending runtime confirmation.

---

## 6. Exact unblock path (to reach a real PASS/FAIL)

1. Provide PostgreSQL access (native creds) **or** `docker compose -f deploy/self-hosted/docker-compose.windows.yml up` (requires Docker Desktop running).
2. Remediate **DEF-005** (auth filter) and **DEF-004** (column whitelist) — otherwise endpoint and push tests are meaningless.
3. `mvn spring-boot:run` → Flyway full chain → verify 4 sync tables + RLS + trigger (correct names).
4. curl each G7 endpoint; capture request→response→status→headers→body→DB-effect.
5. Execute the 20 offline/sync scenarios (Phase 7) + `./mvnw test`.
6. Recompute requirement matrix / gates / DoD from that runtime evidence; re-issue the final decision.

Until those complete, **RELEASE_GATE remains BLOCKED and G8 PERMISSION remains DENIED**, per M13 governance.
