# SNAD Unified Error Model

## Content Type
`application/problem+json`

## Structure
```json
{
  "type": "https://snad.ai/errors/val-001",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/users",
  "code": "SANAD-VAL-001",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-07-01T12:00:00Z",
  "errors": [
    {"field": "email", "code": "INVALID_FORMAT", "message": "must be a valid email address"}
  ]
}
```

## Fields
| Field | Type | Description |
|---|---|---|
| type | String | URI or identifier for error type |
| title | String | Short human-readable title |
| status | Integer | HTTP status code |
| detail | String | Safe, non-sensitive explanation |
| instance | String | Request path |
| code | String | Machine-readable SANAD error code |
| requestId | String | Correlation identifier |
| timestamp | String | UTC timestamp ISO-8601 |
| errors | Array | Field validation errors (optional) |

## Security Rules
- No stack traces
- No SQL statements
- No database/table names
- No Java class names
- No secret values
- No JWT values
- No internal IDs beyond what's necessary
