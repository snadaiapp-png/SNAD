# SNAD Brand Assets

> **Custodian:** SNAD Executive Office
> **Maintainer:** `design@snad.ai`
> **Status:** Logo files to be added separately by the brand team.

This directory holds the official SNAD brand asset files: logo lockups,
app icons, favicons, social cards, and print collateral. Every file in
this directory is governed by
[`apps/web/design-system/documentation/LOGO_USAGE.md`](../../design-system/documentation/LOGO_USAGE.md)
and
[`apps/web/design-system/documentation/BRAND_GOVERNANCE.md`](../../design-system/documentation/BRAND_GOVERNANCE.md).

## Required files

| File | Format | Dimensions | Use |
| --- | --- | --- | --- |
| `logo-primary.svg` | SVG (vector) | per lockup | Default lockup — Latin `SNAD` + Arabic `سند` stacked |
| `logo-horizontal.svg` | SVG (vector) | per lockup | Horizontal lockup — `SNAD \| سند` inline |
| `logo-mark.svg` | SVG (vector) | square | Mark-only — glyph + accent dot, no wordmark |
| `logo-white.svg` | SVG (vector) | per lockup | White reverse — single-color white for dark backgrounds |
| `logo-black.svg` | SVG (vector) | per lockup | Monochrome black — for fax / single-color print |
| `logo-arabic.svg` | SVG (vector) | per lockup | Arabic-only lockup — `سند` with glyph |
| `logo-english.svg` | SVG (vector) | per lockup | English-only lockup — `SNAD` with glyph |
| `app-icon-512.png` | PNG @1x | 512 × 512 | PWA install prompt, splash screen |
| `app-icon-256.png` | PNG @1x | 256 × 256 | PWA install prompt (smaller) |
| `app-icon-128.png` | PNG @1x | 128 × 128 | PWA install prompt (smallest) |
| `app-icon-64.png` | PNG @1x | 64 × 64 | Browser chrome, taskbar |
| `app-icon-32.png` | PNG @1x | 32 × 32 | Browser tab fallback |
| `favicon-32.png` | PNG @1x | 32 × 32 | Modern browser favicon |
| `favicon-16.png` | PNG @1x | 16 × 16 | Legacy browser favicon |
| `favicon.ico` | ICO (multi-res) | 16, 32, 48 | Universal favicon (IE / legacy) |
| `favicon.svg` | SVG (vector) | square | Vector favicon — uses `currentColor` for glyph |
| `social-card-og.png` | PNG @1x | 1200 × 630 | OpenGraph (Facebook, LinkedIn, Slack unfurl) |
| `social-card-twitter.png` | PNG @1x | 1200 × 600 | Twitter card |
| `social-card-square.png` | PNG @1x | 1080 × 1080 | Instagram / LinkedIn feed |
| `logo-print-cmyk.eps` | EPS (CMYK) | per lockup | Print collateral — CMYK color space, embedded fonts |
| `logo-print-mono.eps` | EPS (monochrome) | per lockup | Single-color print (fax, stamps) |

## File format requirements

### SVG (primary format)

* **Vector only** — no embedded raster images.
* **`currentColor`** for the glyph so it adapts to the surrounding text
  color (except in `logo-primary.svg`, `logo-white.svg`, `logo-black.svg`
  where colors are baked in).
* **No external font references** — convert all text to outlines.
* **No `width` / `height` attributes** on the root `<svg>` — use
  `viewBox` only so the logo scales fluidly.
* **Optimized** with `svgo` to strip comments, metadata, and unused
  definitions.
* **No JavaScript, no external references, no `<use href="http...">`**.

### PNG (raster fallback)

* **@1x, @2x, @3x** versions for every raster asset used in email or
  partner integrations.
* **Transparent background** (alpha channel) unless the asset is
  explicitly a "card" (e.g. `social-card-og.png` is opaque).
* **8-bit color** minimum; 24-bit for photographic assets (none currently
  planned).
* **Optimized** with `pngquant --quality=80-90` to keep file size under
  100 KB per asset.

### ICO (favicon)

* **Multi-resolution** (16, 32, 48) baked into a single `.ico` file.
* Generated from `favicon.svg` via `convert favicon.svg -define
  icon:auto-resize=16,32,48 favicon.ico` (ImageMagick).

### EPS (print)

* **CMYK color space** — never RGB.
* **Embedded fonts** (convert text to outlines before saving).
* **Petroleum CMYK**: C:100 M:80 Y:70 K:60.
* **Gold CMYK**: C:15 M:35 Y:90 K:5.

## Clear space

The logo must always have **minimum 1× the logo's glyph height** of
clear space on all four sides. No other element (text, image, button,
edge of canvas) may enter this clear-space zone.

```
┌──────────────────────────────────────────┐
│                                          │
│          ┌─ clear space (1× glyph) ─┐    │
│          │                          │    │
│          │   ┌──────────────────┐   │    │
│          │   │  [glyph]  SNAD   │   │    │
│          │   │           سند    │   │    │
│          │   └──────────────────┘   │    │
│          │                          │    │
│          └──────────────────────────┘    │
│                                          │
└──────────────────────────────────────────┘
```

## Minimum sizes

| Variation | Minimum width | Minimum height |
| --- | --- | --- |
| Primary (stacked) | 120 px / 32 mm | 48 px / 13 mm |
| Horizontal | 160 px / 42 mm | 32 px / 8 mm |
| Mark-only | 24 px / 6 mm | 24 px / 6 mm |
| App icon | 32 px | 32 px |
| Favicon | 16 px | 16 px |

Below these sizes, use the mark-only variation.

## Color

| Background | Logo file | Glyph | Wordmark | Accent dot |
| --- | --- | --- | --- | --- |
| Light (ivory, white, warm gray) | `logo-primary.svg` | `#0E3D38` | `#0E3D38` | `#D4AF37` |
| Dark (petroleum, charcoal) | `logo-white.svg` | `#FFFFFF` | `#FFFFFF` | `#D4AF37` |
| Photo (busy) | `logo-white.svg` in a petroleum chip | `#FFFFFF` | `#FFFFFF` | `#D4AF37` |
| Print (single-color) | `logo-black.svg` | `#000000` | `#000000` | `#000000` |
| Print (CMYK) | `logo-print-cmyk.eps` | petroleum CMYK | petroleum CMYK | gold CMYK |

## Prohibited modifications

1. ❌ Recolor the logo (no gradients, no duotones, no filters).
2. ❌ Stretch, skew, rotate, or distort the logo.
3. ❌ Rearrange the lockup.
4. ❌ Add effects (drop shadow, glow, bevel, 3D).
5. ❌ Place on busy photo backgrounds without the petroleum chip
   clear-space ring.
6. ❌ Use the wordmark alone (except in body copy where the brand name
   appears as text).
7. ❌ Use the glyph alone in body copy.
8. ❌ Animate beyond a single 220ms fade-in.
9. ❌ Combine with another brand's logo without written approval.
10. ❌ Crop the logo.

See [`LOGO_USAGE.md`](../../design-system/documentation/LOGO_USAGE.md)
for the full rules.

## Status

> **⚠️ Logo files have NOT been added yet.**

The brand team will deliver the final SVG / PNG / EPS files in a
follow-up PR. Until then, the app uses a text-based wordmark fallback
in the auth screens and dashboard header:

```tsx
<span className="snad-brand-mark">SNAD</span>
```

```css
.snad-brand-mark {
  font-family: var(--snad-font-display);
  font-size: var(--snad-text-h3);
  font-weight: 800;
  color: var(--snad-color-brand-primary);
  letter-spacing: 0.02em;
}
```

When the logo files land, the fallback will be replaced with an `<img>`
or inline SVG component that references the files in this directory.

## Contacts

| Role | Owner |
| --- | --- |
| Brand custodian | SNAD Executive Office |
| Design system maintainer | `design@snad.ai` |
| Compliance automation | `scripts/ci/check-design-system-compliance.py` |
