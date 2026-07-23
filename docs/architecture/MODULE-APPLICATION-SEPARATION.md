# SANAD Module-to-Application Separation Program

**Program:** APP-SEP-001  
**Tracker:** GitHub Issue #701  
**Baseline branch:** `architecture/app-separation-system-health-20260723`  
**Decision:** replace deployment-time monolith coupling with independently owned applications through incremental extraction.

## 1. Current verified boundary

The repository currently has one primary Next.js application (`apps/web`) and one primary Spring Boot runtime (`apps/sanad-platform`). Internal packages and routes provide useful logical boundaries, but they are not yet independent build, deployment, failure, scaling or data boundaries.

This program does not classify a directory as an independent application unless all acceptance gates in section 7 pass.

## 2. Target application model

Each business or platform module becomes an application boundary with:

1. an explicit owner and bounded responsibility;
2. an independent build artifact;
3. an independent CI/CD pipeline;
4. independent runtime configuration and secrets;
5. an independently deployable and rollback-capable release;
6. an owned persistence boundary, or an explicit stateless declaration;
7. versioned HTTP/event contracts;
8. tenant, identity, authorization and audit contracts;
9. independent telemetry, SLOs and incident ownership;
10. tests that prohibit direct source, database and runtime coupling.

A module is **not** independent when it merely has a separate menu entry, package, controller, schema prefix or feature flag inside the existing runtime.

## 3. Mandatory architecture rules

### 3.1 Data ownership

- An application is the only writer to its owned data.
- Cross-application database joins and direct table access are prohibited.
- Reads across boundaries use versioned APIs, events or governed read models.
- Data migration uses expand/migrate/contract and must preserve rollback until cutover acceptance.

### 3.2 Identity and tenancy

- Identity authentication may use a shared identity provider.
- Every application validates the authenticated subject, tenant context and application-specific permissions.
- Executive roles do not automatically grant operational application access.
- Tenant context must be propagated explicitly and verified at every boundary.

### 3.3 Integration

- Synchronous calls require timeout, failure mapping, idempotency where applicable and circuit-breaking policy.
- Asynchronous contracts require an event version, producer owner, consumer compatibility policy and replay semantics.
- Shared Java/TypeScript libraries may contain contracts and SDK utilities only; they must not contain another application's business logic or persistence model.

### 3.4 Failure isolation

- Failure of Executive Management must not stop System Health or operational applications.
- Failure of an operational application must not stop unrelated applications.
- System Health must remain observable through an independent runtime path.

## 4. Extraction sequence

| Order | Application | Current source boundary | Start gate | Target state |
|---:|---|---|---|---|
| 1 | System Health & Self-Healing | new independent capability | authorized now | standalone application under `apps/system-health` |
| 2 | CRM | `apps/web/app/crm`, `com.sanad.platform.crm` | CRM-008 closed; PR #691 merged with exact-head evidence | standalone CRM web/service/data boundary |
| 3 | Identity & Access | auth, security and access packages | identity contract and session compatibility approved | independent identity/access service and administration UI |
| 4 | Tenant Administration | tenant packages and control-plane routes | identity boundary stable | independent tenant control application |
| 5 | Workflow | workflow runtime packages | command/event contract approved | independent workflow runtime and operator UI |
| 6 | AI Runtime | AI integration/runtime packages | workflow and tool contracts approved | independent AI runtime and governance UI |
| 7 | ERP | ERP bounded contexts | shared master-data contracts approved | independent ERP application set |
| 8 | Accounting | accounting bounded contexts | posting contracts and audit controls approved | independent accounting application |
| 9 | HRM | HRM bounded contexts | identity/employee boundary approved | independent HRM application |
| 10 | Commerce | commerce bounded contexts | customer/order contracts approved | independent commerce application |
| 11 | POS | POS bounded contexts | offline/sync contract approved | independent POS application |
| 12 | Analytics | read models and analytics | event/data-product contracts approved | independent analytics application |
| 13 | Executive Management | workspace and executive surfaces | all source application summary contracts stable | read-oriented executive application only |

## 5. Strangler execution per application

Every extraction follows the same governed sequence:

1. **Inventory:** enumerate routes, packages, tables, migrations, jobs, events, permissions and external dependencies.
2. **Contract:** publish versioned API/event contracts and compatibility tests while the current runtime remains authoritative.
3. **Isolate:** remove forbidden source and persistence dependencies using ports/adapters and architecture tests.
4. **Replicate safely:** construct the new owned data boundary through migration or event-fed projection.
5. **Shadow:** execute the new application without serving authoritative writes; compare outputs and telemetry.
6. **Canary:** route an explicitly bounded tenant/traffic slice with rollback enabled.
7. **Cut over:** move authority only after parity, tenant-isolation, security, performance and recovery gates pass.
8. **Contract:** remove obsolete monolith code and tables only after the rollback window closes.

## 6. First reference extraction: System Health

`apps/system-health` is the first independent reference application because it has no legitimate dependency on Executive Management and can establish the separation standard without modifying CRM-008.

Initial scope:

- independent Spring Boot artifact and process;
- independent static operational UI;
- versioned `/api/v1/system-health` contract;
- independent liveness/readiness probes and Prometheus endpoint;
- path-scoped CI and container build;
- no import or runtime dependency on `apps/web` or `apps/sanad-platform`;
- no shared database in the foundation increment.

The foundation is not yet a complete self-healing platform. Incident persistence, telemetry ingestion, dependency graph, runbooks and remediation execution require separate governed work packages.

## 7. Application independence acceptance gates

An application can be marked `INDEPENDENT` only when all items pass on an exact commit SHA:

- [ ] Builds and tests without building another application.
- [ ] Produces a distinct versioned artifact/container.
- [ ] Deploys and rolls back without deploying another application.
- [ ] Has independent secrets and runtime configuration.
- [ ] Has an owned database/schema instance or is explicitly stateless.
- [ ] Has no direct imports from another application's internal packages.
- [ ] Has no direct access to another application's tables.
- [ ] Publishes and validates versioned contracts.
- [ ] Enforces tenant isolation and application-specific authorization.
- [ ] Emits independent logs, metrics and traces.
- [ ] Has SLOs, alerts, runbooks and an operational owner.
- [ ] Passes failure-isolation and dependency-outage tests.
- [ ] Has canary and rollback evidence.
- [ ] Legacy routing and code removal are separately approved.

## 8. Governance constraints

- PR #691 remains governed by CRM-008 and is not modified by APP-SEP-001.
- CRM-009 implementation remains prohibited until its existing activation gates pass.
- No bulk source movement, shared-table shortcut or production cutover is authorized by this document.
- Current production/go-live authority remains controlled by the repository's current governance sources.
- Every extracted application requires its own issue, PR, exact-SHA evidence and closure decision.
