# G7 API CONTRACT FINAL

## Phase 5: API Reconciliation

**Document ID:** G7-API-CONTRACT-FINAL
**Version:** 1.0.0
**Status:** FINAL
**Date:** 2026-08-11
**Scope:** Complete API contract definition for G7 Mobile Sync feature

---

## 1. EXISTING APIs (Mobile-Relevant)

The following APIs already exist in the production system. They provide CRM entity access but are NOT optimized for mobile sync.

### 1.1 GET /api/v1/crm/accounts

**Status:** EXISTS - Not mobile-optimized

**Purpose:** List all accounts for the authenticated user's tenant.

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| page | integer | No | Page number (default: 1) |
| per_page | integer | No | Items per page (default: 20, max: 100) |
| search | string | No | Full-text search query |
| sort | string | No | Sort field (default: created_at) |
| order | string | No | Sort order: asc/desc (default: desc) |

**Response:** 200 OK

```json
{
    "data": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Acme Corporation",
            "industry": "Technology",
            "phone": "+1-555-0100",
            "website": "https://acme.example.com",
            "version": 42,
            "created_at": "2026-01-15T10:00:00Z",
            "updated_at": "2026-08-11T14:30:00Z"
        }
    ],
    "meta": {
        "page": 1,
        "per_page": 20,
        "total": 150,
        "total_pages": 8
    }
}
```

**Limitations for Mobile:**
- Returns all fields (heavy payload)
- Offset-based pagination (not efficient for delta sync)
- No version/cursor support
- No conflict detection

### 1.2 GET /api/v1/crm/accounts/{id}

**Status:** EXISTS

**Purpose:** Get single account by ID.

**Response:** 200 OK with full account payload.

### 1.3 GET /api/v1/crm/contacts

**Status:** EXISTS - Not mobile-optimized

**Purpose:** List contacts with offset pagination.

### 1.4 GET /api/v1/crm/contacts/{id}

**Status:** EXISTS

**Purpose:** Get single contact by ID.

### 1.5 GET /api/v1/crm/leads

**Status:** EXISTS - Not mobile-optimized

**Purpose:** List leads with offset pagination.

### 1.6 GET /api/v1/crm/leads/{id}

**Status:** EXISTS

**Purpose:** Get single lead by ID.

### 1.7 GET /api/v1/crm/opportunities

**Status:** EXISTS - Not mobile-optimized

**Purpose:** List opportunities with offset pagination.

### 1.8 GET /api/v1/crm/opportunities/{id}

**Status:** EXISTS

**Purpose:** Get single opportunity by ID.

### 1.9 GET /api/v1/crm/tasks

**Status:** EXISTS - Not mobile-optimized

**Purpose:** List tasks with offset pagination.

### 1.10 GET /api/v1/crm/tasks/{id}

**Status:** EXISTS

**Purpose:** Get single task by ID.

---

## 2. REQUIRED NEW APIs (TRUE_REQUIRED_G7_APIS)

The following nine APIs are **mandatory** for G7 mobile sync functionality. They must be implemented and deployed before any G7 client code is released.

### 2.1 GET /api/v2/mobile/sync/pull

**Priority:** P0 BLOCKER

**Purpose:** Delta sync pull. Fetch entities changed since the client's last sync cursor.

**Requirement:** G7-MOB-FR-003, G7-MOB-SYNC-002

**Authentication:** JWT required

**Authorization:** RBAC read permissions on requested entity types

**Tenant:** RLS enforced (from JWT tenant_id claim)

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| entity_types | string[] | Yes | Entity types to pull (e.g., ["account", "contact", "lead"]) |
| cursor | string | No | Opaque cursor token from previous pull. First pull: omit. |
| limit | integer | No | Max entities per response (default: 100, max: 500) |
| device_id | string | Yes | Registered device identifier |

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes | Bearer {jwt_token} |
| If-None-Match | No | ETag from previous response (304 support) |

**Request Body:**

```json
{
    "entity_types": ["account", "contact"],
    "cursor": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
    "limit": 100,
    "device_id": "ios-device-abc123"
}
```

---

**Response:**

**200 OK**

| Field | Type | Description |
|-------|------|-------------|
| entities | object[] | Array of changed entities |
| entity_type | string | Entity type for this batch |
| new_cursor | string | Opaque cursor for next pull request |
| has_more | boolean | True if more entities available |
| etag | string | Response ETag for caching |

**Response Body:**

```json
{
    "entities": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "type": "account",
            "version": 43,
            "created_at": "2026-01-15T10:00:00Z",
            "updated_at": "2026-08-11T14:30:00Z",
            "deleted_at": null,
            "payload": {
                "name": "Acme Corporation",
                "industry": "Technology",
                "phone": "+1-555-0100",
                "website": "https://acme.example.com"
            }
        },
        {
            "id": "660e8400-e29b-41d4-a716-446655440001",
            "type": "contact",
            "version": 12,
            "created_at": "2026-02-20T11:00:00Z",
            "updated_at": "2026-08-10T09:15:00Z",
            "deleted_at": null,
            "payload": {
                "first_name": "Jane",
                "last_name": "Doe",
                "email": "jane.doe@example.com",
                "phone": "+1-555-0101"
            }
        }
    ],
    "entity_type": "account",
    "new_cursor": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
    "has_more": true
}
```

**Response Headers:**

| Header | Description |
|--------|-------------|
| ETag | Response ETag for conditional requests |
| X-Sync-Version | Server sync version |
| X-Entity-Count | Number of entities in response |

---

**Error Responses:**

| Code | Description | Response |
|------|-------------|----------|
| 400 | Invalid request (bad cursor, invalid entity_types) | `{ "error": "INVALID_REQUEST", "message": "..." }` |
| 401 | Unauthorized (invalid/expired JWT) | `{ "error": "UNAUTHORIZED", "message": "..." }` |
| 403 | Forbidden (insufficient RBAC permissions) | `{ "error": "FORBIDDEN", "message": "..." }` |
| 429 | Rate limited | `{ "error": "RATE_LIMITED", "retry_after": 60 }` |
| 500 | Server error | `{ "error": "INTERNAL_ERROR", "message": "..." }` |
| 503 | Service unavailable | `{ "error": "SERVICE_UNAVAILABLE", "retry_after": 30 }` |

---

**Idempotency:** Not required (read operation). Client may retry safely.

**Retryability:** Yes for transient errors (429, 500, 503). No for client errors (400, 401, 403).

**ETag Support:** Response includes ETag header. Client may send `If-None-Match` to receive 304 Not Modified if no changes.

**Cursor Protocol:**
- First pull: omit cursor parameter
- Subsequent pulls: include cursor from previous response
- Cursor is opaque (client must not decode or manipulate)
- Cursor expiration: 7 days of inactivity triggers full resync
- Cursor invalidation: server may invalidate cursor (returns `needs_full_resync: true` in status endpoint)

**Rate Limiting:** 100 requests per minute per device. Exceeding returns 429 with `retry_after` header.

---

### 2.2 POST /api/v2/mobile/sync/push

**Priority:** P0 BLOCKER

**Purpose:** Batch sync push. Submit local mutations for server processing with conflict detection.

**Requirement:** G7-MOB-FR-004, G7-MOB-SYNC-003

**Authentication:** JWT required

**Authorization:** RBAC write permissions per entity type

**Tenant:** RLS enforced (from JWT tenant_id claim)

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| operations | object[] | Yes | Array of mutation operations (max 100 per batch) |

**Operation Object:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| idempotency_key | string | Yes | Client-generated unique key for deduplication |
| entity_type | string | Yes | Entity type (account, contact, etc.) |
| entity_id | UUID | Yes | Entity ID (UUID for create, existing ID for update) |
| operation | string | Yes | Operation: CREATE, UPDATE, DELETE |
| base_version | integer | Yes | Client's known version (for optimistic locking) |
| payload | object | Yes | Entity data (for CREATE/UPDATE), null for DELETE |

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes | Bearer {jwt_token} |
| Content-Type | Yes | application/json |

**Request Body:**

```json
{
    "operations": [
        {
            "idempotency_key": "idem-push-contact-001",
            "entity_type": "contact",
            "entity_id": "660e8400-e29b-41d4-a716-446655440001",
            "operation": "UPDATE",
            "base_version": 12,
            "payload": {
                "first_name": "Jane",
                "last_name": "Doe",
                "email": "jane.updated@example.com",
                "phone": "+1-555-0102"
            }
        },
        {
            "idempotency_key": "idem-push-contact-002",
            "entity_type": "contact",
            "entity_id": "770e8400-e29b-41d4-a716-446655440002",
            "operation": "CREATE",
            "base_version": 0,
            "payload": {
                "first_name": "John",
                "last_name": "Smith",
                "email": "john.smith@example.com",
                "phone": "+1-555-0103"
            }
        }
    ]
}
```

---

**Response:**

**200 OK** (individual operations may succeed or fail)

| Field | Type | Description |
|-------|------|-------------|
| results | object[] | Array of operation results |

**Result Object:**

| Field | Type | Description |
|-------|------|-------------|
| idempotency_key | string | Echo of request idempotency_key |
| status | string | APPLIED, CONFLICT, or ERROR |
| new_version | integer | New version (if APPLIED) |
| entity_id | UUID | Entity ID (assigned for CREATE) |
| conflict | object | Conflict details (if CONFLICT) |
| error | object | Error details (if ERROR) |

**Response Body:**

```json
{
    "results": [
        {
            "idempotency_key": "idem-push-contact-001",
            "status": "CONFLICT",
            "entity_id": "660e8400-e29b-41d4-a716-446655440001",
            "conflict": {
                "server_version": 13,
                "client_version": 12,
                "server_payload": {
                    "first_name": "Jane",
                    "last_name": "Doe",
                    "email": "jane.server@example.com"
                },
                "conflict_id": "aa0e8400-e29b-41d4-a716-446655440005"
            }
        },
        {
            "idempotency_key": "idem-push-contact-002",
            "status": "APPLIED",
            "new_version": 1,
            "entity_id": "770e8400-e29b-41d4-a716-446655440002"
        }
    ]
}
```

---

**Error Responses:**

| Code | Description | Response |
|------|-------------|----------|
| 400 | Invalid request (bad payload, missing fields) | `{ "error": "INVALID_REQUEST", "message": "..." }` |
| 401 | Unauthorized (invalid/expired JWT) | `{ "error": "UNAUTHORIZED", "message": "..." }` |
| 403 | Forbidden (insufficient RBAC permissions) | `{ "error": "FORBIDDEN", "message": "..." }` |
| 412 | Precondition Failed (batch-level version conflict) | `{ "error": "VERSION_CONFLICT", "message": "..." }` |
| 422 | Unprocessable Entity (validation errors) | `{ "error": "VALIDATION_ERROR", "details": [...] }` |
| 429 | Rate limited | `{ "error": "RATE_LIMITED", "retry_after": 60 }` |
| 500 | Server error | `{ "error": "INTERNAL_ERROR", "message": "..." }` |

---

**Idempotency:** REQUIRED. Each operation includes `idempotency_key`. Server deduplicates by key within 24-hour window. Duplicate key returns cached result.

**Idempotency Protocol:**

1. Client generates unique `idempotency_key` (UUID or client-generated string)
2. Server stores result keyed by idempotency_key for 24 hours
3. Duplicate push with same key returns cached response (no re-processing)
4. Client may safely retry with same idempotency_key on network error

**Retryability:** Yes for transient errors (429, 500). No for 412 (conflict) - client must resolve conflict first. No for 400/401/403 (client errors).

**Conflict Detection:**

1. Client sends `base_version` (version client last saw)
2. Server compares against current `version` in database
3. If `base_version < current_version`: CONFLICT detected
4. Server returns both server_payload and client_payload
5. Client must resolve conflict via `/api/v2/mobile/conflicts/{id}/resolve`

**Batch Size:** Maximum 100 operations per push request. Exceeding returns 422.

**Processing Order:** Operations are processed sequentially within a batch. If one operation fails, subsequent operations are still attempted. Each operation has independent result.

---

### 2.3 GET /api/v2/mobile/sync/status

**Priority:** P1 CRITICAL

**Purpose:** Get current sync state for a device. Returns cursor positions and whether a full resync is needed.

**Requirement:** G7-MOB-FR-005

**Authentication:** JWT required

**Authorization:** RBAC read

**Tenant:** RLS enforced

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| device_id | string | Yes | Device identifier |

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes | Bearer {jwt_token} |

---

**Response:**

**200 OK**

```json
{
    "cursors": [
        {
            "entity_type": "account",
            "last_sync_at": "2026-08-11T14:00:00Z",
            "last_sync_version": 42
        },
        {
            "entity_type": "contact",
            "last_sync_at": "2026-08-11T14:00:00Z",
            "last_sync_version": 12
        },
        {
            "entity_type": "lead",
            "last_sync_at": "2026-08-10T10:00:00Z",
            "last_sync_version": 8
        }
    ],
    "needs_full_resync": false,
    "server_time": "2026-08-11T15:00:00Z"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| cursors | object[] | Array of sync cursors per entity type |
| cursors[].entity_type | string | Entity type |
| cursors[].last_sync_at | timestamp | When this entity type was last synced |
| cursors[].last_sync_version | integer | Version at last sync |
| needs_full_resync | boolean | True if cursor expired or invalidated |
| server_time | timestamp | Current server time |

---

**Error Responses:**

| Code | Description |
|------|-------------|
| 400 | Invalid request (missing device_id) |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Device not registered |
| 429 | Rate limited |
| 500 | Server error |

**Priority:** P1 CRITICAL

**Idempotency:** Not required (read operation).

**Retryability:** Yes for transient errors.

---

### 2.4 POST /api/v2/mobile/device/register

**Priority:** P2 HIGH

**Purpose:** Register a mobile device for sync operations.

**Requirement:** G7-MOB-SEC-003

**Authentication:** JWT required

**Authorization:** Any authenticated user (self-registration)

**Tenant:** RLS enforced

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| device_id | string | Yes | Unique device identifier |
| device_name | string | No | Human-readable device name |
| platform | string | Yes | Platform: ios, android, web |

**Request Body:**

```json
{
    "device_id": "ios-device-abc123",
    "device_name": "iPhone 15 Pro",
    "platform": "ios"
}
```

---

**Response:**

**201 Created**

```json
{
    "device_registry_id": "550e8400-e29b-41d4-a716-446655440000",
    "device_id": "ios-device-abc123",
    "device_name": "iPhone 15 Pro",
    "platform": "ios",
    "registered_at": "2026-08-11T10:00:00Z"
}
```

**200 OK** (if device already registered, returns existing registration)

---

**Error Responses:**

| Code | Description |
|------|-------------|
| 400 | Invalid request (missing device_id, invalid platform) |
| 401 | Unauthorized |
| 409 | Conflict (device registered by different user) |
| 429 | Rate limited |
| 500 | Server error |

**Idempotency:** Implicit (returns existing registration if device_id already exists).

**Retryability:** Yes for transient errors.

---

### 2.5 GET /api/v2/mobile/entity/{type}/{id}

**Priority:** P1 CRITICAL

**Purpose:** Get single entity with mobile-optimized payload. Returns only essential fields, reducing bandwidth.

**Requirement:** G7-MOB-FR-002

**Authentication:** JWT required

**Authorization:** RBAC read on entity type

**Tenant:** RLS enforced

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| type | string | Yes (path) | Entity type: account, contact, lead, opportunity, task |
| id | UUID | Yes (path) | Entity ID |
| fields | string | No (query) | Comma-separated field list (default: all essential fields) |

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes | Bearer {jwt_token} |
| If-None-Match | No | ETag from previous response |

---

**Response:**

**200 OK**

```json
{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "type": "account",
    "version": 43,
    "created_at": "2026-01-15T10:00:00Z",
    "updated_at": "2026-08-11T14:30:00Z",
    "payload": {
        "name": "Acme Corporation",
        "industry": "Technology",
        "phone": "+1-555-0100",
        "website": "https://acme.example.com"
    }
}
```

**304 Not Modified** (if ETag matches, no body)

---

**Mobile-Optimized Fields:**

| Entity Type | Essential Fields |
|-------------|------------------|
| account | name, industry, phone, website |
| contact | first_name, last_name, email, phone, account_id |
| lead | first_name, last_name, email, phone, status |
| opportunity | title, amount, stage, account_id, contact_id |
| task | subject, status, priority, due_date |

Non-essential fields (audit logs, custom fields, relationships) are excluded by default. Client may request specific fields via `fields` query parameter.

**Error Responses:**

| Code | Description |
|------|-------------|
| 400 | Invalid entity type or ID |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Entity not found |
| 429 | Rate limited |
| 500 | Server error |

**Idempotency:** Not required (read operation).

**Retryability:** Yes for transient errors.

---

### 2.6 GET /api/v2/mobile/entity/{type}

**Priority:** P1 CRITICAL

**Purpose:** List entities with mobile-optimized payload. Returns paginated list with reduced payloads.

**Requirement:** G7-MOB-FR-001

**Authentication:** JWT required

**Authorization:** RBAC read on entity type

**Tenant:** RLS enforced

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| type | string | Yes (path) | Entity type: account, contact, lead, opportunity, task |
| cursor | string | No (query) | Cursor for pagination |
| limit | integer | No (query) | Items per page (default: 50, max: 200) |
| search | string | No (query) | Search query |
| sort | string | No (query) | Sort field (default: updated_at) |
| order | string | No (query) | Sort order: asc/desc (default: desc) |
| fields | string | No (query) | Comma-separated field list |

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes | Bearer {jwt_token} |
| If-None-Match | No | ETag from previous response |

---

**Response:**

**200 OK**

```json
{
    "data": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "type": "account",
            "version": 43,
            "updated_at": "2026-08-11T14:30:00Z",
            "payload": {
                "name": "Acme Corporation",
                "industry": "Technology",
                "phone": "+1-555-0100"
            }
        }
    ],
    "meta": {
        "cursor": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
        "has_more": true,
        "total": 150
    }
}
```

**304 Not Modified** (if ETag matches)

---

**Pagination:** Cursor-based. Client uses `cursor` from response to get next page. `has_more` indicates if more pages exist.

**Error Responses:**

| Code | Description |
|------|-------------|
| 400 | Invalid entity type or query parameters |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Entity type not found |
| 429 | Rate limited |
| 500 | Server error |

**Idempotency:** Not required (read operation).

**Retryability:** Yes for transient errors.

---

### 2.7 GET /api/v2/mobile/conflicts

**Priority:** P1 CRITICAL

**Purpose:** List unresolved conflicts requiring user resolution.

**Requirement:** G7-MOB-SYNC-006

**Authentication:** JWT required

**Authorization:** RBAC read

**Tenant:** RLS enforced

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| status | string | No (query) | Filter by resolution status (default: PENDING) |
| entity_type | string | No (query) | Filter by entity type |
| cursor | string | No (query) | Cursor for pagination |
| limit | integer | No (query) | Items per page (default: 20, max: 100) |

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| Authorization | Yes | Bearer {jwt_token} |

---

**Response:**

**200 OK**

```json
{
    "conflicts": [
        {
            "id": "aa0e8400-e29b-41d4-a716-446655440005",
            "entity_type": "contact",
            "entity_id": "660e8400-e29b-41d4-a716-446655440001",
            "server_version": 13,
            "client_version": 12,
            "server_payload": {
                "first_name": "Jane",
                "last_name": "Doe",
                "email": "jane.server@example.com"
            },
            "client_payload": {
                "first_name": "Jane",
                "last_name": "Doe",
                "email": "jane.client@example.com"
            },
            "resolution": "PENDING",
            "created_at": "2026-08-11T14:30:00Z",
            "resolved_at": null
        }
    ],
    "meta": {
        "total": 3,
        "cursor": "...",
        "has_more": false
    }
}
```

---

**Error Responses:**

| Code | Description |
|------|-------------|
| 400 | Invalid query parameters |
| 401 | Unauthorized |
| 403 | Forbidden |
| 429 | Rate limited |
| 500 | Server error |

**Idempotency:** Not required (read operation).

**Retryability:** Yes for transient errors.

---

### 2.8 POST /api/v2/mobile/conflicts/{id}/resolve

**Priority:** P1 CRITICAL

**Purpose:** User resolves a conflict by choosing server version, client version, or merged payload.

**Requirement:** G7-MOB-SYNC-006

**Authentication:** JWT required

**Authorization:** RBAC write + entity ownership

**Tenant:** RLS enforced

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | UUID | Yes (path) | Conflict ID |
| resolution | string | Yes | Resolution: SERVER_WINS, CLIENT_WINS, MERGED |
| merged_payload | object | Conditional | Required if resolution = MERGED |

**Request Body:**

```json
{
    "resolution": "MERGED",
    "merged_payload": {
        "first_name": "Jane",
        "last_name": "Doe",
        "email": "jane.merged@example.com",
        "phone": "+1-555-0102"
    }
}
```

---

**Response:**

**200 OK**

```json
{
    "status": "RESOLVED",
    "new_version": 14,
    "resolved_at": "2026-08-11T15:00:00Z"
}
```

---

**Resolution Types:**

| Resolution | Description | Payload Required |
|------------|-------------|------------------|
| SERVER_WINS | Server version is kept | No |
| CLIENT_WINS | Client version is applied | No |
| MERGED | Custom merged payload is applied | Yes (merged_payload) |

---

**Error Responses:**

| Code | Description |
|------|-------------|
| 400 | Invalid resolution type or missing merged_payload |
| 401 | Unauthorized |
| 403 | Forbidden (not entity owner) |
| 404 | Conflict not found |
| 409 | Conflict already resolved |
| 429 | Rate limited |
| 500 | Server error |

**Idempotency:** Implicit (resolve is idempotent - resolving an already-resolved conflict returns success).

**Retryability:** Yes for transient errors.

---

### 2.9 POST /api/v2/mobile/conflicts/{id}/skip

**Priority:** P1 CRITICAL

**Purpose:** User defers conflict resolution. Marks conflict as SKIPPED without resolution.

**Requirement:** G7-MOB-SYNC-006

**Authentication:** JWT required

**Authorization:** RBAC read

**Tenant:** RLS enforced

**Version:** v2

---

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | UUID | Yes (path) | Conflict ID |

**Request Body:** Empty (POST with no body)

---

**Response:**

**200 OK**

```json
{
    "status": "SKIPPED",
    "skipped_at": "2026-08-11T15:00:00Z"
}
```

---

**Error Responses:**

| Code | Description |
|------|-------------|
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Conflict not found |
| 409 | Conflict already resolved |
| 429 | Rate limited |
| 500 | Server error |

**Idempotency:** Implicit (skip is idempotent).

**Retryability:** Yes for transient errors.

---

## 3. API VERSIONING STRATEGY

### 3.1 Version Selection

- All new G7 APIs use `/api/v2/` prefix
- Existing CRM APIs remain at `/api/v1/`
- No breaking changes to existing v1 APIs

### 3.2 Deprecation Policy

- v1 mobile-relevant APIs (accounts, contacts, etc.) remain available
- Mobile clients should migrate to v2 mobile-optimized endpoints
- v1 deprecation timeline: TBD (minimum 12 months notice)

### 3.3 Version Negotiation

- Client may specify `Accept-Version: v2` header (optional)
- Server defaults to latest version if not specified
- Response includes `X-API-Version` header

---

## 4. AUTHENTICATION & AUTHORIZATION

### 4.1 JWT Authentication

All G7 APIs require JWT Bearer token in Authorization header.

**JWT Claims:**

| Claim | Description |
|-------|-------------|
| sub | User ID |
| tenant_id | Tenant ID (for RLS) |
| roles | User roles array |
| permissions | Granular permissions array |

### 4.2 RBAC Permissions

| API | Required Permission |
|-----|---------------------|
| GET /api/v2/mobile/sync/pull | read:{entity_type} |
| POST /api/v2/mobile/sync/push | write:{entity_type} |
| GET /api/v2/mobile/sync/status | read:sync_status |
| POST /api/v2/mobile/device/register | create:device |
| GET /api/v2/mobile/entity/{type}/{id} | read:{entity_type} |
| GET /api/v2/mobile/entity/{type} | read:{entity_type} |
| GET /api/v2/mobile/conflicts | read:conflicts |
| POST /api/v2/mobile/conflicts/{id}/resolve | write:conflicts |
| POST /api/v2/mobile/conflicts/{id}/skip | read:conflicts |

### 4.3 Tenant Isolation

- RLS enforced at database level
- Application layer also filters by tenant_id from JWT
- Cross-tenant access is impossible

---

## 5. RATE LIMITING

### 5.1 Limits

| API | Rate Limit | Window |
|-----|------------|--------|
| GET /api/v2/mobile/sync/pull | 100 requests | 1 minute |
| POST /api/v2/mobile/sync/push | 20 requests | 1 minute |
| GET /api/v2/mobile/sync/status | 60 requests | 1 minute |
| POST /api/v2/mobile/device/register | 10 requests | 1 minute |
| GET /api/v2/mobile/entity/{type}/{id} | 200 requests | 1 minute |
| GET /api/v2/mobile/entity/{type} | 60 requests | 1 minute |
| GET /api/v2/mobile/conflicts | 30 requests | 1 minute |
| POST /api/v2/mobile/conflicts/{id}/resolve | 30 requests | 1 minute |
| POST /api/v2/mobile/conflicts/{id}/skip | 30 requests | 1 minute |

### 5.2 Rate Limit Response

```json
{
    "error": "RATE_LIMITED",
    "message": "Rate limit exceeded",
    "retry_after": 60
}
```

### 5.3 Headers

| Header | Description |
|--------|-------------|
| X-RateLimit-Limit | Maximum requests per window |
| X-RateLimit-Remaining | Remaining requests in window |
| X-RateLimit-Reset | Window reset timestamp |

---

## 6. PAGINATION STRATEGY

### 6.1 Cursor-Based Pagination

G7 v2 APIs use cursor-based pagination (not offset-based).

**Advantages:**
- Consistent results during concurrent modifications
- No skipped or duplicated items
- Efficient for large datasets

**Protocol:**

1. Client sends request without cursor (first page)
2. Response includes `cursor` in meta object
3. Client sends next request with cursor from previous response
4. Response includes `has_more: true` if more pages exist
5. When `has_more: false`, all items have been retrieved

### 6.2 Cursor Format

- Opaque string (JWT, base64-encoded state, etc.)
- Client must not decode or manipulate cursor
- Server may invalidate cursor (returns error or `needs_full_resync: true`)

---

## 7. ETag & CACHING

### 7.1 ETag Generation

- Server generates ETag from response content hash
- ETag returned in response header
- Client stores ETag with cached response

### 7.2 Conditional Requests

- Client sends `If-None-Match: {etag}` header
- If content unchanged: server returns 304 Not Modified (no body)
- If content changed: server returns 200 OK with new content and ETag

### 7.3 Cache Invalidation

- Mutations (POST/PUT/DELETE) invalidate related ETags
- Server purges stale ETags after 24 hours
- Client should re-fetch if 304 response is stale (> 1 hour)

---

## 8. ERROR HANDLING

### 8.1 Error Response Format

All error responses follow consistent format:

```json
{
    "error": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": [],
    "request_id": "uuid-for-debugging"
}
```

### 8.2 HTTP Status Codes

| Code | Description | When to Use |
|------|-------------|-------------|
| 200 | OK | Successful read/update |
| 201 | Created | Successful create |
| 304 | Not Modified | ETag match |
| 400 | Bad Request | Invalid request parameters |
| 401 | Unauthorized | Invalid/expired JWT |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Entity not found |
| 409 | Conflict | Resource conflict (version, idempotency) |
| 412 | Precondition Failed | Version conflict in batch |
| 422 | Unprocessable Entity | Validation errors |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server-side error |
| 503 | Service Unavailable | Temporary outage |

### 8.3 Retry Strategy

| Status | Retryable | Backoff |
|--------|-----------|---------|
| 400 | No | N/A |
| 401 | No | N/A |
| 403 | No | N/A |
| 404 | No | N/A |
| 409 | No | N/A |
| 412 | No | N/A |
| 422 | No | N/A |
| 429 | Yes | Exponential (1s, 2s, 4s, 8s, max 60s) |
| 500 | Yes | Exponential (1s, 2s, 4s, 8s, max 30s) |
| 503 | Yes | Exponential (1s, 2s, 4s, 8s, max 60s) |

---

## 9. REQUEST/RESPONSE SCHEMAS

### 9.1 JSON Schema Definitions

All request/response bodies are documented in OpenAPI 3.0 specification. Key schemas:

**Entity Payload:**

```json
{
    "type": "object",
    "properties": {
        "id": { "type": "string", "format": "uuid" },
        "type": { "type": "string", "enum": ["account", "contact", "lead", "opportunity", "task", "activity", "note"] },
        "version": { "type": "integer" },
        "created_at": { "type": "string", "format": "date-time" },
        "updated_at": { "type": "string", "format": "date-time" },
        "deleted_at": { "type": "string", "format": "date-time", "nullable": true },
        "payload": { "type": "object" }
    },
    "required": ["id", "type", "version", "created_at", "updated_at", "payload"]
}
```

**Sync Cursor:**

```json
{
    "type": "object",
    "properties": {
        "entity_type": { "type": "string" },
        "last_sync_at": { "type": "string", "format": "date-time" },
        "last_sync_version": { "type": "integer" }
    },
    "required": ["entity_type", "last_sync_at", "last_sync_version"]
}
```

**Conflict Object:**

```json
{
    "type": "object",
    "properties": {
        "id": { "type": "string", "format": "uuid" },
        "entity_type": { "type": "string" },
        "entity_id": { "type": "string", "format": "uuid" },
        "server_version": { "type": "integer" },
        "client_version": { "type": "integer" },
        "server_payload": { "type": "object" },
        "client_payload": { "type": "object" },
        "resolution": { "type": "string", "enum": ["PENDING", "SERVER_WINS", "CLIENT_WINS", "MERGED", "MANUAL"] },
        "created_at": { "type": "string", "format": "date-time" },
        "resolved_at": { "type": "string", "format": "date-time", "nullable": true }
    },
    "required": ["id", "entity_type", "entity_id", "server_version", "client_version", "created_at"]
}
```

---

## 10. TESTING REQUIREMENTS

### 10.1 Unit Tests

- All API handlers must have unit tests
- Mock database layer for isolation
- Test all error paths

### 10.2 Integration Tests

- Test against real database (test schema)
- Verify RLS enforcement
- Verify idempotency behavior
- Verify conflict detection

### 10.3 Contract Tests

- OpenAPI spec validated against implementation
- Client SDK generated from spec
- Contract tests verify client-server compatibility

### 10.4 Performance Tests

- Pull endpoint: 1000 concurrent clients, p95 < 200ms
- Push endpoint: 100 concurrent clients, p95 < 500ms
- Conflict resolution: 100 concurrent resolutions, p95 < 300ms

---

## 11. DEPLOYMENT CHECKLIST

| Item | Status | Owner |
|------|--------|-------|
| OpenAPI spec published | PENDING | TBD |
| v2 endpoints deployed | PENDING | TBD |
| RLS policies verified | PENDING | TBD |
| Rate limiting configured | PENDING | TBD |
| ETag support implemented | PENDING | TBD |
| Idempotency layer deployed | PENDING | TBD |
| Conflict resolution UI | PENDING | TBD |
| Mobile client SDK updated | PENDING | TBD |
| Load testing completed | PENDING | TBD |
| Security audit completed | PENDING | TBD |

---

## 12. API DEPENDENCY GRAPH

```
Mobile Client
    |
    +-- POST /api/v2/mobile/device/register (one-time)
    |
    +-- GET /api/v2/mobile/sync/status (check state)
    |       |
    |       +-- needs_full_resync == true?
    |               |
    |               +-- YES: GET /api/v2/mobile/entity/{type} (full fetch)
    |               |
    |               +-- NO: GET /api/v2/mobile/sync/pull (delta sync)
    |
    +-- POST /api/v2/mobile/sync/push (submit changes)
    |       |
    |       +-- status == CONFLICT?
    |               |
    |               +-- GET /api/v2/mobile/conflicts (list conflicts)
    |                       |
    |                       +-- POST /api/v2/mobile/conflicts/{id}/resolve
    |                       |
    |                       +-- POST /api/v2/mobile/conflicts/{id}/skip
    |
    +-- GET /api/v2/mobile/entity/{type}/{id} (detail view)
```

---

## 13. SIGN-OFF

| Role | Name | Date | Status |
|------|------|------|--------|
| API Architect | TBD | TBD | PENDING |
| Backend Lead | TBD | TBD | PENDING |
| Security Lead | TBD | TBD | PENDING |
| G7 Tech Lead | TBD | TBD | PENDING |

---

*Document generated: 2026-08-11*
*G7 API Contract Reconciliation Complete*
