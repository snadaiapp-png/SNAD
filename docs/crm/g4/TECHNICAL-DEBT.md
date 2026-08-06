# TECHNICAL DEBT

**Date**: 2026-08-06
**HEAD**: ab37bb40

---

## Remaining Technical Debt

| # | Item | Severity | Impact | Priority | Status |
|---|------|----------|--------|----------|--------|
| 1 | Legacy services (LegacyCrmInfrastructureService) still used by v1+v2 controllers | LOW | Maintenance burden | LOW | ACCEPTED |
| 2 | Mock adapters @ConditionalOnProperty with matchIfMissing=true | LOW | Intelligence fallback | LOW | ACCEPTED |
| 3 | SELECT * in JdbcRepository queries | LOW | Minor performance | LOW | ACCEPTED |

---

## Detail

### 1. Legacy Services

**Files**:
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/LegacyCrmInfrastructureService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/LegacyOpportunityService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/LegacyLeadService.java`

**Used by**:
- `CrmController.java` (v1)
- `CrmContractController.java` (v2 read)
- `CrmContractControllerR1.java` (v2 mutation)
- `CrmService.java` (facade)

**Root Cause**: Legacy services provide working implementations. New domain/application layers exist but legacy is still wired.

**Migration Path**: Gradually replace Legacy*Service calls with domain UseCases. Not blocking.

### 2. Mock Adapters

**Files**:
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/infrastructure/MockCommerceDataAdapter.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/infrastructure/MockHrmDataAdapter.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/infrastructure/MockAccountingDataAdapter.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/infrastructure/MockErpDataAdapter.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/intelligence/infrastructure/MockPosDataAdapter.java`

**Root Cause**: Intelligence module uses `@ConditionalOnProperty(name="sanad.intelligence.*.provider", havingValue="mock", matchIfMissing=true)`.

**Impact**: Mock adapters active in production unless overridden. Intelligence features return synthetic data.

**Migration Path**: Configure real providers via environment variables. Not blocking.

### 3. SELECT * in Queries

**Files**: Various `Jdbc*Repository.java` files

**Root Cause**: Simplicity for small CRM tables.

**Impact**: Minor performance overhead on large tables.

**Migration Path**: Optimize queries when tables grow. Not blocking.

---

## Acceptance

All 3 items are **accepted** as LOW severity, LOW priority technical debt. None are blocking, none affect correctness, none affect security.
