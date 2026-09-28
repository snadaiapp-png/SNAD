package com.sanad.platform.access.evaluation;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.override.UserPermissionOverrideRepository;
import com.sanad.platform.access.relationship.RelationshipResolver;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CapabilityEvaluationService {

    /** Committed default: legacy facade behavior bit-for-bit (owner order §6). */
    private static final String PIPELINE_ENABLED_PROPERTY = "sanad.uac.pipeline-enabled";
    /** Committed default: SANAD_UAC_MODE=legacy — authoritative requires explicit owner opt-in. */
    private static final String MODE_PROPERTY = "sanad.uac.mode";

    private final UserRoleGrantService grantService;
    private final RoleService roleService;
    private final RoleCapabilityService roleCapabilityService;
    private final AccessCapabilityService capabilityService;
    private final OrganizationRepository organizationRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final RelationshipResolver relationshipResolver;
    private final Environment environment;

    public CapabilityEvaluationService(
            UserRoleGrantService grantService,
            RoleService roleService,
            RoleCapabilityService roleCapabilityService,
            AccessCapabilityService capabilityService,
            OrganizationRepository organizationRepository,
            UserPermissionOverrideRepository overrideRepository,
            RelationshipResolver relationshipResolver,
            Environment environment) {
        this.grantService = grantService;
        this.roleService = roleService;
        this.roleCapabilityService = roleCapabilityService;
        this.capabilityService = capabilityService;
        this.organizationRepository = organizationRepository;
        this.overrideRepository = overrideRepository;
        this.relationshipResolver = relationshipResolver;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public AccessDecisionResponse evaluate(
            UUID tenantId, UUID userId, String capabilityCode, UUID organizationId) {
        if (pipelineEnabled()) {
            // Authoritative mode (explicit owner opt-in): the legacy facade
            // delegates to the five-stage pipeline; reason strings and the
            // response shape stay binary-compatible.
            AuthorizationDecision decision =
                    evaluateDetailed(tenantId, userId, capabilityCode, organizationId);
            return new AccessDecisionResponse(tenantId, userId, organizationId,
                    decision.capability(), "ALLOW".equals(decision.decision()),
                    mapReason(decision.reason()), decision.matchedRoleId(),
                    decision.matchedRoleCode());
        }
        return evaluateLegacy(tenantId, userId, capabilityCode, organizationId);
    }

    private boolean pipelineEnabled() {
        boolean enabled = "true".equalsIgnoreCase(
                environment.getProperty(PIPELINE_ENABLED_PROPERTY, "false"));
        boolean authoritative = "authoritative".equalsIgnoreCase(
                environment.getProperty(MODE_PROPERTY, "legacy"));
        return enabled && authoritative;
    }

    /** Baseline reason strings already flow through the pipeline unchanged. */
    private static String mapReason(String reason) {
        return reason;
    }

    private AccessDecisionResponse evaluateLegacy(
            UUID tenantId, UUID userId, String capabilityCode, UUID organizationId) {
        validateOrganization(tenantId, organizationId);

        AccessCapability capability;
        try {
            capability = capabilityService.loadByCode(capabilityCode);
        } catch (AccessResourceNotFoundException exception) {
            return denied(tenantId, userId, organizationId,
                    normalize(capabilityCode), "CAPABILITY_NOT_FOUND");
        }

        if (capability.getStatus() != CapabilityStatus.ACTIVE) {
            return denied(tenantId, userId, organizationId,
                    capability.getCode(), "CAPABILITY_INACTIVE");
        }

        for (UserRoleGrant grant : grantService.activeGrants(tenantId, userId)) {
            if (!scopeMatches(grant, organizationId)) continue;
            Role role = roleService.load(tenantId, grant.getRoleId());
            if (role.getStatus() != RoleStatus.ACTIVE) continue;
            if (roleCapabilityService.roleHasCapability(
                    tenantId, role.getId(), capability.getId())) {
                return new AccessDecisionResponse(tenantId, userId, organizationId,
                        capability.getCode(), true, "ROLE_CAPABILITY_MATCH",
                        role.getId(), role.getCode());
            }
        }

        return denied(tenantId, userId, organizationId,
                capability.getCode(), "NO_MATCHING_ACTIVE_ROLE");
    }

    /**
     * Five-stage unified decision pipeline (spec §4.1 Revision D):
     * A. hard guards · B. explicit direct DENY (capability-wide) ·
     * C. candidate ALLOW sources · D. scope resolution/union · E. default DENY.
     *
     * <p>There is no PROTECTED_SAFETY source and no allow source that outranks
     * an active direct DENY. Break-glass grants are ordinary ALLOW overrides
     * evaluated by this pipeline. Commercial entitlement remains enforced by
     * the existing {@code @RequireCapability} aspect / ControlPlaneAccessService
     * layer — the pipeline extends, never duplicates, that gate.</p>
     */
    @Transactional(readOnly = true)
    public AuthorizationDecision evaluateDetailed(
            UUID tenantId, UUID userId, String capabilityCode, UUID organizationId) {
        List<String> trace = new ArrayList<>();
        UUID decisionId = UUID.randomUUID();
        String capabilityCodeNormalized = normalize(capabilityCode);

        // ---- Stage A: hard guards ----
        if (tenantId == null || userId == null) {
            return deny(decisionId, capabilityCodeNormalized, "SUBJECT_CONTEXT_REQUIRED", trace);
        }
        trace.add("STAGE_A:subject-ok");
        try {
            validateOrganization(tenantId, organizationId);
        } catch (AccessResourceNotFoundException exception) {
            return deny(decisionId, capabilityCodeNormalized, "ORGANIZATION_NOT_FOUND", trace);
        }
        trace.add("STAGE_A:tenant-boundary-ok");

        AccessCapability capability;
        try {
            capability = capabilityService.loadByCode(capabilityCode);
        } catch (AccessResourceNotFoundException exception) {
            return deny(decisionId, capabilityCodeNormalized, "CAPABILITY_NOT_FOUND", trace);
        }
        if (capability.getStatus() != CapabilityStatus.ACTIVE) {
            return deny(decisionId, capability.getCode(), "CAPABILITY_INACTIVE", trace);
        }
        trace.add("STAGE_A:capability-active:" + capability.getCode());

        // ---- Stage B: explicit direct DENY (capability-wide in v1) ----
        Instant now = Instant.now();
        List<UserPermissionOverrideRepository.ActiveOverride> activeOverrides =
                overrideRepository.findOverrides(tenantId, userId, capability.getId()).stream()
                        .filter(override -> isActive(override, now))
                        .toList();
        boolean directDeny = activeOverrides.stream()
                .anyMatch(override -> "DENY".equals(override.effect()));
        if (directDeny) {
            trace.add("STAGE_B:explicit-direct-deny-active");
            return new AuthorizationDecision("DENY", capability.getCode(),
                    DecisionSource.EXPLICIT_DENY, "EXPLICIT_DIRECT_DENY",
                    null, null, null, null, decisionId, Instant.now(), trace);
        }
        trace.add("STAGE_B:no-active-direct-deny");

        // ---- Stage C: candidate ALLOW sources ----
        List<Candidate> candidates = new ArrayList<>();
        for (UserPermissionOverrideRepository.ActiveOverride override : activeOverrides) {
            if ("ALLOW".equals(override.effect())) {
                candidates.add(new Candidate(DecisionSource.EXPLICIT_ALLOW, "EXPLICIT_ALLOW_MATCH",
                        override.scopeType(), override.scopeReference(), null, null));
            }
        }
        for (UserRoleGrant grant : grantService.activeGrants(tenantId, userId)) {
            if (!scopeMatches(grant, organizationId)) continue;
            Role role = roleService.load(tenantId, grant.getRoleId());
            if (role.getStatus() != RoleStatus.ACTIVE) continue;
            if (roleCapabilityService.roleHasCapability(
                    tenantId, role.getId(), capability.getId())) {
                candidates.add(new Candidate(DecisionSource.ROLE_GRANT, "ROLE_CAPABILITY_MATCH",
                        "TENANT_ALL", null, role.getId(), role.getCode()));
            }
        }
        relationshipResolver.resolveRelationshipGrant(tenantId, userId, capability.getId(), organizationId)
                .ifPresent(grant -> candidates.add(new Candidate(
                        DecisionSource.RELATIONSHIP_POLICY, "RELATIONSHIP_POLICY_MATCH",
                        grant.scopeType(), grant.objectId(), null, null)));
        // Delegated-administration grants: W2 scope. Deliberately absent in W1 —
        // no candidate is produced from delegation until its wave lands.
        if (candidates.isEmpty()) {
            trace.add("STAGE_C:no-candidate");
        } else {
            trace.add("STAGE_C:candidates:" + candidates.size());
        }

        // ---- Stage D: scope resolution / union ----
        List<Candidate> matching = candidates.stream()
                .filter(candidate -> scopeMatches(candidate, tenantId, userId, organizationId))
                .toList();
        for (Candidate candidate : matching) {
            trace.add("STAGE_D:match:" + candidate.source() + ":" + candidate.scopeType());
        }
        if (matching.isEmpty()) {
            trace.add("STAGE_D:no-scope-match");
        }

        // ---- Stage E: result ----
        if (!matching.isEmpty()) {
            Candidate winner = matching.get(0);
            trace.add("STAGE_E:allow");
            return new AuthorizationDecision("ALLOW", capability.getCode(),
                    winner.source(), winner.reason(),
                    winner.matchedRoleId(), winner.matchedRoleCode(),
                    winner.scopeType(), winner.policy(), decisionId, Instant.now(), trace);
        }
        return deny(decisionId, capability.getCode(), "NO_MATCHING_ACTIVE_ROLE", trace);
    }

    private void validateOrganization(UUID tenantId, UUID organizationId) {
        if (organizationId != null
                && organizationRepository.findByTenantIdAndId(tenantId, organizationId).isEmpty()) {
            throw new AccessResourceNotFoundException("Organization not found");
        }
    }

    private static boolean scopeMatches(UserRoleGrant grant, UUID organizationId) {
        if (organizationId == null) return grant.isTenantWide();
        return grant.isTenantWide() || organizationId.equals(grant.getOrganizationId());
    }

    private boolean scopeMatches(Candidate candidate, UUID tenantId, UUID userId, UUID organizationId) {
        if ("TENANT_ALL".equals(candidate.scopeType())) return true;
        if (candidate.scopeReference() != null && organizationId != null
                && candidate.scopeReference().equals(organizationId)) {
            return true;
        }
        if (candidate.scopeType() == null) return false;
        return relationshipResolver.supportsRelationshipScope(
                tenantId, userId, candidate.scopeType(), candidate.scopeReference());
    }

    private static boolean isActive(UserPermissionOverrideRepository.ActiveOverride override, Instant now) {
        boolean started = override.validFrom() == null || !override.validFrom().isAfter(now);
        boolean notExpired = override.validUntil() == null || override.validUntil().isAfter(now);
        return started && notExpired;
    }

    private static AuthorizationDecision deny(
            UUID decisionId, String capabilityCode, String reason, List<String> trace) {
        trace.add("STAGE_E:default-deny:" + reason);
        return new AuthorizationDecision("DENY", capabilityCode,
                DecisionSource.DEFAULT_DENY, reason,
                null, null, null, null, decisionId, Instant.now(), trace);
    }

    private record Candidate(
            DecisionSource source,
            String reason,
            String scopeType,
            java.util.UUID scopeReference,
            UUID matchedRoleId,
            String matchedRoleCode) {

        private String policy() {
            return null;
        }
    }

    private static AccessDecisionResponse denied(
            UUID tenantId, UUID userId, UUID organizationId, String code, String reason) {
        return new AccessDecisionResponse(tenantId, userId, organizationId,
                code, false, reason, null, null);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
