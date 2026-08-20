# G8 EXECUTION 02 — CALLER IDENTIFICATION CORE (TRACK A + B) — EXECUTION REPORT

> **Command:** G8 EXECUTION COMMAND 02 (server-side core only)
> **Date:** 2026-08-20
> **Base SHA:** 36d0512c · **Final SHA:** b3169320 (origin/main)
> **Scope delivered:** TRACK A (Canonical Data & Matching Engine) + TRACK B (Caller Identification API) + RBAC/RLS-compatible access, audit, metrics, tests.
> **NOT started (by design):** crm_call_events (Track C), Offline Caller Dataset (D), Android (E), iOS (F), PBX/VoIP (G), Caller UI (I), production closure (J).

---

## 1. DELIVERED COMPONENTS

| Component | File | Notes |
|-----------|------|-------|
| Single normalization authority | `crm/party/domain/PhoneNumberNormalizer.java` (new) | Extracted from `AddressCommunicationUseCases` without behavior change (G8-02 §4–§5 — ONE authority; CRM-007 write path delegates; regression: `AddressCommunicationUseCasesTest` 4/4). `00→+`, E.164, `05/5/966` Saudi forms with `countryHint=SA`. |
| Match statuses | `crm/caller/domain/CallerMatchStatus.java` | EXACT · AMBIGUOUS · UNKNOWN · PRIVATE_NUMBER · INVALID_NUMBER · RESTRICTED (G8-02 §12). |
| Source contract | `crm/caller/domain/CallerLookupSource.java` | MANUAL · ANDROID_CALL · IOS_CALLER_EXTENSION · PBX · VOIP (forward contract; adapters later). |
| Candidate model | `crm/caller/domain/CallerCandidate.java` | Minimal caller-card projection (§9); `matchSource` tags CANONICAL_COMMUNICATION_METHOD / LEGACY_LEAD_PHONE (§10). |
| Repository contract | `crm/caller/domain/CallerIdentificationRepository.java` | `findActiveCallerCandidates` + `findActiveLeadCandidates`; exact-only; bounded 20+1 (§6/§34); static `legacyLeadPhoneForms`. |
| JDBC implementation | `crm/caller/infrastructure/JdbcCallerIdentificationRepository.java` | Exact point lookup over `idx_crm_communication_methods_lookup (tenant_id, method_type, normalized_value, status)` with `method_type IN ('PHONE','MOBILE')`, `status='ACTIVE'`, ACTIVE owner lifecycle; deterministic ordering `verified DESC, preferred DESC, updated_at ASC, id ASC`; LIMIT 21 (§33 plan test asserts index usage). Lead fallback: exact legacy forms, tenant-scoped, `status IN (NEW,ASSIGNED,CONTACTED,QUALIFIED)`, bounded. |
| Matching engine | `crm/caller/application/CallerIdentificationService.java` | Chain: raw → private sentinel → normalize → rate gate → canonical lookup → lead fallback (only when canonical empty) → tiered ranking (verified PERSON > preferred PERSON > PERSON > ACCOUNT > LEAD) → ambiguity (NO_RANDOM_MATCH, count-only) → privacy (RESTRICTED → RESTRICTED; CONFIDENTIAL → server-side masked names unless READ_RESTRICTED) → result. Audit (no full phone) + Micrometer counters/timer (labels result/source only) + masked debug log. |
| Anti-enumeration | `crm/caller/application/CallerLookupRateLimiter.java` | Per-caller (tenant+user) windowed 60/60s + burst 10/1s, in-memory (single-instance posture, same as CaffeineLoginRateLimiter); 429 via stable `RATE_LIMITED`. The platform `RateLimitFilter` does not engage for JWT traffic (pre-existing documented gap) — G8 applies the minimal compatible protection (§29). |
| API endpoint | `crm/caller/web/CallerIdentificationController.java` | `POST /api/v2/crm/caller-identification/lookup` — POST (no GET, no number in URLs) · `@RequireCapability("CRM.CALLER_ID.READ")` · tenant/user ONLY from authenticated details (contextId pattern) · `tenantId` in body → 400 (explicitly rejected) · source enum validated · `countryHint` ISO alpha-2 · minimal data-minimized card (`CallerLookupResponse` with NON_NULL). |
| Error contract | `CrmErrorCode.CALLER_PHONE_INVALID` (422, documented in CRM-ERROR-CATALOG) + `CallerIdentificationExceptionHandler` (extends central CRM handler; envelope stable) | Invalid phone → 422 structured (baseline §12.1), never 500. |
| RBAC migration | `V20260820_10__seed_crm_caller_identification_capabilities.sql` | Forward-only; idempotent `WHERE NOT EXISTS`; seeds `CRM.CALLER_ID.READ` + `CRM.CALLER_ID.READ_RESTRICTED` and grants to every ACTIVE `ADMIN` role (pattern V20260717_101; role mapping for sales/executive deferred to Track H per governance). |

## 2. GATE EVIDENCE (per command §58)

| Gate | Status | Evidence |
|------|--------|----------|
| PHONE_NORMALIZATION_REUSE | PASS | `PhoneNumberNormalizer` single authority; `PhoneNumberNormalizerTest` 6/6; `AddressCommunicationUseCasesTest` 4/4 (no behavior change); Saudi matrix `05/5/966/00/+966/spaces/dashes` |
| CANONICAL_PHONE_SOURCE | PASS | Lookup queries `crm_communication_methods.normalized_value` only (G8-ADR-001); legacy columns not canonical |
| EXACT_LOOKUP | PASS | `findActiveCallerCandidates` exact equality; index-plan PG test (`lookupQueryUsesTheCommittedIndex` asserts `idx_crm_communication_methods_lookup`, no Seq Scan) |
| TENANT_SCOPED_LOOKUP | PASS | `sameNumberTwoTenantsAreIsolated` (A→A only, B→B only); tenant from authenticated context only |
| MATCHING_ENGINE | PASS | `CallerIdentificationServiceTest` 18/18: verified>preferred>person>account>lead, archived/inactive excluded, deterministic |
| AMBIGUOUS_HANDLING | PASS | Same-rank duplicates → AMBIGUOUS count-only (2) — service unit + API test; overflow bound → AMBIGUOUS |
| UNKNOWN_HANDLING | PASS | No record → UNKNOWN; no auto-create; lookup remains READ-ONLY |
| PRIVATE_NUMBER_HANDLING | PASS | PRIVATE/WITHHELD/BLOCKED/ANONYMOUS/UNKNOWN → PRIVATE_NUMBER without normalizer/DB (unit + API) |
| RESTRICTED_HANDLING | PASS | RESTRICTED → RESTRICTED (no fields) without capability; EXACT full card with `CRM.CALLER_ID.READ_RESTRICTED` |
| RBAC | PASS | Capability aspect enforced; API tests: 401 (no token), 403 (role without caller caps), 200 (granted) |
| AUTH | PASS | `unauthenticatedLookupIsRejected` → 401 |
| PRIVACY | PASS | CONFIDENTIAL masked server-side (names never sent unredacted); RESTRICTED gated; audit/JSON payload contains no full phone |
| ANTI_ENUMERATION | PASS | `burstBeyondLimitIsRateLimited` → 429 on the 11th rapid lookup |
| LOG_REDACTION | PASS | Masked phone in debug log (`••••` + last 4), no names; metrics labels result/source only |
| OPENAPI | PASS | Regenerated via governed pipeline: 148 paths / 194 ops (+ `/caller-identification/lookup` only, 81-line diff, 0 removals); TS types regenerated; CRM API Contract Validation workflow PASS (byte-equality against CI runtime) |
| POSTGRESQL_DIRECT_TESTS | PASS | Repository PG tests on PostgreSQL (CI service 16 / local gate); no Testcontainers |
| REGRESSION | PASS | Full backend suite green in Post-Merge (all 1,9xx tests); CRM-007/communication tests green |
| CI | PASS | Post-Merge Main Verification green on b3169320 (Final gate PASS); CRM API Contract Validation green |

## 3. LEAD FALLBACK — G8-ADR-002 RESOLUTION (recorded)

Baseline ADR-002 pointed to Track A for finalization. Resolved and implemented:
**Option C — secondary source for LEAD only**: `crm_leads.phone` is queried ONLY when the canonical pool is empty, as EXPLICIT · LOWER PRIORITY (tier 4) · TENANT-SCOPED · EXACT-forms-only (derived deterministically from the normalized E.164: `+966…`, digits, national, `0`+national for 966 numbers; reduced exact set otherwise). Every lead result is tagged `matchSource=LEGACY_LEAD_PHONE`. A canonical candidate can never lose to a lead (tier ordering) — the lead query is skipped when canonical candidates exist (hot-path index-friendliness).

## 4. CONTRACT DELTAS

- **API counts:** CRM v2 ops 193 → **194**; platform total 710 → **711**; committed contract 147/193 → **148/194** (`PlatformApiCountTest`, `CrmOpenApiContractTest`, `crm-api-contract-validation.yml`).
- **Error catalog:** `CALLER_PHONE_INVALID` (422) documented.
- **Flyway governance:** `V20260820_10` added as new forward-only migration; applied-migration chains re-asserted (`CrmPostgresMigrationTest` LATEST=20260820.10 + pending lists + `assertMigration` + capability counts 83→85; `CrmFlywayHistoryAssertionTest`; `Crm008bFoundationAcceptanceTest`).

## 5. SCOPE RESPECT (§51/§57)

No ERP/Accounting/HR/POS/Ecommerce/AI/G9/G7 changes. No call events table, no offline projection, no native modules, no UI, no mobile changes, no production deployment certification. The platform's own publish-deploy pipeline ran on the push (recorded as pipeline behavior; G8 is NOT production-certified per command §57).
