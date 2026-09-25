package com.sanad.platform.subscription.read;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialConsistencyDiagnosticsContractTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private TenantRlsTransactionContext tenantRlsContext;

    @Test
    void reportsCanonicalCommercialAnomalyClassesUsingSelectOnlyTenantScopedQueries() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(jdbc.queryForList(anyString(), eq(UUID.class))).thenReturn(List.of(tenantId));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("code", "ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION", "tenant_id", tenantId,
                        "evidence", "tenant=ACTIVE,effective=0"),
                Map.of("code", "DUNNING_STATE_MISMATCH", "tenant_id", tenantId,
                        "evidence", "billing=CURRENT,lifecycle=PAST_DUE")
        ));

        var service = new CommercialConsistencyDiagnosticsService(jdbc, tenantRlsContext);
        var anomalies = service.scan();

        assertThat(anomalies).extracting(CommercialConsistencyDiagnosticsService.CommercialAnomaly::code)
                .contains("ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION", "DUNNING_STATE_MISMATCH");
        verify(tenantRlsContext).applyForCurrentTransaction(tenantId);

        ArgumentCaptor<String> tenantSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(tenantSql.capture(), eq(UUID.class));
        assertSelectOnly(tenantSql.getValue());

        ArgumentCaptor<String> diagnosticsSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(diagnosticsSql.capture());
        assertSelectOnly(diagnosticsSql.getValue());
    }

    private static void assertSelectOnly(String sql) {
        String normalized = sql.toUpperCase();
        assertThat(normalized).contains("SELECT");
        assertThat(normalized)
                .doesNotContain("UPDATE ")
                .doesNotContain("INSERT ")
                .doesNotContain("DELETE ");
    }
}
