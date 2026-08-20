package com.sanad.platform.module.lifecycle;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registry of module-specific resettable tables, ordered children-first for safe FK deletion.
 *
 * <p>Only tables explicitly listed here can be reset. Any table NOT in this registry
 * is implicitly PROTECTED and can never be reset.
 *
 * <p>The registry is hard-coded (metadata is static) and keyed by module code.
 * Currently only CRM has implemented tables; other modules throw
 * {@link UnsupportedOperationException} when reset is attempted.
 */
public final class ModuleResetRegistry {

    /** Singleton instance. Initialized lazily to avoid static initialization order issues. */
    private static ModuleResetRegistry instance;

    /** Protected tables — hardcoded denylist that can NEVER appear in any reset list. */
    private static final Set<String> PROTECTED_TABLES = Set.of(
            // Identity & Access
            "tenants", "organizations", "organization_memberships",
            "users", "roles", "access_capabilities", "role_capabilities",
            "user_role_assignments", "refresh_tokens", "password_reset_tokens",
            // SaaS / Billing / Subscriptions
            "saas_plans", "saas_plan_entitlements", "tenant_subscriptions",
            "subscription_change_events", "billing_invoices", "tenant_quota",
            // Module Registry & Entitlements
            "modules", "module_capabilities", "plan_module_entitlements",
            "tenant_entitlement_cache",
            // Platform Observability / Flyway
            "platform_audit_logs", "system_services", "flyway_schema_history"
    );

    /** CRM tables in children-first FK deletion order. */
    private static final Set<String> CRM_TABLES = createOrderedSet(
            // History / audit tables (children first)
            "crm_timeline_events",
            "crm_account_merge_history",
            "crm_account_status_history",
            "crm_account_relationships",
            "crm_account_identifiers",
            "crm_account_addresses",
            "crm_opportunity_stage_history",
            "crm_contact_relationship_history",
            "crm_contact_ownership_history",
            "crm_communication_method_history",
            "crm_party_address_history",
            "crm_customer_score_history",
            // Assignment / lookup / communication
            "crm_assignments",
            "crm_transfers",
            "crm_transfer_requests",
            "crm_transfer_steps",
            "crm_audit_logs",
            "crm_reports",
            "crm_phone_numbers",
            "crm_contact_lookup_index",
            "crm_contact_account_relationships",
            "crm_communication_methods",
            "crm_communication_policies",
            "crm_party_addresses",
            "crm_tag_assignments",
            "crm_tags",
            // Leaf operational tables
            "crm_tasks",
            "crm_notes",
            "crm_cases",
            "crm_email_logs",
            "crm_activities",
            "crm_custom_field_values",
            "crm_custom_field_definitions",
            "crm_import_errors",
            "crm_import_files",
            "crm_import_jobs",
            "crm_idempotency_records",
            // Customer intelligence
            "crm_segment_memberships",
            "crm_customer_segments",
            "crm_next_best_actions",
            "crm_customer_scores",
            "crm_scoring_models",
            // Integration / CRM-009
            "service_callback_replay",
            "crm_integration_command_artifacts",
            "crm_integration_command_executions",
            "crm_integration_decisions",
            "crm_integration_outbox",
            "crm_integration_requests",
            "crm_assignment_rule_counters",
            "crm_assignment_rule_versions",
            "crm_assignment_rules",
            // Workforce / ownership
            "crm_workload_assignments",
            "crm_service_assignments",
            "crm_staff_availability",
            "crm_staff_skills",
            "crm_capacity_plans",
            "crm_shift_assignments",
            "crm_shift_templates",
            "crm_queue_memberships",
            "crm_queues",
            "crm_team_memberships",
            "crm_sales_teams",
            "crm_territory_closure",
            "crm_territory_assignments",
            "crm_territories",
            "crm_ownership_history",
            "crm_contact_relationship_roles",
            // Mid-level aggregates
            "crm_leads",
            "crm_opportunities",
            // Root aggregates
            "crm_contacts",
            "crm_accounts",
            // Catalog tables (pipeline stages before pipelines)
            "crm_pipeline_stages",
            "crm_pipelines"
    );

    private ModuleResetRegistry() {
        // Defense-in-depth: verify no protected table is in any module's reset list
        assertNoProtectedTableInResetList("CRM", CRM_TABLES);
    }

    /**
     * Get the singleton instance.
     */
    public static ModuleResetRegistry getInstance() {
        if (instance == null) {
            instance = new ModuleResetRegistry();
        }
        return instance;
    }

    /**
     * Get the ordered set of resettable tables for a module.
     *
     * @param moduleCode the module code (e.g., "CRM")
     * @return ordered set of table names (children-first), never null
     * @throws UnsupportedOperationException if the module has no registered tables
     */
    public Set<String> getResettableTables(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            throw new IllegalArgumentException("moduleCode must not be blank");
        }
        String code = moduleCode.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (code) {
            case "CRM" -> Collections.unmodifiableSet(CRM_TABLES);
            default -> throw new UnsupportedOperationException(
                    "Module '" + code + "' has no registered resettable tables. Reset is not supported.");
        };
    }

    /**
     * Check if a table is protected (can NEVER be reset).
     */
    public boolean isProtectedTable(String tableName) {
        if (tableName == null || tableName.isBlank()) return true;
        return PROTECTED_TABLES.contains(tableName.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Check if a module supports reset.
     */
    public boolean supportsReset(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) return false;
        String code = moduleCode.trim().toUpperCase(java.util.Locale.ROOT);
        return "CRM".equals(code);
    }

    /**
     * Defense-in-depth: verify no protected table appears in any module's reset list.
     */
    private void assertNoProtectedTableInResetList(String moduleCode, Set<String> tables) {
        for (String table : tables) {
            if (PROTECTED_TABLES.contains(table.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalStateException(
                        "FATAL: Protected table '" + table + "' found in reset list for module '" + moduleCode + "'");
            }
        }
    }

    /**
     * Create a LinkedHashSet preserving insertion order (for FK-safe deletion sequence).
     */
    private static Set<String> createOrderedSet(String... tables) {
        Set<String> set = new LinkedHashSet<>();
        for (String t : tables) {
            set.add(t);
        }
        return Collections.unmodifiableSet(set);
    }
}
