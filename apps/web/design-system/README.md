# SNAD Design System (SDS)

> **Official source of truth** for the visual language of the SNAD | سند platform.

[![SDS version](https://img.shields.io/badge/SDS-2.0.0-0E3D38)](./tokens/tokens.json)
[![Brand primary](https://img.shields.io/badge/primary-%230E3D38-0E3D38)](./tokens/theme.css)
[![Brand accent](https://img.shields.io/badge/accent-%23D4AF37-D4AF37)](./tokens/theme.css)

The SNAD Design System (SDS) is a token-driven, theme-aware foundation shared
by every surface of the SNAD web application. It is the **mandatory** starting
point for all UI work — no component may ship with hardcoded colors, fonts,
spacing, or shadows.

## Brand identity (immutable)

| Property | Value | Notes |
| --- | --- | --- |
| Official name (Latin) | `SNAD` | Always uppercase, never `SANAD` or `Snad`. |
| Official name (Arabic) | `سند` | Use this in Arabic copy and Arabic UI surfaces. |
| Primary color | `#0E3D38` | Dark Petroleum Green |
| Accent color | `#D4AF37` | Royal Polished Gold |
| Primary font (Arabic) | `Tajawal` | Fallbacks: IBM Plex Sans Arabic → Noto Sans Arabic → Tahoma |
| Primary font (Latin) | `Inter` | Fallbacks: Noto Sans → Arial |

## Directory map

```
design-system/
├── README.md                  ← you are here
├── tokens/
│   ├── tokens.json            ← machine-readable source of truth
│   └── theme.css              ← CSS custom properties (light + dark + OS fallback)
├── themes/
│   ├── light.css              ← light-only overrides (print, email)
│   └── dark.css               ← dark-only overrides (embedded dark surfaces)
├── components/                ← component CSS modules (Phase 2)
└── documentation/             ← long-form docs
    ├── BRAND_GOVERNANCE.md
    ├── DESIGN_SYSTEM.md
    ├── DESIGN_TOKENS.md
    ├── COLOR_SYSTEM.md
    ├── TYPOGRAPHY.md
    ├── COMPONENT_USAGE.md
    ├── LOGO_USAGE.md
    ├── ACCESSIBILITY.md
    ├── RTL_LTR_GUIDE.md
    ├── VISUAL_TESTING.md
    └── BRAND_CHANGE_PROCESS.md
```

## Quick start

```css
/* In your CSS module: */
@import "../../design-system/tokens/theme.css";

.card {
  background: var(--snad-color-surface-primary);
  border: 1px solid var(--snad-color-border-default);
  border-radius: var(--snad-radius-lg);
  padding: var(--snad-space-6);
  box-shadow: var(--snad-shadow-sm);
  color: var(--snad-color-text-primary);
  font: var(--snad-text-body);
}
```

```tsx
// Inline styles must also reference tokens — never raw colors:
<p style={{ color: "var(--snad-color-text-muted)" }}>
  {t("noResults")}
</p>
```

## Compliance

Every PR that touches `.tsx`, `.ts`, or `.css` files under `apps/web/` is
checked by **`scripts/ci/check-design-system-compliance.py`**. The script
detects:

* Hardcoded `#XXXXXX` / `#XXX` hex colors
* Hardcoded `rgb()` / `rgba()` / `hsl()` / `hsla()` values
* Hardcoded `font-family` declarations not referencing `var(--snad-font-*)`

Raw color values are permitted **only** in:

* `apps/web/design-system/tokens/theme.css`
* `apps/web/design-system/tokens/tokens.json`
* `apps/web/app/snad-tokens.css` (legacy — will be migrated in a future phase)

Run locally:

```bash
python3 scripts/ci/check-design-system-compliance.py
```

## Versioning

SDS follows **semantic versioning**. Any change to a token *value* (even a
one-character hex change) is a **major** bump. Adding new tokens is a **minor**
bump. Documentation-only changes are a **patch** bump. See
`documentation/BRAND_CHANGE_PROCESS.md` for the formal change-request flow.

## Contacts

| Role | Owner |
| --- | --- |
| Brand custodian | SNAD Executive Office |
| Design system maintainer | design@snad.ai |
| Compliance gate | `scripts/ci/check-design-system-compliance.py` |
