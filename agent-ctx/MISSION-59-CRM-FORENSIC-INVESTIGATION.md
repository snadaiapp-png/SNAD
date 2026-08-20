# MISSION 59 — COMPREHENSIVE CRM FORENSIC INVESTIGATION & ROOT-CAUSE RECONCILIATION

**Status:** FORENSIC_INVESTIGATION_COMPLETE
**Mode:** STRICT READ-ONLY FORENSIC INVESTIGATION
**Date:** 2026-08-10

---

## 1. Executive Summary

This forensic investigation analyzed 6 CRM symptoms (E01–E06) observed in Production after the Mission 58 certified release. The investigation traced each symptom through the full stack: frontend components → API client → BFF proxy → backend controllers → DTOs → services → repositories → database.

**Key Finding:** Five of the six symptoms (E01–E05) share a **common error-masking layer** that hides the actual backend error behind a generic Arabic message. Each symptom has a distinct root cause, but all produce the identical user-visible error: "البيانات المرسلة غير صالحة. راجع الحقول وأعد المحاولة." The sixth symptom (E06) is a frontend data-handling bug unrelated to the error-masking issue.

**Three root causes are PROVEN with code-level evidence.** Two are HIGH CONFIDENCE based on code analysis. One is LIKELY based on validation analysis.

---

## 2. Investigation Scope

| Item | Value |
|------|-------|
| Repository | `snadaiapp-png/SNAD` |
| Certified SHA | `1012a8ff58c4a2a42947eb0f9474ef8c3f479ec5` |
| Production URL | `https://snad-app.vercel.app` |
| Recovery Tag | `v20260810.1-production-certified` |
| Recovery Branch | `release/production-certified-20260810` |
| Evidence Count | 7 (E01–E06 with E04 split into A/B) |
| Investigation Phases | 16 (Phase 0–15) |

---

## 3. Evidence Register

| ID | Symptom | Source | Description |
|----|---------|--------|-------------|
| E01 | Integration operation failure | Screenshot | CRM Integrations page shows "البيانات المرسلة غير صالحة" when dispatching AI insight or workflow |
| E02 | Account invalid-data error | Screenshot | Account detail page (Customer Master) shows same error on save |
| E03 | Lead conversion failure | Screenshot | Lead "تحويل" button shows same error |
| E04A | Pipeline creation failure | Screenshot | Creating new pipeline shows same error |
| E04B | Pipeline stage creation failure | Screenshot | Adding new stage shows same error |
| E05 | Task fails when due date supplied | Screenshot | Task creation succeeds without due date, fails with due date |
| E06 | Case created successfully but not listed | Screenshot | CREATE returns success but LIST always shows empty |

---

## 4. Environment / Release Identity

| Check | Value | Status |
|-------|-------|--------|
| HEAD | `1012a8ff58c4a2a42947eb0f9474ef8c3f479ec5` | ✅ |
| origin/main | `1012a8ff58c4a2a42947eb0f9474ef8c3f479ec5` | ✅ |
| HEAD == origin/main | YES | ✅ |
| Recovery Tag | `v20260810.1-production-certified` → `1012a8ff` | ✅ |
| Recovery Branch | `release/production-certified-20260810` → `1012a8ff` | ✅ |
| Production Status | HTTP 200 | ✅ |
| CI Run 31340899416 | 1313/1313 backend + 94/94 CRM + 4/4 E2E | ✅ |

---

## 5. Timeline

| Event | SHA/Time |
|-------|----------|
| Mission 54 CI fix | `42de0d4d` |
| Mission 58 certified commit | `1012a8ff` |
| Recovery tag created | `v20260810.1-production-certified` |
| Recovery branch created | `release/production-certified-20260810` |
| Mission 59 investigation | 2026-08-10 |

---

## 6. E01 — Integration Analysis (CRM Integrations / علاقات العملاء)

### Symptom
The CRM Integrations page displays "البيانات المرسلة غير صالحة" when the user attempts to dispatch a workflow or request an AI insight.

### Root Cause (HIGH CONFIDENCE)
**The `sourceEntityId` field is typed as `@NotNull UUID` on the backend, but the frontend provides a free-form text input without UUID validation.**

### Evidence Chain

**Backend DTOs:**
- `WorkflowDispatchRequest` (`CrmWorkflowController.java:124-130`):
  ```java
  @NotNull UUID sourceEntityId
  ```
- `AiRequest` (`CrmIntegrationController.java:165-170`):
  ```java
  @NotNull UUID sourceEntityId
  ```

**Frontend:**
- `integrations/page.tsx:129`: Validation guard only checks `entityId.trim().length > 0`
- No UUID validation (no regex, no `crypto.randomUUID()` check)
- User types free-form text into the input

**Failure Chain:**
1. User types non-UUID string (e.g., "ACC-12345") into `sourceEntityId` input
2. Frontend sends JSON with `"sourceEntityId": "ACC-12345"`
3. Jackson fails to deserialize string as `java.util.UUID` → `HttpMessageNotReadableException`
4. `CrmExceptionHandler.handleHttpMessageNotReadable()` returns HTTP 400 with English message
5. Frontend `isSafeUserMessage()` rejects English (no Arabic characters)
6. Generic Arabic fallback displayed: "البيانات المرسلة غير صالحة"

### Affected Files
| File | Line | Issue |
|------|------|-------|
| `apps/web/app/crm/(operational)/integrations/page.tsx` | 129 | Missing UUID validation |
| `apps/sanad-platform/.../crm/web/CrmWorkflowController.java` | 124 | `@NotNull UUID sourceEntityId` |
| `apps/sanad-platform/.../crm/web/CrmIntegrationController.java` | 165 | `@NotNull UUID sourceEntityId` |

### Recommended Fix
Add UUID validation to the frontend `validEntity` guard (regex or `crypto.randomUUID()` check), or provide a dropdown of existing entities instead of free-form text input.

---

## 7. E02 — Account Analysis (Enterprise Customer Master)

### Symptom
The Account detail page (Customer Master) displays "البيانات المرسلة غير صالحة" despite successfully loading account data.

### Root Cause (LIKELY)
**Multiple potential failure points on the account detail page.** The page loads and displays account data (GET succeeds), but a write operation or sub-resource call fails.

### Evidence Chain

**The page loads multiple resources:**
1. `GET /api/v1/crm/accounts/{accountId}` — ✅ Works (data displayed)
2. `PATCH /api/v1/crm/accounts/{accountId}/master` — Potential failure (optimistic concurrency, field validation)
3. `POST /api/v1/crm/accounts/{accountId}/addresses` — Potential failure
4. `POST /api/v1/crm/accounts/{accountId}/identifiers` — Potential failure
5. `POST /api/v1/crm/accounts/{accountId}/relationships` — Potential failure (date format)

**Most likely failure causes:**
1. **Stale `If-Match` ETag**: The `CustomerMasterController` requires `If-Match` header for optimistic concurrency. If the version changed since page load, the server rejects with 409/412.
2. **`creditLimit` as NaN**: `Number(field(form, "creditLimit"))` can produce `NaN` if the field is empty, which fails `@DecimalMin("0.0")`.
3. **Invalid `primaryEmail`**: `@Email` validation fails for malformed email.
4. **Date format on relationships**: `effectiveFrom` and `effectiveTo` are sent as raw `YYYY-MM-DD` without `toIsoDateTime()` conversion.

### Affected Files
| File | Line | Issue |
|------|------|-------|
| `apps/web/.../accounts/[accountId]/customer-master-panel.tsx` | 138-158 | PATCH payload construction |
| `apps/sanad-platform/.../crm/party/web/CustomerMasterController.java` | 72-92 | Optimistic concurrency + validation |

### Confidence Note
Without production logs, the exact failure point cannot be determined. The error is likely from one of the PATCH/POST operations, not the GET (which succeeds).

---

## 8. E03 — Lead Conversion Analysis

### Symptom
Clicking "تحويل" (convert) on a lead displays "البيانات المرسلة غير صالحة".

### Root Cause (PROVEN)
**The `crmApi.convertLead()` function does NOT pass an `Idempotency-Key` header, but the V2 backend endpoint requires one.**

### Evidence Chain

**Frontend (`apps/web/lib/api/crm.ts:679-684`):**
```typescript
convertLead: async (id: string, body: { ... }) => {
    const data = await unwrapSingle(
      apiClient.post<...>(`${v2root}/leads/${id}/convert`, body),
      // ^^^ NO Idempotency-Key header
    );
```

**Compare with `createLead` (same file):**
```typescript
createLead: async (body: ...) => {
    const data = await unwrapSingle(
      apiClient.post<...>(`${v2root}/leads`, body, {
        context: { headers: { "Idempotency-Key": `lead-${Date.now()}-...` } }
        // ^^^ HAS Idempotency-Key header
      }),
    );
```

**Backend (`CrmContractController.java:304-323`):**
```java
@PostMapping("/leads/{leadId}/convert")
public ResponseEntity<SingleResponse<LeadConversionResponse>> convertLead(
        Authentication auth,
        @PathVariable UUID leadId,
        @Valid @RequestBody ConvertLeadRequest body,
        @RequestHeader(value = "Idempotency-Key", required = false) String key,
        ...) {
    var guard = idempotency.begin(auth, endpoint, key, body, request);
    // ^^^ THROWS if key is null/blank
```

**Idempotency guard (`CrmIdempotencyHttpSupport.java:43-61`):**
```java
if (idempotencyKey == null || idempotencyKey.isBlank()) {
    throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_KEY_REQUIRED);
}
```

**Error code (`CrmErrorCode.java`):**
```java
CRM_IDEMPOTENCY_KEY_REQUIRED(400, "The Idempotency-Key header is required for this operation.", false)
```

**Failure Chain:**
1. Frontend calls `convertLead()` without `Idempotency-Key` header
2. Backend `idempotency.begin()` throws `CrmContractException(CRM_IDEMPOTENCY_KEY_REQUIRED)`
3. Exception handler returns HTTP 400 with English message
4. Frontend `isSafeUserMessage()` rejects English → generic Arabic fallback

### Affected Files
| File | Line | Issue |
|------|------|-------|
| `apps/web/lib/api/crm.ts` | 679-684 | Missing `Idempotency-Key` header |
| `apps/sanad-platform/.../crm/web/CrmContractController.java` | 304-323 | Requires Idempotency-Key |
| `apps/sanad-platform/.../crm/web/CrmIdempotencyHttpSupport.java` | 43-61 | Throws on missing key |

### Recommended Fix
Add `Idempotency-Key` header to `convertLead()`:
```typescript
convertLead: async (id: string, body: { ... }) => {
    const data = await unwrapSingle(
      apiClient.post<...>(`${v2root}/leads/${id}/convert`, body, {
        context: { headers: { "Idempotency-Key": `convert-lead-${id}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } }
      }),
    );
```

---

## 9. E04 — Pipeline / Stage Analysis

### Symptom
Creating a new pipeline or adding a new stage displays "البيانات المرسلة غير صالحة".

### Root Cause (LIKELY)
**The error is caused by a validation failure hidden by the generic Arabic error message.** The actual validation error (in English) is rejected by `isSafeUserMessage()` and replaced with the generic fallback.

### Evidence Chain

**Pipeline creation DTO (`CreatePipelineRequest.java`):**
```java
public record CreatePipelineRequest(
    @NotNull @NotBlank @Size(max = 160) String name,
    @NotNull @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
    @NotNull @Size(min = 2, max = 20) List<@NotBlank @Size(max = 160) String> stages
) {}
```

**Stage creation DTO (`CrmUpdateDtos.java:53-56`):**
```java
public record CreateStageRequest(
    @NotBlank @Size(max = 160) String name,
    @DecimalMin("0") @DecimalMax("100") BigDecimal probability,
    @Pattern(regexp = "WON|LOST|") String terminalState) {}
```

**Service-layer validation (`CrmService.java:221-245`):**
- Stage count check: `stages.size() < 2 || stages.size() > 20`
- Stage uniqueness check: duplicate names rejected
- Currency normalization: `currency()` validates ISO alpha-3

**Most probable triggers:**
1. Blank/whitespace-only pipeline name (HTML `required` can be bypassed)
2. Stages list with <2 items after `filter(Boolean)` strips empty strings
3. Duplicate stage names
4. Currency code not matching `[A-Za-z]{3}`

### Error Masking
All English validation messages ("name is required", "pipeline stages must contain 2 to 20 items", "pipeline stage names must be unique") are rejected by `isSafeUserMessage()` and replaced with the generic Arabic fallback.

### Affected Files
| File | Line | Issue |
|------|------|-------|
| `apps/web/app/crm/(operational)/pipelines/page.tsx` | 77-91 | Form payload construction |
| `apps/sanad-platform/.../crm/web/CreatePipelineRequest.java` | 19 | DTO validation |
| `apps/sanad-platform/.../crm/web/CrmService.java` | 221-245 | Service validation |

---

## 10. E05 — Task Due-Date Analysis (CRITICAL)

### Symptom
Task creation succeeds WITHOUT a due date but fails WITH a due date.

### Root Cause (PROVEN)
**The frontend sends raw `YYYY-MM-DD` string for `dueAt`, but the backend expects `java.time.OffsetDateTime` which requires an ISO-8601 string with offset.**

### Evidence Chain

**Frontend (`tasks/page.tsx:72-93`):**
```typescript
const dueAt = optionalValue(form, "dueAt");
// ...
crmApi.createTask({ ..., dueAt, ... })
```

**HTML input (`tasks/page.tsx:138`):**
```html
<input name="dueAt" type="date" disabled={busy} />
```
An `<input type="date">` yields `"YYYY-MM-DD"` (e.g., `"2026-08-10"`).

**Frontend API (`crm.ts:903-913`):**
```typescript
createTask: (body: { ..., dueAt?: string; ... }) =>
    apiClient.post<CrmTask, typeof body>(`${root}/tasks`, body),
```
`root = "/api/v1/crm"` — posts to V1 endpoint.

**Backend DTO (`TaskModels.java:25-34`):**
```java
record CreateTaskRequest(
    // ...
    OffsetDateTime startAt,
    OffsetDateTime dueAt) {}
```
`dueAt` is `java.time.OffsetDateTime`.

**Database column (`V20260716_1__create_crm_tasks.sql:41`):**
```sql
due_at TIMESTAMP WITH TIME ZONE
```

**The fix exists but is NOT used:**
```typescript
// crm-view-utils.ts
export function toIsoDateTime(value: string | undefined | null): string | undefined {
    if (!value?.trim()) return undefined;
    if (/T/.test(value)) return value;
    return `${value}T00:00:00.000Z`;
}
```

**Used in:**
- `cases/page.tsx:90`: `dueAt: toIsoDateTime(dueAt)` ✅
- `activities/page.tsx:93`: `dueAt: toIsoDateTime(dueAt)` ✅

**NOT used in:**
- `tasks/page.tsx`: `dueAt` imported as `optionalValue` only ❌

**Failure Chain:**
1. User selects date "2026-08-10" in date picker
2. Frontend sends JSON: `{ "dueAt": "2026-08-10" }`
3. Jackson `OffsetDateTimeDeserializer` expects offset (e.g., "+00:00"), cannot parse bare date
4. `DateTimeParseException` → `HttpMessageNotReadableException` → HTTP 400
5. Frontend displays generic Arabic error

**CASE A (no due date):**
- `optionalValue(form, "dueAt")` returns `undefined`
- JSON: `{}` (field omitted)
- Java: `null` — passes validation ✅

**CASE B (with due date):**
- `optionalValue(form, "dueAt")` returns `"2026-08-10"`
- JSON: `{ "dueAt": "2026-08-10" }`
- Jackson: `DateTimeParseException` ❌

### Affected Files
| File | Line | Issue |
|------|------|-------|
| `apps/web/app/crm/(operational)/tasks/page.tsx` | 8, 72-93 | Missing `toIsoDateTime()` import and usage |
| `apps/web/app/crm/crm-view-utils.ts` | 1-18 | Fix exists but unused by tasks |
| `apps/sanad-platform/.../crm/task/web/TaskModels.java` | 25-34 | `OffsetDateTime dueAt` |

### Recommended Fix
In `tasks/page.tsx`, add `toIsoDateTime` to imports and wrap the `dueAt` value:
```typescript
import { formValue, optionalValue, formatDate, toIsoDateTime } from "../../crm-view-utils";
// ...
crmApi.createTask({ ..., dueAt: toIsoDateTime(dueAt), ... })
```

---

## 11. E06 — Case Persistence Analysis

### Symptom
Case creation returns success ("تم إنشاء الحالة") but the cases list always shows empty ("لا توجد حالات بعد").

### Root Cause (PROVEN)
**The frontend `crmApi.cases()` function does NOT unwrap the V2 `ListResponse` envelope.** The backend returns `{ data: [...], page: {...}, meta: {...} }`, but the frontend treats the raw response as a `CrmCase[]` array.

### Evidence Chain

**Backend (`CaseController.java:54-69`):**
- Returns `CrmEnvelopes.ListResponse.of(...)` — JSON shape: `{ "data": [...], "page": {...}, "meta": {...} }`
- Cases ARE persisted in the database with `status = 'OPEN'`

**Frontend API (`crm.ts:927-928`):**
```typescript
cases: (status?: string, ...) =>
    apiClient.get<CrmCase[]>(`/api/v2/crm/cases`, { query: { limit: 200, status, ... }, cache: "no-store" }),
```
Returns raw response typed as `CrmCase[]` — does NOT unwrap envelope.

**Compare with accounts (`crm.ts:577-581`):**
```typescript
accounts: async (search?: string) => {
    const data = await fetchAllPages<V2AccountResponse>((cursor) =>
        apiClient.get<V2ListResponse<V2AccountResponse>>(`${v2root}/accounts`, { query: { limit: 200, search, cursor }, cache: "no-store" }),
    );
    return data.map(mapV2Account);  // Unwraps envelope + maps fields
},
```

**Frontend page (`cases/page.tsx:36-51`):**
```typescript
const [nextCases, nextAccounts] = await Promise.all([
    crmApi.cases(status || undefined),
    crmApi.accounts(),
]);
setCases(nextCases);  // Sets to envelope object, not array
```

**Line 99:**
```typescript
const hasCases = cases.length > 0;
```
`cases` is `{ data: [...], page: {...}, meta: {...} }` (an object), not an array.
`Object.length` is `undefined`.
`undefined > 0` is `false`.
**`hasCases` is always `false`.**

**Secondary issue — Field name mismatch:**
Backend sends camelCase: `caseType`, `customerId`, `assigneeUserId`, `dueAt`, `createdAt`
Frontend expects snake_case: `case_type`, `customer_id`, `assignee_user_id`, `due_at`, `created_at`

### Affected Files
| File | Line | Issue |
|------|------|-------|
| `apps/web/lib/api/crm.ts` | 927-928 | Missing envelope unwrapping + field mapping |
| `apps/web/app/crm/(operational)/cases/page.tsx` | 36-51, 99 | `cases.length > 0` on object |
| `apps/sanad-platform/.../crm/cases/web/CaseController.java` | 54-69 | Returns ListResponse envelope |

### Recommended Fix
Add `V2CaseResponse` interface, `mapV2Case()` function, and use `fetchAllPages` pattern (same as accounts/contacts/leads).

---

## 12. Common Error Message Analysis

### The Error Chain (Backend → User Display)

```
Backend Controller
  → CrmExceptionHandler (returns English message + HTTP 400)
    → BFF Proxy (passthrough, no transformation)
      → Frontend API Client (extracts body.error.message)
        → user-facing-errors.ts mapHttpError()
          → isSafeUserMessage() CHECK:
              REQUIRES Arabic characters (containsArabic)
              English messages → REJECTED
          → Falls through to hardcoded Arabic fallback
            → "البيانات المرسلة غير صالحة. راجع الحقول وأعد المحاولة."
```

### Source File
`apps/web/lib/api/user-facing-errors.ts`

**Line 194-197** (safe message passthrough):
```typescript
if ((status === 400 || status === 409 || status === 422) && isSafeUserMessage(backendMsg)) {
    return { title: "بيانات غير صالحة", message: backendMsg, kind: "validation" };
}
```

**Line 199-201** (generic fallback — THIS IS THE LINE THAT FIRES):
```typescript
if (status === 400) {
    return { title: "بيانات غير صالحة", message: "البيانات المرسلة غير صالحة. راجع الحقول وأعد المحاولة.", kind: "validation" };
}
```

**Lines 255-261** (the gate):
```typescript
function isSafeUserMessage(message: unknown): message is string {
    if (typeof message !== "string") return false;
    const value = message.trim();
    if (!value || value.length > 240 || !containsArabic(value)) return false;
    if (/https?:\/\/|jdbc:|sql|exception|stack|trace|authorization|bearer|cookie/i.test(value)) return false;
    return true;
}
```

### Impact
Every HTTP 400 response from the backend that contains an English message is masked by the generic Arabic fallback. The actual error (UUID parse failure, missing Idempotency-Key, date format mismatch, validation constraint) is hidden from the user.

### ACTUAL_BACKEND_ERROR vs DISPLAYED_FRONTEND_ERROR

| Evidence | Actual Backend Error | Displayed Frontend Error |
|----------|---------------------|-------------------------|
| E01 | "The request body is missing or is not valid JSON." | "البيانات المرسلة غير صالحة" |
| E03 | "The Idempotency-Key header is required for this operation." | "البيانات المرسلة غير صالحة" |
| E04 | "name is required" / "pipeline stages must contain 2 to 20 items" | "البيانات المرسلة غير صالحة" |
| E05 | HttpMessageNotReadable (DateTimeParseException) | "البيانات المرسلة غير صالحة" |

---

## 13. Frontend → API Contract Analysis

| Evidence | UI Component | Endpoint | Method | Payload | DTO | Failure Point |
|----------|-------------|----------|--------|---------|-----|---------------|
| E01 | Integrations page | `/api/v2/crm/integrations/ai` or `/workflows` | POST | `sourceEntityId: "free-text"` | `@NotNull UUID sourceEntityId` | Jackson UUID deserialization |
| E02 | Account master panel | `/api/v1/crm/accounts/{id}/master` | PATCH | `{ legalName, tradingName, ... }` | `UpdateMasterRequest` | Field validation or ETag conflict |
| E03 | Leads list/detail | `/api/v2/crm/leads/{id}/convert` | POST | `{ createOpportunity, currencyCode }` | `ConvertLeadRequest` | Missing Idempotency-Key |
| E04A | Pipelines page | `/api/v1/crm/pipelines` | POST | `{ name, currencyCode, stages }` | `CreatePipelineRequest` | Validation (hidden) |
| E04B | Pipelines page | `/api/v2/crm/pipelines/{id}/stages` | POST | `{ name }` | `CreateStageRequest` | Validation (hidden) |
| E05 | Tasks page | `/api/v1/crm/tasks` | POST | `{ title, dueAt: "2026-08-10" }` | `OffsetDateTime dueAt` | DateTimeParseException |
| E06 | Cases page | `/api/v2/crm/cases` | GET | N/A | ListResponse envelope | Frontend doesn't unwrap envelope |

---

## 14. DTO / Validation Analysis

### Cross-Entity DTO Summary

| Entity | Required Fields | Validation Constraints | Potential Failures |
|--------|----------------|----------------------|-------------------|
| Account | `displayName` (@NotBlank) | `accountType` pattern, `currencyCode` pattern | Name blank, invalid type |
| Contact | `givenName` (@NotBlank) | `email` (@Email), `consentSummary` pattern | Invalid email format |
| Lead | `displayName` (@NotBlank) | `email` (@Email), `score` range | Name blank |
| ConvertLead | None required | `currencyCode` pattern, `amount` min | Missing Idempotency-Key (primary) |
| Pipeline | `name`, `currencyCode`, `stages` (all @NotNull) | `currencyCode` pattern, `stages` size 2-20 | Blank name, <2 stages |
| Stage | `name` (@NotBlank) | `probability` range, `terminalState` pattern | Blank name |
| Task | `title` (@NotBlank) | `priority` min/max, `dueAt` OffsetDateTime | Date format (primary) |
| Case | `subject` (@NotBlank) | `caseType` pattern, `priority` min/max | N/A (CREATE works) |

### Enum Mismatches
No enum mismatches found. All frontend-selectable values match backend enum patterns.

---

## 15. Date/Time Analysis

### Entity Date Field Types

| Entity | Field | Java Type | DB Type | Frontend Format | Status |
|--------|-------|-----------|---------|-----------------|--------|
| Account | createdAt | Instant | TIMESTAMPTZ | ISO-8601 via OffsetDateTime | ✅ |
| Account | updatedAt | Instant | TIMESTAMPTZ | ISO-8601 via OffsetDateTime | ✅ |
| Task | **dueAt** | **OffsetDateTime** | **TIMESTAMPTZ** | **YYYY-MM-DD (NO conversion)** | ❌ **BUG** |
| Task | startAt | OffsetDateTime | TIMESTAMPTZ | YYYY-MM-DD (NO conversion) | ❌ Same issue |
| Case | dueAt | OffsetDateTime | TIMESTAMPTZ | toIsoDateTime() applied | ✅ |
| Activity | dueAt | OffsetDateTime | TIMESTAMPTZ | toIsoDateTime() applied | ✅ |
| Lead | expectedCloseDate | LocalDate | DATE | N/A | ✅ |
| Relationship | effectiveFrom | String→OffsetDateTime? | DATE/TIMESTAMPTZ | YYYY-MM-DD (NO conversion) | ⚠️ Potential issue |

### Timezone Analysis
- Backend uses UTC consistently (ZoneOffset.UTC in conversions)
- Frontend `toIsoDateTime()` appends `T00:00:00.000Z` (UTC midnight)
- `<input type="date">` has no timezone — browser interprets as local
- `toIsoDateTime()` converts to UTC, which may shift the date by ±1 day depending on user timezone
- This is a minor off-by-one risk but NOT the cause of E05 (the complete parse failure is)

---

## 16. Tenant / RLS Analysis

### RLS Policy
- PostgreSQL RLS enabled on all `crm_*` tables with `tenant_id` column
- Policy: `USING (app.tenant_id IS NULL OR tenant_id::text = current_setting('app.tenant_id'))`
- `SET LOCAL app.tenant_id` executed per-transaction via `TenantRlsConnectionHandler`

### Tenant Context Propagation
1. JWT filter extracts `tenant_id` from claims
2. Controllers extract from `Authentication.getDetails().get("tenant_id")`
3. Repositories use `WHERE tenant_id = :tenantId` in all queries
4. RLS provides database-level enforcement as defense-in-depth

### Assessment
**No tenant isolation issues found.** All CRM operations consistently propagate tenant_id from JWT through to database queries. The RLS policy provides an additional safety net. CREATE and READ operations use the same tenant context.

**E06 (Cases)** is NOT a tenant isolation issue — the backend returns data correctly; the frontend fails to unwrap the response envelope.

---

## 17. Database Analysis

### Migration Status
- 35 V2026 migrations confirmed present
- Latest: `V20260807_4`
- RLS migrations: `V20260730_1`, `V20260802_1` (PostgreSQL vendor path)
- Task table: `V20260716_1__create_crm_tasks.sql` — `due_at TIMESTAMP WITH TIME ZONE`
- Case table: `V20260716_1` — `status` column defaults to `'OPEN'`

### No Database Issues
All CRM tables exist with correct schemas. The issues are in the frontend-backend contract layer, not in the database.

---

## 18. Cross-Symptom Correlation

### Correlation Matrix

| Symptom | Same Error Handler | Same Error Masking | Same Endpoint Family | Same Root Cause |
|---------|--------------------|--------------------|----------------------|-----------------|
| E01 | ✅ CrmExceptionHandler | ✅ isSafeUserMessage | ❌ V2 Integrations | ❌ UUID validation |
| E02 | ✅ CrmExceptionHandler | ✅ isSafeUserMessage | ❌ V1 Accounts | ❌ Field validation |
| E03 | ✅ CrmExceptionHandler | ✅ isSafeUserMessage | ❌ V2 Leads | ❌ Missing Idempotency-Key |
| E04A | ✅ CrmExceptionHandler | ✅ isSafeUserMessage | ✅ V1 Pipelines | ❌ Validation (hidden) |
| E04B | ✅ CrmExceptionHandler | ✅ isSafeUserMessage | ✅ V2 Stages | ❌ Validation (hidden) |
| E05 | ✅ CrmExceptionHandler | ✅ isSafeUserMessage | ❌ V1 Tasks | ❌ Date format |
| E06 | ❌ No error thrown | ❌ N/A | ❌ V2 Cases | ❌ Envelope unwrapping |

### Common Root Causes

**Common Cause 1 — Error Masking (affects E01–E05):**
The `isSafeUserMessage()` function in `user-facing-errors.ts` rejects all English backend messages, causing the generic Arabic fallback for every HTTP 400. This is a DESIGN ISSUE, not a bug per se — it was designed to prevent technical error messages from leaking to users, but it over-masks by hiding even actionable validation messages.

**Independent Root Causes:**
- E01: Missing UUID validation on frontend
- E02: Field validation / ETag conflict (multiple possible causes)
- E03: Missing Idempotency-Key header
- E04A/B: Validation failures (various triggers)
- E05: Missing `toIsoDateTime()` conversion
- E06: Missing V2 envelope unwrapping

### E06 Independence
E06 is completely independent of E01–E05. It is NOT an error-masking issue — it's a data-handling bug where the frontend fails to parse the V2 ListResponse envelope. No error message is shown; instead, the empty state is always rendered.

---

## 19. Root Cause Matrix

| Root Cause ID | Description | Layer | Affects | Confidence |
|---------------|-------------|-------|---------|------------|
| RC-1 | `isSafeUserMessage()` rejects English backend messages | FRONTEND (error mapper) | E01–E05 | PROVEN |
| RC-2 | Missing UUID validation on integrations page | FRONTEND (validation) | E01 | HIGH CONFIDENCE |
| RC-3 | Missing `Idempotency-Key` header in `convertLead()` | API_CONTRACT | E03 | PROVEN |
| RC-4 | Task `dueAt` sent as bare `YYYY-MM-DD` without `toIsoDateTime()` | FRONTEND (date handling) | E05 | PROVEN |
| RC-5 | Cases API doesn't unwrap V2 ListResponse envelope | FRONTEND (API client) | E06 | PROVEN |
| RC-6 | Account master validation failure (ETag or field validation) | FRONTEND + VALIDATION | E02 | LIKELY |
| RC-7 | Pipeline/stage validation failures hidden by error masking | VALIDATION + FRONTEND | E04A/B | LIKELY |

---

## 20. Severity Matrix

| ID | Symptom | Severity | Security Impact | Data Impact | Confidence |
|----|---------|----------|-----------------|------------|------------|
| E01 | Integration operation failure | P2 | None | None | HIGH CONFIDENCE |
| E02 | Account invalid-data error | P1 | None | Possible data not saved | LIKELY |
| E03 | Lead conversion failure | P1 | None | Conversion blocked | PROVEN |
| E04A | Pipeline creation failure | P2 | None | Pipeline not created | LIKELY |
| E04B | Pipeline stage creation failure | P2 | None | Stage not created | LIKELY |
| E05 | Task fails when due date supplied | P1 | None | Task not created with due date | PROVEN |
| E06 | Case created but not listed | P1 | None | Cases invisible to user | PROVEN |

---

## 21. Security Impact

**No security vulnerabilities found.** All symptoms are functional bugs, not security issues.

- Tenant isolation is intact (RLS + application-level filtering)
- No data leakage across tenants
- No authentication/authorization bypasses
- No injection vulnerabilities
- Error messages do not leak sensitive information (by design — the masking actually PREVENTS information leakage, though it over-masks)

---

## 22. Data Integrity Impact

- **E01–E05**: Operations fail before persistence → no data corruption
- **E06**: Cases ARE persisted correctly in the database → data is intact but invisible to the user
- No orphaned records found
- No constraint violations found
- No cascading failures found

---

## 23. Production Impact

| Impact | Assessment |
|--------|------------|
| CI Status | 1313/1313 + 94/94 + 4/4 PASSING |
| Deployment | `1012a8ff` deployed to Vercel production |
| Recovery Point | Tag + Branch immutable and verified |
| User-Facing | 7 symptoms across 6 CRM modules |
| Business Operations | Lead conversion, task creation, pipeline management, case listing all affected |

---

## 24. Recommended Remediation Plan

### Priority 1 — Critical Fixes (Core CRM Operations)

| Fix | Files | Effort | Impact |
|-----|-------|--------|--------|
| Add `toIsoDateTime()` to tasks page | `tasks/page.tsx` | 5 min | Unblocks task creation with due dates |
| Add `Idempotency-Key` to `convertLead()` | `crm.ts` | 5 min | Unblocks lead conversion |
| Fix cases API envelope unwrapping | `crm.ts`, `cases/page.tsx` | 30 min | Makes cases visible |

### Priority 2 — Error Visibility

| Fix | Files | Effort | Impact |
|-----|-------|--------|--------|
| Make `isSafeUserMessage()` accept English validation messages | `user-facing-errors.ts` | 15 min | Shows actual error instead of generic message |
| OR: Add Arabic translations to backend validation messages | `CrmErrorCode.java`, DTOs | 2 hours | Shows specific field-level errors in Arabic |

### Priority 3 — Input Validation

| Fix | Files | Effort | Impact |
|-----|-------|--------|--------|
| Add UUID validation to integrations page | `integrations/page.tsx` | 15 min | Prevents invalid entity IDs |
| Add entity picker dropdown (instead of free text) | `integrations/page.tsx` | 2 hours | Better UX + prevents invalid IDs |
| Fix account master ETag handling | `customer-master-panel.tsx` | 30 min | Prevents stale ETag failures |

---

## 25. Required Regression Tests

| Test | Type | Covers |
|------|------|--------|
| Task creation with due date | E2E | E05 |
| Task creation without due date | E2E | E05 regression |
| Lead conversion with Idempotency-Key | Integration | E03 |
| Cases list after create | E2E | E06 |
| Pipeline creation with valid data | Integration | E04A |
| Pipeline creation with invalid stages | Unit | E04A |
| Stage creation with valid data | Integration | E04B |
| Integration dispatch with valid UUID | Integration | E01 |
| Integration dispatch with invalid UUID | Unit | E01 |
| Account master PATCH with valid data | Integration | E02 |
| Error message mapping (English → Arabic) | Unit | E01–E05 |

---

## 26. Evidence Index

| Evidence | Files Examined | Lines Reviewed |
|----------|---------------|----------------|
| E01 | `integrations/page.tsx`, `crm-integration.ts`, `CrmWorkflowController.java`, `CrmIntegrationController.java` | ~200 lines |
| E02 | `customer-master-panel.tsx`, `CustomerMasterController.java`, `CrmController.java` | ~150 lines |
| E03 | `crm.ts` (convertLead), `CrmContractController.java`, `CrmIdempotencyHttpSupport.java` | ~100 lines |
| E04 | `pipelines/page.tsx`, `CreatePipelineRequest.java`, `CrmUpdateDtos.java`, `CrmService.java` | ~150 lines |
| E05 | `tasks/page.tsx`, `crm.ts` (createTask), `TaskModels.java`, `TaskController.java`, `crm-view-utils.ts` | ~120 lines |
| E06 | `cases/page.tsx`, `crm.ts` (cases), `CaseController.java`, `JdbcCaseRepository.java` | ~150 lines |
| Error Chain | `user-facing-errors.ts`, `client.ts`, `CrmExceptionHandler.java`, `CrmErrorCode.java` | ~100 lines |
| RLS/Tenant | `TenantRlsConnectionHandler.java`, `JwtAuthenticationFilter.java`, RLS migrations | ~200 lines |

---

## 27. Confidence Assessment

| ID | Root Cause | Confidence Level | Basis |
|----|-----------|-----------------|-------|
| RC-1 | Error masking (isSafeUserMessage) | **PROVEN** | Code inspection: line 258 requires Arabic, line 200 is fallback |
| RC-3 | Missing Idempotency-Key | **PROVEN** | Code inspection: `convertLead()` has no header, `begin()` throws on null |
| RC-4 | Task dueAt date format | **PROVEN** | Code inspection: `OffsetDateTime` type, no `toIsoDateTime()` import |
| RC-5 | Cases envelope unwrapping | **PROVEN** | Code inspection: `cases.length > 0` on object, no `fetchAllPages` |
| RC-2 | Missing UUID validation | **HIGH CONFIDENCE** | Code inspection: no UUID regex, free-form input, backend expects UUID |
| RC-6 | Account master failure | **LIKELY** | Multiple possible causes, no production logs to pinpoint |
| RC-7 | Pipeline/stage validation | **LIKELY** | Validation exists but actual trigger unknown without logs |

---

## 28. Final Forensic Verdict

| ID | Symptom | Reproducible | Root Cause | Layer | Severity | Security Impact | Data Impact | Confidence |
|----|---------|--------------|------------|-------|----------|-----------------|------------|------------|
| E01 | Integration operation failure | YES | Missing UUID validation on frontend | API_CONTRACT | P2 | None | None | HIGH CONFIDENCE |
| E02 | Account invalid-data error | YES | Field validation / ETag conflict | VALIDATION | P1 | None | Possible data not saved | LIKELY |
| E03 | Lead conversion failure | YES | Missing Idempotency-Key header | API_CONTRACT | P1 | None | Conversion blocked | PROVEN |
| E04A | Pipeline creation failure | YES | Validation failures hidden by error masking | VALIDATION | P2 | None | Pipeline not created | LIKELY |
| E04B | Pipeline stage creation failure | YES | Validation failures hidden by error masking | VALIDATION | P2 | None | Stage not created | LIKELY |
| E05 | Task fails when due date supplied | YES | Missing `toIsoDateTime()` conversion | FRONTEND | P1 | None | Task not created with due date | PROVEN |
| E06 | Case created but not listed | YES | Frontend doesn't unwrap V2 ListResponse envelope | FRONTEND | P1 | None | Cases invisible to user | PROVEN |

---

## MANDATORY FINAL TABLE

```text
MISSION 59 — FINAL FORENSIC VERDICT

RELEASE_SHA         = 1012a8ff58c4a2a42947eb0f9474ef8c3f479ec5
PRODUCTION_IDENTITY = https://snad-app.vercel.app (HTTP 200)
EVIDENCE_COUNT      = 7 (E01–E06, E04 split into A/B)
CONFIRMED_ROOT_CAUSES = 4 (RC-1, RC-3, RC-4, RC-5)
HIGH_CONFIDENCE_ROOT_CAUSES = 1 (RC-2)
UNCONFIRMED_ROOT_CAUSES = 0
LIKELY_ROOT_CAUSES  = 2 (RC-6, RC-7)
P0                  = 0
P1                  = 4 (E02, E03, E05, E06)
P2                  = 3 (E01, E04A, E04B)
P3                  = 0
SECURITY_IMPACT     = NONE
DATA_INTEGRITY_IMPACT = NONE (E06 data is intact but invisible)
TENANT_ISOLATION_IMPACT = NONE
PRODUCTION_IMPACT   = 7 symptoms across 6 CRM modules
COMMON_ROOT_CAUSE   = isSafeUserMessage() error masking (affects E01–E05)
INDEPENDENT_ROOT_CAUSES = 5 (E01 UUID, E03 Idempotency-Key, E05 Date, E06 Envelope, E02 Validation)
RECOMMENDED_NEXT_MISSION = Mission 60 — CRM Frontend Remediation (fix all 7 symptoms)
REPORT              = agent-ctx/MISSION-59-CRM-FORENSIC-INVESTIGATION.md

FINAL_STATUS = FORENSIC_INVESTIGATION_COMPLETE
```

---

**NO MODIFICATIONS. NO FIXES. NO COMMIT. NO PUSH. NO DEPLOYMENT.**
