package com.sanad.platform.crm.task.contract;

import com.sanad.platform.crm.dto.CrmDtos;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the CRM Task bounded context.
 * <p>
 * Verifies the public API contract shape (camelCase DTOs, record types,
 * mapper round-trip) — same pattern as {@code CrmActivityContractTest}.
 * <p>
 * Branch: feature/crm-tasks
 */
class CrmTaskContractTest {

    private final CrmDtoMapper mapper = new CrmDtoMapper();

    @Test
    void taskResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.TaskResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "TaskResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "TaskResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void taskSummaryResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.TaskSummaryResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "TaskSummaryResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "TaskSummaryResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void taskResponseIsARecord() {
        assertTrue(CrmDtos.TaskResponse.class.isRecord(),
                "TaskResponse must be a Java record (immutable, typed)");
        assertTrue(CrmDtos.TaskSummaryResponse.class.isRecord(),
                "TaskSummaryResponse must be a Java record (immutable, typed)");
    }

    @Test
    void mapperRoundTripsTaskRow() {
        assertNotNull(mapper, "CrmDtoMapper must be instantiable");
    }

    @Test
    void mapperMapsTaskRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        java.time.OffsetDateTime dueAt = now.plusHours(1);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", 0L);
        row.put("title", "Follow up with client");
        row.put("description", "Discuss pricing options");
        row.put("related_type", "ACCOUNT");
        row.put("related_id", relatedId);
        row.put("assignee_user_id", assigneeId);
        row.put("owner_user_id", ownerId);
        row.put("status", "OPEN");
        row.put("priority", 50);
        row.put("start_at", null);
        row.put("due_at", dueAt);
        row.put("completed_at", null);
        row.put("result", null);
        row.put("created_at", now);
        row.put("updated_at", now);

        CrmDtos.TaskResponse dto = mapper.toTaskResponse(row);

        assertEquals(id, dto.id());
        assertEquals(0L, dto.version());
        assertEquals("Follow up with client", dto.title());
        assertEquals("Discuss pricing options", dto.description());
        assertEquals("ACCOUNT", dto.relatedType());
        assertEquals(relatedId, dto.relatedId());
        assertEquals(assigneeId, dto.assigneeUserId());
        assertEquals(ownerId, dto.ownerUserId());
        assertEquals("OPEN", dto.status());
        assertEquals(50, dto.priority());
        assertNotNull(dto.dueAt());
        assertNull(dto.completedAt());
        assertNull(dto.result());
    }

    @Test
    void nullRowProducesNullDto() {
        assertNull(mapper.toTaskResponse(null));
        assertNull(mapper.toTaskSummary(null));
    }
}
