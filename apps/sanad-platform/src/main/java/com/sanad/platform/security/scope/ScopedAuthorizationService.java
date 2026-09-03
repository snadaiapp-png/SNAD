package com.sanad.platform.security.scope;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.hr.security.HrAuthorizationResourceContext;
import com.sanad.platform.hr.security.HrResourceContextResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
public class ScopedAuthorizationService {

    private final CapabilityEvaluationService capabilityEvaluationService;
    private final JdbcAccessScopeRepository scopeRepository;
    private final HrResourceContextResolver resourceContextResolver;

    public ScopedAuthorizationService(
            CapabilityEvaluationService capabilityEvaluationService,
            JdbcAccessScopeRepository scopeRepository,
            HrResourceContextResolver resourceContextResolver) {
        this.capabilityEvaluationService = Objects.requireNonNull(capabilityEvaluationService, "capabilityEvaluationService");
        this.scopeRepository = Objects.requireNonNull(scopeRepository, "scopeRepository");
        this.resourceContextResolver = Objects.requireNonNull(resourceContextResolver, "resourceContextResolver");
    }

    public ScopedAuthorizationDecision authorize(ScopedAuthorizationRequest request) {
        if (request == null || request.tenantId() == null || request.userId() == null
                || request.capabilityCode() == null || request.capabilityCode().isBlank()
                || request.resource() == null || request.authorizationTime() == null) {
            return ScopedAuthorizationDecision.deny("SCOPE_INVALID_REQUEST");
        }

        HrAuthorizationResourceContext resource = request.resource();
        if (!request.tenantId().equals(resource.tenantId())) {
            return ScopedAuthorizationDecision.deny("SCOPE_TENANT_MISMATCH");
        }

        AccessDecisionResponse coarse = capabilityEvaluationService.evaluate(
                request.tenantId(), request.userId(), request.capabilityCode(), resource.organizationId());
        if (coarse == null || !coarse.allowed()) {
            return ScopedAuthorizationDecision.deny("CAPABILITY_DENIED");
        }

        LocalDate authorizationDate = LocalDate.ofInstant(request.authorizationTime(), ZoneOffset.UTC);
        for (AccessScopeGrant grant : scopeRepository.findEffectiveGrants(
                request.tenantId(), request.userId(), coarse.matchedRoleId(),
                request.capabilityCode(), request.authorizationTime())) {
            if (!validDirectException(grant, request)) continue;
            if (matches(grant, request, authorizationDate)) {
                return ScopedAuthorizationDecision.allow(grant.scopeType());
            }
        }
        return ScopedAuthorizationDecision.deny("SCOPE_DENIED");
    }

    public void require(ScopedAuthorizationRequest request) {
        ScopedAuthorizationDecision decision = authorize(request);
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.reason());
        }
    }

    private boolean validDirectException(AccessScopeGrant grant, ScopedAuthorizationRequest request) {
        if (!grant.directException()) return true;
        return request.userId().equals(grant.userId())
                && grant.reason() != null && !grant.reason().isBlank()
                && grant.grantedBy() != null
                && grant.effectiveTo() != null
                && !grant.effectiveTo().isBefore(request.authorizationTime());
    }

    private boolean matches(AccessScopeGrant grant, ScopedAuthorizationRequest request, LocalDate authorizationDate) {
        HrAuthorizationResourceContext resource = request.resource();
        if (grant.organizationId() != null && !grant.organizationId().equals(resource.organizationId())) return false;
        if (grant.legalEntityId() != null && !grant.legalEntityId().equals(resource.legalEntityId())) return false;

        return switch (grant.scopeType()) {
            case SELF -> resourceContextResolver.isSelf(
                    request.tenantId(), request.userId(), resource.personId());
            case DIRECT_REPORTS -> resourceContextResolver.isDirectReport(
                    request.tenantId(), request.userId(), resource.employmentId(), authorizationDate);
            case REPORTING_TREE -> resourceContextResolver.isInReportingTree(
                    request.tenantId(), request.userId(), resource.employmentId(), authorizationDate);
            case ORG_UNIT -> resourceContextResolver.orgUnitContains(
                    request.tenantId(), grant.organizationId(), grant.orgUnitId(), resource.orgUnitId(), authorizationDate);
            case ORGANIZATION -> grant.organizationId() != null
                    && grant.organizationId().equals(resource.organizationId());
            case TENANT -> request.tenantId().equals(resource.tenantId());
        };
    }
}
