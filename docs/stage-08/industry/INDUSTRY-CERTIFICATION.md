# SANAD Stage 08 — Industry Pack Certification

**Document ID:** `SANAD-ST08-IND-CERT-001`
**Track:** 8.4
**Date:** 2026-07-06

---

## 1. Certification Levels

| Level       | Requirements                                              |
|-------------|-----------------------------------------------------------|
| Draft       | Manifest valid; basic tests pass                          |
| Reviewed    | + Manual review by System Owner; sandbox install OK       |
| Certified   | + Penetration test; supply-chain provenance SLSA L3       |

---

## 2. Required Tests

* Manifest schema valid.
* Migrations up/down idempotent.
* Seed data loads without error.
* Demo data loads without error.
* Roles and permissions match catalog.
* Workflows execute end-to-end.
* Forms render in ar-SA and en-US.
* Reports produce expected output.
* Dashboards render in RTL and LTR.
* KPIs compute correctly.
* AI skills produce grounded output.
* No cross-tenant data leakage.
* No outbound network to non-allowlisted hosts.
