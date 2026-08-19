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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression tests for the 2026-08-19 G7 correctness hardening. */
class G7CorrectnessHardeningTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void pushUpdateBindsPayloadValuesAsScalarJdbcParameters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());
        ObjectNode payload = new ObjectMapper().createObjectNode()
                .put("display_name", "Acme Updated")
                .put("source", "mobile");
        PushSyncRequest.MutationEnvelope mutation = new PushSyncRequest.MutationEnvelope(
                "idem-flat-params", "account",
                "00000000-0000-0000-0000-000000000010",
                "UPDATE", 7L, payload, null);

        PushSyncResponse response = service.push(TENANT, DEVICE, USER, new PushSyncRequest(List.of(mutation)));
        assertEquals(1, response.applied());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), args.capture());

        int updateIndex = -1;
        for (int i = 0; i < sql.getAllValues().size(); i++) {
            if (sql.getAllValues().get(i).startsWith("UPDATE crm_accounts SET")) {
                updateIndex = i;
                break;
            }
        }
        assertTrue(updateIndex >= 0, "account UPDATE was not executed");
        Object[] bound = args.getAllValues().get(updateIndex);
        assertEquals(5, bound.length, "two fields + tenant + id + version must be five scalar parameters");
        assertFalse(bound[0] instanceof Object[], "payload values must not be nested as a JDBC array parameter");
        assertEquals("Acme Updated", bound[0]);
        assertEquals("mobile", bound[1]);
    }

    @Test
    void pullUsesMonotonicChangeIdAndDoesNotTreatRowVersionAsCursor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID entity = UUID.fromString("00000000-0000-0000-0000-000000000020");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of(
                        "change_id", 101L,
                        "entity_id", entity,
                        "entity_version", 1L,
                        "change_operation", "INSERT",
                        "changed_at", Timestamp.from(Instant.parse("2026-08-19T08:00:00Z")),
                        "sync_version", 1L,
                        "display_name", "Acme",
                        "created_at", Timestamp.from(Instant.parse("2026-08-19T08:00:00Z")),
                        "updated_at", Timestamp.from(Instant.parse("2026-08-19T08:00:00Z"))
                )
        ));

        PullSyncService service = new PullSyncService(jdbc);
        DeltaSyncResponse response = service.pull(TENANT, DEVICE, new DeltaSyncRequest("account", null, 100));
        assertEquals(1, response.entities().size());
        assertEquals(1L, response.entities().get(0).version());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("mobile_change_log"), "delta cursor must be driven by the change feed");
        assertTrue(sql.getValue().contains("change_id > ?"), "cursor comparison must use monotonic change_id");
        assertFalse(sql.getValue().contains("sync_version > ?"), "row-local sync_version must never be the delta cursor");
        assertNotNull(response.nextCursor());
    }

    @Test
    void conflictSkipMovesToPendingWithoutWritingInvalidResolution() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ConflictService service = new ConflictService(jdbc, new ObjectMapper());
        UUID conflictId = UUID.fromString("00000000-0000-0000-0000-000000000030");

        boolean skipped = service.skipConflict(TENANT, conflictId, USER);
        assertTrue(skipped);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("status = 'RESOLUTION_PENDING'"));
        assertTrue(sql.getValue().contains("resolution = NULL"));
        for (Object arg : args.getValue()) {
            assertNotEquals("DEFERRED", arg, "DEFERRED is a workflow status, not a permitted resolution value");
        }
    }
}
