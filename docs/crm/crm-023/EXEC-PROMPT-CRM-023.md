# EXEC-PROMPT-CRM-023 — Wire Transfers and Employees Tabs

**Issue:** TBD (create when starting work)
**PR:** TBD
**Status:** AUTHORIZED
**Dependencies:** EXEC-PROMPT-CRM-021 (Wire tasks tab)
**Owner:** Frontend squad

---

## 1. OBJECTIVE

Wire the `transfers` and `employees` tabs in the CRM Command Center, replacing
the current `CrmEmptyState` rendering with functional UI components backed by
the G1 extension tables.

---

## 2. ACCEPTANCE CRITERIA

### 2.1 Transfers Tab

| Criterion | Status |
|-----------|--------|
| Lists account/opportunity transfer requests from `crm_transfers` table | ⬜ |
| Shows transfer status (pending, approved, rejected) | ⬜ |
| Provides accept/reject actions for authorized users | ⬜ |
| Displays transfer reason and timestamps | ⬜ |
| No longer renders `CrmEmptyState` | ⬜ |

### 2.2 Employees Tab

| Criterion | Status |
|-----------|--------|
| Lists CRM-assigned employees per tenant | ⬜ |
| Shows employee role and capability summary | ⬜ |
| Displays assignment status and history | ⬜ |
| No longer renders `CrmEmptyState` | ⬜ |

---

## 3. TECHNICAL REQUIREMENTS

### 3.1 Database Tables

The following tables are already created by G1 migrations:

| Table | Migration | Purpose |
|-------|-----------|---------|
| `crm_transfers` | `V20260717_6__create_crm_g1_extension_tables.sql` | Account/opportunity transfers |
| `crm_employees` | `V20260717_6__create_crm_g1_extension_tables.sql` | CRM employee assignments |

### 3.2 Backend API

Verify or create the following endpoints:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/crm/transfers` | GET | List transfer requests |
| `/api/crm/transfers/{id}/approve` | POST | Approve transfer |
| `/api/crm/transfers/{id}/reject` | POST | Reject transfer |
| `/api/crm/employees` | GET | List CRM employees |

### 3.3 Frontend Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `CrmTransfersTab` | `apps/web/app/crm/` | Transfers list with actions |
| `CrmEmployeesTab` | `apps/web/app/crm/` | Employees list with roles |

### 3.4 i18n

All user-facing strings must use the i18n system:

```typescript
const { t } = useTranslation();
// Use t('crm.transfers.title'), t('crm.employees.role'), etc.
```

---

## 4. IMPLEMENTATION PLAN

### Phase 1: Backend Verification
1. Verify `crm_transfers` table schema
2. Verify `crm_employees` table schema
3. Check if API endpoints exist
4. Create missing endpoints if needed

### Phase 2: Frontend Implementation
1. Create `CrmTransfersTab` component
2. Create `CrmEmployeesTab` component
3. Wire tabs to Command Center
4. Add i18n keys

### Phase 3: Testing
1. Unit tests for new components
2. Integration tests for API endpoints
3. Manual testing in browser

### Phase 4: Documentation
1. Update roadmap status
2. Create stage report
3. Update README if needed

---

## 5. DEPENDENCIES

| Dependency | Status | Notes |
|------------|--------|-------|
| CRM-021 (Wire tasks tab) | NOT_STARTED | Must be completed first |
| CRM-008 (G1 extension tables) | DONE | Tables already exist |
| CRM-017 (CRM entity wiring) | DONE | Backend ready |

---

## 6. CONSTRAINTS

1. Do not modify existing CRM tables
2. Do not break existing functionality
3. Follow SDS compliance rules
4. Use i18n for all user-facing strings
5. Write tests for new code

---

## 7. SUCCESS METRICS

| Metric | Target |
|--------|--------|
| Tabs render functional UI | 2/2 |
| API endpoints respond correctly | 4/4 |
| No `CrmEmptyState` rendering | ✅ |
| Tests pass | ✅ |
| i18n coverage | 100% |

---

**Created:** 2026-07-30
**Status:** AUTHORIZED — Ready to start after CRM-021 completes
