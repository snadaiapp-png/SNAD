# SNAD | سند — Official Brand Assets

**Version:** 1.0.1
**Last Updated:** 2026-09-24
**Status:** ACTIVE — official source of truth for SNAD brand assets

## Available Logo Files

| File | Format | Usage |
|------|--------|-------|
| snad-logo-primary.svg | SVG | Primary horizontal lockup — headers, nav bars, email signatures |
| snad-logo-vertical.svg | SVG | Vertical lockup — mobile headers, sidebars, business cards |
| snad-logo-white.svg | SVG | White version — dark backgrounds (#0E3D38 or darker) |
| snad-logo-mono.svg | SVG | Monochrome (currentColor) — stamps, embossing, single-color print |
| snad-app-icon.svg | SVG | Square app icon — favicons, home screen, social media |
| snad-favicon.svg | SVG | Simplified 32x32 favicon — browser tabs |
| snad-logo-official-wordmark.png | PNG RGBA, 1162×337 | User-approved official wordmark for authentication surfaces; exact bytes are governed and must not be recolored or vectorized |

## Authentication Wordmark Integrity

`snad-logo-official-wordmark.png` SHA-256: `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`

The approved authentication wordmark is intentionally limited to authentication surfaces in Login v2 Phase A. The existing SVG family remains in use on non-auth surfaces until a separate global migration is explicitly approved. No white, compact, monochrome, favicon, or SVG derivative may be synthesized from the approved PNG.

## Brand Colors

| Token | Hex | Usage |
|-------|-----|-------|
| --snad-color-brand-primary | #0E3D38 | Dark Petroleum Green |
| --snad-color-brand-accent | #D4AF37 | Royal Polished Gold |

NEVER use raw hex values in components. Always reference SDS tokens.

## Logo Usage Rules

### Minimum Sizes
- Digital (primary horizontal): 120px
- Digital (vertical): 80px
- Digital (app icon): 32px
- Print (primary): 30mm
- Favicon: 16px

### Clear Space
Minimum 1x logo height on all sides.

### Prohibited Modifications
- Do NOT change colors, proportions, or add effects
- Do NOT stretch, rotate, compress, or crop
- Do NOT change the gold accent dot position
- Do NOT use low-resolution raster versions
- Do NOT trace or vectorize the approved authentication PNG

### Background Selection
- Authentication light/neutral panel: snad-logo-official-wordmark.png
- White/light non-auth surfaces: snad-logo-primary.svg
- Petroleum green: snad-logo-white.svg
- Dark photographic: snad-logo-white.svg
- Single-color print: snad-logo-mono.svg

## Icon Mark
Rounded square (petroleum green) + stylized Arabic "س" (gold) + gold accent dot applies to the existing SVG family. It does not redefine the separately approved authentication wordmark.

## Typography
- Latin "SNAD": Inter, weight 800
- Arabic "سند": Tajawal, weight 700

## File Format Policy
- SVG: canonical for the existing non-auth logo family
- Approved authentication PNG: exact governed source for Login v2 auth surfaces
- PNG @1x/2x/3x: legacy exports only where explicitly approved
- PDF: print
- ICO: legacy favicons (from snad-favicon.svg)

## Brand Change Process
See apps/web/design-system/documentation/BRAND_CHANGE_PROCESS.md

## Cross-References
- Logo usage: apps/web/design-system/documentation/LOGO_USAGE.md
- Brand governance: apps/web/design-system/documentation/BRAND_GOVERNANCE.md
- Design tokens: apps/web/design-system/tokens/theme.css
- Component library: apps/web/components/sds/
