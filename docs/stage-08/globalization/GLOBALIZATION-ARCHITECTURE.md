# SANAD Stage 08 — Globalization Architecture

**Document ID:** `SANAD-ST08-GLOBAL-001`
**Track:** 8.2 — Global Expansion and Localization Platform
**Date:** 2026-07-06

---

## 1. Scope

* Multi-language (Arabic primary, English secondary, extensible).
* RTL and LTR.
* Locale-aware formatting (dates, numbers, currencies, addresses).
* Multi-currency.
* Time zones.
* Calendars (Gregorian + Hijri support optional).
* Country-specific business identifiers.
* Tax localization.
* Invoice localization.
* Regional data residency.
* Country configuration.
* Regional feature flags.

---

## 2. Core Models

### 2.1 Country

```text
Country
  - code (ISO 3166-1 alpha-2)
  - name (localized)
  - default_locale
  - default_currency (ISO 4217)
  - tax_regime_id
  - residency_zone
  - feature_flags[]
```

### 2.2 Region

```text
Region
  - id
  - country_id
  - name (localized)
  - tax_override_id
```

### 2.3 Locale

```text
Locale
  - code (BCP 47, e.g., ar-SA, en-US)
  - direction (rtl|ltr)
  - date_format
  - number_format
  - currency_format
```

### 2.4 Currency

```text
Currency
  - code (ISO 4217)
  - symbol
  - decimal_places
  - fx_rate_source
```

### 2.5 Tax Regime

```text
TaxRegime
  - id
  - country_id
  - rules[] (configuration-first)
```

### 2.6 Legal Entity Type

```text
LegalEntityType
  - id
  - country_id
  - code
  - label (localized)
  - compliance_profile_id
```

### 2.7 Data Residency Zone

```text
DataResidencyZone
  - code
  - region
  - provider
  - compliance_profile_id
```

### 2.8 Regional Compliance Profile

```text
ComplianceProfile
  - id
  - data_residency_required
  - audit_retention_days
  - encryption_requirements
  - breach_notification_hours
```

---

## 3. Reference Markets

* Primary: Kingdom of Saudi Arabia (ar-SA, SAR, VAT 15%).
* GCC: UAE, Bahrain, Kuwait, Qatar, Oman.
* MENA: Egypt, Jordan.
* International: subsequent phases.

---

## 4. Configuration-First Principle

* No hardcoded tax rules.
* No hardcoded business identifiers.
* All country/region/locale/currency rules are configuration.
* Configuration is versioned and audited.

---

## 5. RTL/LTR Support

* All UI components support both directions.
* CSS logical properties (margin-inline-start, etc.).
* Icons direction-aware where applicable.
- Number formatting uses locale-aware digits (Arabic-Indic optional per locale).

---

## 6. Outputs

* `docs/stage-08/globalization/GLOBALIZATION-ARCHITECTURE.md` (this file)
* `docs/stage-08/globalization/COUNTRY-ONBOARDING-FRAMEWORK.md`
* `docs/stage-08/globalization/LOCALIZATION-STANDARDS.md`
* `docs/stage-08/globalization/DATA-RESIDENCY-MATRIX.md`
* `docs/stage-08/globalization/TAX-LOCALIZATION-FRAMEWORK.md`
