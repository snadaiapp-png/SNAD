# ERP Readiness — Governance & Integration Preparation

> **READINESS != IMPLEMENTATION**
>
> ERP is PLANNED / NOT_IMPLEMENTED / GOVERNANCE_READY.
> No ERP business functionality is implemented in this mission.

## Current State

- **Registry**: ERP is registered in the module catalog (V20260814_1, display_order=40)
- **Implementation**: NOT_STARTED — no `erp_*` tables, no ERP services, no ERP APIs
- **Governance**: READY — the governance contracts exist and require ZERO core modification
- **Data Ports**: `ErpDataPort` interface exists in CRM intelligence (customer snapshot port) — this is a CRM-side integration port, NOT an ERP implementation

## Future Scope (Design Only)

### Conceptual Areas
- Master Data (products, suppliers, customers)
- Procurement (purchase orders, supplier invoices)
- Inventory (warehouses, stock movements, valuation)
- Manufacturing (BOMs, production orders)
- Supply Chain (logistics, shipments)

### Future Platform Integrations
- Senior Management: implement `ManagementGovernanceModuleContract`
- System Health: implement `SystemHealthContributor`
- Workflow: use existing Workflow Engine for approval flows
- Analytics: register data sources via existing API
- CRM: reference customers/suppliers by ID (no duplication)
- Finance: procurement → invoices/payments; inventory valuation
- Stores: ERP may provide product/inventory data to Stores
- POS: ERP may provide inventory via `InventoryAvailabilityPort`

### Future Workflow Use Cases (Design Only)
- Purchase Order Approval
- Supplier Approval
- Stock Adjustment Approval
- Inventory Exception
- Operational Exception

These use the EXISTING Workflow Engine. No module-specific approval engine.

### Future Analytics (Design Only)
- Inventory metrics
- Procurement metrics
- Supplier performance
- Warehouse utilization
- Order fulfillment

## Integration Contracts

### ERP → Senior Management
```java
@Service
public class ErpGovernanceModuleAdapter implements ManagementGovernanceModuleContract {
    // Auto-discovered by ManagementGovernanceModuleRegistry
    // No core modification needed
}
```

### ERP → System Health
```java
@Component
public class ErpSystemHealthContributor implements SystemHealthContributor {
    // Auto-discovered by SystemHealthContributorRegistry
    // No core modification needed
}
```

### ERP → Workflow
- Define workflow definitions as DATA (INSERT into `workflow_definitions`)
- No code changes to Workflow Engine core

### ERP → Finance
- ERP purchase orders generate Finance invoices
- ERP inventory valuation feeds Finance accounting
- Finance remains source of truth for invoices/payments/ledgers

### ERP → CRM
- ERP references CRM `crm_accounts` by `account_id`
- ERP supplier master may reference CRM contacts
- CRM remains source of truth for customer/supplier master data

## Implementation Roadmap (Plan Only)

1. **Phase 1 — Master Data**: products, suppliers, warehouses
2. **Phase 2 — Procurement**: purchase orders, supplier invoices
3. **Phase 3 — Inventory**: stock movements, valuation
4. **Phase 4 — Workflow Integration**: PO/stock approval workflows
5. **Phase 5 — Finance Integration**: invoice/payment handoff
6. **Phase 6 — Analytics**: register data sources, dashboards
7. **Phase 7 — Governance**: implement contract adapters
8. **Phase 8 — Health**: implement health contributor

## Acceptance Gates

- `erp_*` tables added: 0 (this mission)
- ERP services/APIs added: 0 (this mission)
- ERP governance contract ready: YES
- ERP health contract ready: YES
- ERP workflow contract ready: YES
- ERP analytics contract ready: YES
