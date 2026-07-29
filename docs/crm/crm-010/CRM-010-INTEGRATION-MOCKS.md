# CRM-010 External Integration Mock Strategy

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Overview

CRM-010 v1 uses CRM-internal data only. However, the architecture defines **provider-neutral ports** for future external systems. Mock adapters are defined to allow testing the integration layer before real systems exist.

---

## 2. ERP Mock

### Interface
```java
package com.sanad.platform.crm.intelligence.domain;

public interface ErpDataPort {
    ErpCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId);
    List<ErpOrder> findRecentOrders(UUID tenantId, UUID accountId, int limit);

    record ErpCustomerSnapshot(
        UUID accountId, double totalRevenue, int orderCount,
        double outstandingBalance, String paymentStatus,
        String creditStatus, Instant lastOrderAt) {}

    record ErpOrder(
        String orderNumber, double amount, String currency,
        String status, Instant orderDate, List<String> lineItems) {}
}
```

### Mock Implementation
```java
@Component
@Profile({"dev", "test", "local"})
@ConditionalOnProperty(name = "sanad.intelligence.erp.provider", havingValue = "mock", matchIfMissing = true)
public class MockErpDataAdapter implements ErpDataPort {
    // Returns deterministic synthetic data based on accountId hash
}
```

### Example Payload (Success)
```json
{
  "accountId": "UUID",
  "totalRevenue": 250000.00,
  "orderCount": 42,
  "outstandingBalance": 15000.00,
  "paymentStatus": "CURRENT",
  "creditStatus": "GOOD_STANDING",
  "lastOrderAt": "2026-07-15T00:00:00Z"
}
```

### Error Response
```json
{ "status": "UNAVAILABLE", "errorCode": "ERP_NOT_CONFIGURED" }
```

### Upgrade Path
1. Implement `HttpErpDataAdapter` when ERP API is available
2. Set `sanad.intelligence.erp.provider=http`
3. Configure `sanad.intelligence.erp.base-url`
4. Production guard refuses startup if mock is active in prod

---

## 3. HRM Mock

### Interface
```java
public interface HrmDataPort {
    HrmAccountTeamSnapshot loadAccountTeam(UUID tenantId, UUID accountId);
    List<HrmAbsence> findActiveAbsences(UUID tenantId, UUID accountId);

    record HrmAccountTeamSnapshot(
        UUID accountId, String accountManagerName, String accountManagerEmail,
        List<String> teamMembers, int teamSize, String coverageStatus) {}
}
```

### Mock Payload
```json
{
  "accountId": "UUID",
  "accountManagerName": "Ahmed Al-Rashid",
  "accountManagerEmail": "ahmed@sanad.sa",
  "teamMembers": ["Fatima", "Omar", "Layla"],
  "teamSize": 4,
  "coverageStatus": "FULL_COVERAGE"
}
```

### Upgrade Path: `HttpHrmDataAdapter` when HRM module is implemented.

---

## 4. POS Mock

### Interface
```java
public interface PosDataPort {
    PosCustomerSnapshot loadCustomerSnapshot(UUID tenantId, UUID accountId);
    List<PosTransaction> findRecentTransactions(UUID tenantId, UUID accountId, int limit);

    record PosCustomerSnapshot(
        UUID accountId, int transactionCount30d, double avgTransactionValue,
        String preferredStore, double loyaltyPointsBalance) {}
}
```

### Mock Payload
```json
{
  "accountId": "UUID",
  "transactionCount30d": 12,
  "avgTransactionValue": 450.00,
  "preferredStore": "RIYADH_001",
  "loyaltyPointsBalance": 1250.0
}
```

### Upgrade Path: `HttpPosDataAdapter` when POS integration is authorized.

---

## 5. Accounting Mock

### Interface
```java
public interface AccountingDataPort {
    AccountingSnapshot loadSnapshot(UUID tenantId, UUID accountId);

    record AccountingSnapshot(
        UUID accountId, double totalReceivable, double totalPayable,
        int daysSalesOutstanding, String creditRating,
        double revenueYtd, double grossMargin) {}
}
```

### Mock Payload
```json
{
  "accountId": "UUID",
  "totalReceivable": 35000.00,
  "totalPayable": 12000.00,
  "daysSalesOutstanding": 42,
  "creditRating": "A",
  "revenueYtd": 180000.00,
  "grossMargin": 0.35
}
```

### Upgrade Path: `HttpAccountingDataAdapter` when accounting module is available.

---

## 6. Commerce Mock

### Interface
```java
public interface CommerceDataPort {
    CommerceSnapshot loadSnapshot(UUID tenantId, UUID accountId);

    record CommerceSnapshot(
        UUID accountId, int orderCount90d, double avgOrderValue,
        String preferredChannel, double cartAbandonmentRate,
        List<String> productCategories, Instant lastPurchaseAt) {}
}
```

### Mock Payload
```json
{
  "accountId": "UUID",
  "orderCount90d": 8,
  "avgOrderValue": 1200.00,
  "preferredChannel": "WEB",
  "cartAbandonmentRate": 0.25,
  "productCategories": ["ELECTRONICS", "ACCESSORIES"],
  "lastPurchaseAt": "2026-07-20T00:00:00Z"
}
```

### Upgrade Path: `HttpCommerceDataAdapter` when e-commerce platform integrates.

---

## 7. Mock Configuration

```yaml
sanad:
  intelligence:
    erp:
      provider: ${INTELLIGENCE_ERP_PROVIDER:mock}    # mock | http | disabled
      base-url: ${INTELLIGENCE_ERP_BASE_URL:}
    hrm:
      provider: ${INTELLIGENCE_HRM_PROVIDER:mock}
    pos:
      provider: ${INTELLIGENCE_POS_PROVIDER:mock}
    accounting:
      provider: ${INTELLIGENCE_ACCOUNTING_PROVIDER:mock}
    commerce:
      provider: ${INTELLIGENCE_COMMERCE_PROVIDER:mock}
```

---

## 8. Production Guard

```java
@Component
@Profile("prod")
public class IntelligenceProductionGuard implements ApplicationListener<ApplicationReadyEvent> {
    // Refuses startup if any mock adapter is active in production
    // Checks: erp, hrm, pos, accounting, commerce providers != "mock"
}
```

---

## 9. Adapter Strategy Summary

| System | v1 | v2 (future) | Pattern |
|--------|-----|-------------|---------|
| ERP | Mock | HTTP adapter | Port/Adapter |
| HRM | Mock | HTTP adapter | Port/Adapter |
| POS | Mock | HTTP adapter | Port/Adapter |
| Accounting | Mock | HTTP adapter | Port/Adapter |
| Commerce | Mock | HTTP adapter | Port/Adapter |

**Key principle:** All mocks return `UNAVAILABLE` status (not exceptions) when disabled, ensuring the scoring engine degrades gracefully.

---

**Integration Mock Strategy Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED
