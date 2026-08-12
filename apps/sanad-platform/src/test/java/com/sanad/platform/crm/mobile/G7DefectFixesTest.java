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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Focused unit tests for the G7 defect fixes — no Spring context, no database.
 *
 *  - DEF-004: column allowlist prevents SQL injection via mutation payload keys.
 *  - DEF-006: ConflictService classifies the newly-added classes C3 / C4 / C10.
 */
class G7DefectFixesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    // ─────────────────────────────────────────────────────────────────
    // DEF-004: SQL-injection payload keys must be dropped, never spliced.
    // ─────────────────────────────────────────────────────────────────
    @Test
    void pushCreateDropsNonAllowlistedPayloadKeys() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Entity not found → CREATE path; not a duplicate idempotency key.
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenThrow(new RuntimeException("not found"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        PushSyncService service = new PushSyncService(jdbc, new ObjectMapper());

        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("name", "Acme");
        // Malicious payload key attempting SQL injection as a column identifier.
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

        // Allowlisted column is present...
        assertTrue(insert.contains("name"), "allowlisted 'name' column must be in INSERT: " + insert);
        // ...and the injection attempt / tenant_id override is NOT spliced into the SQL.
        assertFalse(insert.contains("evil"), "injection key leaked into SQL: " + insert);
        assertFalse(insert.contains("VALUES (1)"), "injection fragment leaked into SQL: " + insert);

        // The column list must be exactly the server-hardcoded system columns plus
        // the single allowlisted payload column ("name") — the client's "tenant_id"
        // and injection keys must have been dropped (not added as extra columns).
        int colsStart = insert.indexOf('(') + 1;
        int colsEnd = insert.indexOf(')', colsStart);
        String colList = insert.substring(colsStart, colsEnd).replaceAll("\\s+", "");
        assertEquals("tenant_id,id,created_by,sync_version,name,created_at,updated_at", colList,
                "only allowlisted payload columns may appear; got: " + colList);
    }

    // ─────────────────────────────────────────────────────────────────
    // DEF-006: new conflict classes C3 / C4 / C10 are classified.
    // ─────────────────────────────────────────────────────────────────
    @Test
    void conflictClassificationCoversDeleteUpdateAndCrossTenant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1); // logConflict INSERT
        ConflictService service = new ConflictService(jdbc, new ObjectMapper());
        ObjectNode empty = new ObjectMapper().createObjectNode();

        // C10 — cross-tenant attempt (entity not owned by caller's tenant)
        ConflictService.ConflictDetection c10 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e1", 2, empty, 3, empty, "UPDATE", true, false);
        assertEquals("C10", c10.conflictClass());
        assertEquals("CROSS_TENANT_ATTEMPT", c10.conflictType());

        // C3 — server deleted the row, client tries to update
        ConflictService.ConflictDetection c3 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e2", 2, empty, 3, empty, "UPDATE", true, true);
        assertEquals("C3", c3.conflictClass());
        assertEquals("DELETE_VS_UPDATE", c3.conflictType());

        // C4 — client DELETE against a stale copy the server has since updated
        ConflictService.ConflictDetection c4 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e3", 2, empty, 3, empty, "DELETE", false, true);
        assertEquals("C4", c4.conflictClass());
        assertEquals("UPDATE_VS_DELETE", c4.conflictType());

        // Sanity: existing C1 still classified (same version, overlapping field)
        ObjectNode client = new ObjectMapper().createObjectNode().put("name", "A");
        ObjectNode server = new ObjectMapper().createObjectNode().put("name", "B");
        ConflictService.ConflictDetection c1 = service.detectConflict(
                TENANT, DEVICE, USER, "account", "e4", 5, client, 5, server);
        assertEquals("C1", c1.conflictClass());
    }
}
