package com.sanad.platform.crm.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.DeltaSyncResponse;
import com.sanad.platform.crm.mobile.sync.model.PushSyncRequest;
import com.sanad.platform.crm.mobile.sync.model.PushSyncResponse;
import com.sanad.platform.crm.mobile.sync.service.PullSyncService;
import com.sanad.platform.crm.mobile.sync.service.PushSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** G7 zero-data-loss correctness regression tests. */
class G7CorrectnessHardeningTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID ENTITY_1 = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID ENTITY_2 = UUID.fromString("00000000-0000-0000-0000-000000000112");
    private static final UUID ENTITY_3 = UUID.fromString("00000000-0000-0000-0000-000000000113");

    @Test
    void pullUsesGlobalChangeIdSoEqualEntityVersionsCannotBeSkipped() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Object[] params = invocation.getArgument(1);
            if (!sql.contains("mobile_change_log")) return List.of();
            long afterChangeId = ((Number) params[2]).longValue();
            if (afterChangeId == 0L) {
                return List.of(change(101L, ENTITY_1, 1L, "Acme"), change(102L, ENTITY_2, 1L, "Beta"), change(103L, ENTITY_3, 1L, "Gamma"));
            }
            if (afterChangeId == 102L) return List.of(change(103L, ENTITY_3, 1L, "Gamma"));
            return List.of();
        });

        PullSyncService service = new PullSyncService(jdbc, new ObjectMapper());
        DeltaSyncResponse first = service.pull(TENANT, DEVICE, new DeltaSyncRequest("account", null, 2));
        assertEquals(2, first.entityCount());
        assertTrue(first.hasMore());
        assertEquals(List.of(ENTITY_1.toString(), ENTITY_2.toString()), first.entities().stream().map(DeltaSyncResponse.EntityDelta::entityId).toList());
        assertEquals(102L, decode(first.nextCursor()));

        DeltaSyncResponse second = service.pull(TENANT, DEVICE, new DeltaSyncRequest("account", first.nextCursor(), 2));
        assertEquals(1, second.entityCount());
        assertFalse(second.hasMore());
        assertEquals(ENTITY_3.toString(), second.entities().get(0).entityId());
        assertEquals(1L, second.entities().get(0).version());
        assertEquals(103L, decode(second.nextCursor()));
    }

    @Test
    void pushUpdateFlattensJdbcParametersAndMapsLegacyAccountAliasToCanonicalColumn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenAnswer(inv -> {
            Class<?> type = inv.getArgument(1);
            if (type == Integer.class) return 0;
            if (type == Long.class) return 5L;
            return null;
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("name", "Acme Updated");
        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope("g7-update-flat-1", "account", ENTITY_1.toString(), "UPDATE", 5L, payload, null);
        PushSyncResponse response = service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));
        assertEquals(1, response.applied());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            if (sqlCaptor.getAllValues().get(i).startsWith("UPDATE crm_accounts")) {
                String sql = sqlCaptor.getAllValues().get(i);
                Object[] args = argsCaptor.getAllValues().get(i);
                assertTrue(sql.contains("display_name = ?"));
                assertFalse(args[0] instanceof Object[]);
                assertEquals("Acme Updated", args[0]);
                assertEquals(TENANT, args[args.length - 3]);
                assertEquals(ENTITY_1.toString(), args[args.length - 2]);
                assertEquals(5L, ((Number) args[args.length - 1]).longValue());
                return;
            }
        }
        fail("canonical crm_accounts UPDATE was not executed");
    }

    @Test
    void pushNumericFieldsRemainNumericInsteadOfBeingStringified() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenAnswer(inv -> {
            Class<?> type = inv.getArgument(1);
            if (type == Integer.class) return 0;
            if (type == Long.class) return 7L;
            return null;
        });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode().put("amount", new BigDecimal("1444.50"));
        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope("g7-update-number-1", "opportunity", ENTITY_1.toString(), "UPDATE", 7L, payload, null);
        service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            if (sqlCaptor.getAllValues().get(i).startsWith("UPDATE crm_opportunities")) {
                Object value = argsCaptor.getAllValues().get(i)[0];
                assertTrue(value instanceof Number, "numeric CRM fields must bind as Number");
                return;
            }
        }
        fail("crm_opportunities UPDATE was not executed");
    }

    private static Map<String, Object> change(long changeId, UUID entityId, long entityVersion, String name) {
        return Map.of("change_id", changeId, "entity_id", entityId, "operation", "UPDATE", "entity_version", entityVersion,
            "payload", "{\"display_name\":\"" + name + "\",\"created_at\":\"2026-08-19T00:00:00Z\"}",
            "changed_at", Timestamp.from(Instant.parse("2026-08-19T00:00:00Z")));
    }

    private static long decode(String cursor) {
        return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor)));
    }
}
