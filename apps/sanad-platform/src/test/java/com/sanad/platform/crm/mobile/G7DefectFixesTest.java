package com.sanad.platform.crm.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.service.PushSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class G7DefectFixesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void pushCreateDropsNonAllowlistedPayloadKeys() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConflictService conflictService = mock(ConflictService.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenAnswer(inv -> {
            Class<?> clz = inv.getArgument(1);
            if (clz == UUID.class) return UUID.randomUUID();
            if (clz == Long.class) throw new EmptyResultDataAccessException(1);
            return null;
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(
                jdbc, new ObjectMapper(), conflictService, transactionManager());

        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("display_name", "Acme");
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
        assertFalse(insert.contains("evil"));
        assertFalse(insert.contains("VALUES (1)"));

        int colsStart = insert.indexOf('(') + 1;
        int colsEnd = insert.indexOf(')', colsStart);
        String colList = insert.substring(colsStart, colsEnd).replaceAll("\\s+", "");
        assertEquals("tenant_id,id,created_by,updated_by,sync_version,display_name,normalized_name,created_at,updated_at", colList);
    }

    @Test
    void conflictClassificationCoversDeleteUpdateAndCrossTenant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ConflictService service = new ConflictService(jdbc, new ObjectMapper());
        ObjectNode empty = new ObjectMapper().createObjectNode();

        ConflictService.ConflictDetection c10 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e1", 2, empty, 3, empty, "UPDATE", true, false);
        assertEquals("C10", c10.conflictClass());
        assertEquals("CROSS_TENANT_ATTEMPT", c10.conflictType());

        ConflictService.ConflictDetection c3 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e2", 2, empty, 3, empty, "UPDATE", true, true);
        assertEquals("C3", c3.conflictClass());
        assertEquals("DELETE_VS_UPDATE", c3.conflictType());

        ConflictService.ConflictDetection c4 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e3", 2, empty, 3, empty, "DELETE", false, true);
        assertEquals("C4", c4.conflictClass());
        assertEquals("UPDATE_VS_DELETE", c4.conflictType());

        ObjectNode client = new ObjectMapper().createObjectNode().put("name", "A");
        ObjectNode server = new ObjectMapper().createObjectNode().put("name", "B");
        ConflictService.ConflictDetection c1 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e4", 5, client, 5, server);
        assertEquals("C1", c1.conflictClass());
    }

    @Test
    void pushRejectsStaleExpectedVersionAsConflict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConflictService conflictService = mock(ConflictService.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenAnswer(inv -> {
            Class<?> clz = inv.getArgument(1);
            if (clz == UUID.class) return UUID.randomUUID();
            if (clz == Long.class) return 5L;
            if (clz == String.class) return "{}";
            return null;
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(conflictService.detectConflict(any(), any(), any(), anyString(), anyString(),
                anyLong(), any(), anyLong(), any(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(new ConflictService.ConflictDetection(
                        "conflict-1", "VERSION_MISMATCH", "C9", false, 5L, 3L));

        PushSyncService service = new PushSyncService(
                jdbc, new ObjectMapper(), conflictService, transactionManager());

        ObjectNode payload = new ObjectMapper().createObjectNode().put("name", "Acme");
        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope(
            "idem-etag-1", "account", "00000000-0000-0000-0000-000000000010",
            "UPDATE", 3L, payload, null);

        PushSyncResponse response = service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));

        assertEquals(0, response.applied());
        assertEquals(1, response.rejected());
        assertEquals(0, response.duplicates());

        PushSyncResponse.MutationResult result = response.results().get(0);
        assertEquals("CONFLICT", result.status());
        assertEquals("412", result.httpStatus());
        assertNotNull(result.conflictInfo());
        assertEquals("VERSION_MISMATCH", result.conflictInfo().get("conflictType").asText());
        assertEquals(5, result.conflictInfo().get("serverVersion").asInt());
        verify(conflictService).detectConflict(any(), any(), any(), anyString(), anyString(),
                anyLong(), any(), anyLong(), any(), anyString(), anyBoolean(), anyBoolean());
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        return transactionManager;
    }
}
