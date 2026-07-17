# SANAD Status Documentation Policy

**Effective:** 2026-07-17  
**Owner:** Project Owner  
**Responsible:** Program Management and Release Management

## 1. Purpose

This policy prevents historical stage reports, old deployment observations and scope-specific closure records from being interpreted as the current state of the SANAD platform.

## 2. Authority order

Current platform status is determined in this order:

1. GitHub Issue `#516` for the executive remediation checklist.
2. `docs/governance/CURRENT-STATUS.json` for machine-readable status.
3. `docs/governance/CURRENT-IMPLEMENTATION-STATUS.md` for the human-readable summary.
4. `docs/governance/EXECUTIVE-REVIEW-REMEDIATION-2026-07-17.md` for detailed evidence and closure criteria.
5. Exact-SHA CI, deployment and runtime evidence referenced by those records.

A lower-ranked document cannot override a higher-ranked source.

## 3. Document classes

### Current authority

A current-authority document must:

- contain `STATUS_AUTHORITY: CURRENT`;
- state an `as of` date and timezone;
- identify its governing issue or exact evidence source;
- distinguish implemented, verified, deployed and accepted states;
- list open and deferred risks explicitly;
- avoid treating a passing build or HTTP health result as commercial approval.

### Historical evidence

A historical document records what was observed or approved for a specific stage, scope, SHA or date. It must show a visible `DOCUMENT STATUS: HISTORICAL` banner and point to the current status authority.

Statements such as `Production: LIVE`, `READY`, `GO`, `COMPLETE` or `PASS` inside historical documents apply only to the recorded scope and date. They are not current platform declarations.

### Planning baseline or template

A plan, checklist or template must show `DOCUMENT STATUS: PLANNING BASELINE` or `DOCUMENT STATUS: TEMPLATE`. Unchecked targets and desired architecture are not evidence that the target exists.

### Module or execution evidence

Module-level and execution-prompt records may close their own scope. They must not claim that SANAD has no platform blockers unless the executive authority record supports that statement.

## 4. Prohibited status practices

The following are prohibited:

- embedding an unqualified current-state claim in a long-lived historical document;
- reporting a closed GitHub issue as open or an open issue as closed;
- using an obsolete SHA or endpoint as the current production baseline;
- treating stage closure as broad commercial go-live approval;
- treating missing telemetry as success;
- copying status text between documents without a governing reference;
- using `no blockers`, `production ready` or equivalent language without a declared scope and evidence version.

## 5. Status changes

A material status change requires:

1. Exact issue, PR, commit, workflow, deployment or runtime evidence.
2. Update to `CURRENT-STATUS.json`.
3. Update to `CURRENT-IMPLEMENTATION-STATUS.md` and Issue `#516` when applicable.
4. Classification of superseded status documents in `status-document-registry.json`.
5. Passing `Status Documentation Validation` on the exact proposed SHA.

## 6. Historical retention

Historical evidence is retained for auditability. It is not deleted merely because the runtime changed. The visible banner and registry classification prevent it from competing with the current source of truth.

## 7. Review cadence

Program Management reviews the current status records after every material release or risk decision and at least monthly. Any detected conflict is a governance defect and blocks status publication until reconciled.
