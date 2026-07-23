package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for assignment rules and their versions (tenant-scoped). */
public interface AssignmentRuleRepository {

    AssignmentRule save(AssignmentRule rule);

    Optional<AssignmentRule> findById(UUID tenantId, UUID ruleId);

    Optional<AssignmentRule> findByCode(UUID tenantId, String code);

    List<AssignmentRule> findByTenant(UUID tenantId, RuleStatus status);

    AssignmentRuleVersion saveVersion(AssignmentRuleVersion version);

    Optional<AssignmentRuleVersion> findVersion(UUID tenantId, UUID ruleId, int version);

    Optional<AssignmentRuleVersion> findActiveVersion(UUID tenantId, UUID ruleId);

    List<AssignmentRuleVersion> findAllVersions(UUID tenantId, UUID ruleId);

    void activateVersion(UUID tenantId, UUID ruleId, int version, UUID updatedBy);

    AssignmentRuleCounter getOrCreateCounter(UUID tenantId, UUID ruleId);

    AssignmentRuleCounter incrementCounter(UUID tenantId, UUID ruleId);
}
