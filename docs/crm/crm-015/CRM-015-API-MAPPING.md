# CRM-015 API Mapping — Customers (Accounts) Tab

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-015

---

## 1. API Endpoints Used

| # | Endpoint | Method | Purpose | Called By |
|---|----------|--------|---------|-----------|
| 1 | `GET /api/v1/crm/accounts` | GET | List accounts | `crmApi.accounts(search?)` |
| 2 | `POST /api/v1/crm/accounts` | POST | Create account | `crmApi.createAccount(body)` |
| 3 | `PATCH /api/v1/crm/accounts/:id/archive` | PATCH | Archive account | `crmApi.archiveAccount(id)` |
| 4 | `PATCH /api/v1/crm/accounts/:id/restore` | PATCH | Restore account | `crmApi.restoreAccount(id)` |

---

## 2. Request/Response Mapping

### 2.1 List Accounts

```typescript
// Request
GET /api/v1/crm/accounts?limit=200&search={search}

// Response: CrmAccount[]
[
  {
    id: string,
    display_name: string,
    account_type: string,
    lifecycle_status: string,
    primary_currency_code: string | null,
    owner_user_id: string | null,
    updated_at: string
  }
]
```

### 2.2 Create Account

```typescript
// Request
POST /api/v1/crm/accounts
{
  displayName: string,
  accountType: string,
  primaryCurrencyCode: string,
  preferredLocale: string,
  timeZone: string,
  source?: string
}

// Response: CrmAccount
```

### 2.3 Archive Account

```typescript
// Request
PATCH /api/v1/crm/accounts/:id/archive

// Response: CrmAccount
```

### 2.4 Restore Account

```typescript
// Request
PATCH /api/v1/crm/accounts/:id/restore

// Response: CrmAccount
```

---

## 3. DTO Verification

| Field | Type | Required | Source | Verified |
|-------|------|----------|--------|----------|
| `displayName` | string | YES | Form input | ✅ |
| `accountType` | string | YES | Dropdown (CUSTOMER/PARTNER/VENDOR/COMPETITOR/OTHER) | ✅ |
| `primaryCurrencyCode` | string | YES | Dropdown (SAR/USD/EUR) | ✅ |
| `preferredLocale` | string | YES | Hardcoded "ar" | ✅ |
| `timeZone` | string | YES | Hardcoded "Asia/Riyadh" | ✅ |
| `source` | string | NO | Not exposed in form | ✅ |

---

## 4. Type Definition

```typescript
export interface CrmAccount {
  id: string;
  display_name: string;
  account_type: string;
  lifecycle_status: string;
  primary_currency_code?: string | null;
  owner_user_id?: string | null;
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
