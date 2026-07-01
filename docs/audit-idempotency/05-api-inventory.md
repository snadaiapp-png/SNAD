# Stage 05 — API Inventory

## Audit Query API

### `GET /api/v1/audit-events`
- **Capability**: `AUDIT.READ`
- **Tenant-scoped**: Yes (tenant from verified TenantContext)
- **Pagination**: Yes (sort allowlist: occurredAt, action, resourceType, operation, outcome)
- **Filters**: actorUserId, action, resourceType, resourceId, outcome, correlationId, from (ISO-8601), to (ISO-8601)
- **Response**: `PageResponse<AuditEventResponse>`
- **Idempotency**: NOT_APPLICABLE (GET)

### `GET /api/v1/audit-events/{id}`
- **Capability**: `AUDIT.READ`
- **Tenant-scoped**: Yes
- **Response**: `AuditEventResponse`
- **Error**: 404 `SANAD-RES-001` if not found or belongs to another tenant
- **Idempotency**: NOT_APPLICABLE (GET)

### `GET /api/v1/audit-events/integrity`
- **Capability**: `AUDIT.INTEGRITY_VERIFY`
- **Tenant-scoped**: Yes
- **Response**: `VerificationResult` record with `valid`, `firstBrokenEventId`, `eventsChecked`, `calculatedHeadHash`, `storedHeadHash`
- **Idempotency**: NOT_APPLICABLE (GET)

## Idempotent Endpoints

The following endpoints accept the `Idempotency-Key` HTTP header for persistent idempotency enforcement:

| Endpoint | Method | Idempotency | Operation |
|----------|--------|-------------|-----------|
| `/api/v1/organizations` | POST | IDEMPOTENCY_OPTIONAL | ORGANIZATION.CREATE |

Future stages will extend idempotency to:
- User invitation/creation
- Membership assignment
- Role assignment
- Credential rotation command

## Error Codes (Stage 05)

| Code | HTTP Status | Title |
|------|-------------|-------|
| SANAD-AUDIT-001 | 404 | Audit event not found |
| SANAD-AUDIT-002 | 409 | Audit integrity check failed |
| SANAD-IDEMP-001 | 400 | Idempotency key required |
| SANAD-IDEMP-002 | 409 | Idempotency key conflict — payload mismatch |
| SANAD-IDEMP-003 | 409 | Idempotency key in progress — retry later |
| SANAD-IDEMP-004 | 410 | Idempotency record expired |

## Capabilities Seeded (V24)

| Code | Name | Description |
|------|------|-------------|
| AUDIT.READ | Read Audit Events | View audit events for the current tenant |
| AUDIT.INTEGRITY_VERIFY | Verify Audit Integrity | Recompute and verify the audit hash chain |
| AUDIT.EXPORT | Export Audit Events | Export audit events for compliance or legal hold |
| IDEMPOTENCY.ADMIN | Administer Idempotency | View and manage idempotency records for the tenant |
