# G8 CALLER IDENTIFICATION — MASTER REQUIREMENTS BASELINE

> **Report ID:** G8-BASELINE-V1
> **Date:** 2026-08-20
> **Repository:** https://github.com/snadaiapp-png/SNAD.git
> **Branch:** main
> **Baseline HEAD:** 92f5a389 (origin/main at baseline time)
> **Previous group:** G7 Mobile Offline Foundation — COMPLETED / CLOSED (2026-08-20)
> **Mode:** FORENSIC DISCOVERY / ARCHITECTURE LOCK / MASTER REQUIREMENTS BASELINE — **نو توثيقي/حوكمي فقط. لا كود إنتاج، لا Migrations، لا Native modules، لا نشر.**
> **Execution Command:** G8 EXECUTION COMMAND 01

---

## 1. IDENTITY

| Field | Value |
|-------|-------|
| G8_CODE | G8 |
| G8_NAME_AR | معرفة المتصل |
| G8_NAME_EN | Caller Identification |
| G8_PURPOSE_AR | تجهيز معرفة بيانات العميل عند الاتصال |
| G8_PURPOSE_EN | Prepare caller identification |
| G8_STATUS (board) | `IN_PROGRESS` (BASELINED / READY_FOR_IMPLEMENTATION — NOT yet complete; enum has no BASELINED value; see §50 note) |
| G8_DEPENDENCIES | [G7] — G7 = APPROVED (SATISFIED) |
| G8_DOWNSTREAM | G10 (QA, Security & Acceptance) has hard dependency on G8 |
| Source of truth | `apps/web/app/crm/crm-execution-data.ts` lines 139–149 (execution board) |

**Board entry (verbatim, lines 139–149):**
```typescript
{
  code: "G8",
  titleAr: "معرفة المتصل",
  titleEn: "Caller Identification",
  purposeAr: "تجهيز معرفة بيانات العميل عند الاتصال.",
  purposeEn: "Prepare caller identification.",
  status: "IN_PROGRESS" as GroupStatus,      // was NOT_STARTED — flipped by G8 EXECUTION 01
  dependencies: ["G7"],
  canParallelizeWith: [],
  stageReport: "G8-STAGE-REPORT-V1 — معتمدة كـ BASELINE. ...",
}
```

**GroupStatus enum (authoritative, `apps/web/lib/execution/types/execution-entities.ts:11-18`):**
`NOT_STARTED | IN_PROGRESS | BLOCKED | DONE | NEEDS_REVIEW | APPROVED | REJECTED` — there is NO `BASELINED` / `COMPLETE` / `CLOSED` value. Per command §50 the closest truthful status for "baselined, implementation pending" is **IN_PROGRESS**; the stageReport records the BASELINE nuance. G8 must NOT be marked APPROVED until acceptance gates close in a later execution command.

---

## 2. SCOPE

### 2.1 In-scope (G8 target state)

1. **Canonical phone authority** — reuse `crm_communication_methods` as the single source for caller matching (G8-ADR-001).
2. **Phone normalization** — reuse the single CRM normalizer `AddressCommunicationUseCases.normalizePhone` (G8_PHONE_NORMALIZER = REUSE); Saudi E.164 forms `05xxxxxxxx`, `5xxxxxxxx`, `966xxxxxxxxx`, `00966xxxxxxxxx`, `+966xxxxxxxxx` with `countryHint=SA`.
3. **Backend Caller Lookup API** — dedicated, POST-based, tenant-scoped, deterministic; `POST /api/v2/crm/caller-identification/lookup` (baseline; final path to be confirmed at Track B).
4. **Matching engine** — EXACT / AMBIGUOUS / UNKNOWN / PRIVATE_NUMBER / INVALID_NUMBER / RESTRICTED; NO fuzzy matching; NO_RANDOM_MATCH; deterministic, tenant-scoped, no-false-positive preference.
5. **Match resolution policy** — deterministic precedence over ACTIVE/VERIFIED/PREFERRED PERSON → ACTIVE PERSON → ACTIVE ACCOUNT → ACTIVE LEAD (final order justified in §9).
6. **Call event model** — `crm_call_events` as source of truth (G8-ADR-003) with CRM Activity/Timeline as projection; NOT created in this command.
7. **Offline caller dataset** — dedicated caller-lookup projection (Option B, §26) synced via G7 sync contract extension; HMAC-SHA256 `phone_lookup_token` (G8-ADR-004).
8. **Native integration boundaries** — Android CallScreeningService path + iOS Call Directory Extension path as feasibility-gated tracks; no native code in this command.
9. **RBAC / RLS / Privacy / PDPL / Threat model / SLO / Observability** baselines (§§16–21).

### 2.2 G8 is NOT (explicit out-of-scope, command §48)

Call recording · AI transcription · AI summaries · Sentiment analysis · Voice bot · IVR builder · Predictive dialer · Sales call coaching · Full contact center · Telecom billing · Campaign dialer · WhatsApp contact center. These are future or out-of-project capabilities and MUST NOT be implemented as G8.

---

## 3. HISTORICAL NAMING CONFLICT

```text
HISTORICAL_CRM_G8 != CURRENT_G8_CALLER_IDENTIFICATION
```

The repository contains THREE distinct meanings of "G8" — registered, NOT merged:

| # | Meaning | Evidence | Status |
|---|---------|----------|--------|
| 1 | **Old CRM milestone CRM-G8 = "Quality, security, formal commercial GO"** | `docs/crm/stage-reports/CRM-G8-STAGE-REPORT.md` (Report ID G8-STAGE-REPORT-V1, 2026-08-06, gate status CLOSED); `docs/governance/MASTER-EXECUTION-MANIFEST.md:45` (DONE); `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md:61`; `docs/crm/CRM-PORTFOLIO-STATUS.md:31`; `docs/crm/NEXT-WORK-ITEM.md:36` | **Historical — unrelated to Caller Identification; document was NOT modified, NOT reused, NOT deleted** |
| 2 | **G8 as release-permission flag in G7 closure docs** | `G7_FINAL_RELEASE_DECISION.md:29` (`G8_PERMISSION = DENIED`); `G7_M13_RECONCILIATION_AND_CORRECTED_VERDICT.md` (`G8_PERMISSION = GRANTED`, §7 closure 2026-08-20) | Historical decision vocabulary — unrelated |
| 3 | **G8 = Caller Identification (current Execution Board)** | `apps/web/app/crm/crm-execution-data.ts:139-149`; `MODULE-COMPATIBILITY-MATRIX.md:57` (`| G8 | Caller Identification | NOT_STARTED | 0 |`) | **THE governing identity of this baseline** |

G7 docs explicitly defer caller ID to G8: `G7_IDENTITY_FINAL.md:26` ("Caller identification | Deferred to G8"), `G7_MOBILE_FOUNDATION_MASTER_BASELINE.md:89-90, 576-577` ("Caller identification (G8)" listed as G7 NON-SCOPE).

**Rules enforced:** (a) current G8 identity = Caller Identification only; (b) no reuse of CRM-G8 report identity; (c) historical report untouched; (d) conflict registered in this baseline.

---

## 4. CURRENT REPOSITORY EVIDENCE (verified 2026-08-20)

Repository state at baseline: `HEAD == origin/main == 92f5a389`, working tree clean, G7 closed. Verified by direct read:

### 4.1 Phone canonical store — `crm_communication_methods`

Migration `apps/sanad-platform/src/main/resources/db/migration/V20260717_100__crm_addresses_communication_methods.sql:114-172`. **ALL required G8 columns exist** (column → DDL line):
`tenant_id:116 · owner_type:118 · owner_id:119 · method_type:122 · raw_value:123 · normalized_value:124 · display_value:125 · preferred:127 · verified:129 · verification_status:130 · privacy_classification:132 · consent_state_reference:133 · usage_purpose:134 · status:135` (+ version, label, preferred_slot, verified_at, valid_from/to, audit cols, archived_at).

**Indexes (lines 174-181):**
- `uq_crm_communication_methods_preferred (tenant_id, owner_type, owner_id, method_type, preferred_slot)` — unique per owner per preferred slot
- `idx_crm_communication_methods_owner (tenant_id, owner_type, owner_id, status, updated_at DESC, id)`
- **`idx_crm_communication_methods_lookup (tenant_id, method_type, normalized_value, status)` — THE G8 reverse-lookup index EXISTS (non-unique)**
- `idx_crm_communication_methods_privacy (tenant_id, privacy_classification, status)`

**Duplicate reality (§11):** uniqueness on a phone number is enforced **in application code, per-owner only** (`JdbcAddressCommunicationRepository.enforceDuplicatePolicy` lines 406-424, gated by `crm_communication_policies.phone_unique_within_owner`, default TRUE). **The same normalized phone MAY legitimately exist on multiple owners (different Contacts/Accounts) in the same tenant** — DB has no unique constraint on `(tenant_id, normalized_value)`. ⇒ `AMBIGUOUS_MATCH_HANDLING = P0`.

**Freshness caveat:** legacy backfill (`V20260717_100…sql:245-291`) stored `normalized_value` with digit/symbol stripping ONLY, **without E.164 conversion** — historical rows may not match a `+966…` inbound number ⇒ CONDITIONAL re-normalization migration (§23).

### 4.2 Phone normalization

**Single CRM normalizer — REUSE:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/AddressCommunicationUseCases.java:364-376` (`normalizePhone`):
- strips `[\s().-]`; `00` → `+`; E.164 regex `^\+[1-9][0-9]{7,14}$` (line 39)
- with `countryHint="SA"`: `05[0-9]{8}` → `+9665…`; `5[0-9]{8}` → `+966…`; `966[0-9]{9}` → `+966…`
- rejected otherwise (no guessed country) — validation message line 375
- `PHONE_TYPES = {PHONE, MOBILE, FAX, WHATSAPP, SMS}` (line 31); `countryHint` accepted via request DTOs (`CrmAddressCommunicationController.java:614, 626`, `@Pattern("[A-Za-z]{2}")`)

Secondary normalizer exists ONLY for user self-registration (`security/service/RegistrationIdentityFactory.java:35-46` → `users.mobile_number`) — out of CRM path, NOT to be duplicated or merged into G8.

**G8_PHONE_NORMALIZER = REUSE** — a second different normalizer is FORBIDDEN.

### 4.3 API surface today

- **NO caller/phone-lookup endpoint exists** (full sweep: no caller-ID, no reverse lookup, no call events, no telephony webhooks, no Twilio/SIP/PBX/VoIP in backend).
- Closest primitives: `GET /api/v2/crm/communication-methods/search` (`CrmAddressCommunicationOperationsController.java:81-98`, `CRM.COMMUNICATION.READ`) — substring `LIKE` over label/display/normalized; masks values unless `CRM.COMMUNICATION.SENSITIVE.READ`; NOT an exact reverse lookup, NOT latency-specialized. `GET /api/v1/crm/search` searches names/emails only (no phone).
- Dormant G1 tables with zero runtime code: `crm_phone_numbers` (`(tenant_id, e164, archived)` index, V20260717_6:180-216) and `crm_contact_lookup_index` (`(tenant_id, normalized_phone, active)`, V20260717_6:244-245) — see §26/§42 disposition (NOT REBUILD; reuse-or-ignore decision).
- Platform API governance: `PlatformApiCountTest` currently enforces CRM v1=125, CRM v2=193, TOTAL=710 ops; committed contract `docs/crm/contracts/openapi/crm-openapi.json` = 147 paths / 193 ops ⇒ any G8 endpoint bumps these counts in the implementation command.

### 4.4 Tenant / auth / RBAC / RLS

- **TENANT_SOURCE = AUTHENTICATED CONTEXT ONLY** — `JwtAuthenticationFilter.java:60-83` (JWT claims `tenant_id`/`user_id` → `authentication.setDetails({user_id, tenant_id, email, rotation_required, session_version})`); `?tenantId=` query param is rejected unless it equals the JWT tenant (403). `TenantContextPort` javadoc: "CRM modules never read tenant from request body or query parameters" (`crm/integration/domain/TenantContextPort.java:10-14`, impl `SpringTenantContextAdapter.java:11-27`). **Confirmed: tenantId never accepted from body.**
- **RBAC:** `@RequireCapability("DOMAIN.ENTITY.ACTION")` + `CapabilityAuthorizationAspect` (`security/authorization/CapabilityAuthorizationAspect.java:55-100`), capability seeds idempotent `WHERE NOT EXISTS` (V20260717_101:17-19), role templates `V20260820_2/3` (9 templates; `CRM_SALES` has core CRM caps only — communication/address caps are NOT in templates; `V20260820_3` aborts on unknown capability codes ⇒ **new capabilities must be seeded BEFORE being granted**). **No CALLER/CALL capability exists today.**
- **RLS:** core CRM tables (contacts/activities/communication_methods/timeline) have NO RLS — tenant filtering at service layer. RLS pattern established on mobile sync tables: `V20260812_1:131-149` (`ALTER TABLE … ENABLE ROW LEVEL SECURITY; CREATE POLICY {table}_tenant_isolation USING (tenant_id = current_setting('app.tenant_id', true)::UUID)`) + `V20260812_3:12-15` (`FORCE ROW LEVEL SECURITY`); GUC set per transaction by `TenantRlsConnectionHandler.java:59-80`. ⇒ G8 new tables MUST follow ENABLE + FORCE + policy pattern.
- **Idempotency:** `crm_idempotency_records` (`V20260713_1:11-33`, UNIQUE (tenant_id, principal_id, endpoint, idempotency_key)) + `CrmIdempotencyHttpSupport` (replay returns stored response) — reuse for lookup/event ingestion.
- **Rate limiting:** `scale/api/RateLimitFilter.java:40-122` exists (per-tenant API_RPM + 429) **but its `resolveTenantId()` reflection never matches the SANAD principal ⇒ effectively inactive for JWT traffic — G8 must implement/repair a working limiter** (DEFECT-015 in `docs/audit/SANAD-FINAL-REMEDIATION-REPORT.md:143` open: non-distributed rate limiting).

### 4.5 Activities / Timeline / Audit

- `crm_activities` (`V20260702_1:195-222`): `activity_type CHECK ('TASK','CALL','MEETING','EMAIL','NOTE','MESSAGE','OTHER')` — **'CALL' is already a legal type**; timeline index `(tenant_id, related_type, related_id, created_at DESC)`.
- `crm_timeline_events` (`V20260702_1:224-238`) + `TimelineEventPort` (`crm/integration/domain/TimelineEventPort.java:10-14`, impl `JdbcTimelineEventAdapter.java:15-34`) + read projection `JdbcTimelineProjectionRepository` → `GET /api/v2/crm/timeline/{subjectType}/{subjectId}`.
- Audit: `AuditPort` → `PlatformAuditWriter` → `platform_audit_logs` (`V17`), RBAC aspect writes ALLOW/DENY automatically.

### 4.6 Mobile (G7 foundation)

- Expo SDK ~52.0.0, RN 0.79.4, React 19.2.8 (`apps/mobile/package.json:18-26`); **no expo-device / no native android|ios folders / no scheme / no permissions or entitlements in `app.json:15-25`**; plugins = expo-secure-store, expo-crypto, expo-sqlite.
- SQLite `snad_g7_offline.db`, SCHEMA_VERSION=1, transactional migration, NO WAL (`storage/db.ts:14-59`); entity tables for 7 types only: **account, contact, lead, opportunity, task, note, activity** (`types/index.ts:11` — `communication_method` is NOT a mobile EntityType ⇒ `G8_OFFLINE_CALLER_DATASET_GAP = CONFIRMED`).
- Phone fields on Account/Contact/Lead encrypted at rest with AES-256-GCM via Web Crypto, single device key `g7_encryption_key_v1` in SecureStore (`storage/encryption.ts:26-36, 92-104`); **encrypted phone is NOT searchable from a headless native context** (key + SubtleCrypto live in the JS runtime).
- Sync: cursor-based delta pull per entity type, push batch 50, idempotency key = SHA-256 of payload (expo-crypto), retries 5, conflict classes C1-C12 with hybrid policy (auto-merge non-conflicting), `FULL_RESYNC_REQUIRED` state exists but full resync NOT implemented (`sync/sync-engine.ts`, `conflict/resolver.ts:48-71` — C1/C2/C7/C9 detected client-side).
- **No App entry point / no logout purge** (SQLite never cleared; `deleteEncryptionKey()` orphaned) / no tenant switch / no connectivity monitor (offline inferred from errors).
- Server sync: `GET /api/v2/mobile/sync/pull`, `POST /api/v2/mobile/sync/push`, `/status`, `/conflicts` (7 entity types mirror).

### 4.7 Telephony provider

**PRIMARY_TELEPHONY_PROVIDER = NOT YET SELECTED.** Twilio appears only as an optional SMS plan in NOT_STARTED tasks of other modules (`apps/web/app/notifications/notifications-execution-data.ts:184-191`, `apps/web/app/identity/identity-execution-data.ts:684`, stage-15/18 backlog). No SIP/PBX/VoIP/Vonage/Sinch/Plivo/Telnyx/AWS Connect references. No telephony env vars. Classified as **INTEGRATION DECISION REQUIRED BEFORE PROVIDER-SPECIFIC RELEASE** — does not block Caller Matching Core / Lookup API / Offline Projection / Native abstraction.

---

## 5. REUSABLE FOUNDATIONS (VERIFIED — REUSE / EXTEND / NO REBUILD)

Per command §3: REUSE if valid, EXTEND if insufficient, REBUILD ONLY with technical evidence.

| # | Foundation | Verdict | Location (evidence) |
|---|-----------|---------|---------------------|
| F-01 | Phone canonical store (crm_communication_methods + lookup index) | **REUSE** | V20260717_100:114-181 |
| F-02 | Phone normalization (Saudi E.164) | **REUSE** | AddressCommunicationUseCases.java:364-376 |
| F-03 | Tenant context (JWT → details → port) | **REUSE** | JwtAuthenticationFilter.java:60-83; TenantContextPort.java; SpringTenantContextAdapter.java |
| F-04 | RBAC capability framework + aspect + idempotent seeds | **REUSE (EXTEND: new cap codes)** | RequireCapability.java; CapabilityAuthorizationAspect.java; V20260717_101 |
| F-05 | RLS pattern (ENABLE/FORCE + tenant_isolation policy + app.tenant_id GUC) | **REUSE (mandatory for new G8 tables)** | V20260812_1:131-149; V20260812_3:12-15; TenantRlsConnectionHandler.java:59-80 |
| F-06 | Idempotency (records table + HTTP support + replay) | **REUSE** | V20260713_1; CrmIdempotencyHttpSupport.java |
| F-07 | Activities with `activity_type='CALL'` + Timeline port + Audit port | **REUSE (as projection targets)** | V20260702_1:195-238; TimelineEventPort.java; AuditPort.java |
| F-08 | G7 mobile sync contract (delta cursor, idempotent queue, C1-C12) | **REUSE (EXTEND: caller projection entity)** | apps/mobile/src/sync|storage|conflict; G7_SYNC_CONTRACT_TRUTH.md |
| F-09 | AES-256-GCM at rest + SecureStore key mgmt | **REUSE (NOT for native headless lookup; HMAC token for lookup)** | apps/mobile/src/storage/encryption.ts |
| F-10 | crm_phone_numbers / crm_contact_lookup_index (G1 dormant tables) | **DO NOT REBUILD** — either reuse as auxiliary projection or leave dormant; decision bound to §26 Option B outcome | V20260717_6:180-216, 244-245 |
| F-11 | Existing search API (communication-methods/search) | **NOT a substitute** for dedicated lookup (latency/privacy/ambiguity/authorization gaps — §14) | CrmAddressCommunicationOperationsController.java:81-98 |
| F-12 | Platform API governance (counts test + committed OpenAPI) | **REUSE (extend counts at implementation)** | PlatformApiCountTest.java:31-40 |

---

## 6. FUNCTIONAL REQUIREMENTS

### FR-001 Caller lookup on inbound call (P0)
Given an authenticated tenant user (or native call-screening context with a device token) and a normalized E.164 phone, return the caller card: matchStatus + display identity (see §17), within SLO (§20), with NO false-positive preference.
- `MATCH_STATUS ∈ {EXACT, AMBIGUOUS, UNKNOWN, PRIVATE_NUMBER, INVALID_NUMBER, RESTRICTED}` (G8-ADR-005, §10).

### FR-002 Deterministic matching (P0)
Same input → same result; ordering by the §9 policy; NO random tie-break; ambiguous → AMBIGUOUS, never random pick (`NO_RANDOM_MATCH`).

### FR-003 Exact reverse lookup (P0)
Query `crm_communication_methods` by `(tenant_id, method_type IN (PHONE,MOBILE,WHATSAPP,SMS), normalized_value = :e164, status='ACTIVE')` using `idx_crm_communication_methods_lookup` — an exact-match query (the current index has no consumer; one exact-match repository method is REQUIRED).

### FR-004 Phone normalization on ingest (P0)
All caller identities (API input, communication-method write path, call events) are stored normalized via the REUSED `normalizePhone` with `countryHint=SA`; the caller lookup endpoint normalizes input BEFORE matching.

### FR-005 Match resolution policy (P0)
§9 precedence; person-first, account second, lead last; verified/preferred boosters; archived/inactive EXCLUDED from exact candidates (but archived shown with `lifecycleStatus` when it is the only match? — NO: policy keeps archive out of EXACT; archival requires explicit "archived-only" opt-in query or a `deletedAfter` filter; baseline: EXACT ignores ARCHIVED).

### FR-006 Ambiguous handling (P0)
Multiple equal-priority candidates ⇒ `matchStatus=AMBIGUOUS`, `candidates=[]` (count only, NO partial disclosure — privacy §18), explicit `ambiguousCount`; UI offers "open 360 / create new / ignore".

### FR-007 Unknown caller (P0)
No record ⇒ `matchStatus=UNKNOWN`; **UNKNOWN ≠ AUTO-CREATE** (`UNKNOWN NUMBER ≠ AUTO CREATE CUSTOMER`); post-call UI allows authorized user choice: Create Lead / Create Contact / Link to existing / Ignore.

### FR-008 Private / restricted / invalid numbers (P0)
Number absent/withheld/restricted (`PRIVATE_NUMBER`) or failing E.164 (`INVALID_NUMBER`) ⇒ NO reverse lookup attempt, NO partial match; privacy_classification CONFIDENTIAL/RESTRICTED ⇒ `RESTRICTED` unless caller holds explicit capability (§16, §18).

### FR-009 Call event ingestion (P0, server) — G8-ADR-003
Dedicated `crm_call_events` table as SSoT for call lifecycle (ring/answer/end/disposition); projection to `crm_activities` (`activity_type='CALL'`) + `crm_timeline_events` for CRM timelines; idempotency key = `provider_call_id` (or device event id) via `crm_idempotency_records`; out-of-order tolerated (event timestamps authoritative; late events coalesce). **Table NOT created in this command (§23).**

### FR-010 Caller dataset offline projection (P0, design only)
Dedicated `caller_lookup` projection synced to the device (Option B, §26); fields §27; sync via G7 delta extension (§29); used by native lookup; freshness policy §40.

### FR-011 Phone change propagation (P1)
Phone added/changed/removed on `crm_communication_methods` (or lead projection) → reflected in caller dataset delta: `phone changed`, `phone removed`, `entity archived`, `entity reactivated` (§29 delta catalogue).

### FR-012 Post-call workflow hooks (P1, UI track)
Add result / Add note / Create follow-up task / Open Customer 360 / create-with-consent for unknown; NOT automatic creation.

### FR-013 Logout/tenant-switch purge (P0, mobile)
Logout and tenant switch MUST purge caller dataset + offline identity; re-login re-syncs (§31 PDPL).

### FR-014 Stale-data safe behavior (P1)
Offline lookup uses `BEST SAFE LOCAL MATCH` + `STALE INDICATOR`; staleness never hard-blocks the call UX (§40).

---

## 7. DATA REQUIREMENTS

### 7.1 Existing (reused) data
- `crm_communication_methods` — canonical (G8-ADR-001)
- `crm_accounts`, `crm_contacts` (via methods owner_id + account_id/contact_id FKs; `primary_phone` = legacy compatibility, NOT canonical)
- `crm_leads.phone` — orphaned today; decision in G8-ADR-002
- `crm_activities` (`activity_type='CALL'` legal), `crm_timeline_events`
- Dormant G1: `crm_phone_numbers`, `crm_contact_lookup_index`

### 7.2 New data (design only — NO DDL in this command)
- **`crm_call_events`** (SSoT, G8-ADR-003): fields evaluated in §19 — provider, provider_call_id, direction, source, from_number_normalized, to_number, match_status, matched_entity_type, matched_entity_id, matched_contact_id, matched_account_id, agent_user_id, device_id, ringing_at, answered_at, ended_at, duration_seconds, disposition, status, tenant_id, version, created_at/updated_at, sync columns (last_synced_at, sync_version).
- **`caller_lookup` projection** (logical schema, §27) — tenant_id, phone_lookup_token (HMAC-SHA256 of normalized E.164, tenant/device-scoped key), entity_type, entity_id, display_name_encrypted, account_name_encrypted, phone_label, verified, preferred, lifecycle_status, sync_version, updated_at; index `(tenant_id, phone_lookup_token)`.
- RLS: both tables MUST `ENABLE ROW LEVEL SECURITY` + `FORCE` with `tenant_isolation` policy (pattern §4.4).

### 7.3 Consent & privacy metadata
Reuse existing columns on the canonical source: `consent_state_reference`, `privacy_classification`, `verification_status`, `usage_purpose`. Caller display MUST respect them (§18).

---

## 8. PHONE NORMALIZATION BASELINE

**G8_PHONE_NORMALIZER = REUSE (single code path, FORBIDDEN to duplicate).**

| Input form | Expected normalized | Rule |
|---|---|---|
| `+9665xxxxxxxx` | `+9665xxxxxxxx` | E.164 pass-through |
| `05xxxxxxxx` | `+9665xxxxxxxx` | countryHint=SA: `05…` → `+966` + rest |
| `5xxxxxxxx` | `+9665xxxxxxxx` | countryHint=SA: `5…` → `+966` + value |
| `966xxxxxxxxx` | `+966xxxxxxxxx` | countryHint=SA: `966…` → `+` + value |
| `00966xxxxxxxxx` | `+966xxxxxxxxx` | generic `00` → `+` |
| `05x-xxx xxxx` / `(05x) xxx xxxx` | `+9665xxxxxxxx` | strip `[\s().-]` then SA rules |
| invalid / empty / country unknown | reject with validation | NO country guessing; E.164-only without SA hint |

Test matrix (§22) covers all rows + boundary cases. Cross-check: `RegistrationIdentityFactory.normalizeMobileNumber` (identity domain) must NOT be used for caller matching.

---

## 9. MATCH RESOLUTION POLICY (G8-ADR-005 + §10 states)

**Baseline candidate policy (final after CRM-model comparison):**

```
1. PERSON  ACTIVE + VERIFIED        (crm_communication_methods.owner_type='PERSON', status='ACTIVE', verified=TRUE,  method_type IN (PHONE,MOBILE,WHATSAPP,SMS))
2. PERSON  ACTIVE + PREFERRED       (verified=FALSE, preferred=TRUE, same filter)
3. PERSON  ACTIVE                   (any phone method, verified/preferred any)
4. ACCOUNT ACTIVE                   (owner_type='ACCOUNT', status='ACTIVE')
5. LEAD    ACTIVE                   (via G8-ADR-002 lead path, lead status='ACTIVE')
```

**Precedence justification:** person-before-account because a caller card is identity-first (who is calling) and account enrichment follows the matched contact (account_id on the communication method); verified-before-preferred before plain-active follows the platform's verification-first semantics (`verification_status` CHECK: VERIFIED/UNVERIFIED/PENDING…); account before lead because accounts are mastered customers while leads are unqualified prospects; INACTIVE/ARCHIVED excluded from EXACT but may surface via explicit `includeArchived` opt-in (P2) — never by default.

**Governing conditions (command §12):** `DETERMINISTIC` (stable ORDER BY + tie → AMBIGUOUS) · `TENANT_SCOPED` (WHERE tenant_id = context only) · `NO FALSE POSITIVE PREFERENCE` (never pick a weaker candidate to avoid UNKNOWN; weakly-matching records collapse to AMBIGUOUS at their own tier).

**Match states (G8-ADR-005, baseline locked):** `EXACT` (single top-tier candidate) · `AMBIGUOUS` (≥2 candidates at the winning tier, or cross-tier equal strength) · `UNKNOWN` (no candidate) · `PRIVATE_NUMBER` (no number available: PRIVATE/WITHHELD/BLOCKED) · `INVALID_NUMBER` (input fails normalization) · `RESTRICTED` (candidate exists but privacy_classification CONFIDENTIAL/RESTRICTED and caller lacks the required capability). **NO fuzzy matching of phone numbers — normalized equality only.**

---

## 10. MATCHING ENGINE REQUIREMENTS (P0)

- M-01 Exact equality on `normalized_value` (E.164) — never substring/prefix/LIKE for matching.
- M-02 Tiered resolution per §9; stable deterministic ORDER BY (`verified DESC, preferred DESC, owner_type rank, updated_at ASC, id ASC`).
- M-03 Tie at winning tier ⇒ `matchStatus=AMBIGUOUS`, no candidate details released beyond count (`ambiguousCount`) — privacy §18.
- M-04 Archived/inactive never satisfy EXACT by default.
- M-05 Input normalization precedes matching; INVALID_NUMBER short-circuit before DB.
- M-06 Lookup index `idx_crm_communication_methods_lookup` consumed by an exact-match repository query (currently index unused — Track A adds the consumer).
- M-07 Result caching for the same (tenant, normalized phone) within TTL (P2; NO cross-tenant caching; cache key NEVER the raw phone in plaintext → use token or hash; SLO §20).
- M-08 Offline mirror: same policy implemented against the local `caller_lookup` projection (token equality), with staleness indicator (§40).

---

## 11. DUPLICATE PHONE REALITY (verified)

| Dimension | Reality | Evidence |
|-----------|---------|----------|
| PER OWNER | Enforced in app code when `phone_unique_within_owner=TRUE` (default) | JdbcAddressCommunicationRepository.java:406-424; crm_communication_policies seed (V20260717_100:104-112) |
| PER ENTITY | Same as per-owner (owner IS the entity) | same |
| PER TENANT | **NOT enforced** — same phone may exist on many owners in one tenant | no unique constraint; DDL lines 174-181 |
| GLOBAL | NOT enforced (tenant-scoped data by design) | tenants FK |

**Consequence (locked):** `AMBIGUOUS_MATCH_HANDLING = P0`. The matching engine MUST handle multi-owner duplicates deterministically (FR-006). Do not attempt to globally deduplicate phone numbers as part of G8 — that would be a data-mastering change outside G8 scope; ambiguity is a first-class outcome.

---

## 12. API REQUIREMENTS

### 12.1 Baseline endpoint (G8-ADR-006)

```http
POST /api/v2/crm/caller-identification/lookup
```

(NOT `GET ?phone=` — rationale: minimize number in URLs, avoid access-log leakage, support governed payload, support source/device/context. Confirmed against project structure: no strong reason for GET; existing v2 CRM convention uses POST for parameterized lookups where privacy matters.)

**Request baseline:**
```json
{
  "phone": "+9665XXXXXXXX",
  "countryHint": "SA",
  "source": "CALL_SCREENING|DEVICE|UI|WEBHOOK",
  "deviceId": "uuid"
}
```
- `tenantId` NOT accepted from client (TENANT_SOURCE = AUTHENTICATED CONTEXT ONLY, §16); deviceId bound to a registered device when source=CALL_SCREENING (mobile_device_registry reuse, P1).
- Normalization inside server; INVALID_NUMBER → 422 structured error.

**Response baseline (fields classified §17):** `matchStatus`, `entityType`, `entityId`, `displayName`, `accountId`, `accountName`, `phoneLabel`, `verified`, `preferred`, `assignedOwner`, `lifecycleStatus`, `lastInteraction`, `openOpportunityCount`, `privacyLevel` + `ambiguousCount` (when AMBIGUOUS) — exact envelope finalized in Track B; PII minimization enforced (§18).

### 12.2 Other API surfaces (design-level)
- **Call event ingestion** (provider/device → server): `POST /api/v2/crm/call-events` (or under `/caller-identification/events`) with `Idempotency-Key` = provider_call_id; signature verification for provider webhooks (§34); tenant mapping NEVER from webhook body alone.
- **Caller dataset delta sync (mobile):** dedicated delta endpoint OR G7 generic-sync extension — decision §29 (baseline: G7 extension preferred, avoid second sync machinery; a dedicated `caller-identification/delta` remains as fallback if G7 entity-type extension proves too wide).
- **Caller card API for UI:** the lookup endpoint doubles as the caller-card source (source=UI).
- All G8 endpoints: `@RequireCapability`, idempotency where mutating, RLS-covered queries, audit through aspect + AuditPort, and MUST be counted in `PlatformApiCountTest` + committed OpenAPI (Track J).

### 12.3 Authorization
- Lookup: `CRM.CALLER_ID.READ` (baseline codes §16).
- Read of RESTRICTED/confidential card: `CRM.CALLER_ID.READ_RESTRICTED` (or reuse `CRM.COMMUNICATION.SENSITIVE.READ` — decision in Track B; baseline proposes dedicated code).
- Event ingestion: `CRM.CALL_EVENT.WRITE` (server-side events); device event submission scoped to the authenticated device/tenant (device-bound JWT or short-lived device token — design in Track C).

---

## 13. CALL EVENT MODEL (G8-ADR-003 — LOCKED)

```text
CALL_EVENT_SOURCE_OF_TRUTH = crm_call_events
CRM_ACTIVITY              = BUSINESS/TIMELINE PROJECTION
```

**Decision basis (verified §4.5):** `crm_activities` (`V20260702_1:195-222`) provides business activities with `activity_type='CALL'`, result, status, owner_user_id, timeline index — but lacks telephony attributes (provider, provider_call_id, direction, device_id, ringing/answered/ended timestamps, duration, disposition) and would leak raw telemetry into the business activity model; `crm_timeline_events` has no typed fields either. Therefore a dedicated `crm_call_events` table is the SSoT; a projection writes one `crm_activities` row (`activity_type='CALL'`, `related_type/related_id` per match, `result` per §19 disposition) and one `crm_timeline_events` row (`event_type='crm.call_event.consumed'` or similar) for matching calls. No table created in THIS command; DDL belongs to Track C (migration REQUIRED per §23).

**Field evaluation (§19) — all REQUIRED in crm_call_events (P0):** provider, provider_call_id, direction (INBOUND|OUTBOUND), source, from_number_normalized, to_number (normalized), match_status, matched_entity_type, matched_entity_id, matched_contact_id, matched_account_id, agent_user_id, device_id, ringing_at, answered_at, ended_at, duration_seconds, disposition (ANSWERED|MISSED|REJECTED|CANCELLED|FAILED|VOICEMAIL), status (RECEIVED|PROCESSED|DUPLICATE|ERROR), tenant_id, version, created_by/at, updated_by/at, last_synced_at, sync_version. `matched_*` are denormalized snapshots (not FKs) to preserve the caller card at event time.

**Idempotency (P0):** `provider_call_id` uniqueness enforced via `crm_idempotency_records` (reuse) or a unique index on `(tenant_id, provider, provider_call_id)` — decision in Track C; duplicate events coalesce; out-of-order events accepted (authoritative event timestamps; late ringing after answered → recorded as correction with audit).

---

## 14. BACKEND CALLER LOOKUP GAP (verified)

`DEDICATED_CALLER_LOOKUP_API = MISSING` — no caller endpoint exists anywhere (§4.3). The existing `GET /api/v2/crm/communication-methods/search` is NOT a substitute:

| Dimension | search (existing) | G8 lookup (required) |
|-----------|-------------------|----------------------|
| Latency | substring scan plan (LIKE) | index point-lookup (idx lookup) |
| Privacy | returns records list; masks only without SENSITIVE cap | minimal card; masked by default; RESTRICTED gated |
| Response shape | list envelope of search hits | single card + matchStatus/ambiguousCount |
| Authorization | CRM.COMMUNICATION.READ | CRM.CALLER_ID.READ + restricted-level caps |
| Ambiguity handling | lists all rows | AMBIGUOUS semantics + count-only disclosure |
| Logging | query in logs (q param) | no phone in logs; POST body only (§36) |

---

## 15. CALL SOURCE ARCHITECTURE (G8-ADR-007 — LOCKED)

```text
CALL_SOURCE_ARCHITECTURE = ADAPTER_BASED
```

- Port: `CallSourceAdapter` (name aligned with project port conventions: `*Port` interfaces + infrastructure adapters — mirror `PaymentGatewayPort`/`TimelineEventPort` style). Methods (design): `onEvent(CallSourceEvent)` / `resolveProviderCallId(event)` / `verifySignature(payload, headers)`.
- Source types: `ANDROID_NATIVE | IOS_NATIVE | PBX | SIP | VOIP_PROVIDER | CLOUD_TELEPHONY`.
- **PRIMARY_TELEPHONY_PROVIDER = NOT YET SELECTED** (§4.7) — `INTEGRATION DECISION REQUIRED BEFORE PROVIDER-SPECIFIC RELEASE`; does NOT block core, API, offline projection, or adapter-boundary tracks.
- Provider webhooks (when selected): signature verification + timestamp window + replay protection + `provider_call_id` idempotency + out-of-order handling + retry safety + tenant mapping NEVER from webhook alone (§34).

---

## 16. RBAC BASELINE

No caller/call capabilities exist (verified grep). **Naming convention: `DOMAIN.ENTITY.ACTION`** ⇒ baseline new codes (final names at Track H; seed migrations idempotent `WHERE NOT EXISTS`; `V20260820_3` aborts on unknown codes ⇒ seed BEFORE grant):

| Code (baseline) | Purpose | P0/P1 |
|-----------------|---------|-------|
| `CRM.CALLER_ID.READ` | Caller lookup + caller card (non-restricted) | P0 |
| `CRM.CALLER_ID.READ_RESTRICTED` | View RESTRICTED/CONFIDENTIAL caller identity | P0 |
| `CRM.CALL_EVENT.READ` | Read call events (audit/analytics) | P1 |
| `CRM.CALL_EVENT.WRITE` | Ingest call events (server-side webhook/device path) | P0 |
| `CRM.CALLER_DATASET.MANAGE` | Administer offline caller dataset policy/refresh | P1 |

Role template binding: evaluate `CRM_SALES` + `EXECUTIVE_VIEWER` for `CRM.CALLER_ID.READ`; SENSITIVE/restricted reads and dataset manage remain admin-granted (Track H). All G8 endpoints annotated `@RequireCapability` (aspect enforces + audits DENY/ALLOW).

---

## 17. CALLER LOOKUP RESPONSE CONTRACT

| Field | Class | Notes |
|-------|-------|-------|
| matchStatus | P0 DISPLAY | §10 states |
| entityType / entityId | P0 DISPLAY | matched record |
| displayName | P0 DISPLAY | contact/account/lead name |
| phoneLabel | P0 DISPLAY | label from communication method |
| verified / preferred | P0 DISPLAY | indicators |
| accountId / accountName | P1 CONTEXT | if person matched |
| assignedOwner | P1 CONTEXT | owner_user_id → name via authorized lookup |
| lifecycleStatus | P1 CONTEXT | ACTIVE/INACTIVE/ARCHIVED of entity |
| privacyLevel | P0 DISPLAY | effective privacy classification + disclosure gate |
| lastInteraction | P1 CONTEXT | from activities (related_type/id), P2 if expensive |
| openOpportunityCount | P2 ENRICHMENT | opportunistic count (P2 cost guard) |
| ambiguousCount | P0 DISPLAY | only when AMBIGUOUS (no candidate details) |

Not every field is P0; enrichment fields cost queries — Track B decides lazy vs eager with SLO budget (§20).

---

## 18. PRIVACY / PDPL BASELINE

Phone numbers and caller identity = **Personal Data** (Saudi PDPL — baseline doc `docs/production-readiness/compliance-data-governance.md:15-24`: data minimization, purpose limitation, retention & secure deletion, access/administrative audit logs).

- P-01 Data minimization: card exposes only §17 P0/P1 fields; no raw phone, no email, no address on the card.
- P-02 Purpose limitation: caller identification data used ONLY for calltime identity; consent_state_reference honored.
- P-03 RBAC gating (P0): RESTRICTED/CONFIDENTIAL ⇒ masks + `CRM.CALLER_ID.READ_RESTRICTED` gate (§16).
- P-04 Masking: display masks (`+9665•• ••• •••` pattern) for non-required UI; full number only with capability + purpose.
- P-05 Local encryption: offline projection field-encrypted (AES-256-GCM reuse); lookup tokens HMAC (G8-ADR-004 §28).
- P-06 Logout purge (P0): purge caller dataset on logout/tenant switch (§40).
- P-07 Retention: call events retention + purge policy defined with tenant/jurisdiction policy (Track C).
- P-08 Audit: caller lookups + restricted reads + event ingestion audited via aspect/AuditPort (`platform_audit_logs`).
- P-09 No full phone in logs/telemetry labels (§36).
- P-10 No unauthorized lock-screen disclosure: card content behind biometric/app lock when privacy_level=CONFIDENTIAL/RESTRICTED (native track).
- P-11 AMBIGUOUS/UNKNOWN: no candidate details disclosure (count only).
- P-12 No automatic customer creation from a call (FR-007/§38).

---

## 19. SECURITY THREAT MODEL (G8)

| # | Threat | Mitigation (baseline) | Priority |
|---|--------|----------------------|----------|
| T-01 | Phone enumeration (mass lookup) | working per-tenant rate limit (repair/implement §4.4 gap), exact-match only, POST body, count-only ambiguous, audit | P0 |
| T-02 | Cross-tenant lookup | tenant from auth context only; RLS on new tables; TenantRlsConnectionHandler GUC; dedicated test (§33) | P0 |
| T-03 | URL/log leakage | POST; no phone in access logs; redaction filter in logback for phone patterns; no phone in telemetry labels | P0 |
| T-04 | PII exposure on lock screen | privacy-gated card; biometric/app lock for restricted; no notification payload with identity (P1) | P0 |
| T-05 | Stolen device | SecureStore keys; HMAC token not reversible to phone without key; logout purge; optional remote wipe (P2) | P0 |
| T-06 | Offline database extraction | encrypted fields; token-only index; no plaintext phone column in projection | P0 |
| T-07 | Fake provider webhook | signature verification (HMAC/private key), timestamp window ±5 min, provider identity config | P0 |
| T-08 | Webhook replay | timestamp + nonce/replay cache; idempotency via provider_call_id | P0 |
| T-09 | Duplicate events | unique provider_call_id + idempotency records; DUPLICATE status | P0 |
| T-10 | Spoofed tenant | webhook→tenant mapping via registered provider→tenant table, NEVER body alone; device binding for device events | P0 |
| T-11 | Role abuse | capability aspect; restricted cap; audit of ALLOW/DENY | P0 |
| T-12 | Mass reverse lookup | rate limit + audit + anomaly metrics (lookup_total/tenant per interval) | P0 |
| T-13 | Rate-limit bypass | limiter on authenticated context (fix resolveTenantId); per-device + per-user + per-tenant caps | P1 |
| T-14 | Ambiguous caller disclosure | AMBIGUOUS count-only; no partial names | P0 |
| T-15 | Stale offline identity | dataset age indicator; refresh cadence; purge on logout/tenant switch; match is "safe local best" | P1 |

---

## 20. PERFORMANCE / SLO BASELINE

Caller identification is a latency-sensitive path. Baseline SLOs (to be validated on physical devices + prod in Track E/I/J):

| Metric | SLO (baseline) | Notes |
|--------|----------------|-------|
| LOCAL_LOOKUP_P95 | ≤ 100 ms | SQLite token point-lookup |
| LOCAL_RESOLUTION_P95 | ≤ 200 ms | lookup + card assembly |
| BACKEND_LOOKUP_P95 | ≤ 300 ms | incl. auth filter + aspect |
| BACKEND_LOOKUP_P99 | ≤ 750 ms | |
| CALLER_CARD_TARGET | ≤ 1 s end-to-end | UI from incoming call |
| ANDROID_NATIVE_RESPONSE | < platform hard deadline | CallScreeningService must answer within the platform budget; local-first design mandatory |

Design rules: local-first, token-index point lookups, no enrichment queries on the hot path (P2 fields lazy), no phone in cache keys in plaintext, cache TTL bounded.

---

## 21. OBSERVABILITY BASELINE

Metrics (counter/histogram names baseline — final registration in Track J; **no phone/customer-name/entity-id in labels**):

`caller_lookup_total`, `caller_lookup_exact_total`, `caller_lookup_unknown_total`, `caller_lookup_ambiguous_total`, `caller_lookup_offline_total`, `caller_lookup_latency` (histogram), `caller_cache_age`, `caller_cache_entries`, `caller_sync_failures`, `call_event_duplicates`, `call_webhook_failures`, `call_match_rate`.

Forbidden labels: phone number, customer name, entity id if sensitive. Audit events carry correlation_id only.

---

## 22. TEST MATRIX (pre-implementation — Track J executes)

### Phone normalization
`05xxxxxxxx` · `5xxxxxxxx` · `966xxxxxxxxx` · `+966xxxxxxxxx` · `00966xxxxxxxxx` · spaces · dashes · parentheses · invalid · empty · countryHint missing/unknown · already-E.164 non-SA.

### Matching
exact contact · exact account · lead · verified/unverified · preferred/non-preferred · duplicate same tenant (AMBIGUOUS) · archived (excluded) · inactive (excluded) · unknown · private · ambiguous · restricted (capability gate) · cross-tier tie.

### Security
missing authentication (401) · missing capability (403) · cross-tenant (A lookup → A only; B lookup → B only; §33) · phone enumeration (rate limit 429) · restricted PII (masked without cap) · replay webhook · forged signature · duplicate event idempotent.

### Offline
online sync → offline incoming call · changed number · removed number · archived customer · tenant switch (purge) · logout (purge) · corrupted cache (rebuild) · stale cache (indicator, safe match).

### Telephony
ringing → answered · ringing → missed · ringing → rejected · call with no match (UNKNOWN) · duplicate event · out-of-order event · retry · provider timeout.

### Mobile native
background · app terminated · screen locked · no network · airplane mode · permissions denied · role denied (CallScreening not granted) · hard response timing.

---

## 23. MIGRATION GAP ANALYSIS (design only — NO migration written in this command)

| Prospective migration | Verdict | Reason |
|-----------------------|---------|--------|
| create `crm_call_events` (+ indexes + triggers) | **REQUIRED** (Track C) | G8-ADR-003 SSoT; no existing table |
| RLS on `crm_call_events` (ENABLE + FORCE + policy) | **REQUIRED** (fold into create or follow-up, pattern V20260812_1/3) | §33 |
| create `caller_lookup` projection (server-side if staged) | **REQUIRED** (Track D) | offline dataset Option B |
| unique index `(tenant_id, provider, provider_call_id)` | **REQUIRED** (Track C, unless idempotency records suffice — then CONDITIONAL) | duplicate-event P0 |
| seed caller-id capabilities (CRM.CALLER_ID.*, CRM.CALL_EVENT.*, CRM.CALLER_DATASET.MANAGE) | **REQUIRED** (Track H; before any grant; `V20260820_3` abort safeguard) | §16 |
| role template bindings for new caps | **CONDITIONAL** (decide per-role; `CRM_SALES` + `EXECUTIVE_VIEWER` candidates) | §16 |
| add lead canonical phone projection (crm_leads → communication methods or projection table) | **CONDITIONAL** (bound to G8-ADR-002 choice) | §13 |
| re-normalize historical `normalized_value` backfill without E.164 | **CONDITIONAL** (audit first: how many legacy rows lack `+`; Track A) | §4.1 freshness caveat |
| lookup index on `crm_communication_methods` | **NOT REQUIRED** (index exists) | idx_crm_communication_methods_lookup |
| `crm_phone_numbers` / `crm_contact_lookup_index` activation or retirement | **CONDITIONAL** (reuse-as-auxiliary vs leave dormant; decision with §26) | F-10 |

No migration will be authored before G8 EXECUTION COMMAND 02 (Track A/C/D/H gates).

---

## 24. DEFERRED / OUT-OF-SCOPE (locked)

Call recording · AI transcription/summaries · sentiment · voice bot · IVR · predictive/campaign dialer · coaching · full contact center · telecom billing · WhatsApp contact center (§2.2) · global phone deduplication/mastering · realtime Presence/SIP state · two-way call control (hold/transfer) · automatic customer creation from call.

---

## 25. ACCEPTANCE GATES (implementation command must close these to complete G8)

| Gate | Condition | P0/P1 |
|------|-----------|-------|
| G8-AG-01 | PHONE_NORMALIZATION — §22 normalization matrix green (REUSED normalizer) | P0 |
| G8-AG-02 | EXACT_MATCH — deterministic exact reverse lookup on crm_communication_methods via lookup index | P0 |
| G8-AG-03 | AMBIGUOUS_MATCH — duplicate-owner phone returns AMBIGUOUS count-only | P0 |
| G8-AG-04 | UNKNOWN_CALLER — no record ⇒ UNKNOWN; no auto-creation | P0 |
| G8-AG-05 | TENANT_ISOLATION — A↔B same phone cross-tenant test green (§33) | P0 |
| G8-AG-06 | RBAC — CRM.CALLER_ID.* enforced; 401/403 tests green | P0 |
| G8-AG-07 | PRIVACY — masking, restricted gating, no phone in logs/telemetry, purge tests green | P0 |
| G8-AG-08 | OFFLINE_LOOKUP — local token lookup ≤ SLO; encrypted projection | P0 |
| G8-AG-09 | CALLER_DATA_SYNC — delta catalogue (§29) exercised incl. phone changed/removed/archived | P0 |
| G8-AG-10 | CALL_EVENT_IDEMPOTENCY — duplicate/out-of-order events coalesce | P0 |
| G8-AG-11 | ANDROID_NATIVE — CallScreeningService field-tested on physical device; hard deadline met | P0 |
| G8-AG-12 | IOS_PATH — extension build + distribution path validated on device or explicitly gated | P0 |
| G8-AG-13 | PERFORMANCE — SLOs §20 measured and met | P0 |
| G8-AG-14 | OBSERVABILITY — §21 metrics present; no sensitive labels | P1 |
| G8-AG-15 | POSTGRESQL_DIRECT — all tests on CI PostgreSQL 16 direct; no Docker/Testcontainers | P0 |
| G8-AG-16 | RLS — new tables ENABLE+FORCE+policy; RLS tests green | P0 |
| G8-AG-17 | CI — full Post-Merge suite PASS incl. API-count governance + secret scan | P0 |
| G8-AG-18 | PRODUCTION_SMOKE — deploy + health + lookup smoke on Render | P0 |

---

## 26. OFFLINE CALLER DATASET DECISION (G8-ADR-008 — LOCKED)

`G8_OFFLINE_CALLER_DATASET_GAP = CONFIRMED` (communication_method NOT in mobile EntityType — `apps/mobile/src/types/index.ts:11`; server pull enum same 7).

- **Option A — sync full communication methods:** PII-heavy (raw values), large, couples all comm data to the device.
- **Option B — dedicated caller lookup projection (ADOPTED):** a purpose-built minimal table synced via G7 delta extension.

| Criterion | Option A (full comm methods) | Option B (projection, ADOPTED) |
|-----------|------------------------------|--------------------------------|
| PII exposure | raw + display values on device | encrypted display + HMAC token only |
| storage size | large (all methods/history) | minimal (phone token + card fields) |
| latency | bigger table, scan risk | token point-lookup |
| sync complexity | full G7 entity extension + conflicts | lean dedicated delta |
| coupling | couples communication domain ↔ caller domain | decoupled projection |
| security | keys + more ciphertext at risk | token + encrypted fields, no raw phone |

Server-side source for the projection: canonical `crm_communication_methods` (owner_type PERSON/ACCOUNT, phone-ish method_type, status ACTIVE) + lead path per G8-ADR-002. Disposition of dormant G1 tables (F-10): NOT reactivated as the canonical path; if a future optimization (server-side lookup table) is needed, it will reuse `crm_contact_lookup_index` schema shape — decision recorded, no action now.

---

## 27. OFFLINE CALLER LOOKUP PROJECTION (logical schema — NO DDL in this command)

```text
caller_lookup

tenant_id              UUID        (RLS tenant)
phone_lookup_token     TEXT        HMAC-SHA256(normalized E.164, tenant/device-scoped key)  [G8-ADR-004]
entity_type            TEXT        PERSON | ACCOUNT | LEAD
entity_id              UUID
display_name_encrypted TEXT        AES-256-GCM (reuse key model) — contact/lead name or account name
account_name_encrypted TEXT        AES-256-GCM — null for ACCOUNT/lead-without-account
phone_label            TEXT        label from communication method (encrypted or low-sensitivity)
verified               BOOLEAN
preferred              BOOLEAN
lifecycle_status       TEXT        ACTIVE | INACTIVE | ARCHIVED (snapshot at sync)
sync_version           BIGINT       (G7 cursor semantics)
updated_at             TIMESTAMPTZ
```

Index: `(tenant_id, phone_lookup_token)`. Purgable on logout/tenant switch (P-06). The token table stores no raw phone; reversal requires the key (stolen-device mitigation T-05/T-06).

---

## 28. PHONE LOOKUP TOKEN (G8-ADR-004 — LOCKED)

- `phone_lookup_token = HMAC-SHA256(normalized E.164, K)` where K = tenant/device-scoped key from SecureStore keychain (reuse key hierarchy pattern of `g7_encryption_key_v1`, separate alias for caller tokens).
- Rationale: avoids a plaintext phone index on the device; works with `expo-crypto` (HMAC available; verify API surface at Track D — expo-crypto supports `digestStringAsync` with SHA-256; HMAC-SHA256 keyed operation may require `CryptoJS`-free native API — if unavailable, deterministic SHA-256 over `key || e164` with domain separation is the fallback, recorded in Track D).
- Lookup at call time: compute token from normalized incoming E.164 → point lookup → decrypt display fields.
- Rotation: key rotation invalidates tokens ⇒ rebuild projection (full rebuild path §29).

---

## 29. CALLER DATASET SYNC CONTRACT (design)

**Baseline: G7 generic sync extension** (extend entity types with `caller-lookup` projection entries via the existing pull/push/cursor machinery) **over a dedicated second endpoint** — rationale: one delta pipeline, cursor/conflict semantics reused. Fallback if extension proves too wide: dedicated `caller-identification/delta` (POST, cursor, server-derived tokenized entries) — decision finalized in Track D.

Delta catalogue (must each be testable, §22 offline + AG-09):
`initial snapshot` · `delta (by sync_version)` · `phone changed` (new token; old entry removed) · `phone removed` · `entity archived` (entry flagged/lifecycle_status→ARCHIVED) · `entity reactivated` · `tenant switch` (purge + full rebuild) · `logout purge` · `full rebuild` (on key rotation or corruption).

---

## 30. DATA FRESHNESS POLICY (§40)

- Track `caller dataset last synced at` + `dataset age` (sync_metadata reuse).
- `stale threshold` (baseline: 24 h; tunable), UI/native shows `stale indicator` when exceeded.
- Match behavior on stale data: `BEST SAFE LOCAL MATCH` + stale indicator; staleness NEVER blocks the call UX (FR-014).
- On connectivity return: opportunistic refresh delta.

---

## 31. UNKNOWN / PRIVATE / SPECIAL CALLER POLICIES (locked — command §38/§39)

- `UNKNOWN NUMBER ≠ AUTO CREATE CUSTOMER` — UI shows "Unknown caller"; authorized user chooses: Create Lead / Create Contact / Link to existing record / Ignore (post-call workflow, FR-007/FR-012).
- PRIVATE/WITHHELD/BLOCKED ⇒ `PRIVATE_NUMBER`; INVALID ⇒ `INVALID_NUMBER`; **no reverse lookup attempt without a trustworthy number**.
- No creation implied by an inbound call; creation requires explicit user action + consent handling (P-12).

---

## 32. DELTA / GAP MATRICES

### 32.1 API Gap Matrix (§43 command)

| Capability | Existing | Reusable | Gap | Action |
|-----------|---------|----------|-----|--------|
| Phone normalization | ✅ AddressCommunicationUseCases.normalizePhone | ✅ access via shared service | — | REUSE (Track A) |
| Communication lookup | ✅ search endpoint (LIKE, unfocused) | ✅ table + lookup index | exact-match consumer | ADD exact query (Track A/B) |
| Caller-specific lookup | ❌ none | ✅ auth/RBAC/idempotency | dedicated endpoint | BUILD POST …/caller-identification/lookup (Track B) |
| Match resolver | ❌ none | ✅ policy §9 | zero | BUILD tiered resolver (Track A) |
| Call event ingestion | ❌ none | ✅ idempotency records | zero | BUILD POST …/call-events (Track C) |
| Offline caller delta | ❌ none | ✅ G7 sync machinery | entity-type extension | EXTEND G7 or dedicated endpoint (Track D) |
| Activity projection | ✅ crm_activities CALL + TimelinePort | ✅ | mapping call→activity | BUILD projection (Track C) |
| Timeline projection | ✅ crm_timeline_events | ✅ | mapping | BUILD projection (Track C) |

### 32.2 Data Gap Matrix (§44)

| Data | Existing | Reusable | Gap | Action |
|------|----------|----------|-----|--------|
| crm_accounts | ✅ | ✅ (via methods.account_id) | — | REUSE |
| crm_contacts | ✅ | ✅ (via methods.contact_id) | — | REUSE |
| crm_leads | ✅ table, phone column | ⚠️ orphaned | canonical lead phone path | G8-ADR-002 (Track A) |
| crm_communication_methods | ✅ canonical + lookup index | ✅ | exact-match consumer | REUSE (Track A) |
| crm_activities | ✅ CALL type legal | ✅ | call projection | EXTEND usage (Track C) |
| crm_timeline_events | ✅ | ✅ | call projection | EXTEND usage (Track C) |
| caller lookup projection | ❌ | ✅ G7 entity machinery | table + sync | BUILD (Track D; migration REQUIRED §23) |
| call events | ❌ | ✅ idempotency | table + RLS | BUILD (Track C; migration REQUIRED §23) |
| mobile caller cache | ❌ (7 entity types only) | ✅ SQLite + encryption | projection table | BUILD (Track D) |

### 32.3 Mobile Gap Matrix (§45)

| Area | Status | Action |
|------|--------|--------|
| Expo / RN | ✅ 52.0/0.79.4 (version-mix anomaly noted, verify at prebuild) | VERIFY (Track E/F) |
| SQLite | ✅ v1 schema, transactional | EXTEND (projection table) |
| Secure Store | ✅ tokens + key | REUSE |
| Crypto | ✅ AES-256-GCM; HMAC verify at Track D | REUSE/EXTEND |
| Native Android | ❌ no android/, no manifest service | BUILD CallScreeningService (Track E) |
| Native iOS | ❌ no ios/, no entitlements | BUILD extension (Track F, gated) |
| Caller Cache | ❌ | BUILD (Track D/E) |
| Caller Sync | ❌ | EXTEND G7 (Track D) |
| Caller UI | ❌ web placeholder only (`crm-i18n.tsx:30,48`) | BUILD minimal (Track I) |
| Background/native lifecycle | ❌ no App entry/no headless tasks | BUILD (Track E/F) |

### 32.4 Security Gap Matrix (§46)

| Security | Existing | Gap | Action |
|----------|----------|-----|--------|
| Authentication | ✅ JWT filter + device tokens (tokens exist mobile) | device-bound event auth | BUILD (Track C) |
| Authorization | ✅ aspect + caps | caller caps missing | SEED (Track H) |
| RBAC | ✅ templates + evaluation | template binding for new caps | EXTEND (Track H) |
| Tenant isolation | ✅ auth-context-only + RLS infra | RLS on new tables | BUILD (Track C/D/H) |
| RLS | ✅ pattern ENABLE/FORCE/policy | apply to new tables | BUILD |
| PII masking | ⚠️ masking exists in search API | caller-card masking contract | BUILD (Track B) |
| Local encryption | ✅ AES-256-GCM | projection encryption + token | BUILD (Track D) |
| Log redaction | ⚠️ no phone-specific redaction | redaction filter | BUILD (Track H) |
| Rate limiting | ⚠️ filter exists but inactive for JWT | fix/implement working limiter | FIX (Track B/H) |
| Enumeration protection | ❌ | limiter + audit + metrics | BUILD |
| Webhook verification | ❌ | signature/timestamp/replay | BUILD (Track C/H) |
| Replay prevention | ❌ | nonce/cache + timestamps | BUILD |
| Logout purge | ❌ (SQLite never cleared) | purge caller dataset only (not G7 data) | BUILD (Track D) |

---

## 33. RLS TEST CASE (MANDATORY — AG-05/AG-16)

```text
Tenant A: phone = +9665XXXXXXX
Tenant B: same phone = +9665XXXXXXX
A lookup → A only      (crm_call_events + caller_lookup rows, A context)
B lookup → B only      (never A rows; RLS policy filters)
Cross-tenant probe from authenticated A against B-owned rows → 0 rows (or 403 shape per design)
```
Test runs on CI PostgreSQL 16 direct (PostgreSQL Direct mandate — no Docker/Testcontainers).

---

## 34. WEBHOOK SECURITY BASELINE (provider later)

signature verification · timestamp validation (±5 min window) · replay protection (nonce/cache) · `provider_call_id` · idempotency (records reuse) · out-of-order handling · retry safety · tenant mapping via registered provider→tenant config, **never from webhook body alone**.

---

## 35. PERFORMANCE BASELINE (see §20 — SLO table is normative)

---

## 36. OBSERVABILITY (see §21 — metrics normative) + LOG REDACTION

- Access logs: POST body never logged; no phone patterns in log output (logback redaction filter — Track H).
- Metrics labels: no phone, no customer name, no sensitive entity ids (§21).
- Correlation IDs carried on all G8 audit/event records.

---

## 37. CALLER UI MINIMUM SCOPE (Track I)

P0 DISPLAY: customer/contact name · account/company name · record type · phone label · verification indicator · customer status · owner/assigned employee. P1 CONTEXT: last interaction · open opportunity count · important follow-up. Post-call: add result · add note · create follow-up task · open Customer 360 · create new lead/contact for unknown (explicit user action). NO automatic creation. G8 UI is NOT a Customer 360 rebuild — reuse existing CRM pages; minimal caller card only.

---

## 38. UNKNOWN CALLER POLICY — locked (§31)

---

## 39. PRIVATE NUMBER POLICY — locked (§31); behavior for UNKNOWN/PRIVATE/WITHHELD/BLOCKED/INVALID defined there; NO reverse lookup without trustworthy number.

---

## 40. DATA FRESHNESS POLICY — locked (§30)

---

## 41. REQUIREMENTS REGISTER (governing; 41 requirements)

| ID | Requirement | Category | Priority | Evidence/Source |
|----|------------|----------|----------|-----------------|
| R-01 | Canonical phone source = crm_communication_methods.normalized_value | Data | P0 | G8-ADR-001 |
| R-02 | Reuse single normalizer (Saudi E.164) | Phone | P0 | §8 |
| R-03 | Exact reverse lookup via idx_crm_communication_methods_lookup | Matching | P0 | FR-003 |
| R-04 | Match states EXACT/AMBIGUOUS/UNKNOWN/PRIVATE_NUMBER/INVALID_NUMBER/RESTRICTED | Matching | P0 | G8-ADR-005 |
| R-05 | NO fuzzy matching; NO_RANDOM_MATCH | Matching | P0 | FR-002 |
| R-06 | AMBIGUOUS count-only disclosure | Privacy | P0 | FR-006/P-11 |
| R-07 | Resolution policy §9 (person>account>lead; verified/preferred boost) | Matching | P0 | §9 |
| R-08 | Archived/inactive excluded from EXACT | Matching | P0 | M-04 |
| R-09 | TENANT_SOURCE = authenticated context only | Security | P0 | §16 (verified) |
| R-10 | POST lookup API (no GET ?phone) | API | P0 | G8-ADR-006 |
| R-11 | No tenantId from client | API | P0 | §16 |
| R-12 | Lookup response P0/P1/P2 contract §17 | API | P0 | §17 |
| R-13 | New capabilities CRM.CALLER_ID.READ(+_RESTRICTED), CRM.CALL_EVENT.*, CRM.CALLER_DATASET.MANAGE | RBAC | P0 | §16 |
| R-14 | New tables RLS ENABLE + FORCE + policy | RLS | P0 | §33 |
| R-15 | crm_call_events as call SSoT; activities/timeline projection | Call model | P0 | G8-ADR-003 |
| R-16 | provider_call_id idempotency | Call model | P0 | §13 |
| R-17 | Out-of-order/duplicate event tolerance | Call model | P0 | FR-009 |
| R-18 | CallSourceAdapter port (adapter-based) | Architecture | P0 | G8-ADR-007 |
| R-19 | PRIMARY_TELEPHONY_PROVIDER = NOT YET SELECTED (integration decision before provider release) | Architecture | P0 | §4.7 |
| R-20 | Offline caller dataset = dedicated projection (Option B) | Offline | P0 | G8-ADR-008 |
| R-21 | phone_lookup_token = HMAC-SHA256(E.164, scoped key); no plaintext phone index | Offline/Privacy | P0 | G8-ADR-004 |
| R-22 | Caller dataset delta catalogue (snapshot/delta/change/removal/archive/tenant-switch/purge/rebuild) | Sync | P0 | §29 |
| R-23 | Logout/tenant-switch purge of caller dataset | Privacy | P0 | P-06 |
| R-24 | BEST SAFE LOCAL MATCH + stale indicator; staleness never blocks | Offline | P1 | §30/40 |
| R-25 | UNKNOWN ≠ auto-create; explicit user choice (Lead/Contact/Link/Ignore) | Policy | P0 | FR-007 |
| R-26 | PRIVATE/WITHHELD/BLOCKED ⇒ no lookup attempt | Policy | P0 | §31/39 |
| R-27 | RESTRICTED/CONFIDENTIAL gated by capability + mask | Privacy | P0 | P-03 |
| R-28 | No phone in logs/telemetry labels; POST-only; redaction filter | Privacy | P0 | §36 |
| R-29 | Working per-tenant rate limit for lookups (fix existing filter gap) | Security | P0 | T-01/T-13 |
| R-30 | Webhook signature + timestamp + replay + tenant mapping (never body) | Security | P0 | §34 |
| R-31 | Call event RLS + audit via AuditPort | Audit | P0 | P-08 |
| R-32 | SLO: backend P95 ≤300ms / P99 ≤750ms; local P95 ≤100ms | Performance | P0 | §20 |
| R-33 | Android CallScreeningService + ROLE_CALL_SCREENING + hard deadline + physical tests | Native | P0 | §23 (command) |
| R-34 | iOS extension path (entitlements, App Group, distribution limits) validated or explicitly gated | Native | P0 | §24 (command) |
| R-35 | Caller UI minimal scope; post-call workflow; no 360 rebuild | UI | P1 | §37 |
| R-36 | Observability metrics §21 without sensitive labels | Observability | P1 | §21 |
| R-37 | PostgreSQL Direct (CI 16) for all tests | Test | P0 | AG-15 |
| R-38 | API governance counts/OpenAPI updated with G8 endpoints | Governance | P0 | §12.3 |
| R-39 | Migration chain integrity (new migrations only; no edits to applied) | Governance | P0 | §23 |
| R-40 | Match-result caching without plaintext phone keys (bounded TTL) | Performance | P2 | M-07 |
| R-41 | Archived-only opt-in query (`includeArchived`) | Matching | P2 | §9 note |

**Counts:** TOTAL = 41 · P0 = 33 · P1 = 6 · P2 = 2 · P3 = 0.

---

## 42. MIGRATION REQUIREMENTS SUMMARY — see §23 (none authored in this command)

---

## 43. EXECUTION TRACKS (for G8 EXECUTION COMMAND 02+)

| Track | Name | Scope (summary) | Depends on |
|-------|------|-----------------|------------|
| A | Canonical Data & Matching Engine | exact-match repository consumer, tiered resolver (policy §9), lead phone decision (ADR-002), conditional legacy re-normalization audit | — |
| B | Caller Identification API | POST lookup endpoint, card contract §17, idempotency, rate-limit fix, API-count governance bump | A |
| C | Call Event Persistence & Timeline | crm_call_events migration + RLS + idempotency + activities/timeline projection + ingestion endpoint | A, B |
| D | Offline Caller Dataset | caller_lookup projection (migration), token (ADR-004), delta sync (G7 extension or dedicated), purge/rebuild | B, C |
| E | Android Native Integration | CallScreeningService, ROLE_CALL_SCREENING, local-first lookup, background/locked behavior, physical tests | D |
| F | iOS Platform Integration | extension/entitlements/App Group/directory update mechanism, distribution gate | D |
| G | PBX/VoIP Adapter Boundary | CallSourceAdapter port + provider verification skeleton (no provider bound) | C |
| H | Security / Privacy / RLS | capabilities seed + templates, RLS policies on new tables, redaction, webhook security, PDPL tests | A, C, D |
| I | UI / Post-call workflow | minimal caller card, unknown-caller choice flow, post-call actions | B, D |
| J | Test / Release / Production | full test matrix §22, SLO measurement, observability registration, CI gates, Render deploy + smoke | A–I |

Critical path: A → B → C → D → (E,F) → J; H runs parallel with A/C/D; G parallel with C; I parallel with B/D. G8 closes only when AG-01..AG-18 pass (Track J).

---

## 44. FINAL READINESS VERDICT

```text
G8 IDENTITY                  = LOCKED (Caller Identification; historic CRM-G8 conflict documented)
G8 SCOPE                     = LOCKED (§2; out-of-scope §24)
REPOSITORY DELTA             = VERIFIED (§4; HEAD 92f5a389, clean tree)
REUSABLE FOUNDATION          = VERIFIED (§5; REUSE 12 assets, REBUILD none)
ARCHITECTURAL DECISIONS      = RECORDED (ADR-001..008 §§8/9/13/15/26/28)
MASTER REQUIREMENTS          = BASELINED (41 requirements; P0=33, P1=6, P2=2)
ACCEPTANCE GATES             = DEFINED (AG-01..AG-18, §25)
EXECUTION TRACKS             = DEFINED (A–J with dependencies, §43)
CI                           = VERIFY AFTER COMMIT (this command; docs-only change)
G8_IMPLEMENTATION            = NOT STARTED
G8_BASELINE                  = COMPLETE
G8_READY_FOR_IMPLEMENTATION  = YES (awaiting G8 EXECUTION COMMAND 02)
```

**Stop-point:** per command §59, execution STOPS here. No Track A, no API, no migration, no Android/iOS code, no production deployment, no G9 work.

---

## 45. TRACK E — ANDROID NATIVE EXECUTION 05 (factual decisions, 2026-08-21)

Decisions newly LOCKED by G8 EXECUTION COMMAND 05 (Track E; base `main` 25d332fd; branch `g8/android-native-caller-identification`):

| # | Decision | Evidence |
|---|----------|----------|
| E-1 | ANDROID_RING_PATH = NATIVE ONLY: `SanadCallScreeningService` (CallScreeningService) — no RN bridge, no JS, no network, no backend API on the ring path (§3, §16) | `SanadCallScreeningService.kt` (overBudget→allow fallback, single respondToCall) |
| E-2 | ROLE_CALL_SCREENING via RoleManager; states UNSUPPORTED / AVAILABLE_NOT_GRANTED / GRANTED / REVOKED; caller ID only active when `isRoleHeld == true` (§9, §45) | `SanadCallScreeningModule.kt` + facade `roleState()` |
| E-3 | CARRIER_MIN_API = 29 (Android 10+); older installs: APP MAY INSTALL, CALLER_ID UNSUPPORTED — no undocumented API 24–28 fallback (§8) | `CallerIdConstants.NATIVE_MIN_API` = Q |
| E-4 | EXPO CNG: local native module `apps/mobile/modules/sanad-call-screening` + config plugin `app.plugin.js` OWNS manifest config (service/activity/READ_CONTACTS); idempotent prebuild (§5, §52) | plugin + `validate-call-screening-plugin.js` PASS |
| E-5 | Native ring-time projection = DERIVED CACHE of Track D dataset; NO independent native server sync; generation-atomic commit (partial dataset never active); indexed (tenant_id, phone_lookup_token) (§17–§21, §24–§26) | `AndroidNativeCallerProjection.kt` + `ProjectionEngine` |
| E-6 | Dataset HMAC key wrapped by Android Keystore AES-GCM; display/account names AES-GCM at rest; no plain SharedPreferences/SQLite/BuildConfig/logs (§22–§23, §50) | `NativeCrypto.kt` |
| E-7 | Matching parity: tiered policy (§9) — verified CONTACT > preferred > CONTACT > ACCOUNT > LEAD; EXACT/AMBIGUOUS/UNKNOWN/RESTRICTED/INVALID_NUMBER; RESTRICTED carries no identity (§34, §41) | `NativeCallerResolver` + tests 8/8 |
| E-8 | Normalization + HMAC parity consumed from the SAME shared vectors file (byte-identical copy under test resources) (§31–§33) | `NormalizationParityTest` 18 vectors + `HmacParityTest` |
| E-9 | CALL_BLOCKING OUT OF SCOPE: every eligible incoming call receives ALLOW; budgets 300/750/5000 ms with hard fallback respond (§12–§13) | `RingBudgetPolicy` + service |
| E-10 | Permission policy: READ_PHONE_STATE / READ_CALL_LOG / CALL_PHONE / SYSTEM_ALERT_WINDOW NOT requested; READ_CONTACTS = OPTIONAL coverage permission only, never auto-requested (§10–§11, §46, §58) | plugin forbidden-permission assertions |
| E-11 | Minimal caller-ID card (not Track I): identity fields only; lock-screen minimum disclosure (CONFIDENTIAL masked, RESTRICTED marker-only); Arabic RTL / English LTR via resources (§36–§39) | `SanadCallerIdActivity` + values/values-ar |
| E-12 | Track C boundary: RINGING observation queued natively, flushed by JS later to POST /calls/events — never network-posted inside onScreenCall (§42) | `native_call_observations` + `takePendingCallObservations()` |
| E-13 | PHYSICAL DEVICE evidence (AG gate §57/§73): NOT EXECUTED in this environment (no Android device attached) — Track E code+CI gates PASS; physical-device acceptance remains OPEN | G8_EXECUTION_05 report §57 |

No new Flyway migrations (§64) — NEW FLYWAY = 0. No backend change, no OpenAPI change (§65–§66). Track E does NOT start F (iOS), G (PBX/VoIP), I (Caller UI).
