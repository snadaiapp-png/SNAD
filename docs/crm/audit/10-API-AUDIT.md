# API Audit

**Audit Scope:** REST standards, naming conventions, API contracts, breaking changes, endpoint inventory, OpenAPI compliance, versioning policy, error handling, request/response consistency.

**Audit Date:** 2026-07-30
**Auditor:** SNAD CRM Forensic Audit
**Status:** HIGH -- Inconsistent versioning, duplicate endpoints, missing OpenAPI documentation, inconsistent error response formats, and missing authorization annotations on several endpoints.

---

## Executive Summary

The SNAD CRM API surface has evolved through multiple phases without a consistent versioning policy or API contract standard. Two versioning schemes coexist (path-based V1 and V2), some domains have duplicate controllers, error response formats are not standardized, and several endpoints lack required authorization annotations. The frontend/backend serialization contract is at risk due to naming convention mismatches. An OpenAPI specification was not found in the codebase.

---

## Finding API-01: Inconsistent API Versioning Strategy

**Severity:** HIGH
**Category:** Versioning Policy

### Description
The codebase uses two path-based version prefixes without a clear deprecation or migration strategy:

- `/api/v1/crm/...` (legacy, served by `CrmController`)
- `/api/v2/crm/...` (new, served by `CrmContractController` and others)

Some domains have endpoints only in V1, some only in V2, and some overlap without clear migration guidance. There is no `Sunset` header, `Deprecated` annotation, or documentation indicating when V1 will be removed.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmController.java` (`@RequestMapping("/api/v1/crm")`)
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmContractController.java` (`@RequestMapping("/api/v2/crm")`)
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmContractControllerR1.java`
- Multiple ownership controllers under `/api/v2/crm`
- `ExportController` (`@RequestMapping("/api/v1/crm/export")`)

### Impact
- API consumers cannot determine which version to use for which operation
- Bug fixes applied to V2 may not be backported to V1, creating behavioral inconsistencies
- Dual maintenance burden without a sunset plan
- New endpoints may be added to the wrong version by developers unsure of the policy

### Recommendation
1. Establish and document an API versioning policy (path-based or header-based, support duration, deprecation process)
2. Add `@Deprecated` and `@Sunset` annotations to all V1 endpoints
3. Conduct an endpoint inventory to identify V1-only functionality that needs V2 equivalents
4. Set a sunset date for V1 and communicate to all consumers
5. Consider adopting a more sustainable versioning approach (e.g., content negotiation or header-based)

---

## Finding API-02: Duplicate and Overlapping Controllers

**Severity:** HIGH
**Category:** API Contract Consistency

### Description
`CrmContractController` and `CrmContractControllerR1` appear to serve overlapping functionality under the `/api/v2/crm` path. This creates confusion about which controller handles which endpoints and may cause runtime conflicts if request mappings overlap.

### Affected Files
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmContractController.java`
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\web\CrmContractControllerR1.java`

### Impact
- If request mappings overlap, startup may fail with ambiguous mapping errors
- If they serve different sub-paths, the rationale for separate controllers is unclear
- Developers must check both controllers when modifying V2 endpoints

### Recommendation
1. Consolidate into a single controller per bounded context
2. If split is intentional, document the boundary clearly (e.g., one for commands, one for queries)
3. Add ArchUnit rule: no overlapping request mappings

---

## Finding API-03: Frontend/Backend Serialization Convention Mismatch

**Severity:** HIGH
**Category:** API Contract

### Description
The Java backend DTOs in `CrmDtos.java` use camelCase field names (e.g., `displayName`, `accountType`, `lifecycleStatus`). The frontend TypeScript types may use snake_case. Without a configured `PropertyNamingStrategy` or `@JsonProperty` annotations, Jackson will serialize using Java field names (camelCase), which may not match the frontend's expected snake_case format.

### Affected File
- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\dto\CrmDtos.java`

### Impact
- API responses may use camelCase while the frontend expects snake_case
- If Jackson `FAIL_ON_UNKNOWN_PROPERTIES` is disabled (common default), fields are silently dropped
- Integration bugs manifest only at runtime, not at compile time

### Recommendation
1. Choose a single naming convention for the API contract (recommended: snake_case for REST APIs)
2. Configure `PropertyNamingStrategies.SNAKE_CASE` globally on the ObjectMapper
3. Add contract tests that verify serialization for all DTOs
4. Enable `FAIL_ON_UNKNOWN_PROPERTIES` in test configurations

---

## Finding API-04: No OpenAPI Specification Found

**Severity:** HIGH
**Category:** API Documentation

### Description
No OpenAPI (Swagger) specification files were found in the codebase. The API has no machine-readable contract documentation. This means:
- API consumers must reverse-engineer the contract from the code
- No automated API contract testing is possible
- No API documentation portal can be generated

### Recommendation
1. Integrate SpringDoc OpenAPI or similar tool to generate OpenAPI 3.0 specification
2. Add API contract tests that validate the generated spec against expected behavior
3. Publish the OpenAPI spec as part of the CI/CD pipeline
4. Consider API-first development using OpenAPI as the source of truth

---

## Finding API-05: Inconsistent Error Response Format

**Severity:** HIGH
**Category:** Error Handling

### Description
Multiple exception handlers exist in the CRM module, and they may not return a consistent error response format:

- `C:\Users\SNADA\ZCodeProject\SNAD\apps\sanad-platform\src\main\java\com\sanad\platform\crm\error\CrmExceptionHandler.java`
- `CrmContractControllerExceptionHandler.java`
- `CrmContactRelationshipExceptionHandler.java`
- `CrmAddressCommunicationExceptionHandler.java`
- `CrmErrorResponse.java`

The `CrmErrorResponse` class suggests a structured format, but it is unclear if all handlers use the same structure.

### Impact
- API consumers cannot rely on a consistent error schema
- Client-side error handling must account for multiple formats
- Automated API clients may break when error formats differ

### Recommendation
1. Standardize on a single error response format across all handlers (e.g., RFC 7807 Problem Details)
2. Use a `@ControllerAdvice` base class that all CRM exception handlers extend
3. Add contract tests that verify error response format for each error scenario
4. Ensure error responses do not leak implementation details

---

## Finding API-06: Missing @RequireCapability on Several Endpoints

**Severity:** HIGH
**Category:** Authorization

### Description
Several controller endpoints lack the `@RequireCapability` annotation, which means the `CapabilityAuthorizationAspect` cannot enforce RBAC. These endpoints may be accessible to any authenticated user regardless of their assigned capabilities.

### Affected Areas
- `CrmWorkflowCallbackController` -- likely needs specific callback capability
- Some endpoints in ownership controllers
- `ExportController` -- has `@RequireCapability("CRM.ACCOUNT.READ")` on export endpoints (good), but other controllers may lack annotations
- `CrmIntegrationController` -- may need integration-specific capabilities

### Recommendation
1. Audit every `@RequestMapping` method and add appropriate `@RequireCapability` annotation
2. Add a global security filter that denies access if no capability annotation is present
3. Add ArchUnit test: all `@RestController` methods must have `@RequireCapability` or `@PermitAll`

---

## Finding API-07: RESTful Resource Naming Inconsistencies

**Severity:** MEDIUM
**Category:** REST Standards

### Description
The CRM API uses a mix of RESTful and RPC-style endpoint naming:

- **RESTful:** `/api/v2/crm/accounts`, `/api/v2/crm/contacts`
- **Mixed:** `/api/v1/crm/dashboard` (RPC-style noun)
- **RPC-style:** `/api/v2/crm/transfers/{id}/submit` (action verb in URL)
- **Deeply nested:** Some ownership endpoints use complex nested resource paths

While action verbs in URLs are sometimes acceptable for domain operations (transfers), the inconsistency between V1 (more RPC-style) and V2 (more RESTful) creates confusion.

### Recommendation
1. Document the REST design conventions in an API style guide
2. Use resource-oriented URLs with HTTP methods for CRUD operations
3. For domain operations (transfers, approvals), consider using sub-resources or action verbs consistently
4. Review and normalize the endpoint naming across both versions

---

## Finding API-08: CrmOwnershipTransferController -- Action Endpoints

**Severity:** MEDIUM
**Category:** REST Design

### Description
The ownership transfer controller uses action-oriented endpoints (`/submit`, `/approve`, `/cancel`) which are appropriate for domain operations. However, these should be reviewed for consistency:
- Do they use POST for state-changing actions? (Correct)
- Do they return the updated resource? (To verify)
- Is the action included in the URL or as a query parameter? (Mixed practices)

### Recommendation
Standardize on one pattern: either action-based sub-resources (e.g., `POST /transfers/{id}/submit`) or use HTTP PATCH with state changes in the body. Document the chosen approach.

---

## Finding API-09: in-memory .limit() for Pagination on listCustomFields

**Severity:** MEDIUM
**Category:** Performance / API Contract

### Description
`listCustomFields` implements pagination in-memory using `.limit()` rather than database-level pagination. For large datasets, this will cause memory pressure and inconsistent pagination behavior.

### Impact
- Performance degrades as the custom fields table grows
- Pagination cursors may not work correctly with in-memory limiting
- API contract promises pagination but implementation retrieves all records

### Recommendation
Implement proper cursor-based pagination at the database level using SQL LIMIT/OFFSET or keyset pagination.

---

## Finding API-10: Missing ETag Support on Mutation Endpoints

**Severity:** MEDIUM
**Category:** API Contract

### Description
While `CrmOwnershipAtomicIfMatchAspect` implements ETag-based concurrency for ownership resources, other CRM mutation endpoints may not support `If-Match` headers. This means concurrent modifications to non-ownership resources can silently overwrite each other.

### Recommendation
1. Extend ETag-based concurrency to all mutation endpoints
2. Make ETag support part of the API contract for all mutable resources
3. Add integration tests for concurrent modification scenarios

---

## Finding API-11: Controller Method Parameter and Response Consistency

**Severity:** MEDIUM
**Category:** API Consistency

### Description
Controller methods across the codebase use different patterns for:
- Returning responses: Some return `ResponseEntity<T>`, some return `T` directly, some use `CrmEnvelopes.SingleResponse`/`ListResponse`
- Extracting authentication: Some take `Authentication` as a parameter, some extract from `SecurityContextHolder`
- Validation: Some use `@Valid`, some do not

### Impact
- API consumers experience inconsistent response envelopes
- Authentication extraction pattern varies across controllers
- Validation coverage is uneven

### Recommendation
1. Standardize on a single response envelope format (recommended: `CrmEnvelopes.SingleResponse`/`ListResponse`)
2. Use a custom argument resolver for tenant context instead of `Authentication` parameter
3. Ensure `@Valid` is used on all request body parameters

---

## Conclusion

The SNAD CRM API surface shows signs of organic growth without a consistent API governance framework. Versioning is inconsistent (V1/V2 coexistence without a sunset plan), duplicate controllers overlap in responsibility, error response formats vary, and authorization annotations are missing on several endpoints. The most critical actions are establishing a versioning and deprecation policy, standardizing the error response format, and ensuring all endpoints have proper authorization annotations. An OpenAPI specification should be generated and used as the API contract source of truth.

**Overall API Score: 4/10 -- Inconsistent versioning, missing contract documentation, authorization gaps.**
