# CRM-010 Security Review

**Reviewer:** Agent 3 (CRM-010-AGENT-003)
**Date:** 2026-07-29

---

## 1. SQL Injection

| Check | Status | Evidence |
|-------|--------|----------|
| All queries use NamedParameterJdbcTemplate | ✅ | 20 queries across 4 adapters, all use `:param` syntax |
| No string concatenation in SQL | ✅ | StringBuilder only appends static fragments with named params |
| Score types hardcoded | ✅ | "HEALTH", "CLV", "ENGAGEMENT", "RISK", "LOYALTY" |

**Result: PASS** — No SQL injection risk.

---

## 2. Tenant Isolation

| Adapter | Queries | All Include tenant_id | Status |
|---------|---------|----------------------|--------|
| JdbcScoringAdapter | 6 | 6/6 | ✅ |
| JdbcSegmentAdapter | 5 | 5/5 | ✅ |
| JdbcNextBestActionAdapter | 3 | 3/3 | ✅ |
| JdbcCustomerIntelligenceQueryAdapter | 6 | 6/6 | ✅ |

**Cache tenant isolation:**
- Keys: `scores:v1:{tenantId}:{accountId}`, `view:v1:{tenantId}:{accountId}`
- Unit test verifies isolation: `CustomerIntelligenceCacheTest.java:79-83`

**Validator tenant check:**
- `CustomerIntelligenceValidator.validateCustomer()` calls `accountRepository.findById(tenantId, accountId)`
- Ensures account belongs to caller's tenant

**Result: PASS** — Complete tenant isolation across all layers.

---

## 3. Authentication & Authorization

| Check | Status | Evidence |
|-------|--------|----------|
| REST controllers use @RequireCapability | ✅ | `CrmContractController` enforces on all endpoints |
| Tenant extraction from JWT | ✅ | `tenantId()` helper extracts from authenticated user |
| Application services validate tenant | ✅ | `CustomerIntelligenceValidator.validateCustomer()` |
| No @RequireCapability on services | ⚠️ | Acceptable for internal service calls, insufficient for direct HTTP |

**Observation:** If intelligence services are ever exposed via new REST endpoints, `@RequireCapability` annotations must be added to the controllers.

**Result: PASS** — Security enforced at controller layer with service-layer defense-in-depth.

---

## 4. Sensitive Data Logging

| File | Log Statement | Level | Data Logged | Assessment |
|------|---------------|-------|-------------|------------|
| `CustomerScoringService.java:142` | `log.info("Refreshing all scores for account {}", accountId)` | INFO | UUID only | ✅ Safe |
| `AiScoreOrchestrator.java:81` | `log.warn("AI result below confidence threshold: {} < {} for account {}", ...)` | WARN | Confidence + UUID | ✅ Safe |
| `AiScoreOrchestrator.java:88` | `log.error("AI Gateway request failed for account {}: {}", accountId, e.getMessage())` | ERROR | UUID + error message | ✅ Safe |

**No PII logged.** No passwords, tokens, API keys, or credentials found in any source file.

**Result: PASS** — No sensitive data in logs.

---

## 5. Input Validation

| Validation | Method | Called By | Status |
|------------|--------|-----------|--------|
| Account existence + tenant ownership | `validateCustomer()` | 8 write methods | ✅ Active |
| Score type whitelist | `validateScoreType()` | None | ⚠️ Dead code |
| Confidence threshold | `validateConfidence()` | None | ⚠️ Dead code |

**Observation:** `validateScoreType()` and `validateConfidence()` are defined but never called. Since score types are hardcoded constants in each service, there is no active vulnerability. These methods appear prepared for future use.

**Result: PASS** — Core validation active. Dead code methods are harmless.

---

## 6. Secrets & Configuration

| Secret | Base | Dev | Local | Prod |
|--------|------|-----|-------|------|
| JWT_SECRET | `${JWT_SECRET:}` | `${JWT_SECRET:}` | `${JWT_SECRET:}` (ephemeral if empty) | `${JWT_SECRET:}` |
| DB Password | `${SPRING_DATASOURCE_PASSWORD:}` | `${SPRING_DATASOURCE_PASSWORD:}` | `${SPRING_DATASOURCE_PASSWORD:}` | `${SPRING_DATASOURCE_PASSWORD:}` |
| Encryption Key | `${CRM_CUSTOM_FIELD_ENCRYPTION_KEY:}` | `${CRM_CUSTOM_FIELD_ENCRYPTION_KEY:}` | Test-only default | `${CRM_CUSTOM_FIELD_ENCRYPTION_KEY:}` |

**No hardcoded secrets** in any Java source file. All externalized via environment variables.

**Result: PASS** — Secrets properly externalized.

---

## 7. Summary

| Category | Status | Confidence |
|----------|--------|------------|
| SQL Injection | PASS | 95 |
| Tenant Isolation | PASS | 95 |
| Authentication/Authorization | PASS | 85 |
| Sensitive Data Logging | PASS | 90 |
| Input Validation | PASS | 85 |
| Secrets/Configuration | PASS | 95 |

**Overall: PASS** — No security issues found. Strong security hygiene throughout.
