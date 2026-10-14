package com.sanad.platform.security.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G1 contract: pre-auth discovery must only retrieve tenant UUIDs. */
class PreAuthTenantResolverTest {

    @Test
    void tenantCandidatesComeFromRestrictedSqlFunctionOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID tenant = UUID.randomUUID();
        when(jdbc.query(eq("SELECT tenant_id FROM public.g1_resolve_login_tenants(?, ?)"),
                any(RowMapper.class), eq("email"), eq("user@example.com")))
                .thenReturn(List.of(tenant));

        PreAuthTenantResolver resolver = new PreAuthTenantResolver(jdbc);
        assertThat(resolver.resolve("email", "user@example.com")).containsExactly(tenant);
        verify(jdbc).query(eq("SELECT tenant_id FROM public.g1_resolve_login_tenants(?, ?)"),
                any(RowMapper.class), eq("email"), eq("user@example.com"));
    }

    @Test
    void rejectsUnknownIdentifierKindsAndEmptyIdentifiers() {
        PreAuthTenantResolver resolver = new PreAuthTenantResolver(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> resolver.resolve("tenant", "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve("email", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
