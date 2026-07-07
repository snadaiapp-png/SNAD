# SNAD Brand Governance

> **Executive owner:** SNAD Executive Office
> **Custodian:** SNAD Design System team (`design@snad.ai`)
> **Effective date:** 2026-07-07
> **Version:** 2.0.0

This document is the **single source of truth** for the SNAD brand identity.
Every product surface, marketing asset, partner integration, and printed
collateral that carries the SNAD name must conform to the rules below.
Deviations require written approval from the brand custodian — see
[Brand Change Process](./BRAND_CHANGE_PROCESS.md).

## 1. Official brand identity (immutable)

| Property | Value | Notes |
| --- | --- | --- |
| Brand name (Latin) | `SNAD` | Always uppercase. Never `SANAD`, `Sanad`, `Snad`, or `SNAD ERP System` as the primary brand. |
| Brand name (Arabic) | `سند` | Always rendered in Arabic script in Arabic copy. |
| Primary color | `#0E3D38` | Dark Petroleum Green |
| Accent color | `#D4AF37` | Royal Polished Gold |
| Tagline (Latin) | `Trust, engineered.` | Optional. Use only when approved by marketing. |
| Tagline (Arabic) | `الثقة، هندسةً.` | Optional. Use only when approved by marketing. |
| Primary typeface (Arabic) | `Tajawal` | Fallbacks: IBM Plex Sans Arabic → Noto Sans Arabic → Tahoma |
| Primary typeface (Latin) | `Inter` | Fallbacks: Noto Sans → Arial |
| Voice | Confident, precise, trustworthy | Never slang, never hyperbole, never fear-based marketing. |

## 2. Approved name usage

| ✅ Correct | ❌ Incorrect | Why |
| --- | --- | --- |
| SNAD | SANAD | Wrong letter order — confuses Arabic transliteration. |
| SNAD | Sanad | Brand is rendered uppercase in Latin script. |
| SNAD | Snad | Brand is rendered uppercase in Latin script. |
| سند | سناد | Arabic spelling is سند (3 letters). |
| SNAD | SNAD ERP System | "ERP System" is a descriptor, not part of the brand name. Use it only as a tagline or product category, never as the primary logo lockup. |
| SNAD Platform | The SNAD Platform | "SNAD" alone is preferred; "Platform" is acceptable as a product category. |

## 3. Approved color usage

### Primary color — Dark Petroleum Green `#0E3D38`

* **Required for** all primary call-to-action buttons, brand text, app
  headers, and key brand moments.
* **Forbidden** as a body text color on light backgrounds (use
  `--snad-color-text-primary` instead — petroleum is reserved for brand
  emphasis).
* **Minimum contrast**: must achieve WCAG 2.2 AA (4.5:1) against any text
  placed on top of it. White text on `#0E3D38` achieves 11.2:1 — safe.

### Accent color — Royal Polished Gold `#D4AF37`

* **Required for** awards, premium markers, decorative emphasis, and
  limited-time highlight strips.
* **Forbidden** as a body text color on light backgrounds — gold against
  ivory fails WCAG AA (3.4:1, below the 4.5:1 threshold).
* **Forbidden** as a primary action color — gold signals "premium /
  decorative" and would dilute the petroleum CTA hierarchy.
* **Maximum coverage** in any single view: 8% of viewport area. Gold is a
  spice, not a sauce.

## 4. Prohibited usage

1. **Do not** recolor the logo. The logo must always appear in
   petroleum-green-on-light or white-on-petroleum-green. Never gold logo,
   never gradient logo, never embossed logo.
2. **Do not** stretch, skew, rotate, or rearrange the logo lockup.
3. **Do not** place the logo on busy photographic backgrounds without the
   approved clear-space ring (see [Logo Usage](./LOGO_USAGE.md)).
4. **Do not** substitute the official colors with "close enough" teal,
   forest, or khaki values. The hex codes `#0E3D38` and `#D4AF37` are
   non-negotiable.
5. **Do not** use the brand name as a verb ("SNAD your workflow"). The
   brand is a noun.
6. **Do not** combine the SNAD brand with another brand's primary color in
   a co-marketed asset without written approval.
7. **Do not** apply filters, drop shadows, glow effects, or bevels to the
   logo. The logo is a flat 2-color mark.
8. **Do not** animate the logo beyond a single 220ms fade-in. No spin, no
   bounce, no pulse.

## 5. Approved variations

| Variation | When to use |
| --- | --- |
| Primary lockup (Latin `SNAD` + Arabic `سند` stacked) | Default for app headers, marketing homepages, pitch decks. |
| Horizontal lockup (Latin `SNAD | سند` inline) | Tight horizontal spaces: nav bars, email signatures. |
| Mark-only (the petroleum/gold glyph without wordmark) | App icons, favicons, social avatars — never as a standalone brand reference in body copy. |
| White reverse (single-color white) | Dark backgrounds (petroleum, charcoal, photo overlays). |
| Monochrome black | Fax, single-color print, partner co-branded PDFs. |

## 6. Brand change request process

See [BRAND_CHANGE_PROCESS.md](./BRAND_CHANGE_PROCESS.md) for the formal
workflow. Summary:

1. Open a `brand-change-request` issue in the SNAD repo.
2. Attach a written rationale and at least 3 visual mockups.
3. Custodian reviews within 5 business days.
4. Executive sign-off required for any change to the primary color, accent
   color, or logo lockup.
5. Approved changes ship in the next SDS major version.

## 7. Enforcement

* **CI gate**: `scripts/ci/check-design-system-compliance.py` fails any PR
  that introduces hardcoded colors outside the allowlisted source-of-truth
  files.
* **Visual regression**: every component ships with light + dark theme
  snapshots (see [VISUAL_TESTING.md](./VISUAL_TESTING.md)).
* **Quarterly audit**: the design system team runs a manual brand audit
  every quarter and files issues for any drift.

## 8. Contacts

| Role | Owner |
| --- | --- |
| Brand custodian | SNAD Executive Office |
| Design system maintainer | `design@snad.ai` |
| Compliance automation | `scripts/ci/check-design-system-compliance.py` |
