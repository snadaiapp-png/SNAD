# SNAD — Database Module Mapping

**Database:** Supabase PostgreSQL (tkbrvupemreqabwzdpyq)
**Total Tables:** 176
**Migrations Applied:** 115 (0 failed, 0 pending)

## Module → Table Mapping

### Core Platform
| Module | Table(s) | Migration |
|---|---|---|
| Tenancy | `tenants` | V1 |
| Users | `users` (+password_hash, last_login_at columns) | V1, V10 (ALTER) |
| Auth Tokens | `refresh_tokens`, `password_reset_tokens` | V10, V12 |
| Organizations | `organizations`, `organization_memberships` | V16 |
| RBAC | `roles`, `role_capabilities`, `user_role_assignments`, `access_capabilities` | V7, V8, V14 |
| Audit | `platform_audit_logs`, `crm_audit_logs` | V17, V20260717_6 |
| System Services | `system_services` | V18 |

### SaaS Administration
| Module | Table(s) | Migration |
|---|---|---|
| Plans | `saas_plans`, `saas_plan_entitlements` | V19, V20260814_2 |
| Subscriptions | `tenant_subscriptions`, `subscription_change_events` | V19, V20260711_1 |
| Module Capabilities | `module_capabilities`, `plan_module_entitlements` | V20260814_2 |

### CRM (50+ tables)
| Module | Table(s) | Migration |
|---|---|---|
| Accounts | `crm_accounts` | V20260702_1 |
| Contacts | `crm_contacts` | V20260702_1 |
| Leads | `crm_leads` | V20260702_1 |
| Opportunities | `crm_opportunities`, `crm_opportunity_stage_history` | V20260702_1 |
| Activities | `crm_activities` | V20260702_1 |
| Tasks | `crm_tasks` | V20260716_1 |
| Notes | `crm_notes` | V20260716_2 |
| Tags | (via CRM extension tables) | V20260716_3 |
| Pipelines | `crm_pipelines`, `crm_pipeline_stages` | V20260702_1 |
| Custom Fields | `crm_custom_field_definitions`, `crm_custom_field_values` | V20260702_3 |
| Import | `crm_import_jobs`, `crm_import_files`, `crm_import_errors` | V20260702_3 |
| Integration | `crm_integration_*` (command_artifacts, executions, decisions, outbox, requests) | V20260717_6 |
| Scoring | `crm_customer_scores`, `crm_customer_score_history`, `crm_scoring_models` | V20260717_6 |
| Segments | `crm_customer_segments`, `crm_segment_memberships` | V20260717_6 |
| Queues | `crm_queues`, `crm_queue_memberships` | V20260717_6 |
| Territories | `crm_territories`, `crm_territory_assignments`, `crm_territory_closure` | V20260717_6 |
| Sales Teams | `crm_sales_teams`, `crm_team_memberships` | V20260717_6 |
| Workforce | `crm_staff_skills`, `crm_staff_availability`, `crm_shift_templates`, `crm_shift_assignments`, `crm_service_assignments`, `crm_workload_assignments`, `crm_assignment_rules`, `crm_assignment_rule_counters`, `crm_assignment_rule_versions`, `crm_capacity_plans`, `crm_transfer_requests`, `crm_transfer_steps` | V20260717_6 |
| Timeline | `crm_timeline_events` | V20260717_6 |
| Communication | `crm_communication_methods`, `crm_communication_method_history`, `crm_communication_policies` | V20260717_6 |
| Next Best Actions | `crm_next_best_actions` | V20260717_6 |
| Ownership | `crm_ownership_history`, `crm_party_addresses`, `crm_party_address_history` | V20260717_6 |
| Audit | `crm_audit_logs` | V20260717_6 |
| Idempotency | `crm_idempotency_records` | V20260713_1 |

### Finance
| Module | Table(s) | Migration |
|---|---|---|
| Accounts | `finance_accounts` | V20260815_16 |
| Journal | `finance_journal_entries`, `finance_journal_lines` | V20260815_16 |
| Invoices | `finance_invoices`, `finance_invoice_lines` | V20260815_16 |
| Payments | `finance_payments` | V20260815_16 |

### ERP
| Module | Table(s) | Migration |
|---|---|---|
| Items | `erp_items` | V20260816_7 |
| Suppliers | `erp_suppliers` | V20260816_7 |
| Warehouses | `erp_warehouses` | V20260816_7 |
| Inventory | `erp_inventory_balances`, `erp_inventory_movements`, `erp_inventory_reservations` | V20260816_7 |
| Procurement | `erp_purchase_requisitions`, `erp_purchase_requisition_items`, `erp_purchase_orders`, `erp_purchase_order_items` | V20260816_7 |

### Commerce
| Module | Table(s) | Migration |
|---|---|---|
| Stores | `commerce_stores`, `commerce_store_domains` | V20260816_5 |
| Products | `commerce_products`, `commerce_product_variants`, `commerce_collections` | V20260816_5 |

### Workflow Engine
| Module | Table(s) | Migration |
|---|---|---|
| Definitions | `workflow_definitions`, `workflow_steps` | V20260815_10 |
| Instances | `workflow_instances`, `workflow_step_instances` | V20260815_10 |
| Approvals | `workflow_approval_requests` | V20260815_10 |

### Business Process
| Module | Table(s) | Migration |
|---|---|---|
| Process | `bp_process_runs`, `bp_process_steps` | V20260815_12 |
| Inventory | `bp_inventory_balances`, `bp_inventory_movements` | V20260815_12 |
| Finance | `bp_ledger_entries`, `bp_payment_events`, `billing_invoices` | V20260815_12 |
| Workflow | `bp_workflow_approvals` | V20260815_12 |
| Analytics | `bp_analytics_snapshots` | V20260815_12 |

### AI Platform
| Module | Table(s) | Migration |
|---|---|---|
| Agents | `ai_agents` | V20260815_14 |

### Mobile G7
| Module | Table(s) | Migration |
|---|---|---|
| Device | `mobile_device_registry` | V20260812_1 |
| Sync | `mobile_sync_cursor`, `mobile_sync_log` | V20260812_1 |
| Conflict | `mobile_conflict_log` | V20260812_1 |

## Previously "MISSING" Table Resolution

| Searched Name | Actual Name(s) | Status |
|---|---|---|
| `capabilities` | `access_capabilities` + `module_capabilities` | ✅ FOUND |
| `auth_credentials` | COLUMNS in `users` (password_hash, last_login_at) | ✅ FOUND |
| `audit_logs` | `platform_audit_logs` + `crm_audit_logs` | ✅ FOUND |
| `module_registry` | `system_services` + `module_capabilities` + `plan_module_entitlements` | ✅ FOUND |
| `subscriptions` | `tenant_subscriptions` + `subscription_change_events` | ✅ FOUND |
| `erp_inventory` | `erp_inventory_balances` + `erp_inventory_movements` + `erp_inventory_reservations` | ✅ FOUND |
| `ai_gateway_config` | `ai_agents` (config is in application properties) | ✅ FOUND |

**Conclusion:** All 176 tables are accounted for. No migrations need to be added.
