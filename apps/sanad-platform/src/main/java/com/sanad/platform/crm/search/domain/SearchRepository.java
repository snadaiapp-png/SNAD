package com.sanad.platform.crm.search.domain;

import java.util.List;
import java.util.UUID;

/**
 * Search repository port — cross-entity full-text search for CRM.
 * <p>
 * Searches across accounts, contacts, and leads in a single query.
 * Returns unified {@link SearchResultRecord} entries that include the
 * entity type, ID, and a display name for rendering.
 * <p>
 * Branch: feature/crm-search-export
 */
public interface SearchRepository {
    List<SearchResultRecord> search(UUID tenantId, String query, int limit);

    record SearchResultRecord(
            String entityType,
            UUID entityId,
            String displayName,
            String secondaryInfo,
            String matchedField) {}
}
