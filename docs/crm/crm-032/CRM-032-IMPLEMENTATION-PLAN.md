# CRM-032 Implementation Plan

## Date: 2026-07-31
## Ticket: CRM-032 — Penetration test closure for CRM surface

---

## 1. Implementation Steps

### Step 1: Define Pentest Scope
- **Action:** Create pentest scope document
- **Content:**
  - API endpoints to test (authentication, CRUD, RBAC, tenant isolation)
  - UI routes to test (XSS, CSRF, clickjacking)
  - Test cases for OWASP Top 10
  - Multi-tenant isolation bypass attempts
  - RBAC escalation attempts
  - Rate limiting bypass attempts

### Step 2: Execute Penetration Test
- **Action:** Perform pentest against CRM surface
- **Environment:** Production-like (Vercel + Render)
- **Tools:** OWASP ZAP, Burp Suite, manual testing
- **Scope:**
  - API: Authentication, authorization, injection, XSS, CSRF
  - UI: XSS, CSRF, clickjacking, sensitive data exposure
  - Infrastructure: TLS, headers, CORS

### Step 3: Create Pentest Report
- **File:** `docs/audit/CRM-PENTEST-REPORT.md`
- **Action:** CREATE new file
- **Content:**
  - Executive summary
  - Test methodology
  - Findings by severity (Critical, High, Medium, Low, Informational)
  - Evidence for each finding
  - Remediation recommendations
  - Risk acceptance documentation (if applicable)

### Step 4: Remediate or Risk-Accept Findings
- **Action:** Address all Critical and High findings
- **Options:**
  - Code remediation (if finding requires code change)
  - Configuration change (if finding requires config change)
  - Risk acceptance (if finding is acceptable with justification)
- **Approval:** Project owner must sign off on risk acceptances

### Step 5: Update Governance Drift Check
- **File:** `scripts/crm/governance-drift-check.sh`
- **Action:** ADD Section 17
- **Content:**
  - Check that `docs/audit/CRM-PENTEST-REPORT.md` exists
  - If exists, verify no open Critical findings
  - If Critical finding exists, add violation

### Step 6: Commit and Push
- **Branch:** `feature/crm-032-pentest-closure`
- **Commit message:** `feat(crm-032): add penetration test report and closure`
- **PR title:** `CRM-032: Penetration test closure for CRM surface`
- **Merge to:** `main`

---

## 2. Validation Steps

### Step 7: Verify Pentest Report Exists
```bash
test -f docs/audit/CRM-PENTEST-REPORT.md && echo "PASS" || echo "FAIL"
```

### Step 8: Verify No Open Critical Findings
```bash
grep -qi "critical.*open\|open.*critical" docs/audit/CRM-PENTEST-REPORT.md && echo "FAIL" || echo "PASS"
```

### Step 9: Run Drift Check
```bash
bash scripts/crm/governance-drift-check.sh
```

### Step 10: CI Verification
- Ensure all existing CI checks pass
- Verify drift check validates pentest report

---

## 3. Rollback Plan

If CRM-032 needs to be reverted:
- Delete `docs/audit/CRM-PENTEST-REPORT.md`
- Remove Section 17 from drift check script
- Revert merge commit

If code remediation was applied:
- Revert remediation commits
- Re-run CI to verify no regression

---

## 4. Estimated Effort

| Step | Effort |
|------|--------|
| Step 1: Define pentest scope | 2 hours |
| Step 2: Execute penetration test | 8-16 hours |
| Step 3: Create pentest report | 4 hours |
| Step 4: Remediate or risk-accept | 4-8 hours (depends on findings) |
| Step 5: Update drift check | 30 min |
| Step 6: Commit & push | 30 min |
| Step 7-10: Validation | 1 hour |
| **Total** | **20-32 hours** |

---

## 5. Pentest Report Template

```markdown
# CRM Penetration Test Report

## Executive Summary
- Test dates
- Scope
- Overall risk rating
- Critical/High findings count

## Methodology
- Tools used
- Test cases executed
- Environment

## Findings

### Critical Findings
| ID | Title | Status | Evidence |
|----|-------|--------|----------|

### High Findings
| ID | Title | Status | Evidence |
|----|-------|--------|----------|

### Medium Findings
| ID | Title | Status | Evidence |
|----|-------|--------|----------|

### Low Findings
| ID | Title | Status | Evidence |
|----|-------|--------|----------|

### Informational
| ID | Title | Status | Evidence |
|----|-------|--------|----------|

## Remediation
- Actions taken
- Code changes (if any)
- Risk acceptances (if any)

## Sign-off
- Project owner approval
- Security squad approval
```
