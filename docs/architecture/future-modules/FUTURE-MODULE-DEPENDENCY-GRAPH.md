# SNAD Future Module Dependency Graph

> **READINESS != IMPLEMENTATION**

## High-Level Model

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

## Dependency Classification

| From | To | Type | Notes |
|---|---|---|---|
| ERP | Senior Management | HARD | ERP must implement ManagementGovernanceModuleContract |
| ERP | System Health | HARD | ERP must implement SystemHealthContributor |
| ERP | Workflow | HARD | ERP uses Workflow Engine for approvals (PO, stock adjustment) |
| ERP | Analytics | SOFT | ERP publishes data to Analytics via existing data source API |
| ERP | CRM | SOFT | ERP references CRM customers/suppliers by ID; no duplication |
| ERP | Finance | HARD | ERP procurement → Finance invoices/payments; inventory valuation |
| ERP | POS | FUTURE ADAPTER | POS may consume ERP inventory via InventoryAvailabilityPort |
| ERP | Stores | NO CURRENT | Stores may reference ERP products when both implemented |
| POS | Senior Management | HARD | POS must implement ManagementGovernanceModuleContract |
| POS | System Health | HARD | POS must implement SystemHealthContributor |
| POS | Workflow | HARD | POS uses Workflow for refund/void/discount approvals |
| POS | Analytics | SOFT | POS publishes sales data to Analytics |
| POS | CRM | SOFT | POS references CRM customer/contact by ID |
| POS | Finance | HARD | POS sales → Finance payments/accounting |
| POS | ERP | FUTURE ADAPTER | POS may consume ERP inventory; ERP may not exist yet |
| Contract Mgmt | Senior Management | HARD | Must implement ManagementGovernanceModuleContract |
| Contract Mgmt | System Health | HARD | Must implement SystemHealthContributor |
| Contract Mgmt | Workflow | HARD | Contract review/approval/renewal via Workflow Engine |
| Contract Mgmt | Analytics | SOFT | Contract KPIs published to Analytics |
| Contract Mgmt | CRM | SOFT | Contract parties reference CRM accounts/contacts |
| Contract Mgmt | Finance | HARD | Contract value/billing → Finance |
| Contract Mgmt | Document Storage | FUTURE | Files/documents via approved storage boundary |
| Websites | Senior Management | HARD | Must implement governance contract when implemented |
| Stores | Senior Management | HARD | Must implement governance contract when implemented |
| Stores | POS | SOFT | Stores may own POS terminals |
| Stores | ERP | SOFT | Stores may reference ERP products/inventory |

## Key Rules

1. **HARD dependency**: the dependent module CANNOT be implemented without the dependency.
2. **SOFT dependency**: the dependent module can be implemented independently but integrates with the dependency when both exist.
3. **FUTURE ADAPTER**: the dependency may not exist yet; the dependent module uses a port/interface that a future adapter can satisfy.
4. **NO CURRENT dependency**: no integration exists today; may be added when both modules are implemented.

## Critical Path

```
1. Senior Management (DONE — v20260815.9)
2. System Health (DONE — v20260816.1)
3. Workflow Engine (DONE — v20260814.7)
4. Analytics Platform (DONE — v20260815.5)
5. CRM (DONE — CRM-006 CLOSED)
6. Finance (DONE — v20260815.1)
7. Websites (NEXT ALLOWED)
8. Stores / E-Commerce (after Websites)
9. ERP (after Stores — requires Finance, Workflow, CRM)
10. POS (after or parallel with ERP — requires Finance, CRM, Workflow)
11. Contract Management (after Finance, CRM, Workflow — independent of ERP/POS)
```

ERP, POS, and Contract Management are NOT blocked by Websites/Stores
from a governance perspective — they are governance-ready now. The
implementation order is a product decision, not a technical dependency.
