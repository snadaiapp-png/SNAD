# SANAD Stage 08 — Localization Standards

**Document ID:** `SANAD-ST08-GLOBAL-LOC-001`
**Track:** 8.2
**Date:** 2026-07-06

---

## 1. Translation Workflow

* Source strings authored in English in code (`t('key')`).
* Translations stored in JSON per locale (`/locales/ar-SA.json`).
* Translations reviewed by native speakers.
* Missing translations fall back to English with a console warning in dev.

---

## 2. Supported Locales (Initial)

| Locale  | Language          | Direction |
|---------|-------------------|-----------|
| ar-SA   | Arabic (Saudi)    | RTL       |
| en-US   | English (US)      | LTR       |

Planned: ar-AE, ar-EG, en-GB.

---

## 3. Format Standards

### 3.1 Date

* ar-SA: `yyyy/MM/dd` (Hijri optional: `hY/hM/hD`).
* en-US: `MM/dd/yyyy`.

### 3.2 Number

* ar-SA: Arabic-Indic digits optional; default Western Arabic.
* Group separator: per locale.

### 3.3 Currency

* Format: symbol placement per locale.
* Symbol: SAR ر.س, USD $, AED د.إ.

### 3.4 Address

* ar-SA: country, region, city, district, street, building, postal.
* en-US: street, building, city, state, postal, country.

---

## 4. String Authoring Rules

* No hardcoded user-facing strings in code.
* No string concatenation; use placeholders (`{name}`).
* Pluralization via ICU MessageFormat.
* No emojis in source strings.
