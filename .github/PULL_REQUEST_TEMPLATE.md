## SNAD Design System — Pull Request Checklist

<!-- This checklist is MANDATORY for all PRs that touch frontend files. -->
<!-- Fill it honestly. PRs that fail this checklist will be blocked. -->

### Brand Identity

- [ ] I used the official name **SNAD** (English) / **سند** (Arabic) — not `SANAD`, `Sanad`, or `Snad`
- [ ] I did not introduce any new brand name variations
- [ ] I used only approved logo files from `apps/web/public/assets/brand/`
- [ ] I did not modify, stretch, or recolor any logo

### Design Tokens (MANDATORY)

- [ ] I used SDS tokens (`var(--snad-color-*)`, `var(--snad-space-*)`, etc.) — NOT hardcoded hex/rgb/hsl values
- [ ] I did not add new CSS custom properties (tokens are frozen — request changes via `BRAND_CHANGE_PROCESS.md`)
- [ ] I used `var(--snad-font-*)` for font families — NOT hardcoded font names
- [ ] I ran `python3 scripts/ci/check-design-system-compliance.py` locally and it PASSED

### Components

- [ ] I used SDS components from `apps/web/components/sds/` where available
- [ ] I did not create duplicate local components when an SDS component exists
- [ ] If I created a new component, it follows SDS patterns (tokens, RTL, WCAG 2.2 AA)

### Internationalization

- [ ] I tested with **Arabic (RTL)** layout
- [ ] I tested with **English (LTR)** layout
- [ ] I used logical CSS properties (`margin-inline-*`, `padding-inline-*`) — NOT physical (`margin-left`)

### Themes

- [ ] I tested in **Light Mode**
- [ ] I tested in **Dark Mode**
- [ ] Text remains readable in both modes (contrast ratio ≥ 4.5:1)

### Accessibility (WCAG 2.2 AA)

- [ ] Interactive elements have `:focus-visible` styles using `var(--snad-color-focus-ring)`
- [ ] Touch targets are at least 44×44px
- [ ] I did not rely on color alone to convey information
- [ ] Form inputs have associated `<label>` elements
- [ ] Error states use `aria-invalid` and `aria-describedby`
- [ ] Modal/dialog uses `role="dialog"` and `aria-modal="true"`
- [ ] Dynamic content updates use `aria-live` where appropriate

### Testing

- [ ] I added/updated unit tests for new components
- [ ] All existing tests pass (`npm test`)
- [ ] I added visual regression tests for significant UI changes (if applicable)

### CI

- [ ] Build Next.js Web passes
- [ ] Lint passes
- [ ] SDS Compliance Check passes (auto-enforced in CI)
- [ ] provenance passes

### Documentation

- [ ] I updated relevant docs in `apps/web/design-system/documentation/` if I changed patterns
- [ ] I updated `COMPONENT_USAGE.md` if I added a new SDS component

---

## Summary

<!-- Brief description of what this PR does -->

## Related Issues

<!-- Link to GitHub issues -->

## Security Impact

<!-- Describe any security implications -->

## Rollback Plan

<!-- How to revert this PR safely -->
