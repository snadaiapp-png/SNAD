# CRM-016 API Mapping — Contacts Tab

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-016

---

## 1. API Endpoints Used

| # | Endpoint | Method | Purpose | Called By |
|---|----------|--------|---------|-----------|
| 1 | `GET /api/v1/crm/contacts` | GET | List contacts | `crmApi.contacts(accountId?, search?)` |
| 2 | `POST /api/v1/crm/contacts` | POST | Create contact | `crmApi.createContact(body)` |
| 3 | `PATCH /api/v1/crm/contacts/:id/archive` | PATCH | Archive contact | `crmApi.archiveContact(id)` |
| 4 | `PATCH /api/v1/crm/contacts/:id/restore` | PATCH | Restore contact | `crmApi.restoreContact(id)` |

---

## 2. Request/Response Mapping

### 2.1 List Contacts

```typescript
// Request
GET /api/v1/crm/contacts?limit=200&search={search}

// Response: CrmContact[]
[
  {
    id: string,
    account_id: string | null,
    given_name: string,
    family_name: string | null,
    display_name: string,
    primary_email: string | null,
    primary_phone: string | null,
    consent_summary: string,
    lifecycle_status: string,
    updated_at: string
  }
]
```

### 2.2 Create Contact

```typescript
// Request
POST /api/v1/crm/contacts
{
  accountId?: string,
  givenName: string,
  familyName?: string,
  primaryEmail?: string,
  primaryPhone?: string,
  preferredLocale: string,
  timeZone: string,
  consentSummary: string
}

// Response: CrmContact
```

### 2.3 Archive Contact

```typescript
// Request
PATCH /api/v1/crm/contacts/:id/archive

// Response: CrmContact
```

### 2.4 Restore Contact

```typescript
// Request
PATCH /api/v1/crm/contacts/:id/restore

// Response: CrmContact
```

---

## 3. DTO Verification

| Field | Type | Required | Source | Verified |
|-------|------|----------|--------|----------|
| `givenName` | string | YES | Form input | ✅ |
| `familyName` | string | NO | Form input | ✅ |
| `primaryEmail` | string | NO | Form input | ✅ |
| `primaryPhone` | string | NO | Form input | ✅ |
| `preferredLocale` | string | YES | Hardcoded "ar" | ✅ |
| `timeZone` | string | YES | Hardcoded "Asia/Riyadh" | ✅ |
| `consentSummary` | string | YES | Dropdown (PENDING/GRANTED/DENIED/WITHDRAWN) | ✅ |
| `accountId` | string | NO | Not exposed in create form (set via customer-360) | ✅ |

---

## 4. Type Definition

```typescript
export interface CrmContact {
  id: string;
  account_id?: string | null;
  given_name: string;
  family_name?: string | null;
  display_name: string;
  primary_email?: string | null;
  primary_phone?: string | null;
  consent_summary: string;
  lifecycle_status: string;
  updated_at: string;
}
```

---

## 5. Error Handling

| Scenario | Handling |
|----------|----------|
| API fetch failure | Error banner with dismiss button |
| Create validation failure | Inline form error message |
| Archive/restore failure | Error banner with dismiss button |
| Empty results | Empty state message |

---

**API Mapping Authority:** CRM G3 Execution Coordinator
**Date:** 2026-07-29
