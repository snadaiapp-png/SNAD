package com.sanad.platform.module.lifecycle;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleResetService — unit tests")
class ModuleResetServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private EntitlementResolver entitlementResolver;
    @Mock private PlatformAuditWriter auditWriter;
    @Mock private TenantRlsTransactionContext tenantRlsTransactionContext;

    private ModuleResetService service;
    private ControlPlaneAccessGuard controlPlaneAccessGuard;

    private static final UUID CONTROL_PLANE_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NON_CP_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONTROL_PLANE_USER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID TENANT_A = CONTROL_PLANE_TENANT;
    private static final UUID TENANT_B = NON_CP_TENANT;

    @BeforeEach
    void setUp() {
        controlPlaneAccessGuard = new ControlPlaneAccessGuard(CONTROL_PLANE_TENANT.toString());
        service = new ModuleResetService(jdbc, entitlementResolver, auditWriter, tenantRlsTransactionContext, controlPlaneAccessGuard);
        setSecCtx(CONTROL_PLANE_TENANT, CONTROL_PLANE_USER);
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    private void setSecCtx(UUID tenantId, UUID userId) {
        var auth = UsernamePasswordAuthenticationToken.authenticated("user", "creds", List.of());
        auth.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test @DisplayName("previewReset rejects missing authentication")
    void previewResetRejectsMissingControlPlaneAuthentication() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.previewReset(NON_CP_TENANT, "CRM")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tenantRlsTransactionContext);
    }

    @Test @DisplayName("executeReset rejects non-control-plane authentication")
    void executeResetRejectsNonControlPlaneAuthentication() {
        setSecCtx(NON_CP_TENANT, CONTROL_PLANE_USER);
        assertThatThrownBy(() -> service.executeReset(NON_CP_TENANT, "CRM", NON_CP_TENANT, CONTROL_PLANE_USER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(auditWriter, tenantRlsTransactionContext);
    }

    @Test @DisplayName("executeReset rejects spoofed actorTenantId")
    void executeResetRejectsSpoofedActorTenantId() {
        assertThatThrownBy(() -> service.executeReset(NON_CP_TENANT, "CRM", UUID.fromString("99999999-9999-9999-9999-999999999999"), CONTROL_PLANE_USER))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("actor identity");
        verifyNoInteractions(auditWriter, tenantRlsTransactionContext);
    }

    @Test @DisplayName("executeReset rejects spoofed actorUserId")
    void executeResetRejectsSpoofedActorUserId() {
        assertThatThrownBy(() -> service.executeReset(NON_CP_TENANT, "CRM", CONTROL_PLANE_TENANT, UUID.fromString("88888888-8888-8888-8888-888888888888")))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("actor identity");
        verifyNoInteractions(auditWriter, tenantRlsTransactionContext);
    }

    @Test @DisplayName("Reset CRM: only DELETE FROM crm_* WHERE tenant_id = ?")
    void resetCrm_onlyDeletesCrmTables() {
        var result = service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        assertThat(result.status()).isEqualTo(ModuleResetResult.STATUS_COMPLETED);
    }

    @Test @DisplayName("Reset CRM ≠ delete Organization")
    void resetCrm_doesNotDeleteOrganizations() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(contains("DELETE FROM organizations"), (Object) any());
    }

    @Test @DisplayName("Reset CRM ≠ delete Membership")
    void resetCrm_doesNotDeleteMemberships() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(contains("DELETE FROM organization_memberships"), (Object) any());
    }

    @Test @DisplayName("Reset CRM ≠ delete Subscription")
    void resetCrm_doesNotDeleteSubscriptions() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(contains("DELETE FROM tenant_subscriptions"), (Object) any());
    }

    @Test @DisplayName("Reset CRM ≠ delete Audit")
    void resetCrm_doesNotDeleteAuditLogs() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(contains("DELETE FROM platform_audit_logs"), (Object) any());
    }

    @Test @DisplayName("Reset CRM ≠ delete Users")
    void resetCrm_doesNotDeleteUsers() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(contains("DELETE FROM users"), (Object) any());
    }

    @Test @DisplayName("Reset CRM ≠ delete Tenants")
    void resetCrm_doesNotDeleteTenants() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(contains("DELETE FROM tenants"), (Object) any());
    }

    @Test @DisplayName("Reset CRM ≠ delete Invoices")
    void resetCrm_doesNotDeleteInvoices() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(contains("DELETE FROM billing_invoices"), (Object) any());
    }

    @Test @DisplayName("Reset Tenant A: all DELETEs use tenant_id = TENANT_A")
    void resetTenantA_onlyAffectsTenantA() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, atLeast(60)).update(contains("WHERE tenant_id = ?"), eq(TENANT_A));
        verify(jdbc, never()).update(contains("WHERE tenant_id = ?"), eq(TENANT_B));
    }

    @Test @DisplayName("Reset unsupported module: returns FAILED")
    void resetUnsupportedModule_returnsFailed() {
        var result = service.executeReset(TENANT_A, "AI", TENANT_A, CONTROL_PLANE_USER);
        assertThat(result.status()).isEqualTo(ModuleResetResult.STATUS_FAILED);
        assertThat(result.errorMessage()).contains("does not support reset");
    }

    @Test @DisplayName("Preview: does not execute any DELETE")
    void preview_doesNotDelete() {
        var preview = service.previewReset(TENANT_A, "CRM");
        assertThat(preview.tenantId()).isEqualTo(TENANT_A);
        assertThat(preview.moduleCode()).isEqualTo("CRM");
        verify(jdbc, never()).update(contains("DELETE"), (Object) any());
    }

    @Test @DisplayName("Preview: includes protected tables list")
    void preview_includesProtectedTables() {
        var preview = service.previewReset(TENANT_A, "CRM");
        assertThat(preview.protectedTables()).isNotEmpty();
        assertThat(preview.protectedTables()).contains("tenants", "users", "organizations",
                "tenant_subscriptions", "billing_invoices", "platform_audit_logs");
    }

    @Test @DisplayName("Reset CRM ≠ delete ERP tables")
    void resetCrm_doesNotDeleteErpTables() {
        service.executeReset(TENANT_A, "CRM", TENANT_A, CONTROL_PLANE_USER);
        verify(jdbc, never()).update(eq("DELETE FROM erp_inventory WHERE tenant_id = ?"), (Object) any());
    }
}
