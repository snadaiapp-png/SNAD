# CRM-008B Migration Baseline Reconciliation

## Design vs Actual Table Count

```text
DESIGN_PLANNED_NEW_TABLES: 14
ACTUAL_NEW_TABLES: 13
EXISTING_G1_TABLES_UPGRADED: 1
UPGRADED_TABLES:
- crm_assignments
```

## Rationale

CRM-G1 (V20260717_6) introduced `crm_assignments` before CRM-008B implementation.
CRM-008B must use a forward-only ALTER/backfill migration to preserve production data.

V20260722_5 is an ALTER migration that:
- Adds new columns (owner_type, owner_user_id, owner_team_id, owner_queue_id, record_type, record_id, etc.)
- Backfills data from existing G1 columns (subject_type → record_type, subject_id → record_id, assigned_user_id → owner_user_id)
- Creates crm_ownership_history as a new table
- Does NOT drop or recreate crm_assignments

## Scope

```text
SCOPE_TOTAL_REMAINS: 14 tenant-owned table structures
DATA_LOSS: PROHIBITED
DROP_AND_RECREATE: PROHIBITED
```

## CRM-008B New Tables (13)

1. crm_sales_teams
2. crm_team_memberships
3. crm_queues
4. crm_queue_memberships
5. crm_territories
6. crm_territory_closure
7. crm_territory_assignments
8. crm_assignment_rules
9. crm_assignment_rule_versions
10. crm_ownership_history
11. crm_transfer_requests
12. crm_transfer_steps
13. crm_assignment_rule_counters

## Existing G1 Table Upgraded (1)

1. crm_assignments (ALTER + backfill, NOT dropped/recreated)
