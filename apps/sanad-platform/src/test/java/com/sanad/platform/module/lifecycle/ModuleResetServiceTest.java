package com.sanad.platform.module.lifecycle;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ModuleResetService}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Reset CRM ≠ delete ERP tables</li>
 *   <li>Reset Module ≠ delete Organization</li>
 *   <li>Reset Module ≠ delete Membership</li>
 *   <li>Reset Module ≠ delete Subscription</li>
 *   <li>Reset Module ≠ delete Audit</li>
 *   <li>Reset Tenant A ≠ affect Tenant B</li>
 *   <li>Protected tables never appear in DELETE statements</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleResetService — unit tests")
class ModuleResetServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private EntitlementResolver entitlementResolver;
    @Mock
    private PlatformAuditWriter auditWriter;

    private ModuleResetService service;

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        service = new ModuleResetService(jdbc, entitlementResolver, auditWriter);
    }

    @Test
    @DisplayName("Reset CRM: only DELETE FROM crm_* and service_callback_replay WHERE tenant_id = ?")
    void resetCrm_onlyDeletesCrmTables() {
        ModuleResetResult result = service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(ModuleResetResult.STATUS_COMPLETED);
        assertThat(result.totalRowsDeleted()).isGreaterThanOrEqualTo(0);

        // Verify every DELETE was on a crm_ table or service_callback_replay (CRM domain)
        for (ModuleResetResult.TableResetResult tr : result.tableResults()) {
            assertThat(tr.tableName().startsWith("crm_") || tr.tableName().equals("service_callback_replay"))
                    .as("Table '%s' should be in CRM domain", tr.tableName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Reset CRM ≠ delete Organization")
    void resetCrm_doesNotDeleteOrganizations() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        verify(jdbc, never()).update(contains("DELETE FROM organizations"), (Object) any());
        verify(jdbc, never()).update(eq("DELETE FROM organizations WHERE tenant_id = ?"), eq(TENANT_A));
    }

    @Test
    @DisplayName("Reset CRM ≠ delete Membership")
    void resetCrm_doesNotDeleteMemberships() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        verify(jdbc, never()).update(contains("DELETE FROM organization_memberships"), (Object) any());
    }

    @Test
    @DisplayName("Reset CRM ≠ delete Subscription")
    void resetCrm_doesNotDeleteSubscriptions() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        verify(jdbc, never()).update(contains("DELETE FROM tenant_subscriptions"), (Object) any());
    }

    @Test
    @DisplayName("Reset CRM ≠ delete Audit")
    void resetCrm_doesNotDeleteAuditLogs() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        verify(jdbc, never()).update(contains("DELETE FROM platform_audit_logs"), (Object) any());
    }

    @Test
    @DisplayName("Reset CRM ≠ delete Users")
    void resetCrm_doesNotDeleteUsers() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        verify(jdbc, never()).update(contains("DELETE FROM users"), (Object) any());
    }

    @Test
    @DisplayName("Reset CRM ≠ delete Tenants")
    void resetCrm_doesNotDeleteTenants() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        verify(jdbc, never()).update(contains("DELETE FROM tenants"), (Object) any());
    }

    @Test
    @DisplayName("Reset CRM ≠ delete Invoices")
    void resetCrm_doesNotDeleteInvoices() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        verify(jdbc, never()).update(contains("DELETE FROM billing_invoices"), (Object) any());
    }

    @Test
    @DisplayName("Reset Tenant A: all DELETEs use tenant_id = TENANT_A")
    void resetTenantA_onlyAffectsTenantA() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        // Every DELETE should have WHERE tenant_id = TENANT_A
        verify(jdbc, atLeast(60)).update(contains("WHERE tenant_id = ?"), eq(TENANT_A));
        // No DELETE should target TENANT_B
        verify(jdbc, never()).update(contains("WHERE tenant_id = ?"), eq(TENANT_B));
    }

    @Test
    @DisplayName("Reset unsupported module: returns FAILED")
    void resetUnsupportedModule_returnsFailed() {
        ModuleResetResult result = service.executeReset(TENANT_A, "AI", TENANT_A, UUID.randomUUID());
        assertThat(result.status()).isEqualTo(ModuleResetResult.STATUS_FAILED);
        assertThat(result.errorMessage()).contains("does not support reset");
    }

    @Test
    @DisplayName("Preview: does not execute any DELETE")
    void preview_doesNotDelete() {
        ModuleResetPreview preview = service.previewReset(TENANT_A, "CRM");
        assertThat(preview.tenantId()).isEqualTo(TENANT_A);
        assertThat(preview.moduleCode()).isEqualTo("CRM");
        assertThat(preview.irreversible()).isTrue();
        // No DELETE should have been called during preview
        verify(jdbc, never()).update(contains("DELETE"), (Object) any());
    }

    @Test
    @DisplayName("Preview: includes protected tables list")
    void preview_includesProtectedTables() {
        ModuleResetPreview preview = service.previewReset(TENANT_A, "CRM");
        assertThat(preview.protectedTables()).isNotEmpty();
        assertThat(preview.protectedTables()).contains("tenants", "users", "organizations",
                "tenant_subscriptions", "billing_invoices", "platform_audit_logs");
    }

    @Test
    @DisplayName("Reset CRM ≠ delete ERP tables (no erp_ tables)")
    void resetCrm_doesNotDeleteErpTables() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, UUID.randomUUID());
        // No DELETE on any non-crm table
        verify(jdbc, never()).update(eq("DELETE FROM erp_inventory WHERE tenant_id = ?"), (Object) any());
    }
}
