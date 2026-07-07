# SNAD Brand Change Process

> **Authority:** SNAD Executive Office
> **Custodian:** `design@snad.ai`
> **Default answer:** no.

This document specifies the formal process for changing any element of
the SNAD brand or the SNAD Design System. The bar is deliberately high —
brand consistency is harder to rebuild than to maintain.

## 1. What counts as a "brand change"

A brand change is **any** modification to:

| Category | Examples | Approval required |
| --- | --- | --- |
| Brand name | `SNAD` → `SNAD ERP` | Executive Office + Board |
| Primary color | `#0E3D38` → `#0E3F40` | Executive Office |
| Accent color | `#D4AF37` → `#D5B038` | Executive Office |
| Logo lockup | Glyph, wordmark, arrangement | Executive Office |
| Primary typeface | `Tajawal` → `Cairo` | Design custodian + Executive Office |
| Tagline | `Trust, engineered.` → `…` | Marketing + Executive Office |
| Brand voice | "Confident, precise, trustworthy" → `…` | Marketing + Executive Office |

A design system change (not brand) is any modification to:

| Category | Examples | Approval required |
| --- | --- | --- |
| Semantic token values | `--snad-color-text-primary` value | Design custodian |
| Token addition | New `--snad-color-banner-promo` | Design custodian |
| Token removal | Deprecate `--snad-color-text-muted` | Design custodian + audit |
| Scale addition | Add `--snad-color-petroleum-25` | Design custodian |
| Component convention | BEM → utility classes | Design custodian |
| Lint rule | Tighten or relax `check-design-system-compliance.py` | Design custodian |

## 2. The process (Brand changes)

### Step 1 — Open a brand-change request

Open a GitHub issue using the `brand-change-request` template. The
template prompts for:

1. **What** is changing (be specific — exact hex codes, exact wordmark
   string, exact logo lockup).
2. **Why** it's changing (business rationale, not aesthetic preference).
3. **What problem** the current brand element causes (with examples —
   screenshots, user feedback, support tickets).
4. **Three visual mockups** of the proposed change in context (not in
   isolation — show it on a real screen, a real email, a real business
   card).
5. **Impact assessment** — what existing assets need updating (logo
   files, marketing collateral, app icons, social cards, email
   templates, partner integrations).
6. **Rollback plan** — if the change is reverted, what's the cost?

### Step 2 — Custodian triage (5 business days)

The design custodian (`design@snad.ai`) reviews the request and either:

* **Closes** with rationale ("not a brand change — see SDS change process
  instead" / "no business rationale provided" / "changes brand
  irreversibly without justification").
* **Requests more information** (mockups missing, impact assessment
  incomplete).
* **Forwards to Executive Office** with a recommendation.

### Step 3 — Executive review (10 business days)

The Executive Office reviews the request and either:

* **Rejects** with rationale.
* **Approves** in principle, pending implementation details.
* **Escalates to Board** (for brand-name or primary-color changes only).

### Step 4 — Implementation (1–4 weeks)

Once approved, the implementation:

1. Updates `apps/web/design-system/tokens/tokens.json` (machine source of
   truth).
2. Updates `apps/web/design-system/tokens/theme.css` (CSS export).
3. Updates `apps/web/app/snad-tokens.css` (legacy shim).
4. Updates `apps/web/app/snad-tailwind.css` (Tailwind bridge).
5. Runs the lint script: `python3 scripts/ci/check-design-system-
   compliance.py`.
6. Updates visual regression baselines:
   `pnpm --filter web test:visual -- --update-snapshots`.
7. Updates the documentation (BRAND_GOVERNANCE.md, DESIGN_TOKENS.md,
   COLOR_SYSTEM.md as relevant).
8. Bumps the SDS version: `meta.version` in `tokens.json`.
9. Opens a PR titled `feat(sds): brand change — [summary]`.
10. PR description links to the original brand-change-request issue.

### Step 5 — Rollout (1–2 weeks)

Once the PR merges:

1. The new tokens ship with the next web deploy.
2. The brand team updates offline assets (logo files, business cards,
   print collateral).
3. The marketing team updates the website, social media, and email
   templates.
4. The partner integrations team notifies partners of the change.
5. The custodian files a "Brand Change Record" in
   `apps/web/design-system/documentation/CHANGELOG.md` with the date,
   rationale, and before/after values.

### Step 6 — Rollback window (30 days)

For 30 days after rollout, the custodian monitors:

* User feedback (support tickets mentioning the change)
* Engineering feedback (broken layouts, contrast failures)
* Designer feedback (mockup friction)
* Brand sentiment (social media mentions)

If a critical issue is found, the custodian can roll back the change by
reverting the PR. After 30 days, rollback requires a new brand-change
request.

## 3. The process (Design System changes)

### Step 1 — Open an SDS change request

Open a GitHub issue using the `sds-change-request` template. The
template prompts for:

1. **What** is changing (token name, current value, proposed value).
2. **Why** it's changing (use case, component that needs it, problem
   with current value).
3. **Impact assessment** — which components use this token today?
4. **Visual mockups** — before / after on at least one component.

### Step 2 — Custodian review (3 business days)

The custodian reviews and either:

* **Closes** with rationale ("duplicate of existing token" / "use case
  doesn't justify a new token" / "would break semantic mapping").
* **Approves** — moves to Step 3.
* **Requests more information**.

### Step 3 — Implementation (1–5 days)

Same as brand change Steps 4.1–4.9, but:

* PR title: `feat(sds): [token name] — [change summary]`
* Major version bump if a token *value* changes.
* Minor version bump if a new token is added.
* Patch version bump for documentation-only changes.

### Step 4 — Rollout

Same as brand change Step 5, but no offline-asset updates needed.

## 4. Emergency changes

If a brand or design system element is causing **active user harm** (e.g.
a contrast failure that makes text unreadable for low-vision users, a
color that triggers photosensitive seizures), the custodian can ship a
hotfix without going through the full process:

1. Open the PR with the fix.
2. Tag the PR with `emergency-brand-fix`.
3. Notify the Executive Office within 24 hours.
4. File a retroactive brand-change-request issue within 5 business days.

Emergency changes are rare (target: 0 per year). Abuse of this path is a
performance issue.

## 5. Versioning

SDS follows **semantic versioning**:

| Change type | Bump | Example |
| --- | --- | --- |
| Token *value* changes (even one hex digit) | **major** | `#0E3D38` → `#0E3F40` |
| Token removed | **major** | remove `--snad-color-text-muted` |
| New token added (no value change) | **minor** | add `--snad-color-banner-promo` |
| Documentation-only change | **patch** | fix a typo in this file |
| Lint rule tightened | **minor** | flag named CSS colors |
| Lint rule relaxed | **patch** | allow `inherit` for font-family |

Every PR that touches `tokens.json` or `theme.css` must bump
`meta.version` in `tokens.json` according to this table.

## 6. Audit

Every quarter, the custodian runs a brand audit:

1. Visually inspect every app route in light + dark, LTR + RTL.
2. Run the lint script and confirm 0 violations.
3. Review the brand-change-request issue queue.
4. Review the SDS change log for the quarter.
5. File a "Q[N] Brand Audit Report" in
   `apps/web/design-system/documentation/audit-reports/`.

Annual audits are reviewed by the Executive Office.
