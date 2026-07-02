# CRM UI License Review

## Decision

The CRM deployment-completion work introduces **no new frontend package or runtime dependency**.

The pipeline board uses the browser HTML Drag and Drop API plus React keyboard and button events. The virtualized table uses React state, scroll events, and semantic HTML tables. No drag-and-drop, grid, virtualization, or accessibility library was added.

## Reviewed package surface

The implementation remains within the packages already declared in `apps/web/package.json`:

| Package | Use in this change | License review result |
|---|---|---|
| `next` | Existing application framework | Existing approved dependency; no version change |
| `react` | Components, state, events | Existing approved dependency; no version change |
| `react-dom` | Existing browser renderer | Existing approved dependency; no version change |
| `vitest` | Interaction and utility tests | Existing development dependency; no version change |
| `@testing-library/react` | Accessible component tests | Existing development dependency; no version change |
| `@testing-library/user-event` | Keyboard/button interaction tests | Existing development dependency; no version change |

## Accessibility implementation

- Opportunities are draggable for pointer users.
- Every movement has explicit **previous** and **next** buttons.
- `Alt+ArrowRight` and `Alt+ArrowLeft` provide keyboard movement.
- Stage changes are announced through an `aria-live` region.
- Cards, stages, and virtualized tables retain semantic labels and row counts.
- Focus-visible outlines are present for controls, cards, and virtualized scroll regions.

## License gate

```text
NEW THIRD-PARTY UI DEPENDENCIES: NONE
NEW LICENSE OBLIGATIONS: NONE
COPYLEFT RUNTIME INTRODUCTION: NONE
LICENSE REVIEW: PASSED FOR THIS CHANGESET
```

This review applies only to the CRM files changed by the deployment-completion branch. It does not replace repository-wide software-composition analysis.
