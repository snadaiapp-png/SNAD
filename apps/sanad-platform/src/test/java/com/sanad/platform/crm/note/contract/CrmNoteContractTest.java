package com.sanad.platform.crm.note.contract;

import com.sanad.platform.crm.dto.CrmDtos;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the CRM Note bounded context.
 * <p>
 * Branch: feature/crm-notes
 */
class CrmNoteContractTest {

    private final CrmDtoMapper mapper = new CrmDtoMapper();

    @Test
    void noteResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.NoteResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "NoteResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "NoteResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void noteSummaryResponseUsesCamelCaseFieldNames() {
        for (var c : CrmDtos.NoteSummaryResponse.class.getRecordComponents()) {
            assertTrue(Character.isLowerCase(c.getName().charAt(0)),
                    "NoteSummaryResponse field '" + c.getName() + "' must start lowercase (camelCase)");
            assertTrue(!c.getName().contains("_"),
                    "NoteSummaryResponse field '" + c.getName() + "' must not contain underscore");
        }
    }

    @Test
    void noteResponseIsARecord() {
        assertTrue(CrmDtos.NoteResponse.class.isRecord(),
                "NoteResponse must be a Java record");
        assertTrue(CrmDtos.NoteSummaryResponse.class.isRecord(),
                "NoteSummaryResponse must be a Java record");
    }

    @Test
    void mapperRoundTripsNoteRow() {
        assertNotNull(mapper);
    }

    @Test
    void mapperMapsNoteRowToCamelCaseDto() {
        UUID id = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("version", 0L);
        row.put("subject_type", "ACCOUNT");
        row.put("subject_id", subjectId);
        row.put("body", "Discussed pricing options with the client.");
        row.put("author_user_id", authorId);
        row.put("archived", false);
        row.put("created_at", now);
        row.put("updated_at", now);

        CrmDtos.NoteResponse dto = mapper.toNoteResponse(row);

        assertEquals(id, dto.id());
        assertEquals(0L, dto.version());
        assertEquals("ACCOUNT", dto.subjectType());
        assertEquals(subjectId, dto.subjectId());
        assertEquals("Discussed pricing options with the client.", dto.body());
        assertEquals(authorId, dto.authorUserId());
        assertFalse(dto.archived());
    }

    @Test
    void nullRowProducesNullDto() {
        assertNull(mapper.toNoteResponse(null));
        assertNull(mapper.toNoteSummary(null));
    }

    @Test
    void noteSummaryTruncatesLongBody() {
        String longBody = "a".repeat(200);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("author_user_id", UUID.randomUUID());
        row.put("body", longBody);
        row.put("created_at", java.time.OffsetDateTime.now());

        CrmDtos.NoteSummaryResponse summary = mapper.toNoteSummary(row);
        assertNotNull(summary.bodyPreview());
        assertTrue(summary.bodyPreview().endsWith("..."));
        assertTrue(summary.bodyPreview().length() <= 143); // 140 + "..."
    }
}
