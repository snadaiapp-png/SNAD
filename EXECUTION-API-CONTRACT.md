# EXECUTION API CONTRACT

**Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**Version:** 1.0.0

---

## 1. Overview

The Execution API Contract defines the standard interfaces for accessing execution data across all SANAD modules.

---

## 2. Provider Interface

Every SANAD module SHALL implement the `ExecutionProvider` interface:

```typescript
interface ExecutionProvider {
  readonly moduleId: string;
  readonly moduleName: string;

  // Data Access
  getPrograms(): Promise<ExecutionProgram[]>;
  getProgram(programId: string): Promise<ExecutionProgram | null>;
  getGroups(programId: string): Promise<ExecutionGroup[]>;
  getGroup(programId: string, groupCode: string): Promise<ExecutionGroup | null>;
  getMilestones(programId: string, groupCode: string): Promise<ExecutionMilestone[]>;
  getTasks(programId: string, groupCode: string): Promise<ExecutionTask[]>;
  getEvidence(programId: string, groupCode: string, taskId: string): Promise<ExecutionEvidence[]>;
  getProgress(programId: string, groupCode: string): Promise<ExecutionProgress>;
  getProgramProgress(programId: string): Promise<ExecutionProgress>;
  getCertification(programId: string, groupCode: string): Promise<Certification | null>;

  // Mutation (optional)
  updateTaskStatus?(programId: string, groupCode: string, taskId: string, status: string): Promise<void>;
  submitForCertification?(programId: string, groupCode: string): Promise<void>;
}
```

---

## 3. REST API Endpoints

### 3.1 Programs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/execution/programs` | List all programs |
| `GET` | `/api/execution/programs/:id` | Get a specific program |

**Response:**
```json
{
  "id": "crm-program",
  "code": "CRM",
  "titleAr": "نظام CRM",
  "titleEn": "CRM System",
  "status": "APPROVED",
  "groups": [...]
}
```

### 3.2 Groups

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/execution/programs/:programId/groups` | List all groups |
| `GET` | `/api/execution/programs/:programId/groups/:code` | Get a specific group |

**Response:**
```json
{
  "id": "g1",
  "code": "G1",
  "titleAr": "قاعدة البيانات",
  "titleEn": "Database Foundation",
  "status": "APPROVED",
  "tasks": [...],
  "milestones": [...]
}
```

### 3.3 Tasks

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/execution/programs/:programId/groups/:code/tasks` | List all tasks |
| `GET` | `/api/execution/programs/:programId/groups/:code/tasks/:id` | Get a specific task |
| `PATCH` | `/api/execution/programs/:programId/groups/:code/tasks/:id` | Update task status |

**Response:**
```json
{
  "id": "g1-t01",
  "number": "G1-01",
  "nameAr": "إنشاء الجداول",
  "nameEn": "Create Tables",
  "status": "DONE",
  "type": "Database",
  "priority": "Critical",
  "evidence": [...]
}
```

### 3.4 Progress

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/execution/programs/:programId/progress` | Get program progress |
| `GET` | `/api/execution/programs/:programId/groups/:code/progress` | Get group progress |

**Response:**
```json
{
  "total": 37,
  "done": 37,
  "approved": 0,
  "inProgress": 0,
  "blocked": 0,
  "notStarted": 0,
  "needsReview": 0,
  "percentage": 100
}
```

### 3.5 Evidence

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/execution/programs/:programId/groups/:code/tasks/:taskId/evidence` | List evidence |

**Response:**
```json
[
  {
    "id": "ev-001",
    "type": "DATABASE_MIGRATION",
    "title": "V20260716_1__create_crm_tasks.sql",
    "description": "Flyway migration creating crm_tasks table",
    "hash": "a762678fef84eb4cb9bd65f7a2d5b1375835b3c5a2f9d8a95bd5ee62698aa5a2",
    "createdAt": "2026-07-16T10:00:00Z"
  }
]
```

### 3.6 Certification

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/execution/programs/:programId/groups/:code/certification` | Get certification |
| `POST` | `/api/execution/programs/:programId/groups/:code/certification` | Submit for certification |

**Response:**
```json
{
  "id": "cert-g1",
  "entityId": "g1",
  "entityType": "GROUP",
  "status": "CERTIFIED",
  "acceptanceCriteria": [
    { "id": "ac-1", "descriptionAr": "...", "passed": true }
  ],
  "certifiedAt": "2026-08-03T12:00:00Z",
  "certifiedBy": "governance-bot"
}
```

### 3.7 Validation

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/execution/programs/:programId/validate` | Validate entire program |
| `GET` | `/api/execution/programs/:programId/groups/:code/validate` | Validate a group |

**Response:**
```json
{
  "totalRules": 23,
  "passed": 23,
  "failed": 0,
  "allPassed": true,
  "results": [
    { "rule": "Progress calculation for G1", "passed": true, "message": "..." }
  ]
}
```

---

## 4. Error Responses

| Status | Error | Description |
|--------|-------|-------------|
| `400` | `VALIDATION_ERROR` | Request validation failed |
| `404` | `NOT_FOUND` | Entity not found |
| `409` | `CONFLICT` | State conflict (e.g., already certified) |
| `422` | `UNPROCESSABLE` | Business logic error |

**Error Response:**
```json
{
  "error": "NOT_FOUND",
  "message": "Group G99 not found in program CRM",
  "details": {
    "programId": "CRM",
    "groupCode": "G99"
  }
}
```

---

## 5. Authentication

All execution API endpoints require authentication:
- `Authorization: Bearer <token>` or session cookie
- Tenant isolation enforced via `tenant_id`

---

## 6. Versioning

| Version | Status | Description |
|---------|--------|-------------|
| `v1` | Current | Initial execution API |

Breaking changes require a new version. Additive changes are permitted within a version.
