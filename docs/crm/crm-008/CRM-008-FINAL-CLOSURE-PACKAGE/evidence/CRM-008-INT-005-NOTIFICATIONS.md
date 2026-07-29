# CRM-008-INT-005: Notification Integration

> **Agent:** Agent 5 — Workflow Engine & Platform Integration
> **Task:** 5 — Notification Integration
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the notification integration for CRM-008 Team Management.

---

## 2. Notification Types

### Assignment Notifications (4)

| Type | Recipient | Trigger |
|------|-----------|---------|
| shift.assigned_to_staff | Staff member | Shift assigned |
| shift.assigned_to_team | Team manager | Shift assigned |
| workload.assigned | Staff member | Work assigned |
| service.assigned_to_team | Team manager | Service assigned |

### Shift Change Notifications (3)

| Type | Recipient | Trigger |
|------|-----------|---------|
| shift.cancelled | Staff member | Shift cancelled |
| shift.updated | Staff member | Shift updated |
| shift_template.published | Staff member | Template activated |

### Availability Notifications (3)

| Type | Recipient | Trigger |
|------|-----------|---------|
| availability.submitted | Team manager | Availability submitted |
| availability.approved | Staff member | Availability approved |
| availability.rejected | Staff member | Availability rejected |

### Capacity Notifications (2)

| Type | Recipient | Trigger |
|------|-----------|---------|
| capacity.alert | Team manager | Capacity exceeds threshold |
| capacity.forecast_alert | Team manager | Forecasted capacity exceeded |

### Workload Notifications (2)

| Type | Recipient | Trigger |
|------|-----------|---------|
| workload.reassigned | Staff member | Work reassigned |
| workload.released | Staff member | Work released |

### Service Assignment Notifications (2)

| Type | Recipient | Trigger |
|------|-----------|---------|
| service.reassigned | Team manager | Service reassigned |
| service.completed | Team manager | Service completed |

---

## 3. Notification Port

| Port | Description |
|------|-------------|
| TeamManagementNotificationPort | Outbound port for sending notifications |

### Port Method

```java
void send(UUID tenantId,
          String notificationType,
          UUID recipientUserId,
          String subjectType,
          UUID subjectId,
          Map<String, Object> payload,
          Instant occurredAt);
```

---

## 4. Adapter Implementation

| Adapter | Description |
|---------|-------------|
| NoOpTeamManagementNotificationAdapter | Logs notifications (dev/test) |

Production adapter to be implemented when notification service is available.

---

## 5. Total Notification Count

| Category | Count |
|----------|-------|
| Assignment | 4 |
| Shift Changes | 3 |
| Availability | 3 |
| Capacity | 2 |
| Workload | 2 |
| Service | 2 |
| **Total** | **16** |

---

## 6. Integration Files

| File | Location |
|------|----------|
| TeamManagementNotificationPort.java | ownership/integration/ |
| TeamManagementNotificationTypes.java | ownership/integration/ |
| NoOpTeamManagementNotificationAdapter.java | ownership/integration/ |

---

**Certification Date:** 2026-07-28
**Agent 5 Task 5 Status:** COMPLETE
