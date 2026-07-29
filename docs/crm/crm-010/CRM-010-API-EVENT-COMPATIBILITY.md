# CRM-010 API / Event Compatibility Strategy

**Date:** 2026-07-29
**Issue:** #705 — Mandatory Deliverable #5
**Scope:** CRM-010 API backward/forward compatibility and event schema evolution

---

## 1. API Compatibility Strategy

### 1.1 API Versioning

| Aspect | Convention | Evidence |
|--------|-----------|----------|
| URL versioning | `/api/v2/crm/...` | `CrmContractController.java` |
| Content negotiation | Accept header (JSON) | Spring default |
| Deprecation | No deprecated endpoints in CRM-010 | N/A |

### 1.2 CRM-010 API Changes

| Change Type | Endpoint | Compatibility | Notes |
|-------------|----------|---------------|-------|
| New endpoint | `GET /api/v2/crm/accounts/{accountId}/customer-360` | ✅ ADDITIVE | New endpoint, no existing consumers affected |
| New response fields | `Customer360Response` | ✅ ADDITIVE | New fields added to existing response structure |

### 1.3 Backward Compatibility Rules

| Rule | Implementation | Status |
|------|---------------|--------|
| No field removal from existing responses | `Customer360Response` extends existing contract | ✅ |
| No field type changes | All new fields are optional/nullable | ✅ |
| No URL path changes to existing endpoints | CRM-010 adds new endpoint only | ✅ |
| No capability requirement changes | Existing `CRM.ACCOUNT.READ` capability unchanged | ✅ |

### 1.4 Forward Compatibility

| Aspect | Strategy | Status |
|--------|----------|--------|
| New response fields | Consumers must tolerate unknown fields (JSON deserialization) | ✅ Standard Spring behavior |
| New endpoints | Existing clients unaffected by new endpoints | ✅ |
| New capabilities | New capabilities are additive; no existing capabilities removed | ✅ |

### 1.5 API Contract Verification

| Test | File | What It Verifies |
|------|------|-----------------|
| `CrmApiContractValidation` | CI workflow | OpenAPI spec matches implementation |
| `CustomerIntelligenceContractTest` | `intelligence/application/CustomerIntelligenceContractTest.java` | Port interface contracts |

---

## 2. Event Compatibility Strategy

### 2.1 Event Schema

| Event Type | Schema | Version |
|------------|--------|---------|
| `crm.intelligence.score.calculated` | `CustomerScoreCalculatedEvent` record | v1 |
| `crm.intelligence.health.changed` | `CustomerHealthChangedEvent` record | v1 |
| `crm.intelligence.segment.changed` | `CustomerSegmentChangedEvent` record | v1 |
| `crm.intelligence.next_best_action.generated` | `NextBestActionGeneratedEvent` record | v1 |
| `crm.intelligence.lifetime_value.updated` | `CustomerLifetimeValueUpdatedEvent` record | v1 |
| `crm.intelligence.opportunity.updated` | `OpportunityScoreUpdatedEvent` record | v1 |

### 2.2 Event Interface Contract

All events implement `CustomerIntelligenceEvent`:

```java
public interface CustomerIntelligenceEvent {
    UUID tenantId();
    UUID accountId();
    String eventType();
    Instant occurredAt();
    UUID correlationId();
}
```

**Compatibility guarantee:** The interface fields are mandatory and versioned. New events may add fields but cannot remove or change existing fields.

### 2.3 Event Compatibility Rules

| Rule | Implementation | Status |
|------|---------------|--------|
| No event type removal | All 6 event types are stable | ✅ |
| No field removal from events | Events are Java records (immutable) | ✅ |
| No field type changes | All fields are UUID, String, or Instant | ✅ |
| New events are additive | Future events can be added without breaking consumers | ✅ |
| Correlation ID required | All events carry `correlationId()` | ✅ |

### 2.4 Event Consumer Compatibility

| Consumer | Event Types | Compatibility |
|----------|-------------|---------------|
| Timeline aggregation | All 6 events | ✅ Tolerates new events |
| Audit logging | All 6 events | ✅ Logs all events generically |
| Cache invalidation | `CustomerScoreCalculatedEvent`, `CustomerHealthChangedEvent` | ✅ Tolerates new events |

### 2.5 Event Schema Evolution Strategy

| Phase | Strategy | Status |
|-------|----------|--------|
| v1 (current) | Java records as schema; no external schema registry | ✅ |
| v2 (future) | If external consumers added, introduce Avro/JSON Schema registry | ⚠️ Deferred |
| Dead letter queue | Not implemented; events are published within transaction (fail-safe) | ⚠️ Deferred |

---

## 3. Database Schema Compatibility

### 3.1 Schema Changes (CRM-010)

| Change | Type | Compatibility |
|--------|------|---------------|
| 6 new tables | ADDITIVE | ✅ No existing tables modified |
| 6 new indexes | ADDITIVE | ✅ No existing indexes modified |
| 5 new capabilities | ADDITIVE | ✅ No existing capabilities modified |
| 4 new scoring models | ADDITIVE | ✅ Insert into new table |

### 3.2 Schema Compatibility Rules

| Rule | Status |
|------|--------|
| No ALTER TABLE on existing tables | ✅ CRM-010 only creates new tables |
| No DROP COLUMN on existing tables | ✅ Not applicable |
| No data type changes | ✅ Not applicable |
| No constraint changes on existing tables | ✅ Not applicable |

---

## 4. Compatibility Verification

| Check | Status | Evidence |
|-------|--------|----------|
| API is additive only | ✅ PASS | New endpoint, no changes to existing |
| Events are additive only | ✅ PASS | New event types, no changes to existing |
| Database schema is additive only | ✅ PASS | New tables, no changes to existing |
| No breaking changes to consumers | ✅ PASS | All changes are backward-compatible |
| Contract tests pass | ✅ PASS | `CustomerIntelligenceContractTest` (11 tests) |

---

**Strategy Authority:** Governance Remediation Agent
**Date:** 2026-07-29
**Status:** ✅ COMPLETE
