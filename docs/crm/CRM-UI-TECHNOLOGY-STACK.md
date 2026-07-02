# CRM UI Technology Stack

## Current platform baseline

- Next.js 16.2.9.
- React 19.2.4.
- TypeScript 5.9.3.
- Tailwind CSS 4.
- Vitest 4.

## Approved CRM libraries

The dependency PR must select the newest stable releases compatible with the current Next.js and React baseline and must update both `package.json` and `package-lock.json` through the package manager.

### Data and server state

- `@tanstack/react-query` for caching, retries, optimistic updates, invalidation, and offline-aware server state.
- `@tanstack/react-table` for headless enterprise tables.
- `@tanstack/react-virtual` for large customer, activity, and opportunity lists.

### Drag and drop

- `@atlaskit/pragmatic-drag-and-drop` as the primary system-wide drag-and-drop engine.
- Use it for pipelines, dashboard layout, field/layout designer, queues, activity planning, automation canvas, and import mapping.
- Every drag operation requires a keyboard-accessible alternative and an auditable domain command.

### Forms and validation

- `react-hook-form` for high-performance forms.
- `zod` for shared runtime validation and typed form schemas.
- Backend validation remains authoritative.

### Client state and accessibility

- `zustand` for bounded UI state that is not server state.
- `react-aria-components` for accessible interactive primitives.
- `next-intl` for Arabic/English messages, locale routing, and formatting.

### Visual intelligence

- `echarts` with a React adapter for pipeline, forecast, activity, and customer-health analytics.
- `lucide-react` for consistent icons.
- `date-fns` for date operations.
- `decimal.js` for UI-side decimal calculations; accounting remains authoritative in the finance domain.
- `libphonenumber-js` for global phone parsing and display.

### AI experience

- Use the approved central AI Gateway contract.
- A frontend AI SDK may be introduced only as a transport/UI layer; it must not contain model-provider credentials or bypass AI governance.

## System-wide drag-and-drop surfaces

1. Opportunity pipeline cards between stages.
2. Custom dashboard widgets and responsive layout.
3. Account and contact page-layout designer.
4. Custom-field palette and form sections.
5. Lead and activity assignment queues.
6. Workflow and automation canvas through Workflow Engine contracts.
7. CSV import mapping.
8. Customer 360 timeline ordering where business policy permits.
9. Saved views, filters, columns, and grouping.

## Dependency controls

- No floating or unbounded versions.
- Lockfile update is mandatory.
- License and supply-chain review is mandatory.
- Bundle-size budget and tree-shaking validation are mandatory.
- Dependency, secret, and container scans must pass.
- Libraries must support React 19 and current browser policy.
- Avoid duplicate state, form, table, or drag-and-drop frameworks.

## Current status

```text
STACK DECISION: APPROVED
DEPENDENCIES INSTALLED: NO
BLOCKER: GitHub Actions and package-manager evidence must be restored
NEXT ACTION: create a dedicated dependency PR with lockfile and build evidence
```
