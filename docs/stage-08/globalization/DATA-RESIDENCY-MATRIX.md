# SANAD Stage 08 — Data Residency Matrix

**Document ID:** `SANAD-ST08-GLOBAL-RESIDENCY-001`
**Track:** 8.2
**Date:** 2026-07-06

---

## 1. Matrix

| Country | Residency Zone | Provider  | PII Storage | Backup Region | Cross-Border Allowed? |
|---------|----------------|-----------|-------------|---------------|------------------------|
| SA      | KSA            | Local CSP | KSA only    | GCC           | No (without consent)   |
| AE      | GCC            | AWS BH    | GCC         | EU            | Yes (with consent)     |
| BH      | GCC            | AWS BH    | GCC         | EU            | Yes                    |
| KW      | GCC            | AWS BH    | GCC         | EU            | Yes (with consent)     |
| QA      | GCC            | AWS BH    | GCC         | EU            | Yes (with consent)     |
| OM      | GCC            | AWS BH    | GCC         | EU            | Yes                    |
| EG      | MENA           | AWS BH    | GCC         | EU            | Yes (with consent)     |
| JO      | MENA           | AWS BH    | GCC         | EU            | Yes (with consent)     |
| US      | US             | AWS VA    | US          | US-WEST       | Yes                    |
| EU      | EU             | AWS FRA   | EU          | EU-WEST       | No (GDPR)              |

---

## 2. Residency Enforcement

* Tenant selects country at onboarding.
* Tenant data persisted only in residency zone.
* Cross-zone access blocked at storage layer.
* Cross-region backup allowed only where matrix permits.

---

## 3. Compliance Notes

* Saudi PDPL: data must remain in KSA unless explicit consent.
* EU GDPR: data must remain in EU/EEA or adequate jurisdictions.
* UAE PDPL: data subject to local regulations.
