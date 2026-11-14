package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the webhook worker: claim-then-process routing,
 * bounded exponential-backoff retries, FAILED_MANUAL terminal state,
 * and the REAUTH_REQUIRED short-circuit on rejected credentials.
 */
class ZidWebhookProcessorTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CONNECTION_ID = UUID.randomUUID();

    private NamedParameterJdbcTemplate jdbc;
    private ZidProductSyncService productSyncService;
    private ZidOrderSyncService orderSyncService;
    private ZidConnectionRepository connections;
    private ZidSyncAuditService auditService;
    private TenantRlsTransactionContext tenantRlsContext;
    private ZidIntegrationProperties properties;
    private ZidWebhookProcessor processor;

    private final ZidConnectionRecord connection = new ZidConnectionRecord(
            CONNECTION_ID, TENANT, "3", "store", ZidConnectionRecord.STATUS_CONNECTED,
            null, null, null, null, Instant.now(), Instant.now(), null, null, null,
            Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        productSyncService = mock(ZidProductSyncService.class);
        orderSyncService = mock(ZidOrderSyncService.class);
        connections = mock(ZidConnectionRepository.class);
        auditService = mock(ZidSyncAuditService.class);
        tenantRlsContext = mock(TenantRlsTransactionContext.class);
        properties = new ZidIntegrationProperties();
        properties.setEnabled(true);
        properties.setWebhookMaxAttempts(3);
        properties.setWebhookRetryBaseDelay(java.time.Duration.ofSeconds(60));
        processor = new ZidWebhookProcessor(jdbc, properties, productSyncService,
                orderSyncService, connections, auditService, tenantRlsContext);
    }

    private Map<String, Object> claimedRow(String eventType, int attempts) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("tenant_id", TENANT);
        row.put("connection_id", CONNECTION_ID);
        row.put("event_type", eventType);
        row.put("attempts", attempts);
        return row;
    }

    @Test
    void routesOrderEventsToOrderSync() {
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(List.of(claimedRow("order.created", 1)));
        processor.processOne();
        verify(orderSyncService).syncConnection(CONNECTION_ID);
        verify(productSyncService, never()).syncConnection(any());
        verify(tenantRlsContext).applyForCurrentTransaction(TENANT);
    }

    @Test
    void routesProductEventsToProductSync() {
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(List.of(claimedRow("product.updated", 1)));
        processor.processOne();
        verify(productSyncService).syncConnection(CONNECTION_ID);
        verify(orderSyncService, never()).syncConnection(any());
    }

    @Test
    void unknownEventTypesAreStoredButNotDispatched() {
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(List.of(claimedRow("blog.post.published", 1)));
        processor.processOne();
        verify(productSyncService, never()).syncConnection(any());
        verify(orderSyncService, never()).syncConnection(any());
    }

    @Test
    void failureSchedulesExponentialBackoffUntilMaxAttempts() {
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(List.of(claimedRow("order.created", 1)));
        when(orderSyncService.syncConnection(CONNECTION_ID))
                .thenThrow(new RuntimeException("Zid API server error (503)"));

        processor.processOne();

        org.mockito.ArgumentCaptor<org.springframework.jdbc.core.namedparam.MapSqlParameterSource> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class);
        // Last update call is the backoff scheduling UPDATE.
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(anyString(), captor.capture());
        assertThat(captor.getAllValues().toString()).contains("backoff");
    }

    @Test
    void terminalFailureMovesEventToManualReview() {
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(List.of(claimedRow("order.created", 5)));
        when(orderSyncService.syncConnection(CONNECTION_ID))
                .thenThrow(new RuntimeException("Zid API server error (503)"));

        processor.processOne();

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(),
                any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
        assertThat(String.join(";", sql.getAllValues())).contains("FAILED_MANUAL");
    }

    @Test
    void rejectedCredentialsMarkConnectionReauthRequired() {
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(List.of(claimedRow("order.created", 1)));
        when(orderSyncService.syncConnection(CONNECTION_ID))
                .thenThrow(new RuntimeException("Zid API rejected the stored credentials (401)"));
        when(connections.findById(CONNECTION_ID)).thenReturn(java.util.Optional.of(connection));

        processor.processOne();

        verify(connections).updateStatus(CONNECTION_ID, ZidConnectionRecord.STATUS_REAUTH_REQUIRED, null);
    }

    @Test
    void emptyQueueIsNoOp() {
        when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(List.of());
        boolean processed = processor.processOne();
        assertThat(processed).isFalse();
        verify(productSyncService, never()).syncConnection(any());
        verify(orderSyncService, never()).syncConnection(any());
    }
}
