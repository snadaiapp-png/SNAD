# CRM-019 API Mapping — Opportunities Tab

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-019

---

## 1. API Endpoints Used

| # | Endpoint | Method | Purpose | Called By |
|---|----------|--------|---------|-----------|
| 1 | `GET /api/v1/crm/opportunities` | GET | List opportunities | `crmApi.opportunities()` |
| 2 | `POST /api/v1/crm/opportunities` | POST | Create opportunity | `crmApi.createOpportunity(body)` |
| 3 | `PATCH /api/v1/crm/opportunities/:id/stage` | PATCH | Move opportunity | `crmApi.moveOpportunity(id, stageId, reason?)` |
| 4 | `GET /api/v1/crm/pipelines` | GET | List pipelines | `crmApi.pipelines()` |
| 5 | `GET /api/v1/crm/pipelines/:id/stages` | GET | List stages | `crmApi.stages(pipelineId)` |

---

## 2. Request/Response Mapping

### 2.1 List Opportunities

```typescript
// Request
GET /api/v1/crm/opportunities?limit=200

// Response: CrmOpportunity[]
```

### 2.2 Create Opportunity

```typescript
// Request
POST /api/v1/crm/opportunities
{
  accountId: string,
  contactId?: string,
  pipelineId: string,
  stageId: string,
  name: string,
  amount?: number,
  currencyCode: string,
  expectedCloseDate?: string
}

// Response: CrmOpportunity
```

### 2.3 Move Opportunity

```typescript
// Request
PATCH /api/v1/crm/opportunities/:id/stage
{
  stageId: string,
  reason?: string
}

// Response: CrmOpportunity
```

### 2.4 List Pipelines

```typescript
// Request
GET /api/v1/crm/pipelines

// Response: CrmPipeline[]
```

### 2.5 List Stages

```typescript
// Request
GET /api/v1/crm/pipelines/:id/stages

// Response: CrmStage[]
```

---

## 3. DTO Verification

### 3.1 CrmOpportunity

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

### 3.2 CrmPipeline

```typescript
export interface CrmPipeline {
  id: string;
  name: string;
  currency_code?: string | null;
  active: boolean;
}
```

### 3.3 CrmStage

```typescript
export interface CrmStage {
  id: string;
  pipeline_id: string;
  name: string;
  sequence: number;
  probability: number;
  terminal_state?: string | null;
}
```

---

## 4. Field Mapping

### 4.1 Create Form Fields

| Field | Type | Required | Source | Verified |
|-------|------|----------|--------|----------|
| `name` | string | YES | Form input | ✅ |
| `pipelineId` | string | YES | Dropdown (from `crmApi.pipelines()`) | ✅ |
| `stageId` | string | YES | Dropdown (filtered by pipeline) | ✅ |
| `amount` | number | NO | Form input | ✅ |
| `currencyCode` | string | YES | Dropdown (SAR/USD/EUR) | ✅ |
| `accountId` | string | YES | Hardcoded placeholder | ✅ |

### 4.2 Move Stage Fields

| Field | Type | Required | Source | Verified |
|-------|------|----------|--------|----------|
| `stageId` | string | YES | Dropdown (filtered by pipeline) | ✅ |
| `reason` | string | NO | Form input | ✅ |

---

## 5. Error Handling

| Scenario | Handling |
|----------|----------|
| API fetch failure | Error banner with dismiss button |
| Create validation failure | Inline form error message |
| Move stage failure | Error banner in dialog |
| Empty results | Empty state message |

---

**API Mapping Authority:** CRM-019 Implementation Authority
**Date:** 2026-07-29
