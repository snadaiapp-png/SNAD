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

    /**
     * A CLEARED security context (null current tenant) is not an identity
     * change: the already-applied SET LOCAL GUC keeps every statement of the
     * transaction scoped to the original tenant (fail-closed under FORCE RLS).
     * Deferred flushes and post-authentication verification queries must not
     * be rejected by the guard. Regression guard for the null-context path.
     */
    @Test
    void keepsAppliedTenantScopeWhenContextIsClearedMidTransaction() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.prepareStatement("SELECT 1"); // applies SET LOCAL for tenantId

        // Actor context ends (request cleared it); a deferred statement follows.
        SecurityContextHolder.clearContext();
        proxy.prepareStatement("SELECT 2"); // must NOT throw; must NOT re-apply

        verify(statement, times(1)).execute(
                "SET LOCAL app.tenant_id = '" + tenantId + "'");
    }

    /** Swapping to a DIFFERENT tenant inside one transaction remains rejected. */
    @Test
    void rejectsRealIdentityChangeInsideActiveTransaction() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        setSecurityContext(tenantA);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.prepareStatement("SELECT 1"); // applies SET LOCAL for tenantA

        setSecurityContext(tenantB);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> proxy.prepareStatement("SELECT 2"))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("Tenant identity changed inside an active RLS transaction");
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

    @Test
    void resetsTenantBindingAfterCommitForPooledConnectionReuse() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        when(delegate.getAutoCommit()).thenReturn(false);
        proxy = wrap();

        setSecurityContext(tenantA);
        proxy.prepareStatement("SELECT 1");
        proxy.commit();

        setSecurityContext(tenantB);
        proxy.prepareStatement("SELECT 1");

        verify(statement).execute("SET LOCAL app.tenant_id = '" + tenantA + "'");
        verify(statement).execute("SET LOCAL app.tenant_id = '" + tenantB + "'");
    }

    // ── Pre-auth classification (silent-skip remediation) ────────────────

    /**
     * An unclassified pre-auth transaction must NOT silently issue writes to
     * FORCE-RLS-protected tenant data: the driver fails closed with an
     * explicit RLS_PRE_AUTH_UNCLASSIFIED marker before any SQL reaches the
     * engine. RED/GREEN companion of the users RLS tripwire.
     */
    @Test
    void failsClosedForPreAuthDataModificationOnForceRlsTable() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        stubCatalog(true); // public.users: ENABLE + FORCE RLS
        stubGuc("");       // no trusted pre-auth scope applied
        proxy = wrap();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> proxy.prepareStatement("insert into public.users (tenant_id, email) values (?, ?)"))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("RLS_PRE_AUTH_UNCLASSIFIED");
    }

    /**
     * A classified privileged pre-auth path (bootstrap / tenant-resolver
     * login / per-tenant system worker) has already applied the trusted
     * tenant GUC on the transaction: the write proceeds.
     */
    @Test
    void allowsPreAuthDataModificationWhenTrustedGucApplied() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        stubCatalog(true);
        stubGuc("11111111-1111-4111-8111-111111111111"); // trusted scope present
        proxy = wrap();

        proxy.prepareStatement("insert into public.users (tenant_id, email) values (?, ?)");

        verify(delegate).prepareStatement("insert into public.users (tenant_id, email) values (?, ?)");
    }

    /** Unclassified pre-auth writes to tables WITHOUT row security pass through. */
    @Test
    void allowsPreAuthDataModificationOnNonRlsTables() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        stubCatalog(false); // tenants table: no FORCE RLS
        proxy = wrap();

        proxy.prepareStatement("insert into tenants (id, name) values (?, ?)");

        verify(delegate).prepareStatement("insert into tenants (id, name) values (?, ?)");
    }

    /**
     * JDBC tooling (Flyway schema-history writes) emits QUOTED identifiers.
     * Writes to quoted non-RLS tables must be classified via the catalog and
     * pass through — a parse failure must not silently widen the guard into
     * a blanket write ban that would break migration startup.
     */
    @Test
    void allowsFlywayQuotedIdentifierHistoryInsertOnNonRlsTable() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        stubCatalog(false); // flyway_schema_history: no row security
        proxy = wrap();

        proxy.prepareStatement("INSERT INTO \"public\".\"flyway_schema_history\" "
                + "(installed_rank, version, description) VALUES (?, ?, ?)");

        verify(delegate).prepareStatement("INSERT INTO \"public\".\"flyway_schema_history\" "
                + "(installed_rank, version, description) VALUES (?, ?, ?)");
    }

    /** Pre-auth reads stay pass-through — FORCE RLS fail-closed policies govern visibility. */
    @Test
    void allowsPreAuthReadsWithoutClassification() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        proxy = wrap();

        proxy.prepareStatement("select * from public.users");

        verify(delegate).prepareStatement("select * from public.users");
    }

    /** Unidentifiable write targets fail toward the safe direction. */
    @Test
    void failsClosedForUnparseablePreAuthWrites() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        stubGuc("");
        proxy = wrap();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> proxy.prepareStatement("  -- only a comment\nDO $$ something $$"))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("RLS_PRE_AUTH_UNCLASSIFIED");
    }

    @Test
    void rejectsTenantSwitchWithinSingleTransaction() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        proxy = wrap();
        setSecurityContext(UUID.randomUUID());
        proxy.createStatement();

        setSecurityContext(UUID.randomUUID());
        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> proxy.prepareStatement("SELECT 1"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Stubs the pg_class catalog probe used by the FORCE-RLS classification. */
    private void stubCatalog(boolean forceRls) throws Exception {
        java.sql.ResultSet catalogRs = mock(java.sql.ResultSet.class);
        when(catalogRs.next()).thenReturn(true);
        when(catalogRs.getObject(1, Boolean.class)).thenReturn(forceRls);
        java.sql.PreparedStatement catalogPs = mock(java.sql.PreparedStatement.class);
        when(catalogPs.executeQuery()).thenReturn(catalogRs);
        when(delegate.prepareStatement(org.mockito.ArgumentMatchers.startsWith(
                "SELECT c.relrowsecurity AND c.relforcerowsecurity")))
                .thenReturn(catalogPs);
    }

    /** Stubs the live-connection trusted-GUC probe (app.tenant_id). */
    private void stubGuc(String value) throws Exception {
        java.sql.ResultSet gucRs = mock(java.sql.ResultSet.class);
        when(gucRs.next()).thenReturn(true);
        when(gucRs.getString(1)).thenReturn(value);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.contains(
                "current_setting('app.tenant_id'")))
                .thenReturn(gucRs);
    }

    private void setSecurityContext(UUID tenantId) {
        Map<String, Object> details = Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString());
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated("user", "creds", java.util.List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void bindProtocolIssuesSlotAndBindWithinTransaction() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);
        com.sanad.platform.test.TenantCtxTestSupport.FIXTURE_KEY_HEX.length(); // fixture key documented

        java.sql.ResultSet slotRs = mock(java.sql.ResultSet.class);
        when(slotRs.next()).thenReturn(true);
        when(slotRs.getString(1)).thenReturn("42:tx-1");
        when(slotRs.getInt(2)).thenReturn(1234);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.contains("app_tenant_ctx_slot")))
                .thenReturn(slotRs);

        java.sql.PreparedStatement bindPs = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet bindRs = mock(java.sql.ResultSet.class);
        when(bindRs.next()).thenReturn(true);
        when(bindRs.getBoolean(1)).thenReturn(true);
        when(delegate.prepareStatement(org.mockito.ArgumentMatchers.contains("app_bind_tenant_ctx")))
                .thenReturn(bindPs);
        when(bindPs.executeQuery()).thenReturn(bindRs);

        proxy = wrapWith(TenantContextSigner.fromHexKey(com.sanad.platform.test.TenantCtxTestSupport.FIXTURE_KEY_HEX));
        proxy.createStatement();

        // The bind must receive the tenant + a mac computed over the minted slot.
        org.mockito.ArgumentCaptor<String> tenant = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> mac = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(bindPs, times(1)).setString(org.mockito.ArgumentMatchers.eq(1), tenant.capture());
        verify(bindPs, times(1)).setString(org.mockito.ArgumentMatchers.eq(2), mac.capture());
        org.assertj.core.api.Assertions.assertThat(tenant.getValue()).isEqualTo(tenantId.toString());
        org.assertj.core.api.Assertions.assertThat(mac.getValue()).hasSize(64);
        // Legacy SET LOCAL must NOT be issued in signed mode.
        verify(statement, never()).execute(org.mockito.ArgumentMatchers.startsWith("SET LOCAL"));
    }

    @Test
    void bindProtocolRefusalFailsTheTransactionClosed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);

        java.sql.ResultSet slotRs = mock(java.sql.ResultSet.class);
        when(slotRs.next()).thenReturn(true);
        when(slotRs.getString(1)).thenReturn("43:tx-2");
        when(slotRs.getInt(2)).thenReturn(1235);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.contains("app_tenant_ctx_slot")))
                .thenReturn(slotRs);

        java.sql.PreparedStatement bindPs = mock(java.sql.PreparedStatement.class);
        when(delegate.prepareStatement(org.mockito.ArgumentMatchers.contains("app_bind_tenant_ctx")))
                .thenReturn(bindPs);
        when(bindPs.executeQuery()).thenThrow(
                new java.sql.SQLException("TENANT_CTX_BIND_REJECTED", "22000"));

        proxy = wrapWith(TenantContextSigner.fromHexKey(com.sanad.platform.test.TenantCtxTestSupport.FIXTURE_KEY_HEX));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proxy.createStatement())
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("TENANT_CTX_BIND_REJECTED");
    }

    private Connection wrap() {
        return wrapWith(TenantContextSigner.disabled());
    }

    private Connection wrapWith(TenantContextSigner signer) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new TenantRlsConnectionHandler(delegate, signer));
    }
}
