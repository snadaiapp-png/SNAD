package com.sanad.platform.crm.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.service.PullSyncService;
import com.sanad.platform.crm.mobile.sync.service.PushSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Focused regression tests for G7 correctness hardening. */
class G7DefectFixesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void pushCreateDropsNonAllowlistedPayloadKeys() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("name", "Acme");
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
            .orElseThrow();
        assertTrue(insert.contains("name"));
        assertFalse(insert.contains("evil"));
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

        ConflictService.ConflictDetection c3 = service.detectConflict(
            TENANT, DEVICE, USER, "account", "00000000-0000-0000-0000-000000000012",
            2, empty, 3, empty, "UPDATE", true, true);
        assertEquals("C3", c3.conflictClass());

        ConflictService.ConflictDetection c4 = service.detectConflict(
            TENANT, DEVICE, USER, "account", "00000000-0000-0000-0000-000000000013",
            2, empty, 3, empty, "DELETE", false, true);
        assertEquals("C4", c4.conflictClass());
    }

    @Test
    void pushRejectsStaleExpectedVersionAsConflict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());

        ObjectNode payload = new ObjectMapper().createObjectNode().put("name", "Acme");
        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope(
            "idem-etag-1", "account", "00000000-0000-0000-0000-000000000010",
            "UPDATE", 3L, payload, null);

        PushSyncResponse response = service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));
        assertEquals(0, response.applied());
        assertEquals(1, response.rejected());
        assertEquals("CONFLICT", response.results().get(0).status());
        assertEquals("412", response.results().get(0).httpStatus());
        assertEquals(5, response.results().get(0).conflictInfo().get("serverVersion").asInt());
    }

    /** Regression for the Object[]-inside-Object[] UPDATE binding defect. */
    @Test
    void pushUpdateFlattensDynamicAndGuardParameters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());

        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("name", "Updated");
        payload.put("status", "ACTIVE");
        String entityId = "00000000-0000-0000-0000-000000000020";
        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope(
            "idem-update-flat", "account", entityId, "UPDATE", 5L, payload, null);

        PushSyncResponse response = service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));
        assertEquals(1, response.applied());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(sqlCaptor.capture(), paramsCaptor.capture());

        int updateIndex = -1;
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            if (sqlCaptor.getAllValues().get(i).startsWith("UPDATE crm_accounts SET")) {
                updateIndex = i;
                break;
            }
        }
        assertTrue(updateIndex >= 0, "CRM UPDATE statement must execute");
        Object[] params = paramsCaptor.getAllValues().get(updateIndex);
        assertEquals(5, params.length, "2 payload values + tenant + id + version");
        assertFalse(params[0] instanceof Object[], "payload parameters must be flat");
        assertFalse(params[1] instanceof Object[], "payload parameters must be flat");
        assertEquals(TENANT, params[2]);
        assertEquals(entityId, params[3]);
        assertEquals(5L, params[4]);
    }

    /** Regression for page-boundary loss when many rows share entity sync_version. */
    @Test
    void pullCursorUsesUniqueChangeIdsAcrossPages() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        PullSyncService service = new PullSyncService(jdbc, mapper);
        UUID e1 = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID e2 = UUID.fromString("00000000-0000-0000-0000-000000000032");
        UUID e3 = UUID.fromString("00000000-0000-0000-0000-000000000033");
        Timestamp now = Timestamp.from(Instant.parse("2026-08-19T00:00:00Z"));

        Map<String, Object> r100 = Map.of(
            "change_id", 100L, "entity_id", e1, "operation", "CREATE",
            "entity_version", 1L, "payload", "{\"name\":\"A\"}", "changed_at", now);
        Map<String, Object> r101 = Map.of(
            "change_id", 101L, "entity_id", e2, "operation", "CREATE",
            "entity_version", 1L, "payload", "{\"name\":\"B\"}", "changed_at", now);
        Map<String, Object> r102 = Map.of(
            "change_id", 102L, "entity_id", e3, "operation", "CREATE",
            "entity_version", 1L, "payload", "{\"name\":\"C\"}", "changed_at", now);

        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            Object[] args = invocation.getArgument(1);
            long cursor = ((Number) args[2]).longValue();
            if (cursor == 0L) return List.of(r100, r101, r102); // limit+1 probe
            if (cursor == 101L) return List.of(r102);
            return List.of();
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        DeltaSyncResponse first = service.pull(TENANT, DEVICE, new DeltaSyncRequest("account", null, 2));
        assertEquals(2, first.entityCount());
        assertTrue(first.hasMore());
        assertEquals(101L, decodeCursor(first.nextCursor()));
        assertEquals(1L, first.entities().get(0).version());
        assertEquals(1L, first.entities().get(1).version());

        DeltaSyncResponse second = service.pull(TENANT, DEVICE,
            new DeltaSyncRequest("account", first.nextCursor(), 2));
        assertEquals(1, second.entityCount());
        assertFalse(second.hasMore());
        assertEquals(102L, decodeCursor(second.nextCursor()));
        assertEquals(e3.toString(), second.entities().get(0).entityId());
    }

    @Test
    void conflictSkipUsesPendingStatusNotInvalidDeferredResolution() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ConflictService service = new ConflictService(jdbc, new ObjectMapper());
        UUID conflictId = UUID.fromString("00000000-0000-0000-0000-000000000040");

        service.deferConflict(TENANT, conflictId, USER);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("status = 'RESOLUTION_PENDING'"));
        assertTrue(sql.getValue().contains("resolution = NULL"));
        assertFalse(sql.getValue().contains("resolution = 'DEFERRED'"));
    }

    private static long decodeCursor(String cursor) {
        return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor)));
    }
}
