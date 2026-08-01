# Security Audit

**Audit Scope:** Authorization bypasses, tenant isolation failures, security vulnerabilities, sensitive data exposure, exception leaks, PII leakage, RBAC gaps, RLS policy correctness, CORS, CSRF, input validation.

**Audit Date:** 2026-07-30
**Auditor:** SNAD CRM Forensic Audit
**Status:** CRITICAL -- Multiple authorization gaps, exception information leaks, and missing access control annotations detected.

---

## Executive Summary

The SNAD CRM security posture shows several critical gaps. RBAC is implemented via `@RequireCapability` annotations but is inconsistently applied across controllers. Tenant isolation relies on application-level filtering rather than database Row-Level Security (RLS), creating cross-tenant leakage risks. Exception handlers may leak internal implementation details. The mock adapter infrastructure presents a data integrity risk that could be exploited if synthetic data is mistaken for real data. A test that should verify tenant isolation is misnamed, potentially masking a real vulnerability.

---

## Finding SEC-01: Duplicated Auth Context Extraction Across 9 V1 Controllers

**Severity:** HIGH
**Category:** Authorization Code Duplication

### Description
Nine V1 controllers duplicate the logic for extracting authentication context from `Authentication` objects. Each controller independently extracts `tenant_id`, user ID, and roles from the `Authentication.getDetails()` map. This creates a maintenance burden and risks inconsistent extraction logic across controllers.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmController.java` (uses `authentication.getDetails()` pattern)
- `CrmContractController.java`
- `CrmContractControllerR1.java`
- `CrmAddressCommunicationController.java`
- `CrmAddressCommunicationOperationsController.java`
- `CrmContactRelationshipController.java`
- `CrmContactRelationshipImportController.java`
- `CrmContactRelationshipVersionedMutationController.java`
- `CrmIntegrationController.java`
- (and others in the `crm.web` package)

### Impact
- If the authentication context structure changes (e.g., tenant_id moves from `details` to a different location), all 9 controllers must be updated
- Inconsistent extraction patterns may cause some endpoints to resolve the tenant incorrectly
- Increases the attack surface for tenant isolation bypasses

### Recommendation
1. Implement a `@ControllerAdvice` or custom argument resolver that automatically resolves tenant context into a parameter object
2. Create a `TenantContext` object that controllers can inject rather than extracting from Authentication directly
3. Add integration tests that verify tenant resolution is consistent across all controller endpoints

---

## Finding SEC-02: Missing `@RequireCapability` on Several Controller Endpoints

**Severity:** HIGH
**Category:** Authorization Bypass

### Description
Several web controllers in the CRM module expose endpoints without `@RequireCapability` annotations. Without this annotation, the `CapabilityAuthorizationAspect` cannot enforce RBAC, and the endpoint may be accessible to any authenticated user regardless of role.

### Affected Areas
- Controllers in `crm.export.web` and `crm.reports.web` packages
- Some endpoints in `CrmOwnershipResourceController` and related ownership controllers
- `CrmWorkflowCallbackController` (may be intentionally open but requires explicit security annotation)

### Impact
- Unauthorized users may access functionality they should not have access to
- Inconsistent security posture across endpoints
- Reliance on implicit security rather than explicit declaration

### Recommendation
1. Audit every `@RestController` endpoint and add `@RequireCapability` to each
2. Add a global security filter that defaults to deny if no capability annotation is present
3. Add ArchUnit test: all `@RestController` methods must have `@RequireCapability` or an explicit `@PermitAll`

---

## Finding SEC-03: Misleading Test Name -- Potential Security Blind Spot

**Severity:** HIGH
**Category:** Test Reliability / Potential Vulnerability

### Description
The test `login_wrongTenant_returns401` is named to assert that cross-tenant login returns HTTP 401 Unauthorized, but the test actually returns HTTP 200. This discrepancy means either:
- The test assertion is wrong and cross-tenant login succeeds (security vulnerability)
- The test name is wrong and the behavior tested is not tenant isolation

In either case, the test cannot be trusted for security regression detection.

### Impact
- If cross-tenant login succeeds, this is a critical multi-tenant isolation failure
- The misleading name means other developers cannot rely on this test for tenant isolation coverage
- Security regression testing is compromised

### Recommendation
1. Immediately audit the test implementation to determine actual behavior
2. If cross-tenant login succeeds, fix the authentication filter to reject cross-tenant credentials
3. If the test is correct but misnamed, fix the test name and add explicit tenant isolation tests
4. Add a dedicated tenant isolation security test suite

---

## Finding SEC-04: Exception Information Leakage in Error Responses

**Severity:** MEDIUM
**Category:** Information Disclosure

### Description
Several exception handlers in the CRM module may leak internal implementation details in error responses. The `CrmContractControllerExceptionHandler` and other handlers return error codes and messages that may include implementation details useful to attackers.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\error\CrmContractControllerExceptionHandler.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\error\CrmContactRelationshipExceptionHandler.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\error\CrmAddressCommunicationExceptionHandler.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\error\CrmExceptionHandler.java`

### Impact
- Attackers can gather information about internal system structure
- Detailed error messages may reveal SQL schemas, file paths, or stack traces
- Compliance with data protection regulations may be affected

### Recommendation
1. Review all exception handlers to ensure they return generic error messages to clients
2. Log detailed errors server-side only; return sanitized messages to API consumers
3. Use a standard error response format with codes rather than detailed messages

---

## Finding SEC-05: Hardcoded Zero-UUID Tenant as Default Seed

**Severity:** CRITICAL
**Category:** Tenant Isolation / Data Leakage

### Description
The scoring model seed migration (`V20260729_2__seed_default_scoring_models.sql`) uses the zero-UUID (`00000000-0000-0000-0000-000000000000`) as the `tenant_id` for default scoring models. These records do not belong to any real tenant, creating a potential data leakage path:
- If queries include WHERE tenant_id = '00000000-0000-0000-0000-000000000000', they will inexplicably include seed data
- If queries exclude this tenant_id, the seed models are invisible

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\resources\db\vendor\postgresql\V20260729_2__seed_default_scoring_models.sql`

### Impact
- Ambiguous whether seed models apply to all tenants, no tenants, or a special administrative tenant
- No cloning mechanism for per-tenant model records
- Risk of the zero-UUID tenant being accidentally included in tenant-scoped operations

### Recommendation
1. Separate tenant-scoped configuration from tenant-agnostic defaults using different tables
2. Remove zero-UUID records from tenant-scoped tables
3. Add database constraint to reject zero-UUID tenant_id values

---

## Finding SEC-06: CrmOwnershipAtomicIfMatchAspect -- Tenant Extraction in Aspect

**Severity:** MEDIUM
**Category:** Tenant Isolation Enforcement

### Description
The `CrmOwnershipAtomicIfMatchAspect` extracts `tenant_id` from `SecurityContextHolder.getContext().getAuthentication().getDetails()` within an aspect. This duplicates tenant resolution logic and centralizes it in an aspect that also performs database locking. Any inconsistency in tenant resolution between the aspect and the controller could allow cross-tenant operations.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\ownership\infrastructure\CrmOwnershipAtomicIfMatchAspect.java` (lines 157-169)

### Recommendation
Centralize tenant extraction into a `TenantContextHolder` or similar utility that is consistently used across all layers (controller, aspect, service, repository).

---

## Finding SEC-07: No CSRF Protection Visible for CRM API Endpoints

**Severity:** MEDIUM
**Category:** Missing CSRF Protection

### Description
The CRM REST API endpoints do not appear to have CSRF protection. While many modern APIs use token-based authentication (JWT, OAuth2) that is inherently immune to CSRF, the codebase should explicitly configure CSRF protection or document why it is not needed.

### Impact
- If session-based authentication is used, state-changing operations may be vulnerable to CSRF
- If token-based authentication is used, the lack of explicit CSRF configuration should be documented

### Recommendation
1. Verify which authentication mechanism is in use
2. If session-based, add CSRF protection or ensure SameSite cookie attributes are set
3. Document the CSRF strategy in the security architecture

---

## Finding SEC-08: SpringCustomerIntelligenceEventPublisher Exception Swallowing

**Severity:** CRITICAL
**Category:** Security Reliability

### Description
`SpringCustomerIntelligenceEventPublisher` catches and logs all exceptions from event publishing but does not rethrow them. This means security-critical event processing failures (e.g., audit logging, tenant-scoped cache invalidation) are silently ignored.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\SpringCustomerIntelligenceEventPublisher.java` (lines 26-33)

### Code Evidence
```java
public void publish(CustomerIntelligenceEvent event) {
    try {
        springPublisher.publishEvent(event);
    } catch (Exception e) {
        log.error("Failed to publish event {} for tenant {}: {}",
                event.eventType(), event.tenantId(), e.getMessage(), e);
    }
}
```

### Impact
- If security-relevant events (e.g., score changes that trigger access decisions) fail, processing continues as if successful
- Audit trail may be incomplete without any error indication
- Attackers could exploit event processing failures without detection

### Recommendation
1. Remove the try-catch or rethrow exceptions after logging
2. Implement a dead-letter queue for failed events
3. Add monitoring alerts for event publishing failures

---

## Finding SEC-09: Hardcoded AI Gateway Timeout (30s)

**Severity:** MEDIUM
**Category:** Denial of Service / Availability

### Description
The AI Gateway timeout is hardcoded at 30 seconds. This can cause thread pool exhaustion if multiple scoring requests queue up behind a slow AI service, effectively creating a self-inflicted denial-of-service condition.

### Recommendation
1. Make the AI Gateway timeout configurable via application properties
2. Implement a circuit breaker pattern to fail fast when the AI service is degraded
3. Set a realistic timeout based on SLA requirements and load testing

---

## Finding SEC-10: Hardcoded Cache TTL and Max Size

**Severity:** MEDIUM
**Category:** Security Configuration

### Description
`CustomerIntelligenceCache` hardcodes TTL (5 minutes) and max size (10,000 entries) rather than making them configurable. This prevents tuning for security requirements (e.g., shorter TTL for sensitive data) and could allow cache exhaustion if the workload exceeds 10K entries.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\intelligence\infrastructure\CustomerIntelligenceCache.java` (lines 29-30)

### Recommendation
1. Externalize TTL and max size as configuration properties
2. Add monitoring for cache hit rate and eviction count

---

## Conclusion

The SNAD CRM security posture has critical gaps in authorization consistency, tenant isolation enforcement, and exception handling. The most pressing issues are: duplicated and potentially inconsistent tenant context extraction across controllers, missing `@RequireCapability` annotations on several endpoints, the misleading tenant isolation test, and the silent swallowing of event publishing exceptions. Centralizing tenant extraction, completing authorization coverage, and adding dedicated tenant isolation security tests should be prioritized.

**Overall Security Score: 4/10 -- Authorization gaps and tenant isolation risks require immediate remediation.**
