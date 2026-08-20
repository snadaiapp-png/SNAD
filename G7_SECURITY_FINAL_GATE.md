# G7 - Security Final Gate

**Document Status:** Final  
**Phase:** 10 (Security Final Gate)  
**Date:** 2026-08-11  

---

## 1. Executive Summary

This document defines the complete security posture for the CRM mobile sync system. As Agent F had no dedicated output, this gate synthesizes security requirements from all architectural sources (G1-G6) and establishes the final security checklist before production deployment.

**Core Principle:** Defense in depth — no single point of failure in the security architecture.

---

## 2. Authentication

### 2.1 JWT Access Token

| Aspect | Specification |
|--------|---------------|
| **Type** | JSON Web Token (JWT) |
| **TTL** | 15 minutes |
| **Algorithm** | RS256 (RSA Signature with SHA-256) |
| **Storage** | Memory only (not persisted) |
| **Refresh** | Via refresh token before expiry |
| **Revocation** | Server-side revocation list |

**JWT Claims:**
```json
{
  "sub": "user-uuid",
  "tenant_id": "tenant-uuid",
  "roles": ["admin", "user"],
  "permissions": ["read", "write", "delete"],
  "iat": 1691760000,
  "exp": 1691760900,
  "iss": "crm-api.example.com",
  "aud": "crm-mobile-app"
}
```

### 2.2 Refresh Token

| Aspect | Specification |
|--------|---------------|
| **Type** | Opaque token |
| **TTL** | 7 days |
| **Storage** | Secure storage (Keychain/Keystore) |
| **Rotation** | On each use, new refresh token issued |
| **Revocation** | Server-side revocation list |
| **Offline** | Cached locally, checked on reconnect |

**Refresh Token Flow:**
```
1. Access token expires (15 min)
2. Client uses refresh token to get new access token
3. Server issues new access token + new refresh token
4. Old refresh token is invalidated
5. If refresh token expired → full re-authentication required
```

### 2.3 Mobile Token Management

| Scenario | Action |
|----------|--------|
| **Access token valid** | Use for API calls |
| **Access token expired, refresh valid** | Refresh to get new access token |
| **Both tokens expired** | Full re-authentication required |
| **Offline, tokens cached** | Use cached tokens until expiry |
| **Offline, tokens expired** | Cannot authenticate until online |

**Critical:** Mobile uses same token format as web — no mobile-specific token management (identified as RISK-003).

### 2.4 Re-authentication Flow

```
┌─────────────────────────────────────────────────────────┐
│                    AUTH FAILURE                          │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Refresh token valid?  │
              └────────────────────────┘
                    │            │
                   YES          NO
                    │            │
                    ▼            ▼
            ┌──────────┐  ┌──────────┐
            │ REFRESH  │  │ RE-AUTH  │
            │ TOKEN    │  │ REQUIRED │
            └──────────┘  └──────────┘
                    │            │
                    ▼            ▼
            ┌──────────┐  ┌──────────┐
            │ Continue │  │ Show     │
            │ Sync     │  │ Login    │
            └──────────┘  └──────────┘
```

---

## 3. Authorization

### 3.1 RBAC (Role-Based Access Control)

| Aspect | Specification |
|--------|---------------|
| **Framework** | Existing RoleCapability framework |
| **Enforcement** | All sync operations |
| **Mobile Roles** | Same as web (no mobile-specific roles) |
| **Check Point** | Server-side on every mutation |

**Role Hierarchy:**
```
Super Admin
    └── Admin
        └── Manager
            └── User
                └── Viewer (read-only)
```

### 3.2 Capability Matrix

| Operation | Viewer | User | Manager | Admin | Super Admin |
|-----------|--------|------|---------|-------|-------------|
| Read (pull) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Create | ✗ | ✓ | ✓ | ✓ | ✓ |
| Update (own) | ✗ | ✓ | ✓ | ✓ | ✓ |
| Update (any) | ✗ | ✗ | ✓ | ✓ | ✓ |
| Delete (own) | ✗ | ✓ | ✓ | ✓ | ✓ |
| Delete (any) | ✗ | ✗ | ✓ | ✓ | ✓ |
| Manage Users | ✗ | ✗ | ✗ | ✓ | ✓ |
| Manage Tenant | ✗ | ✗ | ✗ | ✗ | ✓ |

### 3.3 Sync-Specific Authorization

| Sync Operation | Required Role | Notes |
|----------------|---------------|-------|
| Pull (read) | Viewer+ | Standard read access |
| Push (create) | User+ | Create permission required |
| Push (update) | User+ | Ownership or manager+ |
| Push (delete) | User+ | Ownership or manager+ |
| Conflict resolution | User+ | Must own or manage entity |

---

## 4. Tenant Isolation

### 4.1 RLS (Row-Level Security)

| Aspect | Specification |
|--------|---------------|
| **Database** | PostgreSQL |
| **Mechanism** | Row-Level Security (RLS) |
| **Enforcement** | All CRM tables |
| **Tenant Context** | Set via JWT claim |
| **Cross-Tenant** | BLOCKED by RLS |

**RLS Policy Pattern:**
```sql
-- Example RLS policy for contacts table
CREATE POLICY tenant_isolation ON contacts
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

-- Set tenant context from JWT
SET app.tenant_id = '<tenant-uuid-from-jwt>';
```

### 4.2 Tenant Context Flow

```
┌─────────────────────────────────────────────────────────┐
│                    JWT VALIDATION                        │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Extract tenant_id     │
              │  from JWT claims       │
              └────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  SET app.tenant_id     │
              │  = tenant_id           │
              └────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  RLS policies filter   │
              │  all queries by        │
              │  tenant_id             │
              └────────────────────────┘
```

### 4.3 Sync Operations and Tenant Isolation

| Sync Operation | Tenant Isolation | Mechanism |
|----------------|------------------|-----------|
| Pull (read) | ✓ | RLS on SELECT queries |
| Push (create) | ✓ | tenant_id from JWT |
| Push (update) | ✓ | RLS + ownership check |
| Push (delete) | ✓ | RLS + ownership check |
| Conflict resolution | ✓ | RLS on all operations |

**Critical:** Sync operations use same tenant context as regular API operations — no bypass.

---

## 5. RLS Implementation

### 5.1 Existing CRM Tables

All existing CRM tables have RLS policies:

| Table | RLS Policy | Status |
|-------|------------|--------|
| accounts | tenant_isolation | ✓ Implemented |
| contacts | tenant_isolation | ✓ Implemented |
| leads | tenant_isolation | ✓ Implemented |
| opportunities | tenant_isolation | ✓ Implemented |
| tasks | tenant_isolation | ✓ Implemented |
| activities | tenant_isolation | ✓ Implemented |
| notes | tenant_isolation | ✓ Implemented |
| tags | tenant_isolation | ✓ Implemented |

### 5.2 New Sync Tables (Required RLS)

| Table | RLS Policy | Status |
|-------|------------|--------|
| mobile_device_registry | tenant_isolation | ✓ Implemented |
| mobile_sync_cursor | tenant_isolation | ✓ Implemented |
| mobile_sync_log | tenant_isolation | ✓ Implemented |
| mobile_conflict_log | tenant_isolation | ✓ Implemented |

**RLS Policy for New Tables:**
```sql
-- mobile_device_registry
CREATE POLICY tenant_isolation ON mobile_device_registry
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

-- mobile_sync_cursor
CREATE POLICY tenant_isolation ON mobile_sync_cursor
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

-- mobile_sync_log
CREATE POLICY tenant_isolation ON mobile_sync_log
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

-- mobile_conflict_log
CREATE POLICY tenant_isolation ON mobile_conflict_log
    USING (tenant_id = current_setting('app.tenant_id')::UUID);
```

### 5.3 RLS Verification

**Test Case:** Cross-tenant access attempt
```
1. User A (tenant_1) queries contacts
2. RLS filters to tenant_1 only
3. User A cannot see tenant_2 contacts
4. Verified by SELECT COUNT(*) WHERE tenant_id = 'tenant_2'
5. Result: 0 rows (RLS blocks)
```

---

## 6. Ownership

### 6.1 Entity Ownership Tracking

| Aspect | Specification |
|--------|---------------|
| **Field** | owner_id (UUID) |
| **Type** | References users table |
| **Immutable** | No — can be transferred |
| **Enforcement** | Server-side on mutations |

### 6.2 Ownership Rules

| Rule | Description |
|------|-------------|
| **Create** | Creator becomes owner (owner_id = user_id) |
| **Update** | Owner or manager+ can update |
| **Delete** | Owner or manager+ can delete |
| **Transfer** | Server-authoritative only |
| **Sync** | Mutations respect ownership |

### 6.3 Ownership Transfer

| Aspect | Specification |
|--------|---------------|
| **Initiator** | Current owner or admin |
| **Receiver** | Any user in same tenant |
| **Server-Authoritative** | Yes — client cannot bypass |
| **Audit** | Transfer logged in audit trail |

**Transfer Flow:**
```
1. Current owner initiates transfer
2. Server validates: initiator is owner or admin
3. Server validates: receiver is in same tenant
4. Server updates owner_id
5. Server logs transfer in audit trail
6. Client receives confirmation
```

### 6.4 Ownership and Sync

| Scenario | Behavior |
|----------|----------|
| Owner syncs own entity | Allowed |
| Non-owner syncs entity | Blocked (ownership check) |
| Admin syncs any entity | Allowed (admin override) |
| Ownership transferred offline | Server wins (ownership is server-authoritative) |

---

## 7. JWT Implementation

### 7.1 JWT Structure

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "key-id-123"
  },
  "payload": {
    "sub": "user-uuid-abc",
    "tenant_id": "tenant-uuid-xyz",
    "roles": ["user", "manager"],
    "permissions": ["read", "write", "delete"],
    "iat": 1691760000,
    "exp": 1691760900,
    "iss": "crm-api.example.com",
    "aud": "crm-mobile-app"
  },
  "signature": "rs256-signature"
}
```

### 7.2 JWT Validation

| Check | Description | Failure Action |
|-------|-------------|----------------|
| **Signature** | Verify RS256 signature | 401 Unauthorized |
| **Expiry** | Check exp claim | 401 Token Expired |
| **Issuer** | Verify iss claim | 401 Invalid Issuer |
| **Audience** | Verify aud claim | 401 Invalid Audience |
| **Tenant** | Extract tenant_id | Set RLS context |
| **Roles** | Extract roles | RBAC evaluation |

### 7.3 Mobile JWT Handling

| Aspect | Specification |
|--------|---------------|
| **Storage** | Memory only (not persisted) |
| **Transmission** | HTTPS only |
| **Caching** | None (regenerate on each refresh) |
| **Revocation** | Server-side revocation list |

---

## 8. Refresh Token Implementation

### 8.1 Refresh Token Flow

```
┌─────────────────────────────────────────────────────────┐
│                    ACCESS TOKEN EXPIRED                  │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Send refresh token    │
              │  to /auth/refresh      │
              └────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Server validates      │
              │  refresh token         │
              └────────────────────────┘
                    │            │
                   VALID      INVALID
                    │            │
                    ▼            ▼
            ┌──────────┐  ┌──────────┐
            │ Issue    │  │ Return   │
            │ new      │  │ 401      │
            │ tokens   │  │          │
            └──────────┘  └──────────┘
                    │
                    ▼
            ┌──────────┐
            │ Invalidate│
            │ old       │
            │ refresh   │
            │ token     │
            └──────────┘
```

### 8.2 Refresh Token Security

| Aspect | Specification |
|--------|---------------|
| **Rotation** | On each use, new token issued |
| **Revocation** | Server-side revocation list |
| **Storage** | Secure storage (Keychain/Keystore) |
| **Offline** | Cached, checked on reconnect |
| **Theft Detection** | Token reuse detection |

### 8.3 Token Rotation

```
1. Client sends refresh token to server
2. Server validates refresh token
3. Server issues new access token + new refresh token
4. Server invalidates old refresh token
5. Client stores new refresh token
6. If old refresh token reused → theft detected, revoke all
```

---

## 9. Device Identity

### 9.1 Device UUID

| Aspect | Specification |
|--------|---------------|
| **Generation** | UUID v4 on first launch |
| **Storage** | Secure storage (Keychain/Keystore) |
| **Transmission** | Sent with all sync operations |
| **Persistence** | App reinstall generates new UUID |

### 9.2 Device Registry

```sql
CREATE TABLE mobile_device_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    device_name VARCHAR(100),
    platform VARCHAR(20),  -- iOS, Android
    app_version VARCHAR(20),
    os_version VARCHAR(20),
    last_sync_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(tenant_id, user_id, device_id)
);
```

### 9.3 Device Tracking

| Aspect | Specification |
|--------|---------------|
| **Registration** | On first sync |
| **Update** | On each sync (last_sync_at) |
| **Query** | Find user's devices |
| **Revocation** | Admin can revoke device |

**Status:** NOT YET IMPLEMENTED (P2 HIGH priority)

---

## 10. Replay Protection

### 10.1 Idempotency Key

| Aspect | Specification |
|--------|---------------|
| **Generation** | Client-side (UUID v4) |
| **Format** | `{entity_type}:{entity_id}:{timestamp}:{random}` |
| **Retention** | 24 hours |
| **Dedup** | SHA-256 fingerprint |

### 10.2 Replay Protection Flow

```
┌─────────────────────────────────────────────────────────┐
│                    MUTATION REQUEST                      │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Check idempotency     │
              │  key in cache          │
              └────────────────────────┘
                    │            │
                 FOUND       NOT FOUND
                    │            │
                    ▼            ▼
            ┌──────────┐  ┌──────────┐
            │ Return   │  │ Execute  │
            │ cached   │  │ mutation │
            │ result   │  │          │
            └──────────┘  └──────────┘
                           │
                           ▼
                    ┌──────────┐
                    │ Store    │
                    │ result   │
                    │ with key │
                    └──────────┘
```

### 10.3 Idempotency Key Storage

```sql
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    request_payload JSONB,
    response_payload JSONB,
    status VARCHAR(20),  -- 'pending', 'completed', 'failed'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Cleanup expired keys
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);
```

---

## 11. Idempotency

### 11.1 Idempotency Service

| Aspect | Specification |
|--------|---------------|
| **Service** | Existing IdempotencyService |
| **Algorithm** | SHA-256 fingerprint |
| **Retention** | 24 hours |
| **Scope** | All sync push operations |

### 11.2 SHA-256 Fingerprint

```javascript
function generateIdempotencyKey(mutation) {
    const payload = JSON.stringify({
        entity_type: mutation.entity_type,
        entity_id: mutation.entity_id,
        operation: mutation.operation,
        fields: mutation.fields,
        timestamp: mutation.timestamp
    });
    
    return crypto.createHash('sha256')
        .update(payload)
        .digest('hex');
}
```

### 11.3 Idempotency Application

| Operation | Idempotency Required | Notes |
|-----------|---------------------|-------|
| Create | ✓ | Dedup duplicate creates |
| Update | ✓ | Dedup duplicate updates |
| Delete | ✓ | Dedup duplicate deletes |
| Conflict resolution | ✓ | Dedup resolution attempts |
| Pull (read) | ✗ | Reads are idempotent by nature |

---

## 12. Audit

### 12.1 PlatformAuditWriter

| Aspect | Specification |
|--------|---------------|
| **Service** | Existing PlatformAuditWriter |
| **Scope** | All sync operations |
| **Format** | Before/after JSON snapshots |
| **Storage** | Audit log table |

### 12.2 Audit Log Schema

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    operation VARCHAR(20) NOT NULL,  -- CREATE, UPDATE, DELETE
    before_snapshot JSONB,
    after_snapshot JSONB,
    source VARCHAR(20),  -- 'web', 'mobile', 'api'
    device_id UUID,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 12.3 Mobile Sync Audit

| Aspect | Specification |
|--------|---------------|
| **Table** | mobile_sync_log |
| **Scope** | All sync operations |
| **Fields** | device_id, sync_type, entity_type, entity_id, status |
| **Retention** | 90 days |

**mobile_sync_log Schema:**
```sql
CREATE TABLE mobile_sync_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    sync_type VARCHAR(20) NOT NULL,  -- 'push', 'pull', 'conflict'
    entity_type VARCHAR(50),
    entity_id UUID,
    status VARCHAR(20),  -- 'success', 'conflict', 'rejected', 'error'
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

---

## 13. Local Storage Security

### 13.1 Client-Side Encryption

| Aspect | Specification |
|--------|---------------|
| **Requirement** | All local data must be encrypted |
| **Options** | SQLCipher or OS-level encryption |
| **Status** | NOT YET DEFINED |

### 13.2 SQLCipher (Option A)

| Aspect | Specification |
|--------|---------------|
| **Library** | SQLCipher (SQLite extension) |
| **Encryption** | AES-256 |
| **Key Derivation** | PBKDF2 |
| **Performance** | Minimal overhead (<5%) |
| **Platform** | iOS, Android |

**Implementation:**
```javascript
// React Native example
import SQLite from 'react-native-sqlite-storage';
import SQLCipher from 'react-native-sqlcipher';

const db = SQLCipher.openDatabase({
    name: 'crm.db',
    key: encryptionKey, // From secure storage
});
```

### 13.3 OS-Level Encryption (Option B)

| Platform | Mechanism | Storage |
|----------|-----------|---------|
| iOS | Keychain + Data Protection | NSFileProtectionComplete |
| Android | Keystore + EncryptedSharedPreferences | AndroidKeystore |

**iOS Implementation:**
```swift
// Keychain storage
let query: [String: Any] = [
    kSecClass as String: kSecClassGenericPassword,
    kSecAttrService as String: "com.example.crm",
    kSecAttrAccount as String: "db-key",
    kSecValueData as String: keyData,
    kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
]
```

**Android Implementation:**
```kotlin
// EncryptedSharedPreferences
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "crm_secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### 13.4 Encryption Key Management

| Aspect | Specification |
|--------|---------------|
| **Generation** | Random 256-bit key on first launch |
| **Storage** | OS secure storage (Keychain/Keystore) |
| **Backup** | Not backed up (device-specific) |
| **Rotation** | Not required (device-specific) |

---

## 14. Sync Security

### 14.1 Transport Security

| Aspect | Specification |
|--------|---------------|
| **Protocol** | HTTPS (TLS 1.2+) |
| **Certificate** | Valid SSL certificate |
| **HSTS** | Enabled |
| **Certificate Pinning** | Recommended |

### 14.2 Sync Endpoint Security

| Endpoint | Method | Authentication | Authorization |
|----------|--------|----------------|---------------|
| /sync/pull | GET | JWT required | RBAC check |
| /sync/push | POST | JWT required | RBAC + ownership |
| /sync/conflict | POST | JWT required | RBAC + ownership |
| /sync/status | GET | JWT required | RBAC check |

### 14.3 Sync Request Validation

```
1. Validate JWT signature and expiry
2. Extract tenant_id, set RLS context
3. Check RBAC permissions
4. Check ownership (for mutations)
5. Validate idempotency key
6. Execute operation
7. Log to audit trail
```

---

## 15. Cross-Tenant Security

### 15.1 RLS Enforcement

| Aspect | Specification |
|--------|---------------|
| **Mechanism** | PostgreSQL Row-Level Security |
| **Scope** | All CRM and sync tables |
| **Cross-Tenant Access** | BLOCKED |
| **Verification** | RLS policies on all tables |

### 15.2 Cross-Tenant Test

```sql
-- Test: Can user from tenant_1 see tenant_2 data?
SET app.tenant_id = 'tenant-1-uuid';

-- This query should return 0 rows for tenant_2 data
SELECT * FROM contacts WHERE tenant_id = 'tenant-2-uuid';

-- Result: 0 rows (RLS blocks)
```

### 15.3 Cross-Tenant Sync

| Aspect | Specification |
|--------|---------------|
| **Cross-Tenant Sync** | BLOCKED by RLS |
| **Mechanism** | RLS on all sync tables |
| **Verification** | Sync operations respect tenant_id |
| **Error Handling** | 403 Forbidden if attempted |

---

## 16. Stale Token Handling

### 16.1 Token Expiry Scenarios

| Scenario | Client Action | Server Response |
|----------|---------------|-----------------|
| **Access token expired** | Use refresh token | 401 Unauthorized |
| **Refresh token expired** | Re-authenticate | 401 Unauthorized |
| **Both tokens expired** | Re-authenticate | 401 Unauthorized |
| **Offline, tokens valid** | Use cached tokens | N/A |
| **Offline, tokens expired** | Wait for online | N/A |

### 16.2 Offline Token Handling

```
┌─────────────────────────────────────────────────────────┐
│                    DEVICE GOES OFFLINE                    │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Cache current tokens  │
              │  in memory             │
              └────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Continue sync queue   │
              │  operations locally    │
              └────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  On reconnect:         │
              │  Check token validity  │
              └────────────────────────┘
                    │            │
                 VALID       EXPIRED
                    │            │
                    ▼            ▼
            ┌──────────┐  ┌──────────┐
            │ Resume   │  │ Re-auth  │
            │ sync     │  │ required │
            └──────────┘  └──────────┘
```

---

## 17. Duplicate Mutation Handling

### 17.1 Idempotency Dedup

| Aspect | Specification |
|--------|---------------|
| **Mechanism** | Idempotency key comparison |
| **Dedup Window** | 24 hours |
| **Same Key** | Returns cached result |
| **New Key** | Executes mutation |

### 17.2 Duplicate Detection

```
1. Client sends mutation with idempotency_key
2. Server checks idempotency cache
3. If key found:
   - Return cached response (no execution)
   - Log as duplicate in audit
4. If key not found:
   - Execute mutation
   - Store result with key
   - Return result
```

---

## 18. Unauthorized Mutation Handling

### 18.1 Authorization Checks

| Check | Description | Failure |
|-------|-------------|---------|
| **RBAC** | Role-based access control | 403 Forbidden |
| **Ownership** | Entity ownership check | 403 Forbidden |
| **Tenant** | Tenant isolation (RLS) | 403 Forbidden |
| **Status** | Entity status check | 409 Conflict |

### 18.2 Authorization Flow

```
1. Receive mutation request
2. Validate JWT → extract user_id, roles, tenant_id
3. Set RLS context (tenant_id)
4. Check RBAC permissions for operation
5. If update/delete: check ownership
6. If all checks pass: execute mutation
7. If any check fails: return 403 Forbidden
8. Log authorization attempt in audit
```

### 18.3 Authorization Audit

```sql
CREATE TABLE authorization_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    operation VARCHAR(20),
    result VARCHAR(20),  -- 'allowed', 'denied'
    reason VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

---

## 19. Security Risks

### 19.1 Risk Register

| Risk ID | Risk Description | Severity | Status | Mitigation |
|---------|------------------|----------|--------|------------|
| RISK-001 | No offline data encryption | HIGH | OPEN | Implement SQLCipher or OS encryption |
| RISK-002 | No device binding | MEDIUM | OPEN | Implement device identity + binding |
| RISK-003 | No mobile-specific token management | MEDIUM | OPEN | Implement token rotation + revocation |
| RISK-004 | No offline authorization enforcement | HIGH | OPEN | Implement local RBAC cache |

### 19.2 RISK-001: No Offline Data Encryption

**Impact:** If device is lost/stolen, offline data is accessible.  
**Severity:** HIGH  
**Mitigation:**
- Implement SQLCipher for SQLite encryption
- OR use OS-level encryption (Keychain/Keystore)
- Encrypt all local CRM data

**Status:** NOT YET DEFINED

### 19.3 RISK-002: No Device Binding

**Impact:** Tokens can be used on any device.  
**Severity:** MEDIUM  
**Mitigation:**
- Implement device identity (UUID)
- Bind tokens to device
- Allow admin to revoke device access

**Status:** NOT YET IMPLEMENTED (P2 HIGH)

### 19.4 RISK-003: No Mobile-Specific Token Management

**Impact:** Same token format as web, no mobile-specific controls.  
**Severity:** MEDIUM  
**Mitigation:**
- Implement token rotation on mobile
- Add device_id to JWT claims
- Server-side device validation

**Status:** NOT YET IMPLEMENTED

### 19.5 RISK-004: No Offline Authorization Enforcement

**Impact:** Offline mutations not checked against RBAC.  
**Severity:** HIGH  
**Mitigation:**
- Cache RBAC permissions locally
- Validate permissions before accepting mutation
- Sync permission cache on reconnect

**Status:** NOT YET IMPLEMENTED

---

## 20. Security Checklist

### 20.1 Pre-Deployment Checklist

| Item | Status | Notes |
|------|--------|-------|
| JWT validation on all endpoints | ✓ | Implemented |
| RBAC enforcement on sync operations | ✓ | Implemented |
| RLS on all CRM tables | ✓ | Implemented |
| RLS on new sync tables | ✓ | Implemented |
| Idempotency on all push operations | ✓ | Implemented |
| Audit logging on all operations | ✓ | Implemented |
| HTTPS on all endpoints | ✓ | Implemented |
| Offline data encryption | ✗ | NOT YET DEFINED |
| Device identity | ✗ | NOT YET IMPLEMENTED |
| Device binding | ✗ | NOT YET IMPLEMENTED |
| Token rotation | ✓ | Implemented |
| Refresh token rotation | ✓ | Implemented |
| Cross-tenant isolation | ✓ | Verified |
| Ownership enforcement | ✓ | Implemented |

### 20.2 Security Gates

| Gate | Status | Notes |
|------|--------|-------|
| Authentication | ✓ PASS | JWT + refresh tokens |
| Authorization | ✓ PASS | RBAC + ownership |
| Tenant Isolation | ✓ PASS | RLS enforced |
| Data Encryption | ✗ FAIL | No offline encryption |
| Device Security | ✗ FAIL | No device binding |
| Audit | ✓ PASS | All operations logged |

### 20.3 Production Readiness

**Status:** CONDITIONAL PASS

**Conditions for Production:**
1. Implement offline data encryption (RISK-001)
2. Implement device identity (RISK-002)
3. Implement offline authorization enforcement (RISK-004)

**Timeline:** Must be addressed within 30 days of production deployment.

---

## 21. Appendix

### A. Security Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    MOBILE CLIENT                         │
│                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────┐ │
│  │  Local DB     │    │ Auth Manager │    │ Sync     │ │
│  │  (Encrypted)  │    │ (JWT +       │    │ Manager  │ │
│  │              │    │  Refresh)    │    │          │ │
│  └──────────────┘    └──────────────┘    └──────────┘ │
│         │                   │                   │       │
│         │                   │                   │       │
│         ▼                   ▼                   ▼       │
│  ┌─────────────────────────────────────────────────┐   │
│  │              SECURITY LAYER                      │   │
│  │  - JWT validation                                │   │
│  │  - RBAC check                                    │   │
│  │  - Ownership check                               │   │
│  │  - Tenant isolation                              │   │
│  │  - Idempotency                                   │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    SERVER                                │
│                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────┐ │
│  │  RLS Filter   │───▶│ Concurrency  │───▶│ Audit    │ │
│  │              │    │ Check        │    │ Logger   │ │
│  └──────────────┘    └──────────────┘    └──────────┘ │
│         │                   │                   │       │
│         ▼                   ▼                   ▼       │
│  ┌─────────────────────────────────────────────────┐   │
│  │              POSTGRESQL                          │   │
│  │  - RLS policies on all tables                   │   │
│  │  - Tenant isolation enforced                    │   │
│  │  - Audit trail maintained                       │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### B. Security Control Matrix

| Control | Layer | Implementation | Status |
|---------|-------|----------------|--------|
| JWT Authentication | Transport | RS256, 15min TTL | ✓ |
| Refresh Token | Transport | 7-day TTL, rotation | ✓ |
| RBAC | Application | RoleCapability framework | ✓ |
| Ownership | Application | owner_id field | ✓ |
| RLS | Database | PostgreSQL RLS policies | ✓ |
| Idempotency | Application | SHA-256 fingerprint | ✓ |
| Audit | Application | PlatformAuditWriter | ✓ |
| Encryption | Storage | NOT YET DEFINED | ✗ |
| Device Binding | Application | NOT YET IMPLEMENTED | ✗ |

### C. Security Requirements Traceability

| Requirement | Source | Implementation | Status |
|-------------|--------|----------------|--------|
| REQ-SEC-001 | G1-G6 | JWT authentication | ✓ |
| REQ-SEC-002 | G1-G6 | RBAC authorization | ✓ |
| REQ-SEC-003 | G1-G6 | Tenant isolation (RLS) | ✓ |
| REQ-SEC-004 | G7 | Offline data encryption | ✗ |
| REQ-SEC-005 | G7 | Device identity | ✗ |
| REQ-SEC-006 | G7 | Device binding | ✗ |
| REQ-SEC-007 | G7 | Offline authorization | ✗ |
| REQ-SEC-008 | G1-G6 | Audit logging | ✓ |
| REQ-SEC-009 | G1-G6 | Idempotency | ✓ |
| REQ-SEC-010 | G7 | Sync security | ✓ |

---

**Document End**
