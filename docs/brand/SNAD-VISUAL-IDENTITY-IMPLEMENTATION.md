# SNAD Visual Identity — Implementation Contract v1.0

**Authority:** SNAD Visual Identity Plan v1.0 (`SNAD-BRAND-001`)  
**Official name:** سند / SNAD  
**Status:** Approved implementation baseline

## Runtime source of truth

The canonical runtime source is:

```text
apps/web/app/snad-tokens.css
```

Feature components must consume semantic CSS variables or Tailwind utilities mapped through `apps/web/app/globals.css`. They must not declare independent brand colors.

## Core identity

| Role | Token | Approved value |
|---|---|---|
| Primary | `--snad-brand-primary` | `#003B39` |
| Royal polished gold | `--snad-brand-gold` | `#D4AF37` |
| Canvas | `--snad-surface-canvas` | SNAD Ivory |
| Primary text | `--snad-text-primary` | SNAD Ink |
| Focus | `--snad-focus-ring` | Accessible petroleum teal |

The metallic gold gradient is reserved for logo production, premium marketing, and non-text decorative surfaces. The stable gold token remains the operational digital value.

## Dynamic themes

- Light mode is defined explicitly.
- Dark mode is defined explicitly through `[data-theme="dark"]`.
- System preference is supported automatically through `prefers-color-scheme`.
- A future preference control may set `data-theme="light"` or `data-theme="dark"` on the root element without changing component code.
- Reduced-motion preferences are honored globally.

## Mandatory development rules

1. Use **سند** in Arabic and **SNAD** in English user-facing content.
2. Do not introduce retired project names in active product work.
3. Do not add raw HEX, RGB, HSL, or arbitrary Tailwind color values to feature code.
4. Add new primitives only to the token source after formal brand review.
5. Expose feature styling through semantic roles, not palette names.
6. Validate light/dark, RTL/LTR, focus, contrast, and reduced motion.
7. Keep metallic effects out of long text, forms, and system status messages.

## Automated enforcement

The workflow `.github/workflows/snad-identity-governance.yml` runs on every relevant pull request and fails closed when:

- the token contract is changed incorrectly;
- a retired brand name is added;
- direct color literals are introduced in frontend source;
- lint, tests, or the production build fail.

After R12B closes, repository branch protection must mark **SNAD Identity Governance / Tokens, naming and visual policy** as a required status check.

## Exception process

An exception requires all of the following:

1. documented business or accessibility reason;
2. approval by the brand owner and project manager;
3. token-level implementation rather than a local component override;
4. tests for both themes and relevant locales;
5. update to this implementation contract when the decision is reusable.
