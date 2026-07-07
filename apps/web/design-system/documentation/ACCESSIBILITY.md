# SNAD Accessibility (WCAG 2.2 AA)

> **Target:** WCAG 2.2 Level AA
> **Stretch:** WCAG 2.2 Level AAA where feasible
> **Legal baseline:** Saudi Arabian NDOMC accessibility standard (mirrors WCAG 2.2 AA)

SNAD is an **Arabic-first, bidirectional** enterprise platform.
Accessibility is not optional — it is a legal requirement and a product
quality bar. Every component, page, and interaction must meet WCAG 2.2
AA. This document specifies how.

## 1. The four principles (POUR)

WCAG is organized around four principles. Every SNAD surface must be:

1. **Perceivable** — information and UI components must be presentable to
   users in ways they can perceive.
2. **Operable** — UI components and navigation must be operable.
3. **Understandable** — information and operation of UI must be
   understandable.
4. **Robust** — content must be robust enough to be interpreted by a wide
   variety of user agents, including assistive technologies.

## 2. Color contrast

### Body text (less than 18pt / 14pt bold)

Minimum contrast ratio: **4.5:1** against its background.

| Token pair | Ratio | Pass |
| --- | --- | --- |
| `text-primary` on `background-default` (light) | 16.4:1 | ✓ AAA |
| `text-secondary` on `background-default` (light) | 8.6:1 | ✓ AAA |
| `text-muted` on `background-default` (light) | 3.9:1 | ✗ — non-essential text only |
| `text-inverse` on `action-primary` (light) | 11.2:1 | ✓ AAA |
| `text-inverse` on `action-primary` (dark) | 7.4:1 | ✓ AAA |

### Large text (18pt+ / 14pt+ bold)

Minimum contrast ratio: **3.0:1**.

`text-muted` may be used for large text at 3.0:1+.

### UI components and graphics

Minimum contrast ratio: **3.0:1** against adjacent colors. Applies to:

* Button borders
* Icon-only button icons
* Form control borders
* Focus indicators
* Chart series (when color is the only differentiator — also use shape
  or pattern)

### The `text-muted` exception

`--snad-color-text-muted` (3.9:1 on default background) falls just below
the 4.5:1 AA threshold for body text. It may be used **only** for:

* Placeholder text inside inputs (placeholders are not essential — the
  label is)
* Timestamps on cards
* Disabled control labels
* Tertiary metadata (e.g. "edited 2 hours ago" on a comment)

It must **not** be used for body copy, captions that carry essential
information, or any text the user is expected to read.

## 3. Focus management

### Visible focus

Every interactive element must have a visible focus indicator when
focused via keyboard:

```css
:focus-visible {
  outline: 3px solid var(--snad-color-focus-ring);
  outline-offset: 2px;
  border-radius: var(--snad-radius-sm);
}
```

* `:focus-visible` (not `:focus`) so mouse clicks don't show focus rings.
* 3px thickness (WCAG 2.4.13 minimum is 2px; we go thicker for clarity).
* `outline-offset: 2px` so the ring doesn't overlap the element's border.
* Never set `outline: none` without providing an alternative visible
  focus indicator.

### Focus order

Tab order must follow the visual reading order:

* **LTR layouts**: left-to-right, top-to-bottom.
* **RTL layouts**: right-to-left, top-to-bottom.

Use `tabindex="0"` only when the element is not naturally focusable
(e.g. a `<div>` acting as a button — but you should use a real `<button>`
instead). Never use `tabindex` > 0 — it breaks DOM-order tabbing.

### Focus trapping

Modals, drawers, and popovers must trap focus while open and restore
focus to the trigger element on close. Use `@radix-ui/react-focus-scope`
or an equivalent primitive.

### Skip links

Every page must have a "Skip to main content" link as the first
focusable element:

```tsx
<a href="#main" className="snad-skip-link">تخطّي إلى المحتوى الرئيسي</a>
<main id="main">...</main>
```

```css
.snad-skip-link {
  position: absolute;
  top: -100px;       /* off-screen until focused */
  left: 0;
  background: var(--snad-color-action-primary);
  color: var(--snad-color-text-inverse);
  padding: var(--snad-space-3) var(--snad-space-4);
  z-index: var(--snad-z-toast);
}
.snad-skip-link:focus {
  top: var(--snad-space-2);
}
```

## 4. Target size (WCAG 2.5.5 / 2.5.8)

* **AA minimum (2.5.8)**: 24 × 24 px target.
* **AAA stretch (2.5.5)**: 44 × 44 px target.

SNAD targets AAA. Every interactive element (button, link, checkbox,
radio, switch, icon button, tab, pagination button) must have a minimum
44 × 44 px hit area. Visual size may be smaller; the hit area may be
padded with `padding` or an `::after` pseudo-element.

## 5. Keyboard navigation

Every interactive element must be operable via keyboard alone:

| Element | Key | Action |
| --- | --- | --- |
| Button | Enter, Space | Activate |
| Link | Enter | Navigate |
| Checkbox | Space | Toggle |
| Radio | Arrow keys | Move between options in same group |
| Tab | Arrow keys | Move between tabs |
| Combobox | Arrow keys, Enter, Esc | Navigate, select, dismiss |
| Modal | Esc | Close |
| Drawer | Esc | Close |
| Menu | Arrow keys, Esc | Navigate, dismiss |

Never intercept global keyboard shortcuts (e.g. `Cmd+K`, `Ctrl+S`) for
product features without checking that the user isn't in a screen reader
or other AT context.

## 6. Screen reader support

### Semantic HTML

Use real HTML elements, not `<div>` hacks:

* `<button>` for buttons (not `<div onclick>`)
* `<a href>` for links (not `<div onclick>`)
* `<label for>` for form labels (not `<span>`)
* `<fieldset>` + `<legend>` for radio groups (not `<div>`)
* `<table>` with `<th scope>` for data tables (not `<div>` grids)
* `<nav>`, `<main>`, `<aside>`, `<header>`, `<footer>` for landmarks

### ARIA

Use ARIA only when semantic HTML can't express the pattern:

* `role="dialog"` for modal dialogs
* `aria-modal="true"` on open modals
* `aria-expanded` on disclosure buttons
* `aria-haspopup` on menu triggers
* `aria-live="polite"` for toast regions
* `aria-live="assertive"` for error announcements
* `aria-busy="true"` on regions being fetched

Never use `aria-label` to override visible text — the accessible name
should match the visible label.

### Live regions

Toasts, form errors, and dynamic content updates must be announced:

```tsx
<div role="status" aria-live="polite">
  {toastMessage}
</div>
```

For errors that block form submission:

```tsx
<div role="alert" aria-live="assertive">
  {errorMessage}
</div>
```

## 7. Motion and animation

### Reduced motion

Honor the user's OS preference:

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

### No flashing

Never flash content more than 3 times per second (WCAG 2.3.1). The SNAD
design system's motion tokens cap transitions at 320ms — well under the
flash threshold.

## 8. Bidirectional support

Every SNAD surface must work in both LTR (English) and RTL (Arabic):

* Use logical properties (`margin-inline-start`, not `margin-left`).
* Set `dir="rtl"` on `<html>` for Arabic and `dir="ltr"` for English.
* Set `lang="ar"` or `lang="en"` on `<html>`.
* Test every component in both directions — see
  [RTL_LTR_GUIDE.md](./RTL_LTR_GUIDE.md).

## 9. Forms

* Every input has a `<label for>` — never rely on placeholder text.
* Error messages are linked via `aria-describedby` and have `role="alert"`.
* Required fields are marked with `aria-required="true"` AND a visible
  asterisk (the asterisk must be announced, e.g. via visually-hidden
  text "مطلوب" / "required").
* Inline validation announces via `aria-live="polite"`.
* Submission errors announce via `aria-live="assertive"`.

## 10. Color and meaning

Never rely on color alone to convey meaning:

* Error states: red border + icon + text label
* Success states: green border + icon + text label
* Required fields: red asterisk + text label
* Selected tab: highlighted background + `aria-selected="true"`
* Disabled controls: gray background + `aria-disabled="true"` + cursor

## 11. Testing

### Automated

* `@axe-core/playwright` in CI on every PR
* `jest-axe` on every component unit test
* `lint` script catches hardcoded colors that would break contrast

### Manual

* Keyboard-only walkthrough of every flow (no mouse)
* NVDA + Firefox on Windows (Arabic screen reader, primary AT in Saudi
  market)
* VoiceOver + Safari on macOS (secondary AT)
* Zoom to 200% — no horizontal scroll, no overlapping content
* Zoom to 400% — content reflows to single column

### User research

* Quarterly usability test with Saudi users who rely on screen readers
* Annual third-party accessibility audit
