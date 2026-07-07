# SNAD | سند — Official Brand Assets

**Version:** 1.0.0
**Last Updated:** 2026-07-07
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

### Background Selection
- White/light: snad-logo-primary.svg
- Petroleum green: snad-logo-white.svg
- Dark photographic: snad-logo-white.svg
- Single-color print: snad-logo-mono.svg

## Icon Mark
Rounded square (petroleum green) + stylized Arabic "س" (gold) + gold accent dot.

## Typography
- Latin "SNAD": Inter, weight 800
- Arabic "سند": Tajawal, weight 700

## File Format Policy
- SVG: canonical (all digital)
- PNG: 1x/2x/3x exports for legacy (email, old browsers)
- PDF: print
- ICO: legacy favicons (from snad-favicon.svg)

## Brand Change Process
See apps/web/design-system/documentation/BRAND_CHANGE_PROCESS.md

## Cross-References
- Logo usage: apps/web/design-system/documentation/LOGO_USAGE.md
- Brand governance: apps/web/design-system/documentation/BRAND_GOVERNANCE.md
- Design tokens: apps/web/design-system/tokens/theme.css
- Component library: apps/web/components/sds/
