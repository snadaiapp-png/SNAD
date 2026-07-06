# SANAD Stage 08 — Segregation of Duties

**Document ID:** `SANAD-ST08-ENT-SOD-001`
**Track:** 8.6
**Date:** 2026-07-06

---

## 1. SoD Matrix

| Role A          | Role B            | Conflict? |
|-----------------|-------------------|-----------|
| Invoice creator | Invoice approver  | YES       |
| Payment initiator | Payment approver | YES       |
| Vendor creator  | Purchase order approver | YES  |
| User provisioner| Access reviewer   | YES       |
| Sales rep       | Sales manager     | No        |

---

## 2. Enforcement

* Conflict check at role assignment.
* Conflict check at workflow step.
* Override allowed only with PM approval + audit.
* Quarterly SoD review by Security Owner.
