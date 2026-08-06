# CRM-008-API-006: OpenAPI Documentation

> **Agent:** Agent 4 — REST API & RBAC Implementation
> **Task:** 6 — OpenAPI Documentation
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the API documentation for CRM-008 Team Management.

---

## 2. API Base Path

```
/api/v1/crm
```

---

## 3. Authentication

All endpoints require JWT authentication. The authentication context must include:
- `tenant_id`: UUID of the authenticated tenant
- `user_id`: UUID of the authenticated user

---

## 4. Endpoint Groups

### Teams
- **Path**: `/teams`
- **Capabilities**: CRM.TEAM.READ, CRM.TEAM.WRITE
- **Operations**: List, Get, Create, Update, Archive, Activate

### Shift Templates
- **Path**: `/shift-templates`
- **Capabilities**: CRM.SHIFT.READ, CRM.SHIFT.MANAGE
- **Operations**: List, Get, Create, Update, Publish, Cancel

### Shift Assignments
- **Path**: `/shift-assignments`
- **Capabilities**: CRM.SHIFT.READ, CRM.SHIFT.MANAGE
- **Operations**: List, Create, Update, Cancel

### Availability
- **Path**: `/availability`
- **Capabilities**: CRM.AVAILABILITY.READ, CRM.AVAILABILITY.MANAGE
- **Operations**: Query, Submit, Approve, Reject, Delete

### Skills
- **Path**: `/skills`
- **Capabilities**: CRM.SKILLS.READ, CRM.SKILLS.MANAGE
- **Operations**: List, Register, Update, Delete

### Capacity
- **Path**: `/capacity`
- **Capabilities**: CRM.CAPACITY.READ, CRM.CAPACITY.MANAGE
- **Operations**: List, Get, Create, Adjust, Forecast

### Workload
- **Path**: `/workload`
- **Capabilities**: CRM.WORKLOAD.READ, CRM.WORKLOAD.MANAGE
- **Operations**: List, Get Hours, Assign, Reassign, Release

### Service Assignments
- **Path**: `/service-assignments`
- **Capabilities**: CRM.ASSIGNMENT.READ, CRM.ASSIGNMENT.MANAGE
- **Operations**: List, Get, Assign, Reassign, Complete, Cancel

---

## 5. Error Responses

| Code | Description |
|------|-------------|
| 400 | Bad Request (validation failure) |
| 401 | Unauthorized (missing/invalid auth) |
| 403 | Forbidden (insufficient capabilities) |
| 404 | Not Found |
| 409 | Conflict (optimistic locking, uniqueness) |
| 500 | Internal Server Error |

---

## 6. Response Format

All responses use snake_case keys:
```json
{
  "id": "uuid",
  "tenant_id": "uuid",
  "display_name": "string",
  "status": "ACTIVE",
  "created_at": "2026-07-28T00:00:00Z",
  "version": 1
}
```

---

**Certification Date:** 2026-07-28
**Agent 4 Task 6 Status:** COMPLETE
