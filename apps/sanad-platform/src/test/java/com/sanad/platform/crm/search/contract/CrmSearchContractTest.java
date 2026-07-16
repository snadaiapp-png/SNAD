package com.sanad.platform.crm.search.contract;

import com.sanad.platform.crm.search.domain.SearchRepository.SearchResultRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Contract test for the CRM Search bounded context.
 * <p>
 * Branch: feature/crm-search-export
 */
class CrmSearchContractTest {

    @Test
    void searchResultRecordIsAccessible() {
        UUID id = UUID.randomUUID();
        SearchResultRecord record = new SearchResultRecord(
                "ACCOUNT", id, "Acme Corp", "BUSINESS", "display_name");
        assertEquals("ACCOUNT", record.entityType());
        assertEquals(id, record.entityId());
        assertEquals("Acme Corp", record.displayName());
        assertEquals("BUSINESS", record.secondaryInfo());
        assertEquals("display_name", record.matchedField());
    }

    @Test
    void searchResultRecordHandlesNulls() {
        SearchResultRecord record = new SearchResultRecord(
                "LEAD", UUID.randomUUID(), "John Doe", null, "name");
        assertNotNull(record);
        assertEquals(null, record.secondaryInfo());
    }
}
