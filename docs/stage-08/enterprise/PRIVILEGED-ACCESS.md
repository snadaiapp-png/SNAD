# SANAD Stage 08 — Privileged Access

**Document ID:** `SANAD-ST08-ENT-PRIV-001`
**Track:** 8.6
**Date:** 2026-07-06

---

## 1. Privileged Roles

* Tenant Super Admin.
* SANAD Support (impersonation).
* SANAD SRE (infra access).
* SANAD DBA (DB access).

---

## 2. Controls

* Just-in-time elevation (no standing privilege).
* Time-bound access (max 4 hours).
* Approval workflow for elevation.
* Full session recording (commands, queries).
* Audit of elevated sessions (immutable).
* Quarterly recertification.

---

## 3. Break-Glass

* Emergency admin account.
* Stored in sealed envelope (HSM-backed).
* Use triggers:
  * IdP outage.
  * Lockout of all tenant admins.
* Use requires post-incident review within 24 hours.
