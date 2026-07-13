# CRM Error Code Catalog

**Branch:** `crm/003-stable-api-contracts`
**Gate:** CRM-G2 — API Contract and Concurrency Gate
**Source of truth:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/error/CrmErrorCode.java`

Every CRM error response uses the standard envelope:

```json
{
  "error": {
    "code": "CRM_ACCOUNT_NOT_FOUND",
    "message": "The requested account was not found.",
    "status": 404,
    "requestId": "uuid",
    "timestamp": "2026-07-13T10:00:00Z",
    "fieldErrors": [],
    "details": {}
  }
}
```

The `code` field is a stable, public identifier. Frontend code MAY branch
on it. Adding a new code is a NON-BREAKING change. Removing or renaming
a code is a BREAKING change and must follow the deprecation policy in
[CRM-API-VERSIONING-POLICY.md](./CRM-API-VERSIONING-POLICY.md).

## Not-found codes (HTTP 404)

| Code | Message | Retryable | User-facing | When used |
|---|---|---|---|---|
| `CRM_ACCOUNT_NOT_FOUND` | The requested CRM account was not found. | No | Yes | GET/PATCH `/accounts/{id}` and the account row does not exist OR belongs to another tenant. |
| `CRM_CONTACT_NOT_FOUND` | The requested CRM contact was not found. | No | Yes | GET/PATCH `/contacts/{id}` — same rules. |
| `CRM_LEAD_NOT_FOUND` | The requested CRM lead was not found. | No | Yes | GET/PATCH `/leads/{id}` — same rules. |
| `CRM_OPPORTUNITY_NOT_FOUND` | The requested CRM opportunity was not found. | No | Yes | GET/PATCH `/opportunities/{id}` — same rules. |
| `CRM_ACTIVITY_NOT_FOUND` | The requested CRM activity was not found. | No | Yes | GET/PATCH `/activities/{id}` — same rules. |
| `CRM_PIPELINE_NOT_FOUND` | The requested CRM pipeline was not found. | No | Yes | GET `/pipelines/{id}` — same rules. |
| `CRM_STAGE_NOT_FOUND` | The requested CRM pipeline stage was not found. | No | Yes | GET `/pipelines/{id}/stages/{stageId}` — same rules. |
| `CRM_IMPORT_NOT_FOUND` | The requested CRM import job was not found. | No | Yes | GET `/imports/{jobId}` — same rules. |
| `CRM_CUSTOM_FIELD_NOT_FOUND` | The requested CRM custom field was not found. | No | Yes | GET `/custom-fields/{id}` — same rules. |
| `RESOURCE_NOT_FOUND` | The requested resource was not found. | No | Yes | Generic 404 fallback. |

**Cross-tenant access:** when Tenant B requests an entity owned by Tenant A,
the response is the same `404 *_NOT_FOUND` as if the entity did not exist.
The response NEVER discloses whether the entity exists for another tenant.
This is enforced by `CrmExceptionHandler` and verified by
`CrmTenantIsolationContractTest`.

## Duplicate / state-conflict codes (HTTP 409)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `CRM_DUPLICATE_ACCOUNT` | An account with the same identity already exists. | No | POST `/accounts` with a duplicate `displayName` (case-insensitive normalized). |
| `CRM_DUPLICATE_CONTACT` | A contact with the same email already exists. | No | POST `/contacts` with a duplicate `primaryEmail`. |
| `CRM_DUPLICATE_LEAD` | A lead with the same identity already exists. | No | POST `/leads` with a duplicate `email + displayName` pair. |
| `CRM_LEAD_ALREADY_CONVERTED` | The lead has already been converted and cannot be converted again. | No | POST `/leads/{id}/convert` when the lead's `status` is already `CONVERTED`. |
| `CRM_IDEMPOTENCY_CONFLICT` | The Idempotency-Key was already used with a different request payload. | No | POST with `Idempotency-Key` already seen, but the request body hash differs. |
| `CONFLICT` | The request conflicts with the current state of the resource. | No | Generic 409 fallback. |

## Invalid-state-transition codes (HTTP 422)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `CRM_INVALID_LEAD_TRANSITION` | The requested lead status transition is not allowed. | No | PATCH `/leads/{id}/status` with an invalid `NEW → ARCHIVED` skip, etc. |
| `CRM_INVALID_OPPORTUNITY_STAGE` | The requested opportunity stage move is not allowed. | No | PATCH `/opportunities/{id}/stage` to a stage belonging to a different pipeline, or moving a terminal opportunity. |
| `CRM_IMPORT_MAPPING_INVALID` | The import mapping is invalid or incomplete. | No | POST `/imports/{id}/run` when required columns are unmapped. |
| `CRM_CUSTOM_FIELD_VALIDATION_FAILED` | One or more custom field values failed validation. | No | PUT `/custom-fields/values/{entityType}/{entityId}` with a value that violates the field's data type. |

## Concurrency (HTTP 412)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `CRM_CONCURRENCY_CONFLICT` | The resource was modified by another operation. Please refresh and retry. | Yes | PATCH with a stale `If-Match` ETag. The client SHOULD re-GET the resource, present a merge UI if necessary, then retry the PATCH with the new ETag. |

## Authorization (HTTP 401 / 403)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `UNAUTHORIZED` | Authentication is required to access this resource. | No | Missing or invalid JWT. The client SHOULD re-authenticate. |
| `CRM_TENANT_ACCESS_DENIED` | Access to the requested resource was denied. | No | The authenticated principal's tenant_id does not match the resource's tenant_id. (Most cross-tenant access surfaces as 404 instead — see above.) |
| `CRM_CAPABILITY_REQUIRED` | The authenticated principal lacks the required capability. | No | The principal's role does not include the capability required by the endpoint (e.g. `CRM.LEAD.CONVERT`). |
| `FORBIDDEN` | Access to the requested resource was denied. | No | Generic 403 fallback. |

## Validation / client errors (HTTP 400)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `VALIDATION_ERROR` | The request contains invalid fields. | No | Bean-validation failure. The `fieldErrors` array lists each offending field with a per-field code and message. |

### Per-field codes (in `fieldErrors[].code`)

| Field code | Meaning |
|---|---|
| `REQUIRED` | A required field was missing or blank. |
| `SIZE` | The field value exceeds the maximum length or is below the minimum. |
| `EMAIL` | The field value is not a valid email address. |
| `PATTERN` | The field value does not match the required pattern. |
| `MIN` / `MAX` | The numeric value is below / above the allowed range. |
| `DECIMALMIN` / `DECIMALMAX` | The decimal value is below / above the allowed range. |
| `POSITIVEORZERO` | The value must be ≥ 0. |
| `INVALID` | Catch-all for field-level validation failures. |

## Rate-limiting (HTTP 429)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `RATE_LIMITED` | Too many requests. Please slow down. | Yes (after backoff) | The tenant has exceeded its per-window request quota. |

## Precondition required (HTTP 428)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `CRM_PRECONDITION_REQUIRED` | The If-Match header is required for this operation. | No | PATCH endpoint called without `If-Match` header. The client MUST re-read the resource, obtain the ETag, then retry the PATCH with `If-Match` set. |

## Idempotency key required (HTTP 400)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `CRM_IDEMPOTENCY_KEY_REQUIRED` | The Idempotency-Key header is required for this operation. | No | POST endpoint that requires idempotency was called without `Idempotency-Key` header. |

## Catch-all (HTTP 500)

| Code | Message | Retryable | When used |
|---|---|---|---|
| `INTERNAL_ERROR` | An internal server error occurred. Please try again later. | Yes | Any uncaught exception. The full stack trace is logged at ERROR with the `requestId` so operators can correlate. The body NEVER contains the stack trace, SQL, table name, or package name. |

## Forbidden in error bodies

The following must NEVER appear in a CRM error response body. This is
enforced by `CrmErrorContractTest`:

- Stack traces
- SQL statements or fragments
- Table names, column names, constraint names
- Java package names, class names, or method names
- Tenant IDs of other tenants
- Access tokens, refresh tokens, passwords, API keys
- CSV file contents
- Custom field values marked `sensitive`
- Any indication of whether a resource exists for a different tenant

## Response shape reference

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "status": 400,
    "requestId": "5f8e2d3a-1b6c-4e2d-9a3f-7c8b1d2e3f4a",
    "timestamp": "2026-07-13T10:00:00Z",
    "fieldErrors": [
      {
        "field": "displayName",
        "code": "REQUIRED",
        "message": "Display name is required."
      }
    ],
    "details": {}
  }
}
```
