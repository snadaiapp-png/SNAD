# SANAD Stage 08 — Country Onboarding Framework

**Document ID:** `SANAD-ST08-GLOBAL-COUNTRY-001`
**Track:** 8.2
**Date:** 2026-07-06

---

## 1. Onboarding Workflow

1. Market assessment (PM).
2. Country configuration file drafted (System Owner).
3. Legal review of tax regime and compliance profile (Legal Counsel).
4. Localization pack prepared (translation team).
5. Data residency zone assigned (Infra Owner).
6. Feature flags set (PM).
7. Pilot tenant onboarded.
8. GA after pilot success criteria met.

---

## 2. Country Configuration File Schema

```yaml
country:
  code: SA
  name:
    ar: المملكة العربية السعودية
    en: Kingdom of Saudi Arabia
  default_locale: ar-SA
  default_currency: SAR
  tax_regime:
    type: VAT
    rate: 0.15
    rules:
      - standard_rate: 0.15
      - zero_rated: [export, education, healthcare]
      - exempt: [financial_services]
  residency_zone: KSA
  legal_entity_types:
    - code: LLC
      label_ar: شركة ذات مسؤولية محدودة
      label_en: Limited Liability Company
  feature_flags:
    - hijri_calendar: true
    - arabic_invoice: true
    - vat_invoice: true
```

---

## 3. Acceptance Criteria

* Configuration file passes schema validation.
* Localization pack covers all UI strings.
* Tax regime validated against authoritative source.
* Data residency zone operational.
* Pilot tenant onboarded with zero P0 issues.
