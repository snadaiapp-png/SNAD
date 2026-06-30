# SNAD API Security Review

## Mass Assignment Prevention
- All controllers use explicit DTOs (not entities)
- No `@ModelAttribute` binding to entities
- Server-managed fields (tenantId, createdAt, etc.) not in request DTOs

## Tenant Field Injection
- Tenant ID extracted from JWT, never from request body
- JWT filter validates tenant binding
- Repository queries always filter by tenantId

## Sort Field Safety
- Sort fields validated against allowlist per resource
- No dynamic SQL from sort parameters
- JPA/Hibernate parameterized queries used throughout

## Error Data Leakage
- GlobalExceptionHandler strips internal details
- No stack traces in responses
- No SQL/database names in responses
- No Java class names in responses
- Request ID included for support correlation

## OpenAPI Security
- Bearer JWT documented as security scheme
- Password fields marked write-only
- No token fields in response schemas
- Internal endpoints excluded from public docs
