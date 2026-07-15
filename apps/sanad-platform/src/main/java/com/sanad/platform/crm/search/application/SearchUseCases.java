package com.sanad.platform.crm.search.application;

import com.sanad.platform.crm.search.domain.SearchRepository;
import com.sanad.platform.crm.search.domain.SearchRepository.SearchResultRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Use-case façade for cross-entity CRM search.
 * <p>
 * Branch: feature/crm-search-export
 */
@Service
public class SearchUseCases {
    private final SearchRepository repo;

    public SearchUseCases(SearchRepository repo) {
        this.repo = repo;
    }

    public List<SearchResultRecord> search(UUID tenantId, String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repo.search(tenantId, query.trim(), safeLimit);
    }
}
