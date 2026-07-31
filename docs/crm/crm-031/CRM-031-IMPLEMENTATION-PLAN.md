# CRM-031 Implementation Plan

## Date: 2026-07-31
## Ticket: CRM-031 — Record formal production GO decision

---

## 1. Implementation Steps

### Step 1: Create GO Decision Record
- **File:** `docs/release/CRM-PRODUCTION-GO.md`
- **Action:** CREATE new file
- **Content:**
  - Production GO declaration header
  - Release SHA reference (`beb6e18c19c8fb5809c77f63de0344ff0430b576`)
  - Smoke evidence reference (`evidence/fullstack-remediation-010/REMEDIATION-EVIDENCE.md`)
  - Flyway-history assertion reference (`CrmFlywayHistoryAssertionTest.java`)
  - Branch protection evidence reference (`evidence/branch-protection-crm.json`)
  - Dependency chain summary (CRM-027, CRM-028, CRM-030 all DONE)
  - Signature block for project owner
  - Signature block for single external approver
  - Decision field: GO / NO-GO (initially NO-GO until signatures obtained)

### Step 2: Update Governance Drift Check Script
- **File:** `scripts/crm/governance-drift-check.sh`
- **Action:** ADD Section 16
- **Content:**
  - Check that `docs/release/CRM-PRODUCTION-GO.md` exists
  - If missing, add violation: "CRM-031 drift: CRM-PRODUCTION-GO.md does not exist"
  - If exists, verify it contains required references (SHA, smoke evidence, Flyway evidence)

### Step 3: Commit and Push
- **Branch:** `feature/crm-031-production-go-decision`
- **Commit message:** `feat(crm-031): create production GO decision record`
- **PR title:** `CRM-031: Record formal production GO decision`
- **Merge to:** `main`

---

## 2. Validation Steps

### Step 4: Verify GO Record Exists
```bash
test -f docs/release/CRM-PRODUCTION-GO.md && echo "PASS" || echo "FAIL"
```

### Step 5: Verify Evidence References
```bash
grep -q "beb6e18c" docs/release/CRM-PRODUCTION-GO.md && echo "SHA: PASS" || echo "SHA: FAIL"
grep -q "REMEDIATION-EVIDENCE" docs/release/CRM-PRODUCTION-GO.md && echo "SMOKE: PASS" || echo "SMOKE: FAIL"
grep -q "CrmFlywayHistoryAssertionTest" docs/release/CRM-PRODUCTION-GO.md && echo "FLYWAY: PASS" || echo "FLYWAY: FAIL"
```

### Step 6: Run Drift Check
```bash
bash scripts/crm/governance-drift-check.sh
```

### Step 7: CI Verification
- Ensure all existing CI checks pass (no code changes)
- Verify drift check validates GO record presence

---

## 3. Rollback Plan

If CRM-031 needs to be reverted:
- Delete `docs/release/CRM-PRODUCTION-GO.md`
- Remove Section 16 from drift check script
- Revert merge commit

No code, no database, no workflow changes — rollback is trivial.

---

## 4. Estimated Effort

| Step | Effort |
|------|--------|
| Step 1: Create GO record | 10 min |
| Step 2: Update drift check | 5 min |
| Step 3: Commit & push | 5 min |
| Step 4-7: Validation | 10 min |
| **Total** | **30 min** |
