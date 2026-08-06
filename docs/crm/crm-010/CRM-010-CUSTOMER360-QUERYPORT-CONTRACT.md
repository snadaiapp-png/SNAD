# CRM-010 Customer360QueryPort Contract

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** APPROVED

---

## 1. Purpose

The `Customer360QueryPort` is the unified read port for assembling a complete 360-degree view of a customer. It aggregates data across CRM modules (accounts, contacts, opportunities, activities, timeline, scores, segments, insights) into a single response, eliminating N+1 client calls and ensuring consistent cross-module data representation.

---

## 2. Interface Definition

```java
package com.sanad.platform.crm.intelligence.domain;

public interface Customer360QueryPort {

    Optional<Customer360View> findById(UUID tenantId, UUID accountId);

    Customer360Page search(Customer360SearchCriteria criteria);

    Customer360View enrichWithIntelligence(UUID tenantId, UUID accountId);
}
```

---

## 3. Request Schema

### 3.1 findById

```json
{
  "tenantId": "UUID",
  "accountId": "UUID"
}
```

### 3.2 search

```json
{
  "tenantId": "UUID",
  "query": "string (free-text on displayName, email, phone)",
  "segmentCodes": ["string"],
  "scoreBands": {
    "health": ["HEALTHY", "THRIVING"],
    "engagement": ["HIGH"],
    "risk": ["LOW_RISK"]
  },
  "lifecycleStatus": ["ACTIVE"],
  "customerTier": ["PLATINUM", "GOLD"],
  "page": 0,
  "size": 20,
  "sort": "lastActivityAt,desc"
}
```

---

## 4. Response Schema

### 4.1 Customer360View

```json
{
  "accountId": "UUID",
  "tenantId": "UUID",
  "displayName": "string",
  "accountType": "CUSTOMER|PROSPECT|PARTNER",
  "lifecycleStatus": "ACTIVE|INACTIVE|CHURNED",
  "customerSegment": "string",
  "customerTier": "PLATINUM|GOLD|SILVER|BRONZE",
  "riskRating": "LOW|MEDIUM|HIGH",
  "contacts": [
    {
      "contactId": "UUID",
      "fullName": "string",
      "email": "string",
      "phone": "string",
      "role": "string",
      "isPrimary": true
    }
  ],
  "opportunities": {
    "total": 5,
    "openCount": 2,
    "totalPipelineAmount": 150000.00,
    "currency": "SAR",
    "items": []
  },
  "activities": {
    "total": 42,
    "lastActivityAt": "2026-07-28T10:00:00Z",
    "items": []
  },
  "timeline": {
    "total": 156,
    "items": []
  },
  "intelligence": {
    "scores": {
      "health": { "value": 78.5, "band": "HEALTHY", "calculatedAt": "2026-07-28T00:00:00Z" },
      "lifetimeValue": { "predictedValue": 250000, "tier": "HIGH_VALUE", "confidence": 0.85 },
      "engagement": { "value": 65.0, "band": "MODERATE" },
      "risk": { "value": 22.0, "band": "LOW_RISK" },
      "loyalty": { "value": 80.0, "band": "LOYAL" }
    },
    "nextBestActions": [],
    "segments": []
  },
  "lastScoredAt": "2026-07-28T00:00:00Z",
  "version": 3
}
```

### 4.2 Customer360Page

```json
{
  "content": [Customer360View],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8
}
```

---

## 5. Error Model

| Error Code | HTTP Status | Description |
|-----------|-------------|-------------|
| CUSTOMER_NOT_FOUND | 404 | Account does not exist or not in tenant |
| TENANT_MISMATCH | 403 | Account belongs to different tenant |
| INVALID_SEARCH_CRITERIA | 400 | Malformed query parameters |
| INTELLIGENCE_UNAVAILABLE | 200 | Scores not yet calculated (null intelligence block) |

---

## 6. Pagination

| Parameter | Default | Max | Strategy |
|-----------|---------|-----|----------|
| page | 0 | — | Zero-based offset |
| size | 20 | 100 | Keyset pagination for large datasets |

**Keyset strategy:** For `size > 50`, the port switches to keyset pagination using `(lastActivityAt, accountId)` as the cursor to avoid OFFSET performance degradation.

---

## 7. Filtering

| Filter | Type | Operator | Indexed |
|--------|------|----------|---------|
| query | text | ILIKE | ✅ trigram |
| segmentCodes | array | IN | ✅ |
| scoreBands.health | array | IN | ✅ |
| scoreBands.engagement | array | IN | ✅ |
| scoreBands.risk | array | IN | ✅ |
| lifecycleStatus | array | IN | ✅ |
| customerTier | array | IN | ✅ |

---

## 8. Versioning Strategy

| Aspect | Strategy |
|--------|----------|
| API Version | URL-based (/api/v2/) |
| Contract Version | Semantic versioning (1.0) |
| Response Version | Per-record `version` field (optimistic locking) |
| Backward Compatibility | Additive only — new fields optional, never remove |

---

## 9. Backward Compatibility

| Change Type | Allowed | Migration |
|-------------|---------|-----------|
| Add new field to response | ✅ | No migration needed |
| Remove field from response | ❌ | Deprecation cycle required |
| Change field type | ❌ | New field with v2 suffix |
| Add new filter | ✅ | No migration needed |
| Change default page size | ✅ | Configurable |

---

## 10. Performance Expectations

| Operation | Target | Max |
|-----------|--------|-----|
| findById | 100ms | 500ms |
| search (page 0, size 20) | 200ms | 1s |
| enrichWithIntelligence | 300ms | 1.5s |
| search (keyset, size 100) | 500ms | 2s |

**Performance guarantees:** All queries use indexed lookups. Intelligence enrichment is a secondary read from `crm_customer_scores` (no AI call in read path).

---

## 11. Authorization Requirements

| Endpoint | Capability | Notes |
|----------|------------|-------|
| findById | CRM.CUSTOMER_360.READ | Tenant-scoped |
| search | CRM.CUSTOMER_360.READ | Tenant-scoped |
| enrichWithIntelligence | CRM.CUSTOMER_INTELLIGENCE.READ | Scores/insights visible |

---

**Contract Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ APPROVED
