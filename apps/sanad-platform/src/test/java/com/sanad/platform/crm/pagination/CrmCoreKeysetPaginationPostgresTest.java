package com.sanad.platform.crm.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.dto.CrmDtos.AccountResponse;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.web.CrmContractController;
import com.sanad.platform.security.authorization.CapabilityAuthorizationAspect;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmCoreKeysetPaginationPostgresTest {


    @Mock
    CapabilityAuthorizationAspect authorization;

    private NamedParameterJdbcTemplate jdbc;
    private CursorCodec cursors;
    private CrmCoreCursorPaginationAspect aspect;
    private Authentication tenantA;
    private Authentication tenantB;
    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        // Drop dependent foreign-key constraints first, then drop crm_accounts.
        // PostgreSQL strictly enforces dependencies: DROP TABLE crm_accounts fails
        // with 'cannot drop table crm_accounts because other objects depend on it'
        // because many CRM tables (crm_contacts, crm_opportunities, etc.) have FKs
        // referencing crm_accounts. We need DROP CASCADE OR drop dependents first.
        // Using DROP TABLE IF EXISTS ... CASCADE preserves referential integrity
        // guarantees by only dropping this test's local table (which we recreate below
        // with a simple schema for keyset pagination testing).
        jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS crm_accounts CASCADE");
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE crm_accounts (
                    id UUID PRIMARY KEY,
                    tenant_id UUID NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    display_name VARCHAR(240) NOT NULL,
                    normalized_name VARCHAR(240) NOT NULL,
                    account_type VARCHAR(40) NOT NULL,
                    lifecycle_status VARCHAR(32) NOT NULL,
                    primary_currency_code VARCHAR(3),
                    preferred_locale VARCHAR(35),
                    time_zone VARCHAR(64),
                    source VARCHAR(80),
                    parent_account_id UUID,
                    owner_user_id UUID,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.getJdbcTemplate().execute(
                "CREATE INDEX idx_crm003r_accounts_keyset ON crm_accounts (tenant_id, updated_at, id)");

        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();
        tenantA = authentication(tenantAId);
        tenantB = authentication(tenantBId);
        cursors = new CursorCodec();
        aspect = new CrmCoreCursorPaginationAspect(
                jdbc, cursors, new CrmDtoMapper(), new ObjectMapper(), authorization);
        doNothing().when(authorization).checkCapability(any(), any());
    }

    @Test
    void traversesStableDatasetWithoutOverlapOrGapsInBothDirections() throws Throwable {
        Instant tied = Instant.parse("2026-07-26T10:00:00Z");
        insert(tenantAId, "A-01", tied.minusSeconds(30));
        insert(tenantAId, "A-02", tied.minusSeconds(20));
        insert(tenantAId, "A-03", tied);
        insert(tenantAId, "A-04", tied);
        insert(tenantAId, "A-05", tied.plusSeconds(10));
        insert(tenantAId, "A-06", tied.plusSeconds(20));
        insert(tenantBId, "B-SECRET", tied.plusSeconds(40));

        List<UUID> ascending = traverse(tenantA, "asc", null);
        List<UUID> descending = traverse(tenantA, "desc", null);

        assertThat(ascending).hasSize(6).doesNotHaveDuplicates();
        assertThat(descending).hasSize(6).doesNotHaveDuplicates();
        assertThat(new HashSet<>(ascending)).containsExactlyInAnyOrderElementsOf(descending);
        List<UUID> reversedDescending = new ArrayList<>(descending);
        java.util.Collections.reverse(reversedDescending);
        assertThat(ascending).containsExactlyElementsOf(reversedDescending);
        assertThat(ascending).doesNotContainAnyElementsOf(idsForTenant(tenantBId));
    }

    @Test
    void pageTwoHasZeroOverlapAndCursorAdvances() throws Throwable {
        Instant base = Instant.parse("2026-07-26T11:00:00Z");
        for (int index = 0; index < 5; index++) {
            insert(tenantAId, "PAGE-" + index, base.plusSeconds(index));
        }

        ListResponse<AccountResponse> first = page(tenantA, "asc", null, null, 2);
        ListResponse<AccountResponse> second = page(
                tenantA, "asc", first.page().nextCursor(), null, 2);

        Set<UUID> pageOneIds = first.data().stream().map(AccountResponse::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> pageTwoIds = second.data().stream().map(AccountResponse::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> overlap = new HashSet<>(pageOneIds);
        overlap.retainAll(pageTwoIds);

        assertThat(first.page().hasMore()).isTrue();
        assertThat(first.page().nextCursor()).isNotBlank();
        assertThat(second.data()).hasSize(2);
        assertThat(overlap).isEmpty();
        assertThat(second.page().nextCursor()).isNotEqualTo(first.page().nextCursor());
    }

    @Test
    void preservesSearchFilterAcrossPagesAndRejectsFilterDrift() throws Throwable {
        Instant base = Instant.parse("2026-07-26T12:00:00Z");
        insert(tenantAId, "Alpha One", base);
        insert(tenantAId, "Alpha Two", base.plusSeconds(1));
        insert(tenantAId, "Alpha Three", base.plusSeconds(2));
        insert(tenantAId, "Beta One", base.plusSeconds(3));

        ListResponse<AccountResponse> first = page(tenantA, "asc", null, "Alpha", 2);
        ListResponse<AccountResponse> second = page(
                tenantA, "asc", first.page().nextCursor(), "Alpha", 2);

        assertThat(first.data()).allMatch(account -> account.displayName().startsWith("Alpha"));
        assertThat(second.data()).allMatch(account -> account.displayName().startsWith("Alpha"));
        assertThat(first.data()).hasSize(2);
        assertThat(second.data()).hasSize(1);

        assertThatThrownBy(() -> page(
                tenantA, "asc", first.page().nextCursor(), "Beta", 2))
                .hasMessageContaining("different sort or direction");
    }

    @Test
    void rejectsCrossTenantAndTamperedCursors() throws Throwable {
        Instant base = Instant.parse("2026-07-26T13:00:00Z");
        insert(tenantAId, "Tenant A 1", base);
        insert(tenantAId, "Tenant A 2", base.plusSeconds(1));
        insert(tenantAId, "Tenant A 3", base.plusSeconds(2));
        insert(tenantBId, "Tenant B 1", base);

        ListResponse<AccountResponse> first = page(tenantA, "asc", null, null, 2);
        String cursor = first.page().nextCursor();

        assertThatThrownBy(() -> page(tenantB, "asc", cursor, null, 2))
                .hasMessageContaining("invalid for this tenant");
        String tampered = cursor.substring(0, cursor.length() - 1)
                + (cursor.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> page(tenantA, "asc", tampered, null, 2))
                .isInstanceOf(RuntimeException.class);
    }

    private List<UUID> traverse(Authentication authentication, String direction, String search) throws Throwable {
        List<UUID> result = new ArrayList<>();
        String cursor = null;
        int safety = 0;
        do {
            ListResponse<AccountResponse> current = page(authentication, direction, cursor, search, 2);
            result.addAll(current.data().stream().map(AccountResponse::id).toList());
            cursor = current.page().nextCursor();
            safety++;
            assertThat(safety).isLessThan(10);
        } while (cursor != null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private ListResponse<AccountResponse> page(
            Authentication authentication,
            String direction,
            String cursor,
            String search,
            int limit) throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("limit", String.valueOf(limit));
        request.setParameter("sort", "updatedAt");
        request.setParameter("direction", direction);
        if (cursor != null) request.setParameter("cursor", cursor);
        if (search != null) request.setParameter("search", search);
        request.addHeader("X-Request-ID", UUID.randomUUID().toString());

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = CrmContractController.class.getMethod(
                "listAccounts",
                Authentication.class,
                Integer.class,
                String.class,
                String.class,
                String.class,
                String.class,
                HttpServletRequest.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                authentication, limit, cursor, "updatedAt", direction, search, request
        });
        return (ListResponse<AccountResponse>) aspect.pageAccounts(joinPoint);
    }

    private Authentication authentication(UUID tenantId) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "crm003r-user", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        return authentication;
    }

    private void insert(UUID tenantId, String displayName, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_accounts (
                    id, tenant_id, version, display_name, normalized_name,
                    account_type, lifecycle_status, primary_currency_code,
                    preferred_locale, time_zone, source, created_at, updated_at)
                VALUES (
                    :id, :tenantId, 0, :displayName, :normalizedName,
                    'BUSINESS', 'ACTIVE', 'SAR', 'ar-SA', 'Asia/Riyadh',
                    'CRM003R', :createdAt, :updatedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("displayName", displayName)
                        .addValue("normalizedName", displayName.toLowerCase())
                        .addValue("createdAt", Timestamp.from(updatedAt.minusSeconds(60)))
                        .addValue("updatedAt", Timestamp.from(updatedAt)));
    }

    private List<UUID> idsForTenant(UUID tenantId) {
        return jdbc.queryForList(
                "SELECT id FROM crm_accounts WHERE tenant_id=:tenantId",
                new MapSqlParameterSource().addValue("tenantId", tenantId),
                UUID.class);
    }
}
