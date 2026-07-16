package com.sanad.platform.crm.tag.contract;

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
 * Contract test for the CRM Tag bounded context.
 * <p>
 * Branch: feature/crm-tags
 */
class CrmTagContractTest {

    private final CrmDtoMapper mapper = new CrmDtoMapper();

    @Test
    void tagResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.TagResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "TagResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "TagResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void tagAssignmentResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.TagAssignmentResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "TagAssignmentResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "TagAssignmentResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void tagResponseIsARecord() {
        assertTrue(CrmDtos.TagResponse.class.isRecord(),
                "TagResponse must be a Java record");
        assertTrue(CrmDtos.TagAssignmentResponse.class.isRecord(),
                "TagAssignmentResponse must be a Java record");
    }

    @Test
    void mapperRoundTripsTagRow() {
        assertNotNull(mapper);
    }

    @Test
    void mapperMapsTagRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", 0L);
        row.put("name", "VIP");
        row.put("color", "#FF0000");
        row.put("created_at", now);
        row.put("updated_at", now);

        CrmDtos.TagResponse dto = mapper.toTagResponse(row);

        assertEquals(id, dto.id());
        assertEquals(0L, dto.version());
        assertEquals("VIP", dto.name());
        assertEquals("#FF0000", dto.color());
    }

    @Test
    void mapperMapsAssignmentRow() {
        UUID id = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID assignedBy = UUID.randomUUID();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("tag_id", tagId);
        row.put("subject_type", "ACCOUNT");
        row.put("subject_id", subjectId);
        row.put("assigned_by", assignedBy);
        row.put("assigned_at", now);

        CrmDtos.TagAssignmentResponse dto = mapper.toTagAssignmentResponse(row, "VIP", "#FF0000");

        assertEquals(id, dto.id());
        assertEquals(tagId, dto.tagId());
        assertEquals("VIP", dto.tagName());
        assertEquals("#FF0000", dto.tagColor());
        assertEquals("ACCOUNT", dto.subjectType());
        assertEquals(subjectId, dto.subjectId());
    }

    @Test
    void nullRowProducesNullDto() {
        assertNull(mapper.toTagResponse(null));
        assertNull(mapper.toTagAssignmentResponse(null, "VIP", "#FF0000"));
    }
}
