# SNAD MODULE VISUAL CONTRACT — Canonical v1.0

Date: 2026-10-02 · Authority: this contract · Derived from: CRM reference architecture (crm-shell.tsx + crm-shared-styles.module.css) generalized to module-neutral primitives, bound to SDS tokens (design-system/tokens/theme.css).

## 1. Rule of Consumption

Every SNAD web module (CRM, HR, future modules) MUST render its product shell through the shared module primitives in `apps/web/components/sds/module/`. Modules differ ONLY in: content, labels, routes, capabilities, domain data. No module may define its own shell geometry, sidebar, header, active-nav treatment, or state surfaces.

## 2. Primitive Registry (components/sds/module/)

| Primitive | Purpose | Key contract |
|-----------|---------|--------------|
| SnadModuleShell | 100vh flex-column module root on ivory canvas: sticky HEADER (brand tile + gold dot + titles + user + actions), 264px SIDEBAR (sectioned NAV with 16×16 icons, active = brand fill + gold icon, aria-current), CONTENT region + 1180px inner max | props: brandMark, brandHref, title, subtitle, navSections (visibility resolved by caller), activeHref (longest-prefix wins), navLabel, contentId, dir, user, language/back/logout handlers, authLoading |
| SnadPageHeader | page title 1.5rem/800 + description + eyebrow + trailing | module-neutral |\n| SnadPageFrame | canonical 1180px content stack combining shared page header + 22px vertical rhythm | module-neutral; use for migrated module pages |
| SnadKpiGrid / SnadKpiCard | KPI grid + white card w/ 4px gold inline-start bar, numeric font | tone support |
| SnadOperationalPanel | white section card + brand-bar section title | header slot + body |
| SnadActionBar | action row surface | wraps controls |
| SnadStatusBadge | status pill: dot + soft bg + strong border | tone: success/warning/info/danger/neutral; color never sole signal |
| SnadLoadingState | centered spinner + min-height 320 | reduced-motion respected |
| SnadEmptyState | centered dashed card + icon disc | title/description/hint/action |
| SnadErrorState | soft error surface + retry button | role=alert |
| SnadMobileRecordList | hidden grid → visible ≤47.999rem | mobile record cards |
| icons.tsx | shared 16×16 stroke icon set (currentColor, aria-hidden) | overview/people/org/jobs/positions/assignments/compliance/recruitment/onboarding/goals/reviews/attendance/timesheet/leave/team/approvals/schedules/admin/policy/report/execution/lang/back/logout + CRM set |

## 3. Token + Style Rules

1. All styles live in `components/sds/module/snad-module.module.css`; every value references `var(--snad-*)`. Zero raw colors/fonts (theme.css is the only color source of truth).
2. Spacing scale: 6/8/10/12/14/16/22/26/40px. Radius scale: 8/9/10/12/14/16px. Shadows: `--snad-shadow-sm|md|lg` only.
3. Focus: `outline: 3px solid var(--snad-focus-ring); outline-offset: 2px` on all interactive primitives.
4. RTL/LTR: logical properties only (margin-inline/padding-inline/inset-inline); shell root receives `dir`.
5. Breakpoints: 768px (sidebar → horizontal scroll row; header condenses), 480px (single column, action labels hide), 47.999rem (mobile record list visible).
6. Motion: `--snad-motion-fast|slow` + `--snad-motion-ease-standard|out`; `prefers-reduced-motion` disables spinners/transitions.
7. Targets: interactive nav/action elements ≥44px where pointer-reachable per SDS.
8. Theme: inherit tokens; no `data-theme` branching inside primitives.

## 4. State Vocabulary (shared)

AUTH_LOADING (shell-level AuthLoadingState) → LOADING → EMPTY/READY; FORBIDDEN (role=alert dedicated surface); MUTATION_BUSY (disabled controls); VALIDATION_ERROR (role=alert, not sent to backend); BACKEND_ERROR (role=alert + retry); SUCCESS_NOTICE (role=status). Errors are never converted to empty states.

## 5. Enforcement

- `scripts/ci/check-module-visual-compliance.py` fails closed on: hardcoded colors/fonts outside theme sources, non-token shadows, module-local palettes, duplicate shell implementations (module CSS defining shell+sidebar+header geometry), RTL-hostile physical properties, shell patterns outside shared primitives (allowlist-documented).
- Vitest visual-contract suite asserts CRM and HR consume the same primitives; synthetic non-compliant module fixture must FAIL the compliance script (self-test).

## 6. Legacy

Legacy modules register in the Visual Migration Ledger (this directory). New surfaces must comply immediately; legacy surfaces comply upon their next authorized task.
