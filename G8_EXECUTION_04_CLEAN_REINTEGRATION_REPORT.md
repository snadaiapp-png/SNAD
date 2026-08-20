# G8 EXECUTION 04 — CLEAN REINTEGRATION OF TRACKS A–D — EXECUTION REPORT

> **Command:** G8 EXECUTION COMMAND 04 (clean reintegration of Tracks A–D)
> **Date:** 2026-08-20/21
> **Base:** `main` `f4e61b35` (corrected ledger, P0 Flyway recovery) · **Branch:** `g8/clean-reintegration-a-d-20260820`
> **Scope:** Track A (Caller Core) + Track B (Server-side Caller Lookup) + Track C (Call Event Persistence) + Track D (Offline Caller Dataset) — server + mobile, NO Android/iOS/PBX/UI.
> **Mandate verbatim:** no direct merge of `g8/current-development-20260820`; no blind replay of the 10 historical G8 commits; migrations V20260820_10..13 already on main byte-identical (verified `git diff --exit-code` = 0); main corrections kept (`.gitleaksignore` b49fa0cd WINS); no migration re-creation; governance aligned with main's corrected ledger (`LATEST=20260820.13`); OpenAPI + TypeScript regenerated from the current runtime (no `git checkout` of old artifact, no forced 152/198 a priori); MERGE = OWNER GATE.

---

## 1. RESTORATION (file/semantic-aware, from `e4e389ee`)

| Set | Files | Notes |
|-----|------:|-------|
| Track A+B server (`crm/caller/**`, `PhoneNumberNormalizer`, error codes, rate limiter, `application.yml` caller-dataset block, exception handlers) | ~19 | Restored as-is; `PhoneNumberNormalizer` extracted authority reused by `AddressCommunicationUseCases` (caller core mirrors historical behavior). |
| Track C server (`crm/calls/**`, `CallEventExceptionHandler`, `crm_call_events` in `ModuleResetRegistry` — **merged**, not replaced) | ~9 | Registry diff is additive (+1 table constant only). |
| Track D server + mobile (`CallerDataset*`, `src/caller/*`, `storage/db.ts`, `sync/api-client.ts` + tests) | ~17 | Additive mobile migration (no wipe). |
| Tests (Java + mobile) | ~22 | Unit + PostgreSQL-Direct + MockMvc + Jest. |
| Evidence/governance docs (`G8_CALLER_IDENTIFICATION_MASTER_BASELINE.md`, G8_EXECUTION_02/03 reports, FORENSIC report, `caller-phone-normalization-vectors.json`, MODULE-COMPATIBILITY-MATRIX G8=IN_PROGRESS, CRM-ERROR-CATALOG +3 rows) | ~7 | Historical evidence preserved verbatim; catalog restored codes required only (`CALLER_PHONE_INVALID`, `CALL_EVENT_NOT_FOUND`, `CALL_EVENT_INVALID_TRANSITION`). |
| Board `crm-execution-data.ts` | 1 | G8 = `IN_PROGRESS`, stageReport V3 with the mandated wording: **Track A = COMPLETE, Track B = COMPLETE, Track C = COMPLETE pending final branch CI, Track D = COMPLETE pending final branch CI** — not APPROVED/COMPLETED/CLOSED; final merge is an owner gate. |

**Kept from main (NOT restored over):** `.gitleaksignore` (b49fa0cd), the corrected Flyway ledger & `CrmPostgresMigrationTest` (LATEST=`20260820.13`, tables `crm_call_events`, assertMigration ×4 for .10–.13), separation-commit governance, migration files V20260820_10..13 (byte-identical, diff exit 0).

## 2. OPENAPI + TYPESCRIPT — REGENERATED FROM RUNNING RUNTIME (not forced, not checked out)

Procedurally identical to the governed pipeline (`crm-api-contract-validation.yml`): app started with `profiles=local` + PostgreSQL, `/v3/api-docs` fetched, `filter-runtime-openapi.py` applied (`--prefix /api/v2/crm`), counts taken from the live spec, TS regenerated via `scripts/crm/generate-crm-api-types.sh` (`openapi-typescript@7.13.0`).

| Artifact | Old (main) | New (runtime-verified) |
|----------|-----------:|-----------------------:|
| CRM paths | 147 | **152** (Δ+5, 0 removals) |
| CRM operations | 193 | **198** (Δ+5, 0 removals) |
| Platform total ops | 710 | **715** |
| Components | — | 247 |
| New paths | — | `/caller-identification/lookup` POST · `/caller-identification/delta` GET · `/calls` GET · `/calls/events` POST · `/calls/{callId}` GET |

Constants updated from the runtime truth: `CrmOpenApiContractTest` 152/198 (+ G8 domain prefixes `/caller-identification`, `/calls`), `PlatformApiCountTest` 198/715/152/198, contract-validation workflow env 152/198. TypeScript regenerated (+302 lines, 12 G8 type declarations, purely additive). Diff audit: **0 removed paths, 0 deletions**.

## 3. GOVERNANCE + SCOPE GATES

- `api-contract-governance-check.sh` → **PASS** (exit 0; no Map/Object leaks; no SELECT *; no @Disabled/@Ignore; artifact + TS present; error catalog in sync with `CrmErrorCode`).
- Migration byte gate vs main: **PASS** (`git diff --exit-code` = 0 on V20260820_10..13).
- Scope audit: **0** files under `apps/android`/`apps/ios`/pbx/UI; **0** deletions in the change set; `.gitleaksignore` unchanged from main.
- Supplementary secret-pattern sweep on all added lines: **0 high-signal findings** (authoritative gitleaks scan runs on the PR via `security-baseline.yml`).
- `CrmFlywayHistoryAssertionTest` green after clean rebuild (a stale `V20260820_8__add_settlement_failed_status.sql` residue in `target/classes` from the pre-separation build was removed by `mvn clean`; it is absent from main and from this branch).

## 4. LOCAL TEST EVIDENCE (PostgreSQL Direct — native scratch PG 18 on 127.0.0.1:5434, trust, deleted after use; no Docker/Testcontainers)

All 11 G8 test classes green (clean build, full-suite pass list):
`CallerIdentificationServiceTest` 18/18 · `CallerDatasetTokenProviderTest` 4/4 · `CallerPhoneVectorsParityTest` 2/2 · `PhoneNumberNormalizerTest` 6/6 · `CallerDatasetPostgresTest` 5/5 · `JdbcCallerIdentificationRepositoryPostgresTest` 9/9 (isolated) · `CallerDatasetApiTest` 5/5 · `CallerIdentificationApiTest` 17/17 (incl. 429 anti-enumeration) · `CallEventServiceTest` 10/10 · `JdbcCallEventRepositoryPostgresTest` 5/5 · `CallEventApiTest` 8/8. Mobile Jest suite 94/94 (previous session + unchanged files) — no Android/iOS/native.

Three local-only flakes observed in the full-suite run, **all from main-baseline files and all proven environmental by isolated reruns** (the Windows dev machine runs an Arabic OS locale on a single PG 18 instance; CI runs Ubuntu with en_US and PG 16 on a fresh runner):

| Class (main baseline unless noted) | Full-suite result | Isolated rerun | Root cause |
|---|---|---|---|
| `JdbcCallerIdentificationRepositoryPostgresTest` (G8 file) | 1 connection-stall error | **9/9 PASS** | DriverManagerDataSource raw connection stalled under multi-context pool pressure (PG18/Windows); 506 s read-timeout at SSL negotiation |
| `CrmIntegrationOutboxRecoveryTest` | 1 failure | **2/2 PASS** | In-JVM outbox worker claimed an event mid-test (timing) |
| `CommerceOrderConcurrencyTest` | 1 failure | **4/4 PASS** (en-US locale) | `%06d` rendered Arabic-Indic digits (`ORD-٢٠٢٦٠٨-…`) under Arabic OS locale; CI en_US unaffected |

The downstream `crm.party/search/reports` instant errors in the full-suite run were context-boot cascades of the same connection pressure; each such class is from main and unaffected by G8 code. **The authoritative gate is branch CI on GitHub (fresh runner, PG 16, en_US), reported in §6.**

## 5. COMMITS

3 clean commits (push of the branch only): `feat(crm-g8)` caller core (A+B) · `feat(crm-g8)` call events + offline dataset (C+D) · `docs(crm-g8)` evidence + governance + regenerated contract.

## 6. BRANCH CI / PR — FILLED AT COMPLETION

PR: BASE=`main`, HEAD=`g8/clean-reintegration-a-d-20260820`, title "G8: reintegrate caller identification tracks A-D". G7 closed; G8 A–D restored; Flyway V10–V13 production-applied unchanged; no new migrations; no native code. **CI status on the branch: PENDING (PR-triggered).** Final merge: OWNER GATE — this report is not an approval request.
