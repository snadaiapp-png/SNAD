package com.sanad.platform.security.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link TenantRlsConnectionHandler} — verifies the tenant
 * context propagation logic without requiring Docker/PostgreSQL.
 */
class TenantRlsConnectionHandlerTest {

    private Connection delegate;
    private Statement statement;
    private Connection proxy;

    @BeforeEach
    void setUp() throws Exception {
        delegate = mock(Connection.class);
        statement = mock(Statement.class);
        when(delegate.createStatement()).thenReturn(statement);
        when(delegate.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mock(java.sql.PreparedStatement.class));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appliesSetLocalWhenInTransactionWithTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();

        // Trigger statement creation
        proxy.createStatement();

        // SET LOCAL must have been executed
        verify(statement, times(1)).execute(
                "SET LOCAL app.tenant_id = '" + tenantId + "'");
    }

    @Test
    void doesNotApplySetLocalWhenAutoCommit() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(true); // autocommit mode

        proxy = wrap();
        proxy.createStatement();

        // SET LOCAL must NOT be executed in autocommit mode
        verify(statement, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doesNotApplySetLocalWhenNoTenantContext() throws Exception {
        // No security context set
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.createStatement();

        verify(statement, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doesNotApplySetLocalWhenUnauthenticated() throws Exception {
        Authentication unauthenticated = new UsernamePasswordAuthenticationToken(
                "user", "creds"); // isAuthenticated() = false until granted authorities
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.createStatement();

        verify(statement, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void appliesSetLocalOnlyOncePerConnection() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.createStatement(); // first call — applies SET LOCAL
        proxy.createStatement(); // second call — should NOT re-apply
        proxy.prepareStatement("SELECT 1"); // third call — should NOT re-apply

        verify(statement, times(1)).execute(
                "SET LOCAL app.tenant_id = '" + tenantId + "'");
    }

    @Test
    void doesNotApplySetLocalWhenTenantIdInvalid() throws Exception {
        // Set a malformed tenant_id in the details map
        Map<String, Object> details = Map.of("tenant_id", "not-a-uuid");
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated("user", "creds", java.util.List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.createStatement();

        // Invalid UUID → no SET LOCAL applied (graceful degradation)
        verify(statement, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void setSecurityContext(UUID tenantId) {
        Map<String, Object> details = Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString());
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated("user", "creds", java.util.List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Connection wrap() {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new TenantRlsConnectionHandler(delegate));
    }
}
