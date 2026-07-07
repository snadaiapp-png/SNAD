# SNAD Logo Usage

> **Logo files:** [`apps/web/public/assets/brand/`](../../public/assets/brand/)
> **Custodian:** SNAD Executive Office
> **Status:** Logo files to be added separately by the brand team.

The SNAD logo is a **two-color mark**: a petroleum-green glyph paired
with a gold accent dot, set next to the wordmark `SNAD` (Latin) or
`سند` (Arabic). This document specifies how the logo may and may not be
used.

## 1. Logo variations

| Variation | File (planned) | When to use |
| --- | --- | --- |
| Primary (Latin `SNAD` + Arabic `سند` stacked) | `logo-primary.svg` | Default. App headers, marketing homepages, pitch decks. |
| Horizontal (Latin `SNAD \| سند` inline) | `logo-horizontal.svg` | Tight horizontal spaces: nav bars, email signatures. |
| Mark-only (glyph without wordmark) | `logo-mark.svg` | App icons, favicons, social avatars. Never standalone in body copy. |
| White reverse (single-color white) | `logo-white.svg` | Dark backgrounds (petroleum, charcoal, photo overlays). |
| Monochrome black | `logo-black.svg` | Fax, single-color print, partner co-branded PDFs. |
| App icon (square) | `app-icon-{512,256,128,64,32}.png` | Home screen icons, install prompts. |
| Favicon | `favicon-{32,16}.ico` + `favicon.svg` | Browser tab. |
| Social card | `social-card-{1200x630,1080x1080}.png` | OpenGraph, Twitter, LinkedIn. |
| Print (CMYK) | `logo-print-cmyk.eps` | Printed collateral only. |

## 2. Clear space

The logo must always have **minimum 1× the logo's glyph height** of clear
space on all four sides. No other element (text, image, button, edge of
canvas) may enter this clear-space zone.

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

**For the horizontal lockup**, clear space is 1× the glyph height on top,
bottom, left, and right — even though the lockup is wider than it is tall.

## 3. Minimum sizes

To preserve legibility, the logo must never be rendered smaller than:

| Variation | Minimum width | Minimum height |
| --- | --- | --- |
| Primary (stacked) | 120 px / 32 mm | 48 px / 13 mm |
| Horizontal | 160 px / 42 mm | 32 px / 8 mm |
| Mark-only | 24 px / 6 mm | 24 px / 6 mm |
| App icon | 32 px | 32 px |
| Favicon | 16 px | 16 px |

Below these sizes, the wordmark becomes illegible and the gold accent dot
loses its visual weight. Use the mark-only variation at small sizes.

## 4. Color

| Background | Logo variation | Glyph color | Wordmark color | Accent dot |
| --- | --- | --- | --- | --- |
| Light (ivory, white, warm gray) | Primary | `#0E3D38` | `#0E3D38` | `#D4AF37` |
| Dark (petroleum, charcoal) | White reverse | `#FFFFFF` | `#FFFFFF` | `#D4AF37` |
| Photo (busy) | White reverse in a petroleum chip | `#FFFFFF` | `#FFFFFF` | `#D4AF37` |
| Print (single-color) | Monochrome black | `#000000` | `#000000` | `#000000` |

**Never** recolor the logo. The glyph is always petroleum (or white on
dark) and the accent dot is always gold (or black in monochrome print).

## 5. File format requirements

| Format | Use | Notes |
| --- | --- | --- |
| **SVG** | Primary format for all on-screen use | Vector, infinitely scalable, themable via `currentColor` for the glyph. |
| PNG @1x | Legacy raster fallback | For email clients that block SVG. |
| PNG @2x | Retina raster fallback | For email clients on retina screens. |
| PNG @3x | High-DPI raster fallback | For partner integrations that demand PNG. |
| ICO | Favicon | Multi-resolution (16, 32, 48). |
| EPS (CMYK) | Print | Vector, CMYK color space, embedded fonts. |

## 6. Prohibited modifications

1. ❌ **Recolor** the logo. Never use a different color for the glyph or
   the accent dot. Never apply a gradient, duotone, or color-overlay
   filter.
2. ❌ **Stretch, skew, rotate, or distort** the logo. The aspect ratio is
   locked.
3. ❌ **Rearrange** the lockup. The glyph always precedes the wordmark
   (LTR) or follows it (RTL). The wordmark `SNAD` is always above `سند`
   in the stacked lockup.
4. ❌ **Add effects** to the logo. No drop shadow, no glow, no bevel, no
   3D extrusion, no embossing.
5. ❌ **Place on busy photo backgrounds** without the petroleum chip
   clear-space ring. The logo must always have at least 1× glyph height
   of clear space.
6. ❌ **Use the wordmark alone** without the glyph, except inside body
   copy where the brand name appears as text.
7. ❌ **Use the glyph alone** in body copy — the glyph is a logo element,
   not a decorative bullet.
8. ❌ **Animate** the logo beyond a single 220ms fade-in. No spin, no
   bounce, no pulse, no slide.
9. ❌ **Combine** the SNAD logo with another brand's logo without the
   approved co-branding clear-space rules (contact `design@snad.ai`).
10. ❌ **Crop** the logo. Always show the complete lockup.

## 7. Favicon and app icon

The favicon and app icon use the **mark-only** variation (glyph + accent
dot, no wordmark) because the wordmark is illegible at 16×16 px.

* `favicon.svg` — vector favicon, petroleum glyph + gold dot on
  transparent background.
* `favicon.ico` — multi-resolution raster (16, 32, 48) for legacy
  browsers.
* `app-icon-512.png` through `app-icon-32.png` — raster app icons for
  PWA install prompts and home-screen icons.

The favicon SVG should use `currentColor` for the glyph so it adapts to
the browser's address bar theme.

## 8. Social card

The social card is a 1200 × 630 px (OpenGraph) or 1080 × 1080 px
(Instagram) PNG with the SNAD logo centered on a petroleum background.
The accent dot is gold; the wordmark is white.

**Never** overlay text on the social card. The card is a brand
identifier, not a marketing banner.

## 9. Print collateral

For printed collateral (business cards, letterhead, brochures), use the
**CMYK EPS** version of the logo. The petroleum green CMYK breakdown is
C:100 M:80 Y:70 K:60; the gold is C:15 M:35 Y:90 K:5.

**Never** send the RGB SVG version to a commercial printer — the colors
will shift unpredictably.

## 10. Compliance

The brand asset directory is `apps/web/public/assets/brand/`. Every file
in that directory must be listed in the README and must follow the naming
convention above. The lint script does not currently verify logo usage
(visual review only), but future SDS versions may add an automated
logo-presence check on key routes (home, login, dashboard header).
