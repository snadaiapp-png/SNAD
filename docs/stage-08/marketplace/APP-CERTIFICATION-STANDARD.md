# SANAD Stage 08 — App Certification Standard

**Document ID:** `SANAD-ST08-MKT-CERT-001`
**Track:** 8.3
**Date:** 2026-07-06

---

## 1. Certification Tiers

| Tier         | Requirements                                        |
|--------------|-----------------------------------------------------|
| Self-Service | Manifest validation, basic security scan            |
| Certified    | + Manual security review, sandbox testing           |
| Verified     | + Penetration test, supply-chain provenance SLSA L3 |

---

## 2. Required Checks

* Manifest schema valid.
* Permissions declared and minimal.
* Dependencies scanned (no Critical CVEs).
* Static analysis (SAST) clean.
* Signed package verified.
* Sandbox install successful.
* No outbound network to non-allowlisted hosts.
* No unrestricted database access.
* No unrestricted secret access.
* Tenant isolation verified.

---

## 3. Re-certification Triggers

* Major version bump.
* Security incident.
* Quarterly random sample (10% of certified apps).
