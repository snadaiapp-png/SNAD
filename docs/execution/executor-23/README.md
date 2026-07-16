# Executor #23 — SANAD Master Execution Backlog

## Status

This package is the authoritative, deterministic implementation of **Executor #23 — SANAD Master Execution Backlog**.

It converts the approved SANAD platform scope into an execution hierarchy that can be validated and exported without manually rewriting work items:

```text
Portfolio
└── Epic
    └── Feature
        └── User Story
            └── Task
```

Backend hosting and backend-tunnel remediation are explicitly deferred and are not treated as closed by this package.

## Coverage

The generated backlog covers 20 governed platform streams:

1. Foundation and Strategy
2. Enterprise Architecture
3. SaaS Core
4. Infrastructure and DevOps
5. Security, Governance and Compliance
6. Workflow Engine
7. AI Core and Agent Ecosystem
8. CRM
9. ERP Core
10. Accounting
11. HRM
12. Ecommerce and Customer Experience
13. POS
14. Industry Engine
15. QA and Release
16. Data, Analytics and Intelligence
17. Partner Ecosystem
18. Marketplace
19. Go-Live and Commercial Launch
20. Scale, Growth and Global Expansion

## Quantified structure

| Level | Count |
|---|---:|
| Epic | 20 |
| Feature | 60 |
| User Story | 120 |
| Task | 240 |
| **Total** | **440** |

The package also produces:

- 20 two-week sprint definitions.
- 20 platform-level risks with mitigation and contingency.
- 20 traceability records.
- Board, component, owner and dependency mapping.
- QA, security, data-migration and integration coverage for every work item.

## Generate and validate

Run from the repository root:

```bash
python3 scripts/execution/executor_23_backlog.py generate
python3 scripts/execution/executor_23_backlog.py validate
python3 scripts/execution/executor_23_backlog.py github-import
```

The last command is a dry run by default and performs no GitHub mutation.

Generated files are written to `build/executor-23/`:

| File | Purpose |
|---|---|
| `canonical-backlog.csv` | Authoritative portable backlog |
| `jira-import.csv` | Jira Cloud external-system CSV import |
| `azure-devops-import.csv` | Azure Boards hierarchical CSV import |
| `github-issues-import.ndjson` | GitHub Issues and Projects import source |
| `risk-register.csv` | Delivery and platform risk register |
| `delivery-plan.csv` | Sprint plan, gates, roles and blocker policy |
| `traceability-matrix.csv` | Platform-to-reference and cross-cutting traceability |
| `manifest.json` | Counts, scope, file hashes and deferred scope |

## Import contracts

### Jira Cloud

The Jira export uses:

- Numeric `Issue ID` values.
- Numeric `Parent` references.
- Parent rows before child rows.
- Explicit issue types: Epic, Feature, User Story and Task.
- Acceptance criteria, definition of done, estimates, priorities, labels, components, boards, sprints and traceability.

Import through Jira Cloud external-system CSV import and map `Issue ID` and `Parent` to their corresponding Jira fields.

### Azure DevOps

The Azure export uses the hierarchical indentation model:

- `Title 1` for Epic.
- `Title 2` for Feature.
- `Title 3` for User Story.
- `Title 4` for Task.

The package contains 440 rows, below the 1,000-item limit for a single Azure Boards CSV import operation.

### GitHub Projects

The GitHub export is NDJSON. The importer can:

- Create one issue for every backlog item.
- Apply governed labels.
- Add each issue to the specified ProjectV2.
- Create native parent/sub-issue relationships.

Execution is intentionally opt-in:

```bash
python3 scripts/execution/executor_23_backlog.py github-import \
  --repo OWNER/REPOSITORY \
  --project-id PROJECT_NODE_ID \
  --execute
```

Without `--execute`, the command validates the package and exits without changing GitHub.

## Acceptance gate

Executor #23 is eligible for closure only when all of the following are true:

1. The generator and documentation are merged to `main` on an exact SHA.
2. The Executor #23 workflow passes generation, validation and GitHub dry-run checks.
3. The generated artifact contains all eight files and its hashes match `manifest.json`.
4. The hierarchy, dependency order, estimates, priorities, acceptance criteria and definition of done are complete.
5. All 20 platform streams and required cross-cutting controls are covered.
6. Program Management or the Project Owner records scope acceptance.

A real import into a specific Jira, Azure DevOps or GitHub target requires target administrator credentials and destination identifiers. Those environment values are deployment inputs, not missing backlog content.
