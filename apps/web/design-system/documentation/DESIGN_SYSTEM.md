# SNAD Design System (SDS) — Overview

> **Version:** 2.0.0 • **Status:** foundation shipped • **Owner:** `design@snad.ai`

The SNAD Design System (SDS) is the single source of truth for the visual
language of the SNAD | سند platform. It is a **token-driven,
theme-aware** foundation shared by every surface of the SNAD web
application. No component may ship with hardcoded colors, fonts, spacing,
or shadows — every visual decision must reference an SDS token.

## Why a design system?

1. **Brand integrity.** SNAD's two brand colors — Dark Petroleum Green
   `#0E3D38` and Royal Polished Gold `#D4AF37` — are the company's most
   valuable visual assets. The design system enforces their correct use
   everywhere, every time.
2. **Bilingual by default.** SNAD ships Arabic-first UIs with Latin
   fallbacks. The design system ships a tested Arabic / Latin type stack
   and bidirectional layout rules so every team gets RTL for free.
3. **Dark mode that works.** Tokens come in matched light + dark pairs.
   Switching themes is a single attribute toggle (`data-theme="dark"`),
   not a refactor.
4. **Speed.** A designer can sketch a new screen in Figma using SDS
   tokens; an engineer can implement it in CSS using the same token names.
   No translation, no drift.
5. **Accessibility.** Every token pair is audited for WCAG 2.2 AA
   contrast. Designers and engineers don't have to re-run contrast checks
   on every screen.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      apps/web/app/globals.css                       │
│  (imports tailwindcss → theme.css → snad-tokens.css →               │
│   snad-tailwind.css in this exact order)                            │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│           apps/web/design-system/tokens/theme.css                   │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  :root, [data-theme="light"]   ← every --snad-* token (light)  │ │
│  │  [data-theme="dark"]           ← overrides for dark             │ │
│  │  @media (prefers-color-scheme: dark)  ← OS fallback             │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
       ┌─────────────┐    ┌──────────────┐   ┌─────────────────────┐
       │ tokens.json │    │ light.css    │   │  snad-tailwind.css  │
       │ (machine-   │    │ dark.css     │   │  (Tailwind @theme   │
       │  readable)  │    │ (overrides)  │   │   color map → SDS)  │
       └─────────────┘    └──────────────┘   └─────────────────────┘
```

## The token pipeline

1. **Source of truth**: `tokens/tokens.json` — machine-readable, used by
   design tools (Figma plugin, Style Dictionary) and by the lint script.
2. **CSS export**: `tokens/theme.css` — hand-maintained to stay readable,
   but kept in lockstep with `tokens.json` (CI verifies this).
3. **Tailwind bridge**: `app/snad-tailwind.css` — maps Tailwind CSS 4
   utility names (`bg-primary`, `text-brand`, `border-default`) to
   `--snad-color-*` tokens.
4. **Legacy shim**: `app/snad-tokens.css` — re-exports v1.x token names
   (`--snad-brand-primary`, `--snad-surface-canvas`) as `var()` references
   to the new v2 tokens, so existing components keep working without a
   rewrite.

## How to use the design system

### In a CSS module

```css
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

### In a TSX inline style

```tsx
<p style={{ color: "var(--snad-color-text-muted)" }}>
  {t("noResults")}
</p>
```

### In a Tailwind utility class

```tsx
<button className="bg-brand-primary text-text-inverse
                   hover:bg-brand-primary-hover
                   rounded-md px-space-4 py-space-2
                   font-body text-button">
  {t("save")}
</button>
```

### Theme switching

```tsx
// In a server component or layout:
<html data-theme={userPreference ?? "light"}>
  <body>{children}</body>
</html>

// Or to honor the OS preference, simply omit data-theme — the
// @media (prefers-color-scheme: dark) fallback in theme.css kicks in.
```

## What's in scope for SDS 2.0

| ✅ Shipped in 2.0 | 🔜 Planned for 2.1+ |
| --- | --- |
| Brand colors + scales | Component CSS modules (Button, Card, Input, Modal) |
| Semantic color tokens (light + dark) | React component library (`@snad/ui`) |
| Typography tokens + type scale | Figma plugin |
| Spacing / sizing / radius / shadow / z-index / motion / breakpoints | Storybook |
| Tailwind CSS 4 integration | Iconography system |
| Backward-compat aliases (v1.x → v2.x) | Chart theming |
| CI lint rule for hardcoded colors | Email template system |
| 11 documentation files | Print stylesheet system |

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
* `apps/web/app/snad-tokens.css` (legacy — will be migrated)

Run locally:

```bash
python3 scripts/ci/check-design-system-compliance.py
```

## Documentation index

| Document | Purpose |
| --- | --- |
| [BRAND_GOVERNANCE.md](./BRAND_GOVERNANCE.md) | Brand rules, prohibited usage, change request process |
| [DESIGN_SYSTEM.md](./DESIGN_SYSTEM.md) | This overview |
| [DESIGN_TOKENS.md](./DESIGN_TOKENS.md) | Full token reference with usage examples |
| [COLOR_SYSTEM.md](./COLOR_SYSTEM.md) | Color palette, scales, semantic mapping, dark mode rules |
| [TYPOGRAPHY.md](./TYPOGRAPHY.md) | Font system, type scale, Arabic / English rules |
| [COMPONENT_USAGE.md](./COMPONENT_USAGE.md) | Component conventions (placeholder — expanded in 2.1) |
| [LOGO_USAGE.md](./LOGO_USAGE.md) | Logo rules, clear space, minimum sizes, prohibited modifications |
| [ACCESSIBILITY.md](./ACCESSIBILITY.md) | WCAG 2.2 AA requirements, focus management, contrast rules |
| [RTL_LTR_GUIDE.md](./RTL_LTR_GUIDE.md) | Bidirectional support rules |
| [VISUAL_TESTING.md](./VISUAL_TESTING.md) | Visual regression strategy |
| [BRAND_CHANGE_PROCESS.md](./BRAND_CHANGE_PROCESS.md) | Formal change request process |

## Versioning

SDS follows **semantic versioning**:

| Change type | Bump | Example |
| --- | --- | --- |
| Token *value* changes (even one hex digit) | **major** | `#0E3D38` → `#0E3D40` |
| New token added (no value change) | **minor** | add `--snad-color-banner-promo` |
| Documentation-only change | **patch** | fix a typo in this file |

See [BRAND_CHANGE_PROCESS.md](./BRAND_CHANGE_PROCESS.md) for the formal
change-request flow.
