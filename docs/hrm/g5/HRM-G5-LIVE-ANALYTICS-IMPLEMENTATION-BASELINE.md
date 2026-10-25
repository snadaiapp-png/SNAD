# HRM G5 Live Analytics and AI Insights — Implementation Baseline

**Status:** Discovery baseline; implementation not yet certified.
**Baseline commit:** `b75c97dad333d33992c2dc2a2b8b3aee18d9ba6d`
**Tracking issue:** [#1447](https://github.com/snadaiapp-png/SNAD/issues/1447)
**Branch:** `feat/hrm-g5-live-ai-analytics`

## Verified repository observations

- HR execution roadmap data is maintained in `apps/web/app/hr/hr-execution-data.ts`; G5 is named Analytics & Reporting. Do not mark G5 done based on this planning document.
- Existing attendance report is `apps/web/app/hr/reports/attendance/page.tsx`. It is a client page using `hrG2Api.adminMonthlyAttendanceReport(year, month)` or `hrG2Api.teamMonthlyAttendanceReport(year, month)`, gated by the `HRM.ATTENDANCE.ADMIN` and `HRM.ATTENDANCE.TEAM_VIEW` capabilities.
- The existing attendance report loads on mount and when year/month changes; this page does not currently establish a live push stream or periodic refresh.
- HR navigation is defined in `apps/web/app/hr/components/hr-workspace.tsx` through `HR_WORKSPACE_LINKS` and capability-based visibility.
- Web app scripts include `test` (Vitest), `typecheck` (tsc --noEmit), `lint` (ESLint), and `build` (Next build with integrity validation).
- No live event transport or AI provider integration was established by the files inspected during this discovery pass. Do not assume SSE, WebSocket, realtime, an LLM provider, or a metrics API exists without further code/config inspection.

## Implementation sequence

### G5-T01 — Data contract and metric definitions
- Inventory existing HRM API methods, backend controllers/services, schemas, RLS policies, and tenant context.
- Define metric semantics, time windows, source timestamps, freshness thresholds, and owner/source for every KPI.
- Do not infer payroll status metrics until G4 acceptance evidence is resolved.

### G5-T02 — Secure aggregation API
- Prefer server-side aggregation and existing authenticated transport conventions.
- Enforce capabilities, tenant scope, and row-level security in the authoritative backend.
- Return `generatedAt`, source watermark/freshness, and explicit partial/unavailable states.
- Add cross-tenant negative tests; never use superuser or BYPASSRLS as isolation evidence.

### G5-T03 — Live update transport and dashboard
- Inspect existing realtime infrastructure first; select the supported transport based on repository evidence.
- If polling is necessary, use a bounded interval, abort in-flight requests, avoid duplicate subscriptions, back off after failures, and show stale/offline states.
- Add the dashboard route and capability-gated navigation only after the API contract is defined.

### G5-T04 — Interactive analysis
- Add date and organizational filters, period comparison, and permission-checked drill-down.
- Keep accessible table alternatives, loading/empty/error states, and Arabic/English RTL/LTR support.

### G5-T05 — AI insights
- First establish an approved model/provider and data-handling policy.
- Supply only minimum necessary, appropriately de-identified aggregate context unless privacy/security review authorizes otherwise.
- Return typed claims with supporting metric IDs, time window, rationale, limitations, and uncertainty.
- Clearly separate measured facts, derived interpretations, and forecasts. If data is insufficient, return an explicit insufficient-data result.
- AI output is advisory only; no automated hiring, termination, promotion, discipline, or payroll decisions.

### G5-T06 — Verification and closure
- Unit, API integration, RLS/tenant isolation, role matrix, stale/failure/reconnect, accessibility, performance, and AI output-contract tests.
- Bind all evidence to the exact tested commit; require independent security review and explicit product-owner acceptance.
- Do not close issue #1447 until acceptance evidence is complete.

## Non-negotiable release controls

This branch and plan do not change any release gate. Current project controls remain:
- `G1 = HOLD`
- `P0 SECURITY HOLD = ACTIVE`
- `NO MERGE`
- `NO DEPLOY`
- `PAYMENTS = OFF`
- `commercial_go_live = NOT_APPROVED`

No merge, deployment, production authorization, payments activation, or commercial go-live is part of this work.
