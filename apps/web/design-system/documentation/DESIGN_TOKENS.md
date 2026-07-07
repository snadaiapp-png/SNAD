# SNAD Design Tokens — Reference

> **Source of truth:** [`tokens/tokens.json`](../tokens/tokens.json)
> **CSS export:** [`tokens/theme.css`](../tokens/theme.css)
> **Total tokens:** 110+ across 9 groups

This is the canonical reference for every SNAD design token. Every token
has a unique `--snad-*` name, a defined value, and a defined intent. Use
tokens — never raw values.

## Naming conventions

| Prefix | Group | Example |
| --- | --- | --- |
| `--snad-color-brand-*` | Brand colors (primary, accent) | `--snad-color-brand-primary` |
| `--snad-color-petroleum-{50-950}` | Petroleum Green scale | `--snad-color-petroleum-500` |
| `--snad-color-gold-{50-950}` | Royal Gold scale | `--snad-color-gold-500` |
| `--snad-color-{semantic}` | Semantic colors (background, surface, text, border, action, status) | `--snad-color-text-primary` |
| `--snad-font-*` | Font families | `--snad-font-arabic` |
| `--snad-text-*` | Type scale (font-size + weight + line-height) | `--snad-text-h1` |
| `--snad-space-*` | Spacing (4px base) | `--snad-space-4` |
| `--snad-size-*` | Sizing | `--snad-size-md` |
| `--snad-radius-*` | Border radius | `--snad-radius-lg` |
| `--snad-shadow-*` | Box shadows | `--snad-shadow-md` |
| `--snad-z-*` | Z-index layers | `--snad-z-modal` |
| `--snad-motion-*` | Motion duration + easing | `--snad-motion-standard` |
| `--snad-bp-*` | Breakpoints | `--snad-bp-md` |

## 1. Brand colors

| Token | Light value | Dark value | Use |
| --- | --- | --- | --- |
| `--snad-color-brand-primary` | `#0E3D38` | `#1B6E66` | Brand moments, primary CTAs, headers |
| `--snad-color-brand-accent` | `#D4AF37` | `#E1C266` | Awards, premium markers, decorative emphasis (sparingly) |

```css
.button--primary { background: var(--snad-color-brand-primary); }
.premium-badge   { color: var(--snad-color-brand-accent); }
```

### Color scales (50 lightest → 950 darkest)

**Petroleum Green** (`--snad-color-petroleum-{50|100|200|300|400|500|600|700|800|900|950}`)

| Step | Value | Use |
| --- | --- | --- |
| 50 | `#F3F5F5` | Subtle brand tints, hover backgrounds |
| 100 | `#E7ECEB` | Light brand surfaces |
| 200 | `#C3CECD` | Borders on light brand surfaces |
| 300 | `#9FB1AF` | Disabled brand elements |
| 400 | `#567774` | Secondary brand text on light |
| 500 | `#0E3D38` | **Base brand color** |
| 600 | `#0D3732` | Brand hover (light mode) |
| 700 | `#0A2E2A` | Brand active / pressed |
| 800 | `#082522` | Deep brand for dark surfaces |
| 900 | `#061B19` | Surface inverse (light mode) |
| 950 | `#041211` | Darkest brand — used for high-contrast headers |

**Royal Gold** (`--snad-color-gold-{50|100|200|300|400|500|600|700|800|900|950}`)

| Step | Value | Use |
| --- | --- | --- |
| 50 | `#FDFBF5` | Subtle gold tints |
| 100 | `#FBF7EB` | Light gold backgrounds |
| 200 | `#F4EBCD` | Gold soft (alias of `--snad-brand-gold-soft` legacy) |
| 300 | `#EEDFAF` | Gold borders |
| 400 | `#E1C773` | Gold text on dark |
| 500 | `#D4AF37` | **Base accent color** |
| 600 | `#BF9E32` | Gold hover |
| 700 | `#9F8329` | Gold active |
| 800 | `#7F6921` | Deep gold |
| 900 | `#5F4F19` | Gold on dark surfaces |
| 950 | `#403410` | Darkest gold |

## 2. Semantic colors (light + dark)

### Backgrounds & surfaces

| Token | Light | Dark |
| --- | --- | --- |
| `--snad-color-background-default` | `#FAFAF7` (ivory) | `#061513` |
| `--snad-color-background-elevated` | `#FFFFFF` | `#0A211F` |
| `--snad-color-surface-primary` | `#FFFFFF` | `#0E2927` |
| `--snad-color-surface-secondary` | `#F4F1EA` (warm gray) | `#123532` |

### Text

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `--snad-color-text-primary` | `#1A1A1A` (charcoal) | `#F5FAF9` | Body copy, headings |
| `--snad-color-text-secondary` | `#525252` (warm gray) | `#BDD0CD` | Secondary text, captions |
| `--snad-color-text-muted` | `#8A8A8A` | `#9DB1AE` | Placeholders, disabled text |
| `--snad-color-text-inverse` | `#FFFFFF` | `#0E2927` | Text on brand-colored backgrounds |
| `--snad-color-text-brand` | `#0E3D38` | `#66C7C0` | Brand-colored text emphasis |

### Borders

| Token | Light | Dark |
| --- | --- | --- |
| `--snad-color-border-default` | `#E5E2DA` | `#294744` |
| `--snad-color-border-subtle` | `#F0EDE5` | `#1F403D` |

### Actions

| Token | Light | Dark |
| --- | --- | --- |
| `--snad-color-action-primary` | `#0E3D38` | `#1B6E66` |
| `--snad-color-action-primary-hover` | `#0A2E2A` | `#24857C` |
| `--snad-color-action-primary-active` | `#072221` | `#2C9A90` |
| `--snad-color-action-accent` | `#D4AF37` | `#E1C266` |
| `--snad-color-action-accent-hover` | `#B8941F` | `#E9D084` |
| `--snad-color-focus-ring` | `rgba(14,61,56,0.30)` | `rgba(102,199,192,0.45)` |

### Status

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `--snad-color-success` | `#16835B` | `#4BC38E` | Success messages, valid state |
| `--snad-color-success-soft` | `#E8F7F0` | `#12382C` | Success background tint |
| `--snad-color-warning` | `#9B5B0C` | `#F2B555` | Warnings, pending state |
| `--snad-color-warning-soft` | `#FFF5E6` | `#3B2A12` | Warning background tint |
| `--snad-color-error` | `#B52F3E` | `#F07782` | Errors, invalid state |
| `--snad-color-error-soft` | `#FFF0F2` | `#3E1D22` | Error background tint |
| `--snad-color-info` | `#176B94` | `#58ADD5` | Informational messages |
| `--snad-color-info-soft` | `#EAF6FC` | `#102F3D` | Info background tint |
| `--snad-color-pending` | `#9B5B0C` | `#F2B555` | Pending state (alias of warning) |
| `--snad-color-disabled` | `#C0C0C0` | `#5A6B68` | Disabled text/icon |
| `--snad-color-disabled-background` | `#F0F0F0` | `#1F3532` | Disabled control background |

### Supporting

| Token | Value | Use |
| --- | --- | --- |
| `--snad-color-soft-sage` | `#A8C5B8` | Secondary brand tint |
| `--snad-color-deep-teal` | `#0A6B66` | Tertiary brand tint |
| `--snad-color-ivory-white` | `#FAFAF7` | Canvas background (alias of `background-default`) |
| `--snad-color-warm-gray` | `#525252` | Warm gray for secondary text (alias) |
| `--snad-color-charcoal` | `#1A1A1A` | Charcoal for primary text (alias) |
| `--snad-color-neutral-gray` | `#8A8A8A` | Neutral gray for muted text (alias) |

## 3. Typography

### Font families

| Token | Stack |
| --- | --- |
| `--snad-font-arabic` | `'Tajawal', 'IBM Plex Sans Arabic', 'Noto Sans Arabic', Tahoma, sans-serif` |
| `--snad-font-latin` | `'Inter', 'Noto Sans', Arial, sans-serif` |
| `--snad-font-display` | `var(--snad-font-arabic)` |
| `--snad-font-body` | `var(--snad-font-arabic)` |
| `--snad-font-label` | `var(--snad-font-arabic)` |
| `--snad-font-numeric` | `var(--snad-font-latin)` |

### Type scale (composed shorthands)

Each `--snad-text-*` token is a CSS shorthand: `font-size font-weight line-height`.

```css
h1 { font: var(--snad-text-h1); }       /* 2.25rem 800 1.2  */
.body { font: var(--snad-text-body); }   /* 1rem    400 1.6  */
.caption { font: var(--snad-text-caption); } /* 0.75rem 400 1.4 */
```

| Token | Size | Weight | Line-height | Use |
| --- | --- | --- | --- | --- |
| `--snad-text-display` | 3rem (48px) | 800 | 1.15 | Hero moments |
| `--snad-text-h1` | 2.25rem (36px) | 800 | 1.2 | Page title |
| `--snad-text-h2` | 1.875rem (30px) | 700 | 1.25 | Section title |
| `--snad-text-h3` | 1.5rem (24px) | 700 | 1.3 | Subsection title |
| `--snad-text-h4` | 1.25rem (20px) | 700 | 1.4 | Card title |
| `--snad-text-title` | 1.125rem (18px) | 600 | 1.45 | List item title |
| `--snad-text-subtitle` | 1rem (16px) | 600 | 1.5 | Subtitle |
| `--snad-text-body` | 1rem (16px) | 400 | 1.6 | Body copy |
| `--snad-text-label` | 0.875rem (14px) | 600 | 1.4 | Form labels |
| `--snad-text-caption` | 0.75rem (12px) | 400 | 1.4 | Captions, timestamps |
| `--snad-text-button` | 0.9375rem (15px) | 700 | 1.2 | Button text |
| `--snad-text-table` | 0.875rem (14px) | 500 | 1.5 | Table cells |
| `--snad-text-numeric` | 1rem (16px) | 600 | 1.4 | Numeric data (uses Latin font) |

## 4. Spacing (4px base)

| Token | Value | Token | Value |
| --- | --- | --- | --- |
| `--snad-space-0` | 0px | `--snad-space-9` | 36px |
| `--snad-space-1` | 4px | `--snad-space-10` | 40px |
| `--snad-space-2` | 8px | `--snad-space-11` | 44px |
| `--snad-space-3` | 12px | `--snad-space-12` | 48px |
| `--snad-space-4` | 16px | `--snad-space-14` | 56px |
| `--snad-space-5` | 20px | `--snad-space-16` | 64px |
| `--snad-space-6` | 24px | `--snad-space-20` | 80px |
| `--snad-space-7` | 28px | `--snad-space-24` | 96px |
| `--snad-space-8` | 32px | `--snad-space-28` | 112px |
| | | `--snad-space-32` | 128px |

## 5. Sizing

| Token | rem | px |
| --- | --- | --- |
| `--snad-size-xs` | 1.5rem | 24px |
| `--snad-size-sm` | 2rem | 32px |
| `--snad-size-md` | 2.5rem | 40px |
| `--snad-size-lg` | 3rem | 48px |
| `--snad-size-xl` | 4rem | 64px |
| `--snad-size-2xl` | 5rem | 80px |
| `--snad-size-3xl` | 6rem | 96px |

## 6. Border radius

| Token | Value |
| --- | --- |
| `--snad-radius-none` | 0px |
| `--snad-radius-sm` | 4px |
| `--snad-radius-md` | 8px |
| `--snad-radius-lg` | 12px |
| `--snad-radius-xl` | 16px |
| `--snad-radius-2xl` | 24px |
| `--snad-radius-full` | 9999px |

## 7. Shadows

| Token | Light |
| --- | --- |
| `--snad-shadow-none` | `none` |
| `--snad-shadow-sm` | `0 1px 2px rgba(14,61,56,0.06), 0 1px 3px rgba(14,61,56,0.04)` |
| `--snad-shadow-md` | `0 4px 8px rgba(14,61,56,0.06), 0 12px 24px rgba(14,61,56,0.08)` |
| `--snad-shadow-lg` | `0 12px 24px rgba(14,61,56,0.10), 0 24px 48px rgba(14,61,56,0.12)` |
| `--snad-shadow-xl` | `0 24px 48px rgba(14,61,56,0.14), 0 48px 96px rgba(14,61,56,0.16)` |

(Dark theme uses pure-black shadows for stronger depth perception.)

## 8. Z-index layers

| Token | Value | Use |
| --- | --- | --- |
| `--snad-z-base` | 0 | Default content |
| `--snad-z-dropdown` | 1000 | Dropdowns, select menus |
| `--snad-z-sticky` | 1100 | Sticky headers |
| `--snad-z-fixed` | 1200 | Fixed nav bars |
| `--snad-z-modal-backdrop` | 1300 | Modal scrim |
| `--snad-z-modal` | 1400 | Modal dialog |
| `--snad-z-popover` | 1500 | Popovers, tooltips on hover |
| `--snad-z-tooltip` | 1600 | Tooltips (above popovers) |
| `--snad-z-toast` | 1700 | Toast notifications (always on top) |

## 9. Motion

### Durations

| Token | Value | Use |
| --- | --- | --- |
| `--snad-motion-fast` | 140ms | Hover states, small state changes |
| `--snad-motion-standard` | 220ms | Default transitions, panel slides |
| `--snad-motion-slow` | 320ms | Modal opens, page transitions |

### Easings

| Token | Value | Use |
| --- | --- | --- |
| `--snad-motion-ease-standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | Default |
| `--snad-motion-ease-in` | `cubic-bezier(0.4, 0, 1, 1)` | Element exiting |
| `--snad-motion-ease-out` | `cubic-bezier(0, 0, 0.2, 1)` | Element entering |

## 10. Breakpoints

| Token | Value | Use |
| --- | --- | --- |
| `--snad-bp-sm` | 640px | Large phone / small tablet portrait |
| `--snad-bp-md` | 768px | Tablet portrait |
| `--snad-bp-lg` | 1024px | Tablet landscape / small laptop |
| `--snad-bp-xl` | 1280px | Desktop |
| `--snad-bp-2xl` | 1536px | Large desktop |

> **Note:** Breakpoint tokens are exposed as CSS custom properties for
> JS consumers (e.g. SSR-aware media query matching). Tailwind CSS 4
> still uses its own `--breakpoint-*` namespace for `@media` generation.

## Common recipes

### A brand-tinted translucent overlay (4% petroleum green)

```css
.hover-bg {
  background: color-mix(in srgb, var(--snad-color-brand-primary) 4%, transparent);
}
```

### A focus ring on an input

```css
input:focus-visible {
  outline: 3px solid var(--snad-color-focus-ring);
  outline-offset: 2px;
}
```

### A primary button

```css
.btn-primary {
  background: var(--snad-color-action-primary);
  color: var(--snad-color-text-inverse);
  border-radius: var(--snad-radius-md);
  padding: var(--snad-space-3) var(--snad-space-4);
  font: var(--snad-text-button);
  transition: background var(--snad-motion-fast) var(--snad-motion-ease-standard);
}
.btn-primary:hover { background: var(--snad-color-action-primary-hover); }
.btn-primary:active { background: var(--snad-color-action-primary-active); }
```
