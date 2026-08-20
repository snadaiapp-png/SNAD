package com.sanad.platform.crm.mobile.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class G7PushSyncIntegrityContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mutationFingerprint_isCanonicalAndPayloadSensitive() {
        ObjectNode payloadA = objectMapper.createObjectNode();
        payloadA.put("display_name", "Acme");
        payloadA.put("primary_phone", "+966500000000");

        ObjectNode payloadEquivalent = objectMapper.createObjectNode();
        payloadEquivalent.put("primary_phone", "+966500000000");
        payloadEquivalent.put("display_name", "Acme");

        ObjectNode changedPayload = objectMapper.createObjectNode();
        changedPayload.put("primary_phone", "+966500000001");
        changedPayload.put("display_name", "Acme");

        var a = mutation("idem-1", payloadA, 4L);
        var equivalent = mutation("idem-1", payloadEquivalent, 4L);
        var changed = mutation("idem-1", changedPayload, 4L);

        String fingerprintA = PushSyncService.computeMutationFingerprint(a);
        String fingerprintEquivalent = PushSyncService.computeMutationFingerprint(equivalent);
        String fingerprintChanged = PushSyncService.computeMutationFingerprint(changed);

        assertThat(fingerprintA).matches("[0-9a-f]{64}");
        assertThat(fingerprintEquivalent).isEqualTo(fingerprintA);
        assertThat(fingerprintChanged).isNotEqualTo(fingerprintA);
    }

    @Test
    void staleUpdate_persistsConflictViaConflictService() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConflictService conflicts = mock(ConflictService.class);
        PushSyncService service = new PushSyncService(jdbc, objectMapper, conflicts);

        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        ObjectNode clientPayload = objectMapper.createObjectNode().put("display_name", "Client");
        ObjectNode serverPayload = objectMapper.createObjectNode().put("display_name", "Server");

        var mutation = new PushSyncRequest.MutationEnvelope(
                "idem-stale", "account", entityId.toString(), "UPDATE", 4L,
                clientPayload, "2026-08-20T15:00:00Z");

        // Idempotency claim succeeds.
        when(jdbc.queryForObject(startsWith("INSERT INTO crm_idempotency_records"), eq(UUID.class), any(Object[].class)))
                .thenReturn(UUID.randomUUID());
        when(jdbc.queryForObject(startsWith("SELECT sync_version"), eq(Long.class),
                eq(tenantId), eq(entityId.toString()))).thenReturn(5L);
        when(jdbc.queryForObject(startsWith("SELECT to_jsonb"), eq(String.class),
                eq(tenantId), eq(entityId.toString())))
                .thenReturn(objectMapper.writeValueAsString(serverPayload));

        var detection = new ConflictService.ConflictDetection(
                UUID.randomUUID().toString(), "SAME_FIELD_BOTH_SIDES", "C1", false, 5L, 4L);
        when(conflicts.detectConflict(eq(tenantId), eq(deviceId), eq(userId),
                eq("account"), eq(entityId.toString()), eq(4L), eq(clientPayload),
                eq(5L), any(JsonNode.class), eq("UPDATE"), eq(false), eq(true)))
                .thenReturn(detection);

        var response = service.push(tenantId, deviceId, userId,
                new PushSyncRequest(List.of(mutation)));

        assertThat(response.rejected()).isEqualTo(1);
        assertThat(response.results().getFirst().status()).isEqualTo("CONFLICT");
        assertThat(response.results().getFirst().httpStatus()).isEqualTo("412");
        verify(conflicts).detectConflict(eq(tenantId), eq(deviceId), eq(userId),
                eq("account"), eq(entityId.toString()), eq(4L), eq(clientPayload),
                eq(5L), any(JsonNode.class), eq("UPDATE"), eq(false), eq(true));
    }

    @Test
    void updateWithoutExpectedVersion_isRejectedInsteadOfBlindWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConflictService conflicts = mock(ConflictService.class);
        PushSyncService service = new PushSyncService(jdbc, objectMapper, conflicts);

        var mutation = mutation("idem-no-version",
                objectMapper.createObjectNode().put("display_name", "Changed"), null);

        when(jdbc.queryForObject(startsWith("INSERT INTO crm_idempotency_records"), eq(UUID.class), any(Object[].class)))
                .thenReturn(UUID.randomUUID());

        var response = service.push(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new PushSyncRequest(List.of(mutation)));

        assertThat(response.rejected()).isEqualTo(1);
        assertThat(response.results().getFirst().httpStatus()).isEqualTo("428");
        verify(jdbc, never()).update(startsWith("UPDATE crm_accounts SET"), any(Object[].class));
    }

    private PushSyncRequest.MutationEnvelope mutation(String key, JsonNode payload, Long expectedVersion) {
        return new PushSyncRequest.MutationEnvelope(
                key, "account", UUID.randomUUID().toString(), "UPDATE",
                expectedVersion, payload, "2026-08-20T15:00:00Z");
    }
}
