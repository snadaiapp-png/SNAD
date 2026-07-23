# CRM-010 Acceptance Traceability

Control issue: #705

| Backlog item | Preparation work package | Required executable evidence | Exit gate |
|---|---|---|---|
| CRM-010-S1 Tenant isolation | WP-01, WP-02 | endpoint inventory; two-tenant API and PostgreSQL tests; forged-context negatives | zero unclassified operations and zero cross-tenant exposure |
| CRM-010-S2 Capability/field authorization | WP-01, WP-03 | capability matrix; positive/negative tests; field redaction/mutation tests | deny-by-default and no UI-only authorization claim |
| CRM-010-S3 Migration/recovery | WP-04 | clean install; upgrade; Flyway validation; partial-failure and recovery evidence | migration integrity proven on exact SHA; production restore separately gated |
| CRM-010-S4 API/event compatibility | WP-05 | committed OpenAPI diff; consumer/event compatibility suite | no undocumented breaking change |
| CRM-010-S5 Arabic/English UI | WP-06 | RTL/LTR, accessibility, responsive and localized error tests | both locales release-blocking |
| CRM-010-S6 Logs/metrics/traces/dashboards | WP-07 | telemetry contract tests; redaction checks; dashboard definitions | no secret/restricted payload leakage; bounded cardinality |
| CRM-010-S7 SLO/alerts | WP-08 | measured SLI definitions; candidate objectives; alert tests | objectives approved from evidence; isolation alerts immediate |
| CRM-010-S8 Import/search performance | WP-09 | reproducible benchmarks and regression reports | thresholds tied to representative measurements |
| CRM-010-S9 Runbook/recovery guide | WP-10 | reviewed runbook drills and evidence checklist | ownership, escalation and recovery decisions are explicit |

## Evidence identity

Every accepted artifact must record:
- repository and branch;
- exact 40-character commit SHA;
- workflow/run identifier;
- test command and environment;
- dependency/database versions;
- start/end timestamps;
- result and failure count;
- artifact digest;
- known exclusions and residual risks.

## Prohibited acceptance shortcuts

- accepting checks from another SHA;
- treating compilation as runtime acceptance;
- replacing PostgreSQL evidence with H2-only evidence;
- using UI route hiding as authorization proof;
- accepting unexplained HTTP 5xx;
- skipping Arabic or English paths;
- suppressing Critical/High security findings;
- claiming production readiness from preparation artifacts;
- merge, issue closure, deployment, publication or production mutation from this branch.
