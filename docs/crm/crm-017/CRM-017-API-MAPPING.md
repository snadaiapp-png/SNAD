# CRM-017 API Mapping — Customer-360 View

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-017

---

## 1. API Endpoints Used

| # | Endpoint | Method | Purpose | Called By |
|---|----------|--------|---------|-----------|
| 1 | `GET /api/v1/crm/accounts/:id/customer-360` | GET | Fetch full customer-360 data | `crmApi.customer360(id)` |

---

## 2. Request/Response Mapping

### 2.1 Customer-360

```typescript
// Request
GET /api/v1/crm/accounts/:id/customer-360

// Response: Customer360
{
  account: CrmAccount,
  contacts: CrmContact[],
  opportunities: Array<CrmOpportunity & { pipeline_name?: string; stage_name?: string }>,
  activities: CrmActivity[],
  timeline: CrmTimelineEvent[]
}
```

---

## 3. DTO Verification

### 3.1 Customer360 Type

```typescript
export interface Customer360 {
  account: CrmAccount;
  contacts: CrmContact[];
  opportunities: Array<CrmOpportunity & { pipeline_name?: string; stage_name?: string }>;
  activities: CrmActivity[];
  timeline: CrmTimelineEvent[];
}
```

### 3.2 CrmAccount

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

### 3.3 CrmContact

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

### 3.4 CrmOpportunity

```typescript
export interface CrmOpportunity {
  id: string;
  account_id: string;
  contact_id?: string | null;
  pipeline_id: string;
  stage_id: string;
  name: string;
  amount?: number | null;
  currency_code: string;
  probability: number;
  status: string;
  expected_close_date?: string | null;
  updated_at: string;
}
```

### 3.5 CrmActivity

```typescript
export interface CrmActivity {
  id: string;
  activity_type: string;
  subject: string;
  body?: string | null;
  related_type?: string | null;
  related_id?: string | null;
  status: string;
  priority: number;
  due_at?: string | null;
  updated_at: string;
}
```

### 3.6 CrmTimelineEvent

```typescript
export interface CrmTimelineEvent {
  id: string;
  subject_type: string;
  subject_id: string;
  event_type: string;
  summary: string;
  occurred_at: string;
}
```

---

## 4. Field Mapping

### 4.1 Account Summary Section

| Field | Type | Source | Display |
|-------|------|--------|---------|
| `account.account_type` | string | CrmAccount | KPI card |
| `account.lifecycle_status` | string | CrmAccount | KPI card |
| `account.primary_currency_code` | string \| null | CrmAccount | KPI card |
| `account.updated_at` | string | CrmAccount | KPI card (formatted date) |

### 4.2 Contacts Section

| Field | Type | Source | Display |
|-------|------|--------|---------|
| `contact.display_name` | string | CrmContact | Column 1 (primary) |
| `contact.primary_email` | string \| null | CrmContact | Column 2 |
| `contact.primary_phone` | string \| null | CrmContact | Column 3 |
| `contact.lifecycle_status` | string | CrmContact | Column 4 (status badge) |

### 4.3 Opportunities Section

| Field | Type | Source | Display |
|-------|------|--------|---------|
| `opportunity.name` | string | CrmOpportunity | Column 1 (primary) |
| `opportunity.pipeline_name` | string \| undefined | CrmOpportunity | Column 2 |
| `opportunity.stage_name` | string \| undefined | CrmOpportunity | Column 3 |
| `opportunity.amount` | number \| null | CrmOpportunity | Column 4 (formatted) |
| `opportunity.probability` | number | CrmOpportunity | Column 5 (percentage) |
| `opportunity.status` | string | CrmOpportunity | Column 6 (status badge) |

### 4.4 Activities Section

| Field | Type | Source | Display |
|-------|------|--------|---------|
| `activity.subject` | string | CrmActivity | Column 1 (primary) |
| `activity.activity_type` | string | CrmActivity | Column 2 |
| `activity.status` | string | CrmActivity | Column 3 (status badge) |
| `activity.due_at` | string \| null | CrmActivity | Column 4 (formatted date) |

### 4.5 Timeline Section

| Field | Type | Source | Display |
|-------|------|--------|---------|
| `event.summary` | string | CrmTimelineEvent | Column 1 (primary) |
| `event.event_type` | string | CrmTimelineEvent | Column 2 |
| `event.occurred_at` | string | CrmTimelineEvent | Column 3 (formatted date) |

---

## 5. Error Handling

| Scenario | Handling |
|----------|----------|
| API fetch failure | Error banner with dismiss button |
| Empty data | Empty state messages per section |
| Null fields | Fallback to "—" display |

---

**API Mapping Authority:** CRM-017 Implementation & G3 Closure Agent
**Date:** 2026-07-29
