# SNAD Visual Identity and Typography Contract

## Authority

The exclusive active customer-facing name is **سند / SNAD**. `SANAD`, `QAWN`, and `قاون` are legacy terms and are allowed only in historical audit records or technical package names that cannot be safely changed without a separate migration.

## Brand colors

- Primary petroleum: `#003B39`.
- Royal gold: `#D4AF37`.
- Feature components consume semantic variables from `apps/web/app/snad-tokens.css`; they must not redefine brand values locally.
- Status colors must remain semantic and must not replace the primary brand palette.

## Typography

- Arabic interface and document font: **Noto Sans Arabic**.
- Latin interface font: **Noto Sans**.
- Arabic, Latin, body, display, label, and numeric stacks are exposed as design tokens.
- Numeric data uses the Latin stack with tabular numerals where comparison or alignment matters.
- Font loading uses `next/font` with `display: swap`; no runtime font CDN stylesheet is permitted.
- Email addresses, technical identifiers, hashes, and URLs use explicit LTR direction inside the RTL application.

## Themes and accessibility

Light, dark, and system modes resolve through semantic surface, text, border, focus, and status tokens.

Requirements:

- Interactive controls have visible keyboard focus.
- Text and controls retain sufficient contrast in both themes.
- Reduced-motion preferences are respected.
- Selection color uses the approved semantic accent.
- Layout remains RTL by default.
- Direction changes are applied only to content that requires LTR presentation.

## Component rules

1. Use semantic utility names or design tokens rather than literal brand colors in feature components.
2. Do not add a second product name or unofficial logo text.
3. New authentication and account-recovery screens must use the same identity shell.
4. Do not load fonts from an unapproved remote stylesheet.
5. Keep English product lettering as `SNAD`.
6. Keep the Arabic product name as `سند`.
7. Validate responsive behavior and keyboard navigation before merge.

## Enforcement

`python3 scripts/quality/check_snad_identity.py` validates the canonical colors, approved fonts, active name, and dark-theme mapping.

`.github/workflows/snad-identity-governance.yml` runs:

- Identity validation.
- Web lint.
- Web tests.
- Production build.

Changes to the active identity, palette, or font family require an explicit design-governance decision and updated tests.

## Implementation status

```text
PR: 157
MERGE_SHA: c0115022feeb0cf21fdd0373b812c6eaa5bb120d
IDENTITY_GOVERNANCE: PASS
WEB_CI: PASS
PILOT_DEPLOYMENT: READY
PUBLIC_PILOT_RESPONSE: HTTP 200
```

The identity and typography implementation is merged into `main` and active in the pilot frontend.

This status authorizes use within ongoing development and the controlled pilot. It does not authorize commercial production or override Issue #101.

References: #127, #128, #130, #150.
