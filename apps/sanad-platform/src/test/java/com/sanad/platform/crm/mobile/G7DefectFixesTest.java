package com.sanad.platform.crm.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.service.PushSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Focused unit tests for G7 security/version/conflict defect fixes. */
class G7DefectFixesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void pushCreateDropsNonAllowlistedPayloadKeys() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenThrow(new RuntimeException("not found"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("name", "Acme"); // legacy alias -> canonical display_name
        payload.put("evil) VALUES (1)--", "pwned");
        payload.put("tenant_id", "should-be-ignored");

        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope(
                "idem-1", "account", null, "CREATE", null, payload, null);
        PushSyncResponse response = service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));

        assertEquals(1, response.applied());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(Object[].class));
        String insert = sql.getAllValues().stream()
                .filter(s -> s.startsWith("INSERT INTO crm_accounts"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("INSERT INTO crm_accounts not executed: " + sql.getAllValues()));

        assertTrue(insert.contains("display_name"));
        assertTrue(insert.contains("normalized_name"));
        assertFalse(insert.contains("evil"), "injection key leaked into SQL: " + insert);
        assertFalse(insert.contains("VALUES (1)"), "injection fragment leaked into SQL: " + insert);
        assertFalse(insert.contains("should-be-ignored"));
    }

    @Test
    void conflictClassificationCoversDeleteUpdateAndCrossTenant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ConflictService service = new ConflictService(jdbc, new ObjectMapper());
        ObjectNode empty = new ObjectMapper().createObjectNode();

        ConflictService.ConflictDetection c10 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "00000000-0000-0000-0000-000000000011",
                2, empty, 3, empty, "UPDATE", true, false);
        assertEquals("C10", c10.conflictClass());
        assertEquals("CROSS_TENANT_ATTEMPT", c10.conflictType());

        ConflictService.ConflictDetection c3 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "00000000-0000-0000-0000-000000000012",
                2, empty, 3, empty, "UPDATE", true, true);
        assertEquals("C3", c3.conflictClass());
        assertEquals("DELETE_VS_UPDATE", c3.conflictType());

        ConflictService.ConflictDetection c4 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "00000000-0000-0000-0000-000000000013",
                2, empty, 3, empty, "DELETE", false, true);
        assertEquals("C4", c4.conflictClass());
        assertEquals("UPDATE_VS_DELETE", c4.conflictType());

        ObjectNode client = new ObjectMapper().createObjectNode().put("display_name", "A");
        ObjectNode server = new ObjectMapper().createObjectNode().put("display_name", "B");
        ConflictService.ConflictDetection c1 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "00000000-0000-0000-0000-000000000014",
                5, client, 5, server);
        assertEquals("C1", c1.conflictClass());
    }

    @Test
    void pushRejectsStaleExpectedVersionAsConflict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenAnswer(inv -> {
            Class<?> clz = inv.getArgument(1);
            if (clz == Integer.class) return 0;
            if (clz == Long.class) return 5L;
            return null;
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("display_name", "Acme");
        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope(
            "idem-etag-1", "account", "00000000-0000-0000-0000-000000000010",
            "UPDATE", 3L, payload, null);

        PushSyncResponse response = service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));
        assertEquals(0, response.applied());
        assertEquals(1, response.rejected());
        assertEquals("CONFLICT", response.results().get(0).status());
        assertEquals("412", response.results().get(0).httpStatus());
        assertNotNull(response.results().get(0).conflictInfo());
        assertEquals(5, response.results().get(0).conflictInfo().get("serverVersion").asInt());
    }
}
