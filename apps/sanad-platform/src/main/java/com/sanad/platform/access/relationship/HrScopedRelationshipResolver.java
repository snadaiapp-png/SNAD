package com.sanad.platform.access.relationship;

import com.sanad.platform.hr.security.HrResourceContextResolver;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thin adapter over the existing HR authorization predicates.
 * It never recreates HR reporting-tree or ownership logic and fails closed for
 * scope families that cannot be resolved from the supplied resource context.
 */
@Component
public class HrScopedRelationshipResolver {

    private final HrResourceContextResolver hr;

    public HrScopedRelationshipResolver(HrResourceContextResolver hr) {
        this.hr = hr;
    }

    public boolean matches(
            UUID tenantId, UUID actorUserId, String scopeType, UUID targetId,
            UUID organizationId, LocalDate authorizationDate) {
        if (tenantId == null || actorUserId == null || scopeType == null
                || authorizationDate == null) {
            return false;
        }
        return switch (scopeType) {
            case "SELF", "OWN" -> hr.isSelf(tenantId, actorUserId, targetId);
            case "DIRECT_REPORTS" ->
                    hr.isDirectReport(tenantId, actorUserId, targetId, authorizationDate);
            case "REPORTING_TREE" ->
                    hr.isInReportingTree(tenantId, actorUserId, targetId, authorizationDate);
            default -> false;
        };
    }

    public boolean orgUnitContains(
            UUID tenantId, UUID organizationId, UUID grantedOrgUnitId,
            UUID targetOrgUnitId, LocalDate authorizationDate) {
        return hr.orgUnitContains(tenantId, organizationId, grantedOrgUnitId,
                targetOrgUnitId, authorizationDate);
    }
}
