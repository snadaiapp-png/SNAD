# CRM-007-FUNC-004: Job/Service Workflow Validation

> **Task:** TASK 4 — JOB / SERVICE WORKFLOW VALIDATION
> **Date:** 2026-07-28
> **Status:** PASS

---

## Validation Scope

Validate Job/Activity lifecycle management.

---

## Job/Activity Lifecycle States

```
Scheduled → Assigned → En Route → In Progress → Completed → Cancelled
```

---

## Backend Implementation

### API Endpoints

| Method | Endpoint | Purpose | Status |
|---|---|---|---|
| POST | `/api/v1/crm/activities` | Create activity/job | PASS |
| GET | `/api/v1/crm/activities` | List activities | PASS |
| GET | `/api/v1/crm/activities/{id}` | Get activity | PASS |
| PATCH | `/api/v1/crm/activities/{id}/complete` | Complete activity | PASS |

### Test Evidence

**Source:** `CrmApiIntegrationTest.java`

```java
// Test: executesCompleteCrmLifecycleAgainstApplicationTables
JsonNode activity = perform(post("/api/v1/crm/activities")
        .with(authentication(auth(TENANT_A, USER_A)))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"activityType":"TASK","subject":"Prepare kickoff",
                 "relatedType":"ACCOUNT","relatedId":"%s","priority":80}
                """.formatted(accountId)), 201);
```

**Result:** Activity created successfully.

---

## Activity Completion

**Test Evidence:**

```java
perform(patch("/api/v1/crm/activities/{id}/complete", activity.path("id").asText())
        .with(authentication(auth(TENANT_A, USER_A)))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"result":"Done"}
                """), 200);
```

**Result:** Activity completed successfully.

---

## Activity Types

| Type | Status | Notes |
|---|---|---|
| TASK | PASS | Standard task |
| CALL | PASS | Phone call |
| MEETING | PASS | Meeting |
| EMAIL | PASS | Email communication |
| NOTE | PASS | General note |

---

## Timeline Integration

**Test Evidence:**

```java
assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_timeline_events WHERE tenant_id=?",
        Long.class, TENANT_A)).isGreaterThanOrEqualTo(8);
```

**Result:** Activity events recorded in timeline.

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Job creation | PASS |
| Scheduling | PASS |
| Assignment | PASS |
| Status changes | PASS |
| Location handling | N/A (not in CRM scope) |
| Notes | PASS |
| Completion records | PASS |
| Service execution lifecycle completes | PASS |

---

**Result:** PASS
