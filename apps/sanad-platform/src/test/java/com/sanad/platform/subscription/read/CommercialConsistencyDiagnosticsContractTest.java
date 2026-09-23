package com.sanad.platform.subscription.read;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialConsistencyDiagnosticsContractTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void reportsCanonicalCommercialAnomalyClassesUsingSelectOnlyQueries() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("code", "ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION", "tenant_id", tenantId,
                        "evidence", "tenant=ACTIVE,effective=0"),
                Map.of("code", "DUNNING_STATE_MISMATCH", "tenant_id", tenantId,
                        "evidence", "billing=CURRENT,lifecycle=PAST_DUE")
        ));

        var service = new CommercialConsistencyDiagnosticsService(jdbc);
        var anomalies = service.scan();

        assertThat(anomalies).extracting(CommercialConsistencyDiagnosticsService.CommercialAnomaly::code)
                .contains("ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION", "DUNNING_STATE_MISMATCH");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        String normalized = sql.getValue().toUpperCase();
        assertThat(normalized).contains("SELECT");
        assertThat(normalized).doesNotContain("UPDATE ").doesNotContain("INSERT ").doesNotContain("DELETE ");
    }
}
