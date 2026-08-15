# SNAD Future Modules Governance Readiness

> **READINESS != IMPLEMENTATION**
>
> This document describes the governance readiness contracts for future
> modules (ERP, POS, Contract Management). No business functionality
> is implemented. The contracts defined here prepare Senior Management,
> System Health, Workflow, and Analytics to govern these modules when
> they are implemented later.

## Current State

| Module | Registered | Implementation Status | Governance Ready |
|---|---|---|---|
| ERP | YES (V20260814_1) | NOT_STARTED | YES |
| POS | YES (V20260814_1) | NOT_STARTED | YES |
| CONTRACT_MANAGEMENT | YES (V20260816_1) | NOT_STARTED | YES |

## Governing Baseline

- **Start Baseline**: v20260816.1-system-health-platform-certification
- **Start SHA**: `af113941cc23007a850a2cf170bf067f6fd5fbb6`
- **Test Baseline**: 1864/1864 PASS (0 failures, 0 errors, 0 skipped)

## Hard Exclusions

This mission does NOT implement:
- ERP business tables, services, APIs, repositories
- POS business tables, services, APIs, repositories
- Contract Management business tables, services, APIs, repositories

If any of these are found after this mission, the mission FAILS.

## Future Module Integration Contract

When a future module is implemented, it must participate in the platform
through these existing contracts — NO core modification required:

### 1. Senior Management Governance
- Implement `ManagementGovernanceModuleContract` as a `@Service`
- The `ManagementGovernanceModuleRegistry` auto-discovers it via Spring List injection
- The Command Center automatically includes the new module's data

### 2. System Health
- Implement `SystemHealthContributor` as a `@Component`
- The `SystemHealthContributorRegistry` auto-discovers it via Spring List injection
- The System Health snapshot automatically includes the new module's health

### 3. Workflow
- Use the existing Workflow Engine APIs (`workflow_definitions`, `workflow_instances`)
- Define module-specific workflow definitions as data (not code)
- SOD remains centralized in the Workflow Engine
- No module-specific approval engine may be created

### 4. Analytics
- Register data sources via the existing `analytics_data_sources` API
- Create dashboards via the existing `analytics_dashboards` API
- No module-specific analytics tables

### 5. CRM / Finance Boundaries
- CRM remains the source of truth for customers, contacts, accounts
- Finance remains the source of truth for invoices, payments, ledgers
- Future modules reference CRM/Finance entities by ID — never duplicate

## Dependency Graph

```
                    Senior Management
                           |
        +------------------+------------------+
        |                  |                  |
     Workflow          Analytics         System Health
        |                  |                  |
        +------------------+------------------+
                           |
              Future Module Contracts
                           |
          +----------------+----------------+
          |                |                |
         ERP              POS      Contract Management
          |                |                |
          +-------- Future Integrations ----+
                           |
                     Websites / Stores
```

## Acceptance Gates

- `ERP_BUSINESS_IMPLEMENTATION_ADDED = 0`
- `POS_BUSINESS_IMPLEMENTATION_ADDED = 0`
- `CONTRACT_BUSINESS_IMPLEMENTATION_ADDED = 0`
- `SENIOR_MANAGEMENT_CORE_MODIFIED_FOR_MODULE_SPECIFICS = NO`
- `SYSTEM_HEALTH_CORE_MODIFIED_FOR_MODULE_SPECIFICS = NO`
- `WORKFLOW_CORE_MODIFIED_FOR_MODULE_SPECIFICS = NO`
- `ANALYTICS_CORE_MODIFIED_FOR_MODULE_SPECIFICS = NO`
