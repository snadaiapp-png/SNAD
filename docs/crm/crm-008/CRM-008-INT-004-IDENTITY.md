# CRM-008-INT-004: Identity Integration

> **Agent:** Agent 5 — Workflow Engine & Platform Integration
> **Task:** 4 — Identity Integration
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the identity integration for CRM-008 Team Management.

---

## 2. Organization Binding

| Check | Status |
|-------|--------|
| Tenant ID extracted from Authentication | ✅ |
| Tenant ID never accepted from request body | ✅ |
| Tenant ID never accepted from URL path | ✅ |
| All queries scoped by tenant_id | ✅ |

---

## 3. User Identity

| Check | Status |
|-------|--------|
| User ID extracted from Authentication | ✅ |
| User ID used as actorId for mutations | ✅ |
| User ID used as createdBy/updatedBy | ✅ |

---

## 4. RBAC Propagation

| Capability | Controllers | Use Cases |
|------------|-------------|-----------|
| CRM.TEAM.READ | TeamController | - |
| CRM.TEAM.WRITE | TeamController | - |
| CRM.SHIFT.READ | ShiftTemplateController, ShiftAssignmentController | - |
| CRM.SHIFT.MANAGE | ShiftTemplateController, ShiftAssignmentController | - |
| CRM.AVAILABILITY.READ | AvailabilityController | - |
| CRM.AVAILABILITY.MANAGE | AvailabilityController | - |
| CRM.SKILLS.READ | SkillController | - |
| CRM.SKILLS.MANAGE | SkillController | - |
| CRM.CAPACITY.READ | CapacityController | - |
| CRM.CAPACITY.MANAGE | CapacityController | - |
| CRM.WORKLOAD.READ | WorkloadController | - |
| CRM.WORKLOAD.MANAGE | WorkloadController | - |
| CRM.ASSIGNMENT.READ | ServiceAssignmentController | - |
| CRM.ASSIGNMENT.MANAGE | ServiceAssignmentController | - |

---

## 5. Tenant Context

| Port | Implementation | Usage |
|------|----------------|-------|
| TenantContextPort | SpringTenantContextAdapter | Extract tenant from SecurityContext |
| CorrelationContextPort | SpringCorrelationContextAdapter | Extract/generate correlation ID |

---

## 6. Integration Files

| File | Location |
|------|----------|
| TenantContextPort.java | integration/domain/ |
| CorrelationContextPort.java | integration/domain/ |

---

**Certification Date:** 2026-07-28
**Agent 5 Task 4 Status:** COMPLETE
