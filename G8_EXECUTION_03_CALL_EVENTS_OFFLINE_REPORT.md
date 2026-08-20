# G8 EXECUTION 03 — CALL EVENT PERSISTENCE + OFFLINE CALLER DATASET — EXECUTION REPORT

> **Command:** G8 EXECUTION COMMAND 03 (TRACK C + TRACK D only)
> **Date:** 2026-08-20
> **Base SHA:** e257802b · **Implementation SHA:** 3b380ab0
> **Scope delivered:** TRACK C (Call Event Persistence & CRM Projection) + TRACK D (Offline Caller Dataset & Secure Local Lookup)
> **NOT started (by design, §80–§83):** Android native (E), iOS native (F), PBX/VoIP provider (G), Caller UI (I), G8 production closure, G9.

---

## 1. DELIVERED COMPONENTS

### TRACK C — call events

| Component | Evidence |
|-----------|----------|
| `V20260820_11__create_crm_call_events.sql` | Call aggregate SSoT (G8-ADR-003): normalized-only phones, `direction`/`source`/`status`/`disposition`/`match_status` enums, tenant-safe composite FKs (`crm_contacts`/`crm_accounts`/`users` — additive `uk_users_tenant_id`), `uq (tenant_id, provider, provider_call_id)`, indexes for matched-entity/agent/status/from-number reads, `ENABLE ROW LEVEL SECURITY` + `call_events_tenant_isolation` policy |
| `V20260820_12__force_rls_crm_call_events.sql` | `FORCE ROW LEVEL SECURITY` (pattern V20260812_3) |
| `V20260820_13__seed_crm_call_event_capabilities.sql` | `CRM.CALL_EVENT.READ`/`WRITE` seeded `WHERE NOT EXISTS` + ACTIVE-ADMIN grants |
| `crm/calls/domain/*` | CallEvent (match snapshot constants + timeline event names), CallStatus (legal transition map + monotonicity rank), CallDirection, CallDisposition, CallEventRepository (bounded cursor list) |
| `crm/calls/infrastructure/JdbcCallEventRepository.java` | Atomic create/get/findByProviderCallId/transition/complete/list; optimistic `version` where applicable |
| `crm/calls/application/CallEventService.java` | Idempotent/atomic/state-aware ingestion (provider_call_id gate), out-of-order → no confirmed-state regression, duration computed server-side on terminal, match binding REUSES `CallerIdentificationService` (no second engine), one `activity_type='CALL'` activity per call at the first terminal transition, business-significant timeline (`crm.call.started/answered/completed/missed`), `AuditPort` (`CALL_EVENT_CREATED/STATUS_CHANGED/LINKED?/DISPOSITION_UPDATED` — no phones), Micrometer counters `call_event_{created,duplicate,transition_rejected,completed}_total` |
| `crm/calls/web/CallEventController.java` | `POST /api/v2/crm/calls/events` (201 create / 200 idempotent replay), `GET /calls/{callId}`, `GET /calls` (cursor list, 404/422 envelopes, masked numbers in every response) |
| Errors | `CALL_EVENT_NOT_FOUND`(404), `CALL_EVENT_INVALID_TRANSITION`(422) + catalog rows; `CallEventExceptionHandler` extends the central CRM envelope; `crm_call_events` registered in `ModuleResetRegistry.CRM_TABLES` |

### TRACK D — offline caller dataset

| Component | Evidence |
|-----------|----------|
| `CallerDatasetTokenProvider` | `lookupToken = HMAC-SHA256(normalizedE164, tenantKey)`, `tenantKey = HMAC(masterKey, tenantId)`; master key ONLY via env `CALLER_DATASET_MASTER_KEY` (no default — **fails closed**), never leaves the server; derived tenant key issued ONCE to the device (SecureStore) |
| `CallerDatasetService.delta` | Snapshot/delta over the canonical source ordered `(updated_ms, id)` — no dup/no skip/retry-safe; **tombstones** for archived/inactive methods and owners; **RESTRICTED entries stripped of ALL display PII** (token + marker only, §41); bounded 500; `datasetVersion` gate ⇒ client full rebuild; server-side privacy filtering (§40) |
| `GET /api/v2/crm/caller-identification/delta` | Cursor/`hasMore`/`serverTimestamp`/`fullResyncRequired`/`datasetKey` (first sync); tenant from AUTH context only; metrics `caller_dataset_sync_total`/`caller_dataset_entries` |
| Mobile SQLite v2 | Additive migration (no wipe): `caller_lookup` PK `(tenant_id, phone_lookup_token, entity_type, entity_id)` + P0 index `(tenant_id, phone_lookup_token)` (§47); PII (`display_name`, `account_name`) AES-256-GCM encrypted BEFORE storage (§48) |
| `src/caller/normalizer.ts` | Mobile normalizer with **full semantic parity** to the backend authority (shared vectors gate) |
| `src/caller/hmac.ts` | Pure-TS HMAC-SHA256 (RFC 2104) — expo-crypto has no keyed HMAC; matches javax.crypto on the shared token vector |
| `src/caller/offline-lookup.ts` | Incoming → normalize → HMAC token → indexed SQLite lookup → decrypt matched fields only → EXACT/AMBIGUOUS/UNKNOWN/RESTRICTED/INVALID_NUMBER/PRIVATE_NUMBER with `offline:true`; stale indicator (STALE ≠ DISABLED); corruption ⇒ `fullResyncSuggested` (never a crash) |
| `src/caller/dataset-sync.ts` | Cursor delta loops; idempotent upserts; tombstones; dataset-version mismatch ⇒ clean rebuild; corrupt cursor ⇒ recoverable FULL_RESYNC marker; key lifecycle (SecureStore); logout/tenant-switch purge (rows + key + metadata) |
| Shared vectors | `docs/crm/g8/caller-phone-normalization-vectors.json` — normalization + HMAC token vector consumed by BOTH implementations |

## 2. GATES (command §90–§91)

**TRACK C gates:** `CALL_EVENT_MIGRATION` PASS (chain → 20260820.13; PG test asserts table+policies+indexes) · `CALL_EVENT_RLS` PASS (ENABLE+FORCE+policy asserted) · `CALL_EVENT_TENANT_ISOLATION` PASS (tenant-scoped create/list; API cross-tenant 404) · `CALL_EVENT_STATE_MACHINE` PASS (10/10 unit: transitions/illegal/out-of-order) · `CALL_EVENT_IDEMPOTENCY` PASS (unique constraint + 200 replay; no duplicate activities) · `CALL_EVENT_CONCURRENCY` PASS (`version` bumps) · `CALLER_MATCH_BINDING` PASS (EXACT snapshot w/ ids; AMBIGUOUS/RESTRICTED without identity) · `CRM_ACTIVITY_PROJECTION` PASS (one CALL activity per call at first terminal) · `TIMELINE_PROJECTION` PASS (business-significant events only, matched callers) · `AUDIT` PASS (actions recorded, no full phone — asserted) · `CALL_EVENT_API` PASS (201/200/GET/list/401/403/422/404).

**TRACK D gates:** `CALLER_DATASET_PROJECTION` PASS (PG snapshot) · `CALLER_DATASET_DELTA` PASS (pagination no-dup/no-skip PG) · `CALLER_DATASET_CURSOR` PASS (two-part base64url cursor, retry-safe) · `PHONE_LOOKUP_HMAC` PASS (vector parity Java↔TS; tenant-bound; fails closed w/o master key) · `LOCAL_CALLER_INDEX` PASS (dedicated index; no scan-and-decrypt path) · `LOCAL_PII_ENCRYPTION` PASS (encrypted at rest before storage — asserted `enc:` marker) · `OFFLINE_EXACT/AMBIGUOUS/UNKNOWN/RESTRICTED` PASS (offline-lookup tests + golden scenario) · `NORMALIZATION_PARITY` PASS (shared vectors both sides) · `TENANT_SWITCH_PURGE` PASS (A data not searchable after switch) · `LOGOUT_PURGE` PASS · `FULL_RESYNC` PASS (version mismatch rebuild; corrupt cursor ⇒ recoverable marker) · `CORRUPTION_RECOVERY` PASS · `LOCAL_LOOKUP_PERFORMANCE` PASS (P95 ≤ 100 ms on a 1k-entry dataset — benchmark test).

## 3. GOLDEN SCENARIO (§94–§98)

Tenant A contact محمد + canonical method `0541234567` → dataset sync delivers HMAC entry (token only) → device stores encrypted names → **network disabled** → incoming `0541234567` → mobile normalizer → `+966541234567` → HMAC → indexed lookup → **EXACT محمد (offline:true)** — covered by `offline-lookup.test.ts`. Tenant switch → A rows+key purged → same number ⇒ UNKNOWN (no cross-tenant reveal). Changed-number scenario: delta tombstone for the old token + upsert for the new one (dataset-sync tests). Duplicate scenario: two equally ranked contacts ⇒ AMBIGUOUS both online and offline (service unit + offline tests).

## 4. CONTRACT / GOVERNANCE DELTAS

- **OpenAPI:** 148/194 → **152 paths / 198 ops** (`+ /calls`, `/calls/events`, `/calls/{callId}`, `/caller-identification/delta`); `PlatformApiCountTest` 198/715/152/198; `CrmOpenApiContractTest` 152/198 + prefixes; contract-validation workflow 152/198; artifact + TS regenerated via the governed pipeline.
- **Flyway:** `.11/.12/.13` forward-only; `CrmPostgresMigrationTest` LATEST=20260820.13 + both pending lists + `assertMigration`×3 + cap count 83→**87** + `crm_call_events` in table set; history + acceptance terminals updated; error catalog +2 rows.
- **Secret scan:** 0 findings on all G8-03 changed files.

## 5. SCOPE / STOPPED (§80–§83, §99)

No `android/`, no CallScreeningService, no iOS extension/entitlements, no Twilio/SIP, no caller UI, no production closure. G8 remains **IN_PROGRESS**. `CALLER CORE = COMPLETE · CALL EVENTS = COMPLETE · OFFLINE DATASET = COMPLETE · ANDROID/iOS/PBX/UI = NOT STARTED`.
