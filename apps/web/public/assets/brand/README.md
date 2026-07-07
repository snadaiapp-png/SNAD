# SNAD | سند — Official Brand Assets

**Status:** ACTIVE — immutable source of truth

The only approved artwork is the owner-supplied **سند** logo. It must not be reconstructed, redrawn, retyped, recolored, stretched, cropped beyond the approved exports, or replaced by a generated SVG.

## Canonical files

| File | Purpose | SHA-256 |
|---|---|---|
| `snad-logo-official-primary.webp` | Optimized full lockup for login and large surfaces | `7d3220e2f37b1dd9ca9e635c671693c73856ae4b2adb31263d3491daa18c4786` |
| `snad-logo-official-wordmark.webp` | Approved crop of the Arabic wordmark for compact headers | `406c537c2e537a0f80efd22b2271eddf77452c67b9444eae919ef61771a02403` |

The hashes are enforced by `scripts/ci/check-logo-governance.py`. Any byte-level change requires an approved SNAD Brand Change Request and an intentional hash update in the same reviewed pull request.

## Usage

All application code must render logos through `SnadLogo`. Direct paths and raw `img` usage are prohibited outside that component and visual-governance tests.

- Login: full official lockup.
- Global authenticated header: official wordmark crop.
- Light and dark themes: preserve the original artwork; use an appropriate surface behind it rather than altering the logo.

## Official colors

- Dark Petroleum Green: `#0E3D38`
- Royal Polished Gold: `#D4AF37`

Use SDS tokens in product UI. Raw values above are documented only as brand references.
