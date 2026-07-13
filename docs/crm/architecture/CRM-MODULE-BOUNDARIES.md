# CRM Module Boundaries

## Overview
The CRM module has been refactored from a monolithic service structure to a Modular Monolith with 8 bounded context modules.

## Module Map

| Module | Responsibility | Key Entities |
|---|---|---|
| `crm.party` | Accounts, Contacts, Customer identity | Account, Contact |
| `crm.lead` | Lead lifecycle, status transitions, conversion | Lead |
| `crm.opportunity` | Opportunities, Pipelines, Stages | Opportunity, Pipeline, Stage |
| `crm.activity` | Activities, tasks, completion | Activity |
| `crm.configuration` | Custom field definitions, CRM config | CustomFieldDefinition |
| `crm.dataquality` | Data validation policies (future: dedup, merge) | DataQualityPolicy |
| `crm.query` | Read models, Dashboard, Timeline, Customer 360, Search | TimelineEvent, DashboardKpi |
| `crm.integration` | Audit, Security context, Outbox adapters | AuditPort, TenantContextPort |

## Layer Structure (per module)
```
<module>/
├── api/              # HTTP controllers (only in modules with endpoints)
├── application/      # Use cases, orchestration, @Transactional
├── domain/           # Repository ports, domain policies, value objects
└── infrastructure/   # JDBC adapters, repository implementations
```

## Dependency Rules
- `api → application` (controllers call use cases)
- `application → domain` (use cases call repository ports)
- `infrastructure → domain` (adapters implement ports)
- Cross-module: only via declared ports, never via implementation classes

## Forbidden Dependencies
- controller → JDBC/SQL
- domain → Spring Web / JDBC / JPA
- module A infrastructure → module B infrastructure
- query module → write operations
