package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository {
    Issue save(Issue issue);
    Optional<Issue> findById(UUID tenantId, UUID id);
    Optional<Issue> findByCode(UUID tenantId, String code);
    List<Issue> findByTenant(UUID tenantId, int limit);
    List<Issue> findByTenantAndStatus(UUID tenantId, Issue.Status status, int limit);
    void deleteById(UUID tenantId, UUID id);
}
