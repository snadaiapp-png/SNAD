# SNAD Visual Identity and Typography Contract

## Authority

The exclusive active name is **سند / SNAD**. `SANAD`, `QAWN`, and `قاون` are legacy terms and are allowed only in historical audit records.

## Brand colors

- Primary petroleum: `#003B39`.
- Royal gold: `#D4AF37`.
- Feature components consume semantic variables from `apps/web/app/snad-tokens.css`; they must not redefine brand values.

## Typography

- Arabic interface and document font: **Noto Sans Arabic**.
- Latin interface font: **Noto Sans**.
- Arabic, Latin, body, display, label, and numeric stacks are exposed as design tokens.
- Numeric data uses the Latin stack with tabular numerals where comparison or alignment matters.
- Font loading uses `next/font` with `display: swap`; no runtime font CDN stylesheet is permitted.

## Themes and accessibility

Light, dark, and system modes resolve through semantic surface, text, border, focus, and status tokens. Interactive controls must have visible keyboard focus. Text and controls must retain sufficient contrast in both themes.

## Enforcement

`python3 scripts/quality/check_snad_identity.py` validates the canonical colors, approved fonts, active name, and dark-theme mapping. `.github/workflows/snad-identity-governance.yml` runs the validation, lint, tests, and production build for relevant changes.

## Gate

This implementation remains isolated until R12B closes. Merge requires successful Web CI, identity governance, accessibility review, and explicit project approval. Refs #127, #128, #130, #150.
