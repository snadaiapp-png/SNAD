# SNAD Component Usage (Phase 2 placeholder)

> **Status:** Placeholder. Full component documentation ships in SDS 2.1
> alongside the React component library (`@snad/ui`).

This document will be expanded in SDS 2.1 to cover:

* Button (primary, secondary, ghost, danger, accent)
* Card (default, elevated, interactive)
* Input (text, password, number, search, with leading/trailing icon)
* Select (single, multi, searchable)
* Modal (default, fullscreen, drawer)
* Drawer (left, right, bottom)
* Tooltip
* Popover
* Toast (success, warning, error, info)
* Badge (default, brand, gold, success, warning, error, info)
* DataTable (sortable, filterable, paginated, virtualized)
* Tabs (horizontal, vertical, pill, underline)
* Accordion
* Breadcrumb
* Pagination
* Skeleton
* Spinner
* ProgressBar
* Avatar (image, initials, brand fallback)
* Tag (removable, multi-line)
* Stepper (horizontal, vertical)
* Calendar (single date, range)
* ColorPicker (petroleum / gold presets only — no free hex input)

## Foundation conventions (apply to all components)

While the components themselves ship in 2.1, every team writing custom UI
today must follow these conventions:

### 1. Token-first

Every visual property references a token:

```css
.snad-button {
  background: var(--snad-color-action-primary);
  color: var(--snad-color-text-inverse);
  border-radius: var(--snad-radius-md);
  padding: var(--snad-space-3) var(--snad-space-4);
  font: var(--snad-text-button);
  box-shadow: var(--snad-shadow-sm);
  transition: background var(--snad-motion-fast) var(--snad-motion-ease-standard);
}
```

### 2. BEM with `snad-` prefix

CSS classes follow BEM with a `snad-` block prefix:

```css
.snad-button              /* block */
.snad-button__icon        /* element */
.snad-button--primary     /* modifier */
.snad-button--primary.snad-button--disabled  /* stacked modifiers */
```

### 3. State tokens, not state colors

Every interactive component supports these states, each with its own
semantic token mapping:

| State | Background | Text | Border |
| --- | --- | --- | --- |
| Default | `action-primary` | `text-inverse` | `action-primary` |
| Hover | `action-primary-hover` | `text-inverse` | `action-primary-hover` |
| Active | `action-primary-active` | `text-inverse` | `action-primary-active` |
| Focus-visible | `action-primary` | `text-inverse` | `focus-ring` (3px outline) |
| Disabled | `disabled-background` | `disabled` | `disabled` |

### 4. Accessibility

* Every interactive element has a visible focus ring
  (`outline: 3px solid var(--snad-color-focus-ring); outline-offset: 2px;`).
* Every interactive element has a 44 × 44 px minimum hit area (WCAG 2.5.5
  Target Size — Enhanced).
* Every form control has an associated `<label>` (not a placeholder
  replacement).
* Every icon-only button has an `aria-label`.
* Every modal traps focus and restores it on close.

### 5. Bidirectional support

Every component must work in both LTR and RTL without conditional styles:

```css
/* ❌ Wrong — breaks in RTL */
.snad-icon-button { padding-left: var(--snad-space-2); }

/* ✅ Right — uses logical property */
.snad-icon-button { padding-inline-start: var(--snad-space-2); }
```

Use logical properties everywhere: `padding-inline-start`,
`margin-block-end`, `border-inline-end`, `inset-inline-start`, etc.

### 6. Theme switching

Every component inherits theme from its parent. No component should
hardcode `data-theme` or check `prefers-color-scheme` directly. The
`--snad-color-*` tokens auto-resolve to the right values based on the
nearest `data-theme` ancestor.

### 7. File layout (when components ship in 2.1)

```
design-system/components/
├── button/
│   ├── button.css            ← styles
│   ├── button.tsx            ← React component
│   ├── button.test.tsx       ← unit + a11y tests
│   └── button.stories.tsx    ← Storybook stories (light + dark)
├── card/
│   └── ...
└── ...
```

## Migration plan

| Phase | Scope | ETA |
| --- | --- | --- |
| 2.0 (this PR) | Tokens, themes, lint, docs | shipped |
| 2.1 | Button, Card, Input, Modal, Toast, Badge | +2 weeks |
| 2.2 | DataTable, Tabs, Accordion, Pagination | +4 weeks |
| 2.3 | Calendar, Stepper, Drawer, Popover | +6 weeks |
| 2.4 | Migration of legacy components (crm.module.css etc.) | +8 weeks |

If you need a component that hasn't shipped yet, build it using the
foundation conventions above. The lint script will keep you honest about
token usage.
