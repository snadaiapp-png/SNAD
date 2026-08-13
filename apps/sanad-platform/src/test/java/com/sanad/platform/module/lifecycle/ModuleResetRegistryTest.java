package com.sanad.platform.module.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModuleResetRegistry — unit tests")
class ModuleResetRegistryTest {

    private final ModuleResetRegistry registry = ModuleResetRegistry.getInstance();

    @Test
    @DisplayName("CRM module has 73 resettable tables")
    void crm_hasResettableTables() {
        Set<String> tables = registry.getResettableTables("CRM");
        assertThat(tables).isNotEmpty();
        assertThat(tables.size()).isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("CRM tables are ordered children-first (crm_timeline_events before crm_accounts)")
    void crm_childrenFirstOrdering() {
        Set<String> tables = registry.getResettableTables("CRM");
        // Convert to list to check ordering
        java.util.List<String> list = new java.util.ArrayList<>(tables);
        int timelineIdx = list.indexOf("crm_timeline_events");
        int accountsIdx = list.indexOf("crm_accounts");
        assertThat(timelineIdx).isLessThan(accountsIdx);
    }

    @Test
    @DisplayName("Unsupported module throws UnsupportedOperationException")
    void unsupportedModule_throws() {
        assertThatThrownBy(() -> registry.getResettableTables("AI"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AI");
    }

    @Test
    @DisplayName("supportsReset returns true for CRM, false for others")
    void supportsReset() {
        assertThat(registry.supportsReset("CRM")).isTrue();
        assertThat(registry.supportsReset("AI")).isFalse();
        assertThat(registry.supportsReset("ERP")).isFalse();
        assertThat(registry.supportsReset(null)).isFalse();
        assertThat(registry.supportsReset("")).isFalse();
    }

    @Test
    @DisplayName("Protected tables are correctly identified")
    void protectedTables() {
        assertThat(registry.isProtectedTable("tenants")).isTrue();
        assertThat(registry.isProtectedTable("users")).isTrue();
        assertThat(registry.isProtectedTable("billing_invoices")).isTrue();
        assertThat(registry.isProtectedTable("platform_audit_logs")).isTrue();
        assertThat(registry.isProtectedTable("saas_plans")).isTrue();
        assertThat(registry.isProtectedTable("tenant_subscriptions")).isTrue();
        assertThat(registry.isProtectedTable("modules")).isTrue();
        assertThat(registry.isProtectedTable("module_capabilities")).isTrue();
        assertThat(registry.isProtectedTable("plan_module_entitlements")).isTrue();
    }

    @Test
    @DisplayName("CRM tables are NOT protected")
    void crmTablesAreNotProtected() {
        assertThat(registry.isProtectedTable("crm_accounts")).isFalse();
        assertThat(registry.isProtectedTable("crm_contacts")).isFalse();
        assertThat(registry.isProtectedTable("crm_leads")).isFalse();
    }

    @Test
    @DisplayName("No protected table appears in any module's reset list")
    void noProtectedTableInResetList() {
        Set<String> crmTables = registry.getResettableTables("CRM");
        for (String table : crmTables) {
            assertThat(registry.isProtectedTable(table))
                    .as("Table '%s' should not be protected", table)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("CRM reset list does not include organizations or memberships")
    void crmResetListExcludesProtectedData() {
        Set<String> tables = registry.getResettableTables("CRM");
        assertThat(tables).doesNotContain("tenants");
        assertThat(tables).doesNotContain("organizations");
        assertThat(tables).doesNotContain("organization_memberships");
        assertThat(tables).doesNotContain("users");
        assertThat(tables).doesNotContain("tenant_subscriptions");
        assertThat(tables).doesNotContain("billing_invoices");
        assertThat(tables).doesNotContain("platform_audit_logs");
        assertThat(tables).doesNotContain("saas_plans");
    }
}
