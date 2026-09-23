# SNAD Logo Usage

> **Logo files:** [`apps/web/public/assets/brand/`](../../public/assets/brand/)
> **Custodian:** SNAD Executive Office
> **Status:** ACTIVE

SNAD currently has an existing governed SVG family used across non-auth product surfaces and a separately approved raster wordmark for authentication surfaces. The authentication wordmark is intentionally limited in scope until a separate global brand migration is approved.

## 1. Logo variations

| Variation | File | When to use |
| --- | --- | --- |
| Existing primary | `snad-logo-primary.svg` | Existing non-auth headers and product surfaces |
| Existing vertical | `snad-logo-vertical.svg` | Existing narrow/mobile non-auth surfaces |
| Existing white reverse | `snad-logo-white.svg` | Existing dark non-auth backgrounds |
| Existing monochrome | `snad-logo-mono.svg` | Existing single-color contexts |
| Existing app icon | `snad-app-icon.svg` | App icon contexts |
| Existing favicon | `snad-favicon.svg` | Browser tab |
| **Approved authentication wordmark** | `snad-logo-official-wordmark.png` | Login, session-expired, credential-rotation and related authentication surfaces on a light/neutral panel |

## 2. Authentication wordmark

Authentication surfaces use `snad-logo-official-wordmark.png` only through:

```tsx
<SnadLogo variant="official-wordmark" />
```

The approved source is PNG RGBA `1162×337` with SHA-256:

`98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`

Rules:

1. Serve the complete image at the locked `1162:337` aspect ratio.
2. Use it on a light or neutral surface where the artwork has sufficient contrast.
3. Do not recolor, trace/vectorize, crop, stretch, rotate, compress, apply effects, or change the artwork geometry.
4. Do not synthesize white, dark, monochrome, compact, app-icon, or favicon variants from it.
5. Do not use a dark-mode CSS filter to transform it. If an approved reverse artwork is required later, it must arrive as a separately approved asset.
6. The dark intelligence panel is not a second logo canvas. It may carry product copy, not a fabricated inverse logo.
7. The existing SVG family remains unchanged outside authentication until a separate migration is approved.

## 3. Clear space

The authentication wordmark must have clear space on all four sides and must never touch a field, button, panel edge, or narrative copy. Existing SVG-family clear-space rules remain unchanged.

## 4. Minimum sizes

| Variation | Minimum width | Minimum height |
| --- | --- | --- |
| Authentication wordmark | 170 px on supported mobile auth layouts | auto, aspect ratio locked |
| Existing primary | 120 px / 32 mm | 48 px / 13 mm |
| Existing compact mark | 24 px / 6 mm | 24 px / 6 mm |
| Existing app icon | 32 px | 32 px |
| Existing favicon | 16 px | 16 px |

Auth responsive targets are defined in `AUTH_UI_GUIDE.md`.

## 5. Color and backgrounds

| Background | Approved asset |
| --- | --- |
| Login light/neutral panel | `snad-logo-official-wordmark.png` |
| Existing light non-auth surface | `snad-logo-primary.svg` |
| Existing petroleum/charcoal non-auth surface | `snad-logo-white.svg` |
| Existing single-color print | `snad-logo-mono.svg` |

Never apply a CSS color filter, tint, gradient, glow, duotone, or opacity treatment to the authentication wordmark.

## 6. File format requirements

The existing SVG family remains vector-first. The authentication wordmark is an explicit exception: the approved PNG is the canonical Login v2 source until the brand authority supplies a separately approved vector source. Do not reverse-engineer one.

## 7. Prohibited modifications

1. ❌ Recolor or filter logo artwork.
2. ❌ Stretch, skew, rotate, crop, compress, or distort.
3. ❌ Rearrange elements inside an approved lockup.
4. ❌ Add shadows, glows, bevels, 3D effects, masks, or animated effects.
5. ❌ Vectorize or trace the approved authentication PNG.
6. ❌ Invent an inverse authentication wordmark for dark mode.
7. ❌ Reference `/assets/brand/...` directly from application surfaces; use `<SnadLogo />`.
8. ❌ Replace the authentication wordmark with plain `SNAD` text while the asset is available.

## 8. Favicon, app icon, social, and print

Login v2 does not change favicon, app-icon, social-card, or print artwork. These remain on the existing governed family until a separate migration is approved.

## 9. Compliance

The brand asset directory is `apps/web/public/assets/brand/`. Every governed application reference must flow through `apps/web/components/sds/SnadLogo.tsx`. `scripts/ci/check-logo-governance.py` enforces direct-reference restrictions for both SVG and PNG brand assets and specifically requires the login form to render `<SnadLogo />`.

The authentication PNG is not considered shipped until its repository bytes match the approved SHA-256 above. A path mapping without the matching file is a failed brand gate, not a completed integration.
