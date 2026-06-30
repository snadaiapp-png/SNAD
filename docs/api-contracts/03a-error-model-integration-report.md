# Stage 03A — Error Model Integration Report

**Stage:** 03A
**Branch:** `infra/03a-api-contract-enforcement`

## 1. Objective

Unify the API error model: a single `ApiErrorResponse` record, `application/problem+json` content type on all error responses, no raw `ex.getMessage()` leakage, and a catch-all `@ExceptionHandler(Exception.class)` for unexpected exceptions.

## 2. Unified Error Model

### Single canonical record

`com.sanad.platform.shared.api.ApiErrorResponse` (record) — the ONLY error response shape:

```java
public record ApiErrorResponse(
    String type,        // RFC 9457 "type" — URI to error doc
    String title,       // Static title from ErrorCode enum
    int status,         // HTTP status code
    String detail,      // Safe static detail (NEVER ex.getMessage())
    String instance,    // Request path
    String code,        // SANAD-XXX-NNN
    String requestId,   // Correlates with X-Request-Id header
    Instant timestamp,
    List<FieldValidationError> errors  // Field-level validation errors
) {}
```

### Legacy POJO deleted

`com.sanad.platform.organization.api.ApiErrorResponse` (POJO with `timestamp/status/error/message/path/tenantIds`) was DELETED. All consumers migrated:

- `OrganizationApiExceptionHandler`
- `OrganizationController` (OpenAPI `@ApiResponse` schema refs)
- `UserApiExceptionHandler`
- `UserController` (schema refs)
- `AuthApiExceptionHandler`
- `AccessApiExceptionHandler`
- `OrganizationMembershipApiExceptionHandler`
- `MembershipAssignmentApiExceptionHandler`
- `OrganizationMembershipController` (schema refs)

## 3. Typed Exception Hierarchy

New package `com.sanad.platform.shared.api.exceptions`:

| Class | ErrorCode | HTTP | Use case |
|-------|-----------|------|----------|
| `TypedBusinessException` (abstract base) | — | — | Base for all typed business exceptions |
| `ResourceNotFoundException` | `SANAD-RES-001` | 404 | Resource not visible/exists |
| `ConflictException` | `SANAD-CON-001` | 409 | Uniqueness/state conflict |
| `BusinessRuleException` | `SANAD-BIZ-001` | 422 | Business rule violation |
| `TenantContextException` | `SANAD-TEN-001` | 403 | Missing tenant context |
| `InvalidPaginationException` | `SANAD-PAG-001/002` | 400 | Invalid page/sort params |

Each typed exception carries:
- `ErrorCode` (stable code, title, HTTP status)
- `safeClientDetail` (static, message-catalog style — NEVER raw ex.getMessage())
- `diagnostics` (Map<String, String> — logged server-side only, never sent to client)

## 4. Global Exception Handler

`com.sanad.platform.shared.api.GlobalExceptionHandler` (`@RestControllerAdvice`) handles:

| Exception | ErrorCode | HTTP |
|-----------|-----------|------|
| `MethodArgumentNotValidException` | `SANAD-VAL-001` | 400 (with field errors) |
| `ConstraintViolationException` | `SANAD-VAL-001` | 400 (with field errors) |
| `HttpMessageNotReadableException` | `SANAD-VAL-002` | 400 |
| `MissingServletRequestParameterException` | `SANAD-VAL-003` | 400 |
| `MethodArgumentTypeMismatchException` | `SANAD-VAL-004` | 400 |
| `AuthenticationException` | `SANAD-AUTH-001` | 401 |
| `AccessDeniedException` | `SANAD-SEC-001` | 403 |
| `EntityNotFoundException` | `SANAD-RES-001` | 404 |
| `DataIntegrityViolationException` | `SANAD-CON-001` | 409 |
| `ResponseStatusException` | (varies) | (varies) |
| `NoHandlerFoundException` / `NoResourceFoundException` | `SANAD-RES-001` | 404 |
| `BeanInstantiationException` | (unwraps cause) | (varies) |
| `BindException` | `SANAD-VAL-001` | 400 (with field errors) |
| `IllegalArgumentException` | `SANAD-VAL-001` | 400 |
| `IllegalStateException` | `SANAD-BIZ-001` | 422 |
| `TypedBusinessException` (+ subclasses) | (varies) | (varies) |
| `Exception` (catch-all) | `SANAD-GEN-001` | 500 |

### Catch-all behavior

`@ExceptionHandler(Exception.class)`:
1. Logs the full stack trace server-side with `requestId` for correlation.
2. Returns `SANAD-GEN-001` / 500 / `"An unexpected error occurred. Please retry, and contact support with the request ID if the issue persists."`.
3. NEVER returns `ex.getMessage()`, `ex.getClass().getName()`, or any internal diagnostic to the client.

## 5. Content-Type

All error responses use `application/problem+json` (RFC 9457). All controller `@ApiResponse` annotations for 4xx/5xx were updated from `application/json` to `application/problem+json`.

## 6. Request ID Propagation

`RequestIdFilter` (highest precedence):
1. Reads `X-Request-Id` header from the request.
2. If absent/blank/over 128 chars, generates a new UUID.
3. Places in MDC (`requestId` key) for structured logging.
4. Sets `X-Request-Id` response header.

The `X-Request-Id` response header ALWAYS matches the `requestId` field in the error body (proven by `ErrorModelIntegrationTest`).

## 7. Actuator Exclusion

`GlobalExceptionHandlerActuatorExclusionTest` (5 tests) verifies:
- `/actuator/health` returns 200 (not intercepted)
- `/actuator/info` returns 200 (not intercepted)
- `/actuator/configprops` (not exposed) returns 404, not 500
- `/actuator/heapdump` (not exposed) returns 404, not 500
- `/actuator/threaddump` (not exposed) returns 404, not 500

## 8. Error Model Integration Tests

`ErrorModelIntegrationTest` (12 test cases) verifies:
- 400 — Validation failure
- 400 — Malformed JSON (raw body NOT echoed)
- 400 — Missing parameter
- 400 — Invalid parameter type
- 401 — Unauthenticated (skipped — exercised by TenantBindingSecurityIntegrationTest)
- 403 — Access denied (skipped — exercised by production security filter)
- 404 — Resource not found
- 409 — Conflict (ErrorCode mapping verified)
- 422 — Business validation (skipped — exercised by ApiRegressionSuiteTest)
- 429 — Rate limited (skipped — exercised by AuthApiIntegrationTest)
- 500 — Unexpected exception (ErrorCode mapping verified)
- All error responses use `application/problem+json`
- All error bodies have `code`, `requestId`, `timestamp`, `status`, `title`, `detail`, `instance`
- No error body contains `stackTrace`, `className`, `sql`, `secret`, or `password` fields
- `X-Request-Id` header matches body `requestId`

## 9. Domain Exception Handlers Refactored

All 6 domain-specific `@RestControllerAdvice` classes were refactored to:
- Use the shared `ApiErrorResponse` record (not the deleted POJO)
- Set `Content-Type: application/problem+json`
- Use static safe details (never `ex.getMessage()`)
- Log diagnostic context (tenantId, orgId, email, etc.) server-side only

## 10. Related Debts

- CD-03-P1-004 (multiple incompatible ApiErrorResponse models) — CLOSED
- CD-03-P1-005 (exception responses can expose raw messages) — CLOSED
