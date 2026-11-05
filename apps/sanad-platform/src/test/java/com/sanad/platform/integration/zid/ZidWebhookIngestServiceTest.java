package com.sanad.platform.integration.zid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.integration.zid.application.ZidSyncAuditService;
import com.sanad.platform.integration.zid.application.ZidWebhookIngestService;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidWebhookVerifier;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the webhook ingest decision matrix:
 * unconfigured / invalid signature / unbound store / duplicate /
 * replay mismatch / accepted. Verifies the fail-closed guarantee that
 * NOTHING is persisted for rejected events.
 */
class ZidWebhookIngestServiceTest {

    private static final UUID TENANT_A = UUID.randomUUID();

    private ZidWebhookVerifier verifier;
    private ZidConnectionRepository connections;
    private ZidSyncAuditService auditService;
    private TenantRlsTransactionContext tenantRlsContext;
    private NamedParameterJdbcTemplate jdbc;
    private ZidWebhookIngestService service;

    private final ZidConnectionRecord connection = new ZidConnectionRecord(
            UUID.randomUUID(), TENANT_A, "3", "متجر الاختبار",
            ZidConnectionRecord.STATUS_CONNECTED,
            "enc:v1:auth", "enc:v1:manager", "enc:v1:refresh", "v1",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(86400),
            null, null, null, Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        verifier = Mockito.mock(ZidWebhookVerifier.class);
        connections = Mockito.mock(ZidConnectionRepository.class);
        auditService = Mockito.mock(ZidSyncAuditService.class);
        tenantRlsContext = Mockito.mock(TenantRlsTransactionContext.class);
        jdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(verifier.isConfigured()).thenReturn(true);
        when(connections.findByStoreId("3")).thenReturn(Optional.of(connection));

        service = new ZidWebhookIngestService(verifier, connections, auditService,
                tenantRlsContext, jdbc, objectMapper);
    }

    @Test
    void returnsNotConfiguredWhenSecretMissing() {
        when(verifier.isConfigured()).thenReturn(false);
        ZidWebhookIngestService.IngestResult result =
                service.ingest("{\"event\":\"order.created\"}".getBytes(StandardCharsets.UTF_8), "sig");
        assertThat(result.outcome()).isEqualTo(ZidWebhookIngestService.Outcome.NOT_CONFIGURED);
        verify(jdbc, never()).update(anyString(), (org.springframework.jdbc.core.namedparam.SqlParameterSource) any());
    }

    @Test
    void rejectsInvalidSignatureWithoutPersisting() {
        Mockito.doThrow(new ZidIntegrationException(
                        ZidIntegrationException.Reason.INVALID_WEBHOOK_SIGNATURE, "bad signature"))
                .when(verifier).verify(any(), anyString());
        ZidWebhookIngestService.IngestResult result =
                service.ingest("{\"event\":\"order.created\"}".getBytes(StandardCharsets.UTF_8), "badsig");
        assertThat(result.outcome()).isEqualTo(ZidWebhookIngestService.Outcome.INVALID_SIGNATURE);
        verify(jdbc, never()).update(anyString(), (org.springframework.jdbc.core.namedparam.SqlParameterSource) any());
        verify(tenantRlsContext, never()).applyForCurrentTransaction(any());
    }

    @Test
    void rejectsUnboundStoreWithoutPersisting() {
        byte[] body = "{\"event\":\"order.created\",\"store_id\":\"999\"}".getBytes(StandardCharsets.UTF_8);
        ZidWebhookIngestService.IngestResult result = service.ingest(body, "sig");
        assertThat(result.outcome()).isEqualTo(ZidWebhookIngestService.Outcome.TRUSTED_BINDING_NOT_FOUND);
        verify(jdbc, never()).update(anyString(), (org.springframework.jdbc.core.namedparam.SqlParameterSource) any());
    }

    @Test
    void acceptsEventFromBoundStoreWithTenantGuc() {
        byte[] body = "{\"event\":\"order.created\",\"store_id\":\"3\",\"id\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(1);
        ZidWebhookIngestService.IngestResult result = service.ingest(body, "sig");
        assertThat(result.outcome()).isEqualTo(ZidWebhookIngestService.Outcome.ACCEPTED);
        assertThat(result.receipt().duplicate()).isFalse();
        // Tenant scope MUST come from the stored binding, not the payload.
        verify(tenantRlsContext).applyForCurrentTransaction(TENANT_A);
    }

    @Test
    void treatsIdenticalRedeliveryAsDuplicate() {
        byte[] body = "{\"event\":\"order.created\",\"store_id\":\"3\",\"id\":\"evt-2\"}".getBytes(StandardCharsets.UTF_8);
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("uk_zid_webhook_events"));
        when(jdbc.queryForObject(anyString(),
                any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                Mockito.eq(Integer.class)))
                .thenReturn(1);
        ZidWebhookIngestService.IngestResult result = service.ingest(body, "sig");
        assertThat(result.outcome()).isEqualTo(ZidWebhookIngestService.Outcome.ACCEPTED);
        assertThat(result.receipt().duplicate()).isTrue();
    }

    @Test
    void flagsReplayWhenEventIdCollidesWithDifferentPayload() {
        byte[] body = "{\"event\":\"order.created\",\"store_id\":\"3\",\"id\":\"evt-3\",\"x\":2}".getBytes(StandardCharsets.UTF_8);
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("uk_zid_webhook_events"));
        when(jdbc.queryForObject(anyString(),
                any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                Mockito.eq(Integer.class)))
                .thenReturn(0);
        ZidWebhookIngestService.IngestResult result = service.ingest(body, "sig");
        assertThat(result.outcome()).isEqualTo(ZidWebhookIngestService.Outcome.REPLAY_MISMATCH);
    }

    @Test
    void rejectsPayloadWithoutAnyStoreIdentifier() {
        byte[] body = "{\"event\":\"order.created\",\"data\":{\"order\":{}}}".getBytes(StandardCharsets.UTF_8);
        ZidWebhookIngestService.IngestResult result = service.ingest(body, "sig");
        assertThat(result.outcome()).isEqualTo(ZidWebhookIngestService.Outcome.TRUSTED_BINDING_NOT_FOUND);
        verify(jdbc, never()).update(anyString(), (org.springframework.jdbc.core.namedparam.SqlParameterSource) any());
    }

    @Test
    void auditTrailNeverReceivesRawPayload() {
        byte[] body = ("{\"event\":\"order.created\",\"store_id\":\"3\",\"customer\":{\"email\":\"victim@example.com\"}}")
                .getBytes(StandardCharsets.UTF_8);
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class)))
                .thenReturn(1);
        service.ingest(body, "sig");
        ArgumentCaptor<String> entity = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), any(), any(), entity.capture(),
                any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());
        assertThat(entity.getValue()).doesNotContain("victim@example.com");
    }

    @SuppressWarnings("unused")
    private static void unusedTypeReference() {
        JdbcOperations operations = null;
    }
}
