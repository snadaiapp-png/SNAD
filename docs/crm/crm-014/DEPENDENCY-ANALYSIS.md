# Dependency Analysis — EXEC-PROMPT-CRM-014

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-014 — Wire leads tab to the API client
**Repository:** snadaiapp-png/SNAD
**Agent:** Portfolio Execution Agent

---

## 1. Dependency Map

```
EXEC-PROMPT-CRM-014 (Wire leads tab)
├── EXEC-PROMPT-CRM-005 (Execution Board data registry) ──── DONE ✅
├── CRM-002 (Customer and account foundation) ────────────── DONE ✅
│   ├── Leads backend API ────────────────────────────────── DONE ✅
│   ├── crmApi.leads() client method ─────────────────────── EXISTS ✅
│   └── Lead status enum ─────────────────────────────────── EXISTS ✅
├── EXEC-PROMPT-CRM-004 (Command Center route) ───────────── DONE ✅
├── EXEC-PROMPT-CRM-013 (i18n provider) ──────────────────── DONE ✅
├── CRM-010 (Customer 360 intelligence) ──────────────────── MERGED ✅
└── CRM-G1 (Database foundation) ─────────────────────────── COMPLETE ✅
```

---

## 2. Upstream Dependencies (What Must Be Done First)

| # | Dependency | Status | Evidence | Blocker? |
|---|------------|--------|----------|----------|
| 1 | EXEC-PROMPT-CRM-005 | DONE ✅ | Roadmap status | No |
| 2 | CRM-002 Leads backend | DONE ✅ | `crm_leads` table, API endpoints | No |
| 3 | `crmApi.leads()` | EXISTS ✅ | `apps/web/lib/api/crm.ts` | No |
| 4 | Command Center shell | DONE ✅ | `crm-command-center.tsx` | No |
| 5 | i18n provider | DONE ✅ | `crm-i18n.tsx` | No |
| 6 | CRM-010 intelligence | MERGED ✅ | PR #818 on main | No |

**All upstream dependencies satisfied. No blockers.**

---

## 3. Downstream Dependencies (What This Unblocks)

| # | Dependent Work Item | Depends On | Status |
|---|---------------------|------------|--------|
| 1 | EXEC-PROMPT-CRM-015 (Wire customers tab) | CRM-014 | Can run parallel |
| 2 | EXEC-PROMPT-CRM-016 (Wire contacts tab) | CRM-014 | Can run parallel |
| 3 | EXEC-PROMPT-CRM-017 (Wire customer-360 view) | CRM-015, CRM-016 | After 015+016 |
| 4 | EXEC-PROMPT-CRM-026 (CRM E2E test) | CRM-017 | After 017 |

---

## 4. Parallel Work Opportunities

CRM-014, CRM-015, and CRM-016 all depend only on CRM-005 (DONE) and can be worked in parallel:

```
CRM-005 ✅ ──┬──▶ CRM-014 (Leads tab)     ──┐
              ├──▶ CRM-015 (Customers tab)  ──┼──▶ CRM-017 ──▶ CRM-026
              └──▶ CRM-016 (Contacts tab)   ──┘
```

**Recommendation:** Implement CRM-014 first as the reference pattern, then parallelize CRM-015 and CRM-016.

---

## 5. External Dependencies

| # | Dependency | Type | Status | Risk |
|---|------------|------|--------|------|
| 1 | Backend leads API | Internal | DEPLOYED ✅ | None |
| 2 | PostgreSQL database | Infrastructure | RUNNING ✅ | None |
| 3 | GitHub Actions CI | Platform | ACTIVE ✅ | None |
| 4 | Vercel deployment | Platform | ACTIVE ✅ | None |

---

## 6. Risk Assessment

| # | Risk | Probability | Impact | Mitigation |
|---|------|-------------|--------|------------|
| 1 | `crmApi` methods incomplete | LOW | MEDIUM | Verify before starting |
| 2 | Lead status enum mismatch | LOW | LOW | Check backend/frontend alignment |
| 3 | i18n keys missing | LOW | LOW | Add translations as needed |
| 4 | Existing tests break | LOW | HIGH | Run full test suite |
| 5 | Performance issues with large lists | LOW | MEDIUM | Implement pagination |

---

## 7. Conclusion

**All dependencies are satisfied.** EXEC-PROMPT-CRM-014 may begin immediately with zero blockers.

The only prerequisite action is to verify that `crmApi.leads()`, `crmApi.createLead()`, `crmApi.changeLeadStatus()`, and `crmApi.convertLead()` exist in `apps/web/lib/api/crm.ts` and are complete.

---

**Analysis Authority:** Portfolio Execution Agent
**Date:** 2026-07-29
